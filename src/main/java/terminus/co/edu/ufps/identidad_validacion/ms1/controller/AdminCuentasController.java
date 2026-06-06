package terminus.co.edu.ufps.identidad_validacion.ms1.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CrearCuentaRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CrearCuentaResponseDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CuentaAdminDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Rol;
import terminus.co.edu.ufps.identidad_validacion.ms1.service.AdminCuentasService;

@RestController
@RequestMapping("/api/admin/cuentas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdminCuentasController {

    private final AdminCuentasService adminCuentasService;

    @GetMapping
    public ResponseEntity<Page<CuentaAdminDTO>> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Rol rol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminCuentasService.listar(q, rol, page, size));
    }

    @PostMapping
    public ResponseEntity<CrearCuentaResponseDTO> crear(
            @Valid @RequestBody CrearCuentaRequestDTO req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(adminCuentasService.crear(req, jwt.getSubject()));
    }
}
