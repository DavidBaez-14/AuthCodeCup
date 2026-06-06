package terminus.co.edu.ufps.identidad_validacion.ms1.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.PadronPreviewDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.SolicitarRolRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.SolicitudRolResponseDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.TokenResponseDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.AuthException;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.BadRequestException;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Cuenta;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.CuentaRol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Rol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolJugador;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.CuentaRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.CuentaRolRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.JugadorRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.AppwriteSessionVerifier;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.AppwriteUsersClient;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.JwtTokenProvider;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppwriteSessionVerifier sessionVerifier;
    private final JwtTokenProvider jwtTokenProvider;
    private final CuentaRepository cuentaRepository;
    private final CuentaRolRepository cuentaRolRepository;
    private final JugadorRepository jugadorRepository;
    private final AppwriteUsersClient appwriteUsersClient;

    @Transactional(readOnly = true)
    public TokenResponseDTO exchange(String appwriteJwt) {
        var user = sessionVerifier.verify(appwriteJwt);

        var cuenta = cuentaRepository.findByAppwriteUserId(user.id())
                .orElseThrow(() -> new AuthException("Cuenta no registrada."));

        var rolesAprobados = leerRolesAprobados(cuenta.getId());
        if (rolesAprobados.isEmpty()) {
            throw new AuthException("Cuenta sin roles aprobados.");
        }

        return emitirToken(cuenta, rolesAprobados);
    }

    @Transactional(readOnly = true)
    public PadronPreviewDTO previewPadron(String cedula) {
        var jugador = jugadorRepository.findByCedula(cedula.trim()).orElse(null);
        if (jugador == null) {
            return PadronPreviewDTO.builder().enPadron(false).build();
        }
        return PadronPreviewDTO.builder()
                .enPadron(true)
                .esEstudiante(jugador.getRolJugador() == RolJugador.ESTUDIANTE)
                .nombre(jugador.getNombre())
                .build();
    }

    @Transactional
    public SolicitudRolResponseDTO solicitarRol(SolicitarRolRequestDTO req) {
        if (req.getRol() == Rol.ADMINISTRADOR) {
            throw new AuthException("Rol no permitido.");
        }

        var user = sessionVerifier.verify(req.getAppwriteJwt());
        String cedula = req.getCedula().trim();

        var cuenta = cuentaRepository.findByAppwriteUserId(user.id())
                .orElseGet(() -> crearCuenta(user.id(), cedula, user.email(), user.name()));

        if (!cuenta.getCedula().equals(cedula)) {
            throw new BadRequestException("La cédula no coincide con la registrada para esta cuenta.");
        }

        if (cuentaRolRepository.existsByCuentaIdAndRol(cuenta.getId(), req.getRol())) {
            throw new AuthException("Ya tienes una solicitud para ese rol.");
        }

        var builder = CuentaRol.builder()
                .cuenta(cuenta)
                .rol(req.getRol())
                .fechaSolicitud(LocalDateTime.now())
                .motivoSolicitud(trimOrNull(req.getMotivoSolicitud()));

        if (req.getRol() == Rol.JUGADOR) {
            var jugadorPadron = jugadorRepository.findByCedula(cedula).orElse(null);

            if (jugadorPadron != null) {
                // Fuente de verdad = padrón. Ignoramos lo que digite el frontend
                // para evitar que el usuario altere su rol/semestre/código.
                builder.rolJugador(jugadorPadron.getRolJugador())
                        .codigoUniversitario(jugadorPadron.getCodigoUniversitario())
                        .semestre(jugadorPadron.getSemestre())
                        .estado(EstadoRol.APROBADO)
                        .fechaResolucion(LocalDateTime.now());
            } else {
                // Cédula fuera del padrón: tomamos lo auto-reportado, lo valida un admin.
                validarCamposJugador(req);
                boolean esEstudiante = req.getRolJugador() == RolJugador.ESTUDIANTE;
                builder.rolJugador(req.getRolJugador())
                        .codigoUniversitario(esEstudiante ? trimOrNull(req.getCodigoUniversitario()) : null)
                        .semestre(esEstudiante ? req.getSemestre() : null)
                        .estado(EstadoRol.PENDIENTE_VALIDACION);
            }
        } else {
            builder.estado(EstadoRol.PENDIENTE);
        }

        var cuentaRol = cuentaRolRepository.save(builder.build());

        if (cuentaRol.getEstado() == EstadoRol.APROBADO) {
            var rolesAprobados = sincronizarLabels(cuenta);
            return SolicitudRolResponseDTO.builder()
                    .estado(cuentaRol.getEstado().name())
                    .mensaje("Cuenta de jugador activa.")
                    .token(emitirToken(cuenta, rolesAprobados))
                    .build();
        }

        String mensaje = cuentaRol.getEstado() == EstadoRol.PENDIENTE_VALIDACION
                ? "Tu cédula no aparece en el padrón. Un administrador revisará tu caso."
                : "Solicitud enviada. Espera aprobación del administrador.";

        return SolicitudRolResponseDTO.builder()
                .estado(cuentaRol.getEstado().name())
                .mensaje(mensaje)
                .build();
    }

    @Transactional(readOnly = true)
    public TokenResponseDTO refresh(Jwt jwt) {
        var cuenta = cuentaRepository.findByAppwriteUserId(jwt.getSubject())
                .orElseThrow(() -> new AuthException("Cuenta no registrada."));

        var rolesAprobados = leerRolesAprobados(cuenta.getId());
        if (rolesAprobados.isEmpty()) {
            throw new AuthException("Cuenta sin roles aprobados.");
        }

        return emitirToken(cuenta, rolesAprobados);
    }

    private Cuenta crearCuenta(String appwriteUserId, String cedula, String correo, String nombre) {
        if (cuentaRepository.existsByCedula(cedula)) {
            throw new AuthException("Esa cédula ya tiene una cuenta asociada.");
        }
        return cuentaRepository.save(Cuenta.builder()
                .appwriteUserId(appwriteUserId)
                .cedula(cedula)
                .correo(correo)
                .nombre(nombre)
                .fechaCreacion(LocalDateTime.now())
                .build());
    }

    private void validarCamposJugador(SolicitarRolRequestDTO req) {
        if (req.getRolJugador() == null) {
            throw new BadRequestException("Rol del jugador es obligatorio.");
        }
        if (req.getRolJugador() == RolJugador.ESTUDIANTE) {
            if (req.getCodigoUniversitario() == null || req.getCodigoUniversitario().isBlank()) {
                throw new BadRequestException("El código estudiantil es obligatorio.");
            }
            if (req.getSemestre() == null || req.getSemestre() < 1) {
                throw new BadRequestException("El semestre actual es obligatorio.");
            }
        }
    }

    private List<String> leerRolesAprobados(UUID cuentaId) {
        return cuentaRolRepository.findByCuentaIdAndEstado(cuentaId, EstadoRol.APROBADO)
                .stream()
                .map(cr -> cr.getRol().name().toLowerCase())
                .toList();
    }

    private List<String> sincronizarLabels(Cuenta cuenta) {
        var rolesAprobados = leerRolesAprobados(cuenta.getId());
        appwriteUsersClient.setLabels(cuenta.getAppwriteUserId(), rolesAprobados);
        return rolesAprobados;
    }

    private TokenResponseDTO emitirToken(Cuenta cuenta, List<String> roles) {
        var token = jwtTokenProvider.generarToken(
                cuenta.getAppwriteUserId(),
                cuenta.getCedula(),
                cuenta.getCorreo(),
                cuenta.getNombre(),
                roles);
        return TokenResponseDTO.builder()
                .token(token)
                .expiraEn(jwtTokenProvider.getTtlSeconds())
                .roles(roles)
                .nombre(cuenta.getNombre())
                .correo(cuenta.getCorreo())
                .cedula(cuenta.getCedula())
                .build();
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        var t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
