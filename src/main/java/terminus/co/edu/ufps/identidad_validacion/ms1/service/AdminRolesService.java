package terminus.co.edu.ufps.identidad_validacion.ms1.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CuentaRolPendienteDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.BadRequestException;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.CuentaRol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Jugador;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Rol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolJugador;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.CuentaRolRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.JugadorRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.AppwriteUsersClient;

@Service
@RequiredArgsConstructor
public class AdminRolesService {

    private final CuentaRolRepository cuentaRolRepository;
    private final JugadorRepository jugadorRepository;
    private final AppwriteUsersClient appwriteUsersClient;

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

        cr.setEstado(EstadoRol.RECHAZADO);
        cr.setMotivoRechazo(motivo);
        cr.setFechaResolucion(LocalDateTime.now());
        cuentaRolRepository.save(cr);

        // Re-sync labels: la cuenta puede tener otros roles aprobados que deben preservarse.
        sincronizarLabels(cr);
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
        var cuenta = cr.getCuenta();
        var rolesAprobados = cuentaRolRepository
                .findByCuentaIdAndEstado(cuenta.getId(), EstadoRol.APROBADO)
                .stream()
                .map(c -> c.getRol().name().toLowerCase())
                .toList();
        appwriteUsersClient.setLabels(cuenta.getAppwriteUserId(), rolesAprobados);
    }
}
