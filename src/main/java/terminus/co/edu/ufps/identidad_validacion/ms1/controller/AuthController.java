package terminus.co.edu.ufps.identidad_validacion.ms1.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.ExchangeRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.RegistroJugadorRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.RegistroRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.RegistroResponseDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.TokenResponseDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/exchange")
    public ResponseEntity<TokenResponseDTO> exchange(@Valid @RequestBody ExchangeRequestDTO req) {
        return ResponseEntity.ok(authService.exchange(req.getAppwriteJwt()));
    }

    @PostMapping("/registrar")
    public ResponseEntity<RegistroResponseDTO> registrar(@Valid @RequestBody RegistroRequestDTO req) {
        return ResponseEntity.ok(authService.registrar(req));
    }

    @PostMapping("/registrar-jugador")
    public ResponseEntity<TokenResponseDTO> registrarJugador(@Valid @RequestBody RegistroJugadorRequestDTO req) {
        return ResponseEntity.ok(authService.registrarJugador(req));
    }

    @PostMapping("/refresh")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TokenResponseDTO> refresh(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.refresh(jwt));
    }
}

