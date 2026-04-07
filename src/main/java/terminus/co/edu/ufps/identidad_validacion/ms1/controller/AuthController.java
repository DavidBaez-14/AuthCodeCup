package terminus.co.edu.ufps.identidad_validacion.ms1.controller;

import terminus.co.edu.ufps.identidad_validacion.ms1.dto.LoginRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.LoginResponseDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.RecuperarContrasenaRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "AutenticaciÃ³n", description = "Login local y OAuth2 con Google")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login local", description = "Autentica por correo y contraseÃ±a")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/google")
    @Operation(summary = "Inicio OAuth2 Google", description = "Redirige al flujo OAuth2 de Google")
    public ResponseEntity<Void> google() {
        return ResponseEntity.status(302).header("Location", "/oauth2/authorization/google").build();
    }

    @PostMapping("/recuperar-contrasena")
    @Operation(summary = "Recuperar contrasena", description = "Registra evento para notificaciones externas")
    public ResponseEntity<Map<String, String>> recuperar(@Valid @RequestBody RecuperarContrasenaRequestDTO request) {
        authService.registrarEventoRecuperacion(request.getCorreo());
        return ResponseEntity.ok(Map.of("mensaje", "Si el correo existe, se procesarÃ¡ la recuperaciÃ³n."));
    }
}

