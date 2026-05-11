package terminus.co.edu.ufps.identidad_validacion.ms1.service;

import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CsvResultadoDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.InvalidCsvException;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Jugador;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Rol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolJugador;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.CuentaRolRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.JugadorRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.AppwriteUsersClient;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CsvService {

    private final JugadorRepository jugadorRepository;
    private final CuentaRolRepository cuentaRolRepository;
    private final AppwriteUsersClient appwriteUsersClient;

    @Transactional
    public CsvResultadoDTO procesar(MultipartFile archivo) {
        validarArchivoCsv(archivo);
        CsvResultadoDTO resultado = CsvResultadoDTO.builder().build();

        try (CSVReader reader = new CSVReader(new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null || header.length == 0) {
                throw new InvalidCsvException("El archivo CSV está vacío.");
            }

            Map<String, Integer> index = indexarHeader(header);
            validarColumnasRequeridas(index);

            String[] row;
            int fila = 1;
            while ((row = reader.readNext()) != null) {
                fila++;
                procesarFila(row, fila, index, resultado);
            }

            autoAprobarPendientesValidacion(resultado);
            return resultado;
        } catch (IOException | CsvValidationException e) {
            throw new InvalidCsvException("No fue posible procesar el archivo CSV.");
        }
    }

    private void validarArchivoCsv(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new InvalidCsvException("Debes enviar un archivo CSV no vacío.");
        }
        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!nombre.endsWith(".csv")) {
            throw new InvalidCsvException("Solo se permiten archivos .csv");
        }
    }

    private Map<String, Integer> indexarHeader(String[] header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            index.put(normalizar(header[i]), i);
        }
        return index;
    }

    private void validarColumnasRequeridas(Map<String, Integer> index) {
        for (String requerida : Arrays.asList("nombre", "cedula", "rol_jugador")) {
            if (!index.containsKey(requerida)) {
                throw new InvalidCsvException("Falta la columna requerida: " + requerida);
            }
        }
    }

    private void procesarFila(String[] row, int fila, Map<String, Integer> index, CsvResultadoDTO resultado) {
        try {
            String cedula = obtenerValor(row, index, "cedula");
            String nombre = obtenerValor(row, index, "nombre");
            String codigo = obtenerValorOpcional(row, index, "codigo_universitario");
            RolJugador rolJugador = RolJugador.valueOf(obtenerValor(row, index, "rol_jugador").toUpperCase(Locale.ROOT));
            Integer semestre = parseSemestre(obtenerValorOpcional(row, index, "semestre"));

            if (rolJugador != RolJugador.ESTUDIANTE) {
                semestre = null;
            }

            var existente = jugadorRepository.findByCedula(cedula);
            Jugador jugador = existente.orElseGet(Jugador::new);
            boolean esNuevo = existente.isEmpty();

            jugador.setCedula(cedula);
            jugador.setNombre(nombre);
            jugador.setCodigoUniversitario(codigo);
            jugador.setRolJugador(rolJugador);
            jugador.setSemestre(semestre);
            jugador.setActivo(true);
            jugador.setFechaActualizacion(LocalDateTime.now());

            jugadorRepository.save(jugador);
            if (esNuevo) {
                resultado.setImportados(resultado.getImportados() + 1);
            } else {
                resultado.setActualizados(resultado.getActualizados() + 1);
            }
        } catch (Exception ex) {
            resultado.setRechazados(resultado.getRechazados() + 1);
            resultado.getErrores().add(CsvResultadoDTO.ErrorFilaDTO.builder()
                    .fila(fila)
                    .cedula(obtenerValorOpcional(row, index, "cedula"))
                    .razon(ex.getMessage() == null ? "Fila inválida" : ex.getMessage())
                    .build());
        }
    }

    /**
     * Tras actualizar el padrón, busca solicitudes JUGADOR en estado PENDIENTE_VALIDACION
     * cuya cédula ahora aparezca en padrón y las aprueba automáticamente, sincronizando
     * los labels en Appwrite. Esto vacía la cola del admin sin intervención manual.
     */
    private void autoAprobarPendientesValidacion(CsvResultadoDTO resultado) {
        var pendientes = cuentaRolRepository.findByEstadoAndRol(EstadoRol.PENDIENTE_VALIDACION, Rol.JUGADOR);
        for (var cr : pendientes) {
            var cuenta = cr.getCuenta();
            if (jugadorRepository.findByCedula(cuenta.getCedula()).isEmpty()) {
                continue;
            }
            cr.setEstado(EstadoRol.APROBADO);
            cr.setFechaResolucion(LocalDateTime.now());
            cuentaRolRepository.save(cr);

            List<String> rolesAprobados = cuentaRolRepository
                    .findByCuentaIdAndEstado(cuenta.getId(), EstadoRol.APROBADO)
                    .stream()
                    .map(c -> c.getRol().name().toLowerCase())
                    .toList();
            appwriteUsersClient.setLabels(cuenta.getAppwriteUserId(), rolesAprobados);

            resultado.setAutoAprobados(resultado.getAutoAprobados() + 1);
        }
    }

    private String obtenerValor(String[] row, Map<String, Integer> index, String key) {
        String valor = obtenerValorOpcional(row, index, key);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Campo requerido vacío: " + key);
        }
        return valor;
    }

    private String obtenerValorOpcional(String[] row, Map<String, Integer> index, String key) {
        Integer pos = index.get(key);
        if (pos == null || pos >= row.length) {
            return null;
        }
        String valor = row[pos];
        return valor == null ? null : valor.trim();
    }

    private Integer parseSemestre(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return Integer.parseInt(valor);
    }

    private String normalizar(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }
}
