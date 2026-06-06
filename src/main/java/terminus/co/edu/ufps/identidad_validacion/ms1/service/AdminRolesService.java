package terminus.co.edu.ufps.identidad_validacion.ms1.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CuentaRolPendienteDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.BadRequestException;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.ConflictException;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Cuenta;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.CuentaRol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Jugador;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Rol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolJugador;
import terminus.co.edu.ufps.identidad_validacion.ms1.notification.NotificacionPublisher;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.CuentaRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.CuentaRolRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.JugadorRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.AppwriteUsersClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRolesService {

    private final CuentaRepository cuentaRepository;
    private final CuentaRolRepository cuentaRolRepository;
    private final JugadorRepository jugadorRepository;
    private final AppwriteUsersClient appwriteUsersClient;
    private final NotificacionPublisher notificacionPublisher;

    @Transactional(readOnly = true)
    public List<CuentaRolPendienteDTO> listarPendientes(EstadoRol filtro) {
        List<EstadoRol> estados;
        if (filtro == null) {
            estados = List.of(EstadoRol.PENDIENTE, EstadoRol.PENDIENTE_VALIDACION);
        } else if (filtro == EstadoRol.PENDIENTE || filtro == EstadoRol.PENDIENTE_VALIDACION) {
            estados = List.of(filtro);
        } else {
            throw new BadRequestException("Filtro de estado inválido.");
        }

        return cuentaRolRepository.findByEstadoIn(estados).stream()
                .map(cr -> CuentaRolPendienteDTO.from(
                        cr,
                        jugadorRepository.findByCedula(cr.getCuenta().getCedula()).orElse(null)))
                .toList();
    }

    @Transactional
    public void aprobar(UUID cuentaRolId) {
        var cr = cuentaRolRepository.findById(cuentaRolId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada."));

        if (cr.getEstado() == EstadoRol.APROBADO) {
            return;
        }
        if (cr.getEstado() == EstadoRol.RECHAZADO) {
            throw new BadRequestException("La solicitud ya fue rechazada.");
        }

        // Validación manual de un jugador fuera del padrón:
        // al aprobar también lo agregamos al padrón para mantener una sola fuente de verdad.
        if (cr.getEstado() == EstadoRol.PENDIENTE_VALIDACION && cr.getRol() == Rol.JUGADOR) {
            insertarEnPadronSiHaceFalta(cr);
        }

        cr.setEstado(EstadoRol.APROBADO);
        cr.setFechaResolucion(LocalDateTime.now());
        cuentaRolRepository.save(cr);

        sincronizarLabels(cr);
    }

    @Transactional
    public void rechazar(UUID cuentaRolId, String motivo) {
        var cr = cuentaRolRepository.findById(cuentaRolId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada."));

        if (cr.getEstado() != EstadoRol.PENDIENTE && cr.getEstado() != EstadoRol.PENDIENTE_VALIDACION) {
            throw new BadRequestException("La solicitud ya fue resuelta.");
        }

        var cuenta = cr.getCuenta();
        String cedulaSnap = cuenta.getCedula();
        String correoSnap = cuenta.getCorreo();
        String nombreSnap = cuenta.getNombre();
        String appwriteUserId = cuenta.getAppwriteUserId();
        String rolSnap = cr.getRol().name();

        cuentaRolRepository.delete(cr);

        // Si la cuenta no conserva ningún otro rol vivo (aprobado o pendiente), se va completa.
        // Asi liberamos cedula/correo/appwriteUserId para que la persona pueda volver a registrarse.
        var restantes = cuentaRolRepository.findByCuentaId(cuenta.getId());
        boolean conservaCuenta = restantes.stream().anyMatch(r ->
                r.getEstado() == EstadoRol.APROBADO
                        || r.getEstado() == EstadoRol.PENDIENTE
                        || r.getEstado() == EstadoRol.PENDIENTE_VALIDACION);

        if (conservaCuenta) {
            sincronizarLabelsPorCuenta(cuenta);
        } else {
            cuentaRolRepository.deleteAll(restantes);
            cuentaRepository.delete(cuenta);
            appwriteUsersClient.eliminarUsuario(appwriteUserId);
        }

        log.info("[AUDIT][SOLICITUD_RECHAZADA] cedula={} rol={} motivo={} cuentaEliminada={}",
                cedulaSnap, rolSnap, motivo, !conservaCuenta);

        notificacionPublisher.notificarRechazoSolicitud(
                cedulaSnap, correoSnap, nombreSnap, rolSnap, motivo, !conservaCuenta);
    }

    @Transactional
    public void asignarRol(String cedula, Rol rol, String motivo, String callerAppwriteUserId) {
        if (rol == Rol.JUGADOR) {
            throw new BadRequestException(
                    "El rol JUGADOR se gestiona por padron (CSV) y solicitud del usuario, no por asignacion directa.");
        }

        var cuenta = cuentaRepository.findByCedula(cedula.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe una cuenta registrada con esa cedula."));

        var existente = cuentaRolRepository.findByCuentaIdAndRol(cuenta.getId(), rol);
        if (existente.isPresent() && existente.get().getEstado() == EstadoRol.APROBADO) {
            throw new ConflictException("La cuenta ya tiene el rol " + rol.name() + " aprobado.");
        }

        if (existente.isPresent()) {
            var cr = existente.get();
            cr.setEstado(EstadoRol.APROBADO);
            cr.setFechaResolucion(LocalDateTime.now());
            cr.setMotivoRechazo(null);
            cr.setMotivoSolicitud(motivo);
            cuentaRolRepository.save(cr);
        } else {
            cuentaRolRepository.save(CuentaRol.builder()
                    .cuenta(cuenta)
                    .rol(rol)
                    .estado(EstadoRol.APROBADO)
                    .fechaSolicitud(LocalDateTime.now())
                    .fechaResolucion(LocalDateTime.now())
                    .motivoSolicitud(motivo)
                    .build());
        }

        sincronizarLabelsPorCuenta(cuenta);

        log.info("[AUDIT][ROL_ASIGNADO] caller={} cedula={} rol={} motivo={}",
                callerAppwriteUserId, cuenta.getCedula(), rol.name(), motivo);

        notificacionPublisher.notificarAsignacionRol(
                cuenta.getCedula(), cuenta.getCorreo(), cuenta.getNombre(), rol.name());
    }

    @Transactional
    public void revocarRol(String cedula, Rol rol, String motivo, String callerAppwriteUserId) {
        var cuenta = cuentaRepository.findByCedula(cedula.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe una cuenta registrada con esa cedula."));

        var cr = cuentaRolRepository.findByCuentaIdAndRol(cuenta.getId(), rol)
                .filter(x -> x.getEstado() == EstadoRol.APROBADO)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La cuenta no tiene el rol " + rol.name() + " aprobado."));

        if (rol == Rol.ADMINISTRADOR) {
            if (cuenta.getAppwriteUserId().equals(callerAppwriteUserId)) {
                throw new BadRequestException(
                        "No puedes revocarte tu propio rol de administrador. Pide a otro administrador que lo haga.");
            }
            long admins = cuentaRolRepository.countByRolAndEstado(Rol.ADMINISTRADOR, EstadoRol.APROBADO);
            if (admins <= 1) {
                throw new ConflictException(
                        "No se puede revocar al unico administrador del sistema.");
            }
        }

        cuentaRolRepository.delete(cr);
        sincronizarLabelsPorCuenta(cuenta);

        log.info("[AUDIT][ROL_REVOCADO] caller={} cedula={} rol={} motivo={}",
                callerAppwriteUserId, cuenta.getCedula(), rol.name(), motivo);

        notificacionPublisher.notificarRevocacionRol(
                cuenta.getCedula(), cuenta.getCorreo(), cuenta.getNombre(), rol.name(), motivo);
    }

    @Transactional
    public void eliminarCuenta(String cedula, String motivo, String callerAppwriteUserId) {
        var cuenta = cuentaRepository.findByCedula(cedula.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe una cuenta registrada con esa cedula."));

        if (cuenta.getAppwriteUserId().equals(callerAppwriteUserId)) {
            throw new BadRequestException(
                    "No puedes eliminar tu propia cuenta. Pide a otro administrador que lo haga.");
        }

        boolean esAdmin = cuentaRolRepository
                .findByCuentaIdAndRol(cuenta.getId(), Rol.ADMINISTRADOR)
                .filter(cr -> cr.getEstado() == EstadoRol.APROBADO)
                .isPresent();
        if (esAdmin) {
            long admins = cuentaRolRepository.countByRolAndEstado(Rol.ADMINISTRADOR, EstadoRol.APROBADO);
            if (admins <= 1) {
                throw new ConflictException(
                        "No se puede eliminar al unico administrador del sistema.");
            }
        }

        // Snapshot para el evento, antes de borrar.
        String cedulaSnap = cuenta.getCedula();
        String correoSnap = cuenta.getCorreo();
        String nombreSnap = cuenta.getNombre();
        String appwriteUserId = cuenta.getAppwriteUserId();

        cuentaRolRepository.deleteAll(cuentaRolRepository.findByCuentaId(cuenta.getId()));
        cuentaRepository.delete(cuenta);
        appwriteUsersClient.eliminarUsuario(appwriteUserId);

        log.info("[AUDIT][CUENTA_ELIMINADA] caller={} cedula={} motivo={}",
                callerAppwriteUserId, cedulaSnap, motivo);

        notificacionPublisher.notificarEliminacionCuenta(cedulaSnap, correoSnap, nombreSnap, motivo);
    }

    private void insertarEnPadronSiHaceFalta(CuentaRol cr) {
        var cuenta = cr.getCuenta();
        if (jugadorRepository.findByCedula(cuenta.getCedula()).isPresent()) {
            return;
        }
        if (cr.getRolJugador() == null) {
            throw new BadRequestException(
                    "La solicitud no incluye el rol del jugador para crear su entrada en el padrón.");
        }

        boolean esEstudiante = cr.getRolJugador() == RolJugador.ESTUDIANTE;
        var jugador = Jugador.builder()
                .cedula(cuenta.getCedula())
                .nombre(cuenta.getNombre() != null ? cuenta.getNombre() : "")
                .codigoUniversitario(esEstudiante ? cr.getCodigoUniversitario() : null)
                .rolJugador(cr.getRolJugador())
                .semestre(esEstudiante ? cr.getSemestre() : null)
                .activo(true)
                .fechaActualizacion(LocalDateTime.now())
                .build();
        jugadorRepository.save(jugador);
    }

    private void sincronizarLabels(CuentaRol cr) {
        sincronizarLabelsPorCuenta(cr.getCuenta());
    }

    private void sincronizarLabelsPorCuenta(Cuenta cuenta) {
        var rolesAprobados = cuentaRolRepository
                .findByCuentaIdAndEstado(cuenta.getId(), EstadoRol.APROBADO)
                .stream()
                .map(c -> c.getRol().name().toLowerCase())
                .toList();
        appwriteUsersClient.setLabels(cuenta.getAppwriteUserId(), rolesAprobados);
    }
}
