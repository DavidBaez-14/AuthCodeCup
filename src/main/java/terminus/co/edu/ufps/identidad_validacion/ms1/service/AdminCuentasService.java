package terminus.co.edu.ufps.identidad_validacion.ms1.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CrearCuentaRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CrearCuentaResponseDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CuentaAdminDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.RolEstadoDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.BadRequestException;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.ConflictException;
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
import terminus.co.edu.ufps.identidad_validacion.ms1.security.PasswordGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCuentasService {

    private final CuentaRepository cuentaRepository;
    private final CuentaRolRepository cuentaRolRepository;
    private final JugadorRepository jugadorRepository;
    private final AppwriteUsersClient appwriteUsersClient;
    private final PasswordGenerator passwordGenerator;
    private final NotificacionPublisher notificacionPublisher;

    @Transactional(readOnly = true)
    public Page<CuentaAdminDTO> listar(String q, Rol rol, int page, int size) {
        String qNormalizado = (q == null || q.isBlank()) ? null : q.trim();
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fechaCreacion"));
        return cuentaRepository.buscar(qNormalizado, rol, pageable).map(this::toDto);
    }

    @Transactional
    public CrearCuentaResponseDTO crear(CrearCuentaRequestDTO req, String callerAppwriteUserId) {
        if (req.getRolInicial() == Rol.JUGADOR) {
            throw new BadRequestException(
                    "El rol JUGADOR se gestiona por padron (CSV) y solicitud del usuario, no por creacion manual.");
        }

        String cedula = req.getCedula().trim();
        String correo = req.getCorreo().trim();
        String nombre = req.getNombre().trim();

        if (cuentaRepository.existsByCedula(cedula)) {
            throw new ConflictException("Ya existe una cuenta con esa cedula.");
        }
        if (cuentaRepository.existsByCorreo(correo)) {
            throw new ConflictException("Ya existe una cuenta con ese correo.");
        }

        // Regla: todo DELEGADO tambien es JUGADOR. Validamos los datos
        // academicos arriba para fallar antes de tocar Appwrite/BD.
        boolean delegadoConJugador = req.getRolInicial() == Rol.DELEGADO;
        if (delegadoConJugador) {
            validarCamposJugadorDelegado(req);
        }

        String passwordTemporal = passwordGenerator.generar();
        String appwriteUserId = appwriteUsersClient.crearUsuario(correo, passwordTemporal, nombre);
        List<String> labels = delegadoConJugador
                ? List.of(Rol.DELEGADO.name().toLowerCase(), Rol.JUGADOR.name().toLowerCase())
                : List.of(req.getRolInicial().name().toLowerCase());
        appwriteUsersClient.setLabels(appwriteUserId, labels);

        var cuenta = cuentaRepository.save(Cuenta.builder()
                .appwriteUserId(appwriteUserId)
                .cedula(cedula)
                .correo(correo)
                .nombre(nombre)
                .fechaCreacion(LocalDateTime.now())
                .build());

        // Rol principal (DELEGADO o ARBITRO).
        cuentaRolRepository.save(CuentaRol.builder()
                .cuenta(cuenta)
                .rol(req.getRolInicial())
                .estado(EstadoRol.APROBADO)
                .fechaSolicitud(LocalDateTime.now())
                .fechaResolucion(LocalDateTime.now())
                .motivoSolicitud(req.getMotivo())
                .build());

        // Si es DELEGADO: tambien lo metemos al padron (si no existe) y le
        // creamos el CuentaRol JUGADOR aprobado con los datos academicos
        // que digito el admin. Asi queda totalmente operativo (puede crear
        // equipo en MS2 inmediatamente porque MS1 ya lo reconoce en padron).
        if (delegadoConJugador) {
            insertarEnPadronSiNoExiste(cedula, nombre, req);
            cuentaRolRepository.save(CuentaRol.builder()
                    .cuenta(cuenta)
                    .rol(Rol.JUGADOR)
                    .estado(EstadoRol.APROBADO)
                    .fechaSolicitud(LocalDateTime.now())
                    .fechaResolucion(LocalDateTime.now())
                    .rolJugador(req.getRolJugador())
                    .codigoUniversitario(req.getRolJugador() == RolJugador.ESTUDIANTE
                            ? trimOrNull(req.getCodigoUniversitario()) : null)
                    .semestre(req.getRolJugador() == RolJugador.ESTUDIANTE
                            ? req.getSemestre() : null)
                    .motivoSolicitud(req.getMotivo())
                    .build());
        }

        log.info("[AUDIT][CUENTA_CREADA] caller={} cedula={} correo={} rolInicial={} delegadoConJugador={} motivo={}",
                callerAppwriteUserId, cedula, correo, req.getRolInicial().name(), delegadoConJugador, req.getMotivo());

        notificacionPublisher.notificarCreacionCuenta(
                cedula, correo, nombre, req.getRolInicial().name());

        return CrearCuentaResponseDTO.builder()
                .cuentaId(cuenta.getId())
                .cedula(cedula)
                .correo(correo)
                .passwordTemporal(passwordTemporal)
                .mensaje("Cuenta creada. Comparte la contrasena temporal con el usuario; no se mostrara de nuevo.")
                .build();
    }

    private void validarCamposJugadorDelegado(CrearCuentaRequestDTO req) {
        if (req.getRolJugador() == null) {
            throw new BadRequestException(
                    "El rol del jugador es obligatorio cuando se crea un DELEGADO (todo delegado tambien es jugador).");
        }
        if (req.getRolJugador() == RolJugador.ESTUDIANTE) {
            if (req.getCodigoUniversitario() == null || req.getCodigoUniversitario().isBlank()) {
                throw new BadRequestException("El codigo estudiantil es obligatorio para un estudiante.");
            }
            if (req.getSemestre() == null || req.getSemestre() < 1) {
                throw new BadRequestException("El semestre actual es obligatorio para un estudiante.");
            }
        }
    }

    private void insertarEnPadronSiNoExiste(String cedula, String nombre, CrearCuentaRequestDTO req) {
        if (jugadorRepository.findByCedula(cedula).isPresent()) {
            return;
        }
        boolean esEstudiante = req.getRolJugador() == RolJugador.ESTUDIANTE;
        jugadorRepository.save(Jugador.builder()
                .cedula(cedula)
                .nombre(nombre)
                .codigoUniversitario(esEstudiante ? trimOrNull(req.getCodigoUniversitario()) : null)
                .rolJugador(req.getRolJugador())
                .semestre(esEstudiante ? req.getSemestre() : null)
                .activo(true)
                .fechaActualizacion(LocalDateTime.now())
                .build());
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        var t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private CuentaAdminDTO toDto(Cuenta cuenta) {
        var roles = cuentaRolRepository.findByCuentaId(cuenta.getId()).stream()
                .filter(cr -> cr.getEstado() != EstadoRol.RECHAZADO)
                .map(cr -> RolEstadoDTO.builder()
                        .id(cr.getId())
                        .rol(cr.getRol().name())
                        .estado(cr.getEstado().name())
                        .fechaSolicitud(cr.getFechaSolicitud())
                        .fechaResolucion(cr.getFechaResolucion())
                        .build())
                .toList();

        return CuentaAdminDTO.builder()
                .id(cuenta.getId())
                .cedula(cuenta.getCedula())
                .nombre(cuenta.getNombre())
                .correo(cuenta.getCorreo())
                .fechaCreacion(cuenta.getFechaCreacion())
                .roles(roles)
                .build();
    }
}
