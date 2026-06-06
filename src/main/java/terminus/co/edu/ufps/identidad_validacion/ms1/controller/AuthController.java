package terminus.co.edu.ufps.identidad_validacion.ms1.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.ExchangeRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.PadronPreviewDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.SolicitarRolRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.SolicitudRolResponseDTO;
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

    @PostMapping("/solicitar-rol")
    public ResponseEntity<SolicitudRolResponseDTO> solicitarRol(@Valid @RequestBody SolicitarRolRequestDTO req) {
        return ResponseEntity.ok(authService.solicitarRol(req));
    }

    @GetMapping("/padron-preview/{cedula}")
    public ResponseEntity<PadronPreviewDTO> padronPreview(@PathVariable String cedula) {
        return ResponseEntity.ok(authService.previewPadron(cedula));
    }

    @PostMapping("/refresh")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TokenResponseDTO> refresh(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.refresh(jwt));
    }
}
