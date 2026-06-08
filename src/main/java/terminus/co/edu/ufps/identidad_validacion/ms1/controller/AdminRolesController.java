package terminus.co.edu.ufps.identidad_validacion.ms1.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.AsignarRolRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CuentaRolPendienteDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.EliminarCuentaRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.RechazoRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.RevocarRolRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRol;
import terminus.co.edu.ufps.identidad_validacion.ms1.service.AdminRolesService;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdminRolesController {

    private final AdminRolesService adminRolesService;

    @GetMapping("/pendientes")
    public ResponseEntity<List<CuentaRolPendienteDTO>> listarPendientes(
            @RequestParam(required = false) EstadoRol estado) {
        return ResponseEntity.ok(adminRolesService.listarPendientes(estado));
    }

    @PostMapping("/{cuentaRolId}/aprobar")
    public ResponseEntity<Void> aprobar(@PathVariable UUID cuentaRolId) {
        adminRolesService.aprobar(cuentaRolId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cuenta/{cuentaId}/aprobar-todos")
    public ResponseEntity<Void> aprobarTodos(@PathVariable UUID cuentaId) {
        adminRolesService.aprobarTodos(cuentaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cuenta/{cuentaId}/rechazar-todos")
    public ResponseEntity<Void> rechazarTodos(
            @PathVariable UUID cuentaId,
            @RequestBody RechazoRequestDTO req) {
        adminRolesService.rechazarTodos(cuentaId, req.getMotivo());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{cuentaRolId}/rechazar")
    public ResponseEntity<Void> rechazar(@PathVariable UUID cuentaRolId, @RequestBody RechazoRequestDTO req) {
        adminRolesService.rechazar(cuentaRolId, req.getMotivo());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/asignar")
    public ResponseEntity<Void> asignarRol(
            @Valid @RequestBody AsignarRolRequestDTO req,
            @AuthenticationPrincipal Jwt jwt) {
        adminRolesService.asignarRol(req.getCedula(), req.getRol(), req.getMotivo(), jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/revocar")
    public ResponseEntity<Void> revocarRol(
            @Valid @RequestBody RevocarRolRequestDTO req,
            @AuthenticationPrincipal Jwt jwt) {
        adminRolesService.revocarRol(req.getCedula(), req.getRol(), req.getMotivo(), jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cuenta/{cedula}")
    public ResponseEntity<Void> eliminarCuenta(
            @PathVariable String cedula,
            @Valid @RequestBody EliminarCuentaRequestDTO req,
            @AuthenticationPrincipal Jwt jwt) {
        adminRolesService.eliminarCuenta(cedula, req.getMotivo(), jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
