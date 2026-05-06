package terminus.co.edu.ufps.identidad_validacion.ms1.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.RegistroPendienteDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.RechazoRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.service.RegistroService;

@RestController
@RequestMapping("/api/admin/registros")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdminRegistrosController {

    private final RegistroService registroService;

    @GetMapping("/pendientes")
    public ResponseEntity<List<RegistroPendienteDTO>> listarPendientes() {
        return ResponseEntity.ok(registroService.listarPendientes());
    }

    @PostMapping("/{perfilId}/aprobar")
    public ResponseEntity<Void> aprobar(@PathVariable String perfilId) {
        registroService.aprobar(perfilId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{perfilId}/rechazar")
    public ResponseEntity<Void> rechazar(@PathVariable String perfilId, @RequestBody RechazoRequestDTO req) {
        registroService.rechazar(perfilId, req.getMotivo());
        return ResponseEntity.noContent().build();
    }
}
