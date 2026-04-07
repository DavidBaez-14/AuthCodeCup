package terminus.co.edu.ufps.identidad_validacion.ms1.controller;

import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CambiarEstadoRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CrearUsuarioRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CrearUsuarioResponseDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.UsuarioDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolSistema;
import terminus.co.edu.ufps.identidad_validacion.ms1.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "GestiÃ³n de cuentas de administrador, Ã¡rbitro y delegado")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping
    @Operation(summary = "Crear usuario", description = "Crea una cuenta con rol del sistema")
    public ResponseEntity<CrearUsuarioResponseDTO> crear(@Valid @RequestBody CrearUsuarioRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(request));
    }

    @GetMapping
    @Operation(summary = "Listar usuarios", description = "Lista usuarios con filtros opcionales")
    public ResponseEntity<Page<UsuarioDTO>> listar(
            @RequestParam(required = false) RolSistema rol_sistema,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(usuarioService.listar(rol_sistema, activo, buscar, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar usuario por id", description = "Retorna la cuenta por identificador")
    public ResponseEntity<UsuarioDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.buscarPorId(id));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Cambiar estado", description = "Activa o desactiva una cuenta")
    public ResponseEntity<UsuarioDTO> cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequestDTO request) {
        return ResponseEntity.ok(usuarioService.cambiarEstado(id, request));
    }
}

