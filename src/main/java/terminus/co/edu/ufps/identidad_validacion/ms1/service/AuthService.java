package terminus.co.edu.ufps.identidad_validacion.ms1.service;

import terminus.co.edu.ufps.identidad_validacion.ms1.dto.LoginRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.LoginResponseDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.AuthException;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.CuentaBloqueadaException;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.ProveedorAuth;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Usuario;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.UsuarioRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.JwtTokenProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponseDTO login(LoginRequestDTO request) {
        Usuario usuario = usuarioRepository.findByCorreo(request.getCorreo())
                .orElseThrow(() -> new AuthException("Credenciales incorrectas."));

        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw new org.springframework.security.access.AccessDeniedException("Cuenta desactivada.");
        }

        if (usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(LocalDateTime.now())) {
            long minutos = Math.max(1, Duration.between(LocalDateTime.now(), usuario.getBloqueadoHasta()).toMinutes());
            throw new CuentaBloqueadaException("Cuenta bloqueada. Intenta en " + minutos + " minutos.");
        }

        if (usuario.getProveedorAuth() == ProveedorAuth.GOOGLE) {
            throw new AuthException("Esta cuenta usa acceso con Google.");
        }

        if (usuario.getContrasena() == null || !passwordEncoder.matches(request.getContrasena(), usuario.getContrasena())) {
            int intentos = usuario.getIntentosFallidos() == null ? 0 : usuario.getIntentosFallidos();
            intentos++;
            usuario.setIntentosFallidos(intentos);
            if (intentos >= 5) {
                usuario.setBloqueadoHasta(LocalDateTime.now().plusMinutes(15));
            }
            usuarioRepository.save(usuario);
            throw new AuthException("Credenciales incorrectas.");
        }

        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuario.setFechaUltimoAcceso(LocalDateTime.now());
        usuarioRepository.save(usuario);

        String token = jwtTokenProvider.generarToken(usuario.getCorreo(), usuario.getRolSistema().name(), usuario.getNombre());
        return LoginResponseDTO.builder()
                .token(token)
                .tipo("Bearer")
                .expiraEn(jwtTokenProvider.getExpirationSeconds())
                .rol(usuario.getRolSistema())
                .nombre(usuario.getNombre())
                .correo(usuario.getCorreo())
                .build();
    }

    public LoginResponseDTO loginGooglePorCorreo(String correo) {
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("No tienes una cuenta registrada en el sistema."));

        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw new org.springframework.security.access.AccessDeniedException("Cuenta desactivada.");
        }

        usuario.setFechaUltimoAcceso(LocalDateTime.now());
        usuarioRepository.save(usuario);

        String token = jwtTokenProvider.generarToken(usuario.getCorreo(), usuario.getRolSistema().name(), usuario.getNombre());
        return LoginResponseDTO.builder()
                .token(token)
                .tipo("Bearer")
                .expiraEn(jwtTokenProvider.getExpirationSeconds())
                .rol(usuario.getRolSistema())
                .nombre(usuario.getNombre())
                .correo(usuario.getCorreo())
                .build();
    }

    public void registrarEventoRecuperacion(String correo) {
        log.info("Evento recuperar contraseÃ±a recibido para correo={}", correo);
    }
}

