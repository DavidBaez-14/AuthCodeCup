package terminus.co.edu.ufps.identidad_validacion.ms1.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.RegistroJugadorRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.RegistroRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.RegistroResponseDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.TokenResponseDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.AuthException;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.BadRequestException;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRegistro;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.JugadorAuth;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Perfil;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolJugador;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolSolicitado;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.JugadorAuthRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.PerfilRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.AppwriteSessionVerifier;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.AppwriteUsersClient;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.JwtTokenProvider;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppwriteSessionVerifier sessionVerifier;
    private final JwtTokenProvider jwtTokenProvider;
    private final PerfilRepository perfilRepository;
    private final JugadorAuthRepository jugadorAuthRepository;
    private final AppwriteUsersClient appwriteUsersClient;

    @Transactional(readOnly = true)
    public TokenResponseDTO exchange(String appwriteJwt) {
        var user = sessionVerifier.verify(appwriteJwt);
        var rolesValidos = user.labels().stream()
                .filter(l -> List.of("administrador", "arbitro", "delegado", "jugador").contains(l))
                .toList();

        if (rolesValidos.isEmpty()) {
            throw new AuthException("Usuario sin rol asignado.");
        }

        JugadorAuth jugadorAuth = null;
        if (rolesValidos.contains("jugador")) {
            jugadorAuth = jugadorAuthRepository.findByAppwriteUserId(user.id()).orElse(null);
        }

        String cedula;
        if (jugadorAuth != null) {
            cedula = jugadorAuth.getCedula();
        } else {
            var perfil = perfilRepository.findByAppwriteUserId(user.id())
                    .orElseThrow(() -> new AuthException("Perfil no registrado."));

            if (perfil.getEstado() != EstadoRegistro.APROBADO) {
                throw new AuthException("Perfil pendiente de aprobacion.");
            }
            cedula = perfil.getCedula();
        }

        var token = jwtTokenProvider.generarToken(
                user.id(),
                cedula,
                user.email(),
                user.name(),
                rolesValidos);

        return TokenResponseDTO.builder()
                .token(token)
                .expiraEn(jwtTokenProvider.getTtlSeconds())
                .roles(rolesValidos)
                .nombre(user.name())
                .correo(user.email())
            .cedula(cedula)
                .build();
    }

    @Transactional
    public RegistroResponseDTO registrar(RegistroRequestDTO req) {
        var user = sessionVerifier.verify(req.getAppwriteJwt());

        if (req.getRolSolicitado() == RolSolicitado.ADMINISTRADOR) {
            throw new AuthException("Rol no permitido.");
        }

        if (perfilRepository.findByAppwriteUserId(user.id()).isPresent()) {
            throw new AuthException("Solicitud ya registrada.");
        }

        Perfil perfil = Perfil.builder()
                .appwriteUserId(user.id())
                .cedula(req.getCedula().trim())
                .rolSolicitado(req.getRolSolicitado())
                .estado(EstadoRegistro.PENDIENTE)
                .fechaSolicitud(LocalDateTime.now())
                .correo(user.email())
                .nombre(user.name())
                .build();
        perfilRepository.save(perfil);

        return RegistroResponseDTO.builder()
                .estado("PENDIENTE")
                .mensaje("Solicitud enviada. Espera aprobacion del admin.")
                .build();
    }

    @Transactional
    public TokenResponseDTO registrarJugador(RegistroJugadorRequestDTO req) {
        var user = sessionVerifier.verify(req.getAppwriteJwt());

        if (jugadorAuthRepository.existsByAppwriteUserId(user.id())) {
            throw new AuthException("Perfil ya registrado.");
        }

        String cedula = req.getCedula().trim();
        if (jugadorAuthRepository.existsByCedula(cedula)) {
            throw new AuthException("Cedula ya registrada.");
        }

        String codigoUniversitario = req.getCodigoUniversitario();
        Integer semestre = req.getSemestre();
        if (req.getRolJugador() == null) {
            throw new BadRequestException("Rol del jugador es obligatorio.");
        }
        if (req.getRolJugador() == RolJugador.ESTUDIANTE) {
            if (codigoUniversitario == null || codigoUniversitario.isBlank()) {
                throw new BadRequestException("El codigo estudiantil es obligatorio.");
            }
            if (semestre == null || semestre < 1) {
                throw new BadRequestException("El semestre actual es obligatorio.");
            }
        } else {
            codigoUniversitario = null;
            semestre = null;
        }

        JugadorAuth jugadorAuth = JugadorAuth.builder()
                .appwriteUserId(user.id())
                .cedula(cedula)
                .rolJugador(req.getRolJugador())
                .codigoUniversitario(codigoUniversitario == null ? null : codigoUniversitario.trim())
                .semestre(semestre)
                .nombre(user.name())
                .correo(user.email())
                .fechaRegistro(LocalDateTime.now())
                .build();
        jugadorAuthRepository.save(jugadorAuth);

        appwriteUsersClient.asignarLabels(user.id(), List.of("jugador"));

        var roles = List.of("jugador");
        var token = jwtTokenProvider.generarToken(
                user.id(),
                cedula,
                user.email(),
                user.name(),
                roles);

        return TokenResponseDTO.builder()
                .token(token)
                .expiraEn(jwtTokenProvider.getTtlSeconds())
                .roles(roles)
                .nombre(user.name())
                .correo(user.email())
                .cedula(cedula)
                .build();
    }

    public TokenResponseDTO refresh(Jwt jwt) {
        var userId = jwt.getSubject();
        var cedula = jwt.getClaimAsString("cedula");
        var email = jwt.getClaimAsString("email");
        var nombre = jwt.getClaimAsString("nombre");
        var roles = jwt.getClaimAsStringList("roles");

        var token = jwtTokenProvider.generarToken(userId, cedula, email, nombre, roles);
        return TokenResponseDTO.builder()
                .token(token)
                .expiraEn(jwtTokenProvider.getTtlSeconds())
                .roles(roles)
                .nombre(nombre)
                .correo(email)
                .cedula(cedula)
                .build();
    }
}

