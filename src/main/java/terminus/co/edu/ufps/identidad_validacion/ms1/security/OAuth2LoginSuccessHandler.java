package terminus.co.edu.ufps.identidad_validacion.ms1.security;

import terminus.co.edu.ufps.identidad_validacion.ms1.dto.LoginResponseDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        String correo = user.getAttribute("email");
        try {
            LoginResponseDTO payload = authService.loginGooglePorCorreo(correo);
            String redirect = frontendUrl + "/auth/google/callback"
                    + "?token=" + encode(payload.getToken())
                    + "&rol=" + encode(payload.getRol().name())
                    + "&nombre=" + encode(payload.getNombre())
                    + "&correo=" + encode(payload.getCorreo())
                    + "&debeCambiarContrasena=" + encode(String.valueOf(payload.getDebeCambiarContrasena()));
            response.sendRedirect(redirect);
        } catch (Exception ex) {
            String redirect = frontendUrl + "/login?oauthError=" + encode(ex.getMessage() == null
                    ? "No fue posible autenticar con Google."
                    : ex.getMessage());
            response.sendRedirect(redirect);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}

