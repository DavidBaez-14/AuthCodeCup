package terminus.co.edu.ufps.identidad_validacion.ms1.controller;

import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CsvResultadoDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.JugadorDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolJugador;
import terminus.co.edu.ufps.identidad_validacion.ms1.service.CsvService;
import terminus.co.edu.ufps.identidad_validacion.ms1.service.JugadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/jugadores")
@RequiredArgsConstructor
@Tag(name = "Jugadores", description = "Operaciones de identidad y validaciÃ³n de jugadores")
public class JugadorController {

    private final CsvService csvService;
    private final JugadorService jugadorService;

    @PostMapping("/cargar-csv")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Carga masiva CSV", description = "Importa o actualiza jugadores desde un archivo CSV")
    public ResponseEntity<CsvResultadoDTO> cargarCsv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(csvService.procesar(file));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Listar jugadores", description = "Lista jugadores con filtros opcionales")
    public ResponseEntity<Page<JugadorDTO>> listar(
            @RequestParam(required = false) RolJugador rol_jugador,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(jugadorService.listar(rol_jugador, activo, page, size));
    }

    @GetMapping("/{cedula}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','DELEGADO')")
    @Operation(summary = "Consultar por cÃ©dula", description = "Obtiene datos del jugador por cÃ©dula exacta")
    public ResponseEntity<JugadorDTO> buscarPorCedula(@PathVariable String cedula) {
        return ResponseEntity.ok(jugadorService.buscarPorCedula(cedula));
    }
}

