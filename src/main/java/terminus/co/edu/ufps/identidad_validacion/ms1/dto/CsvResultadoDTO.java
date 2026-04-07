package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CsvResultadoDTO {

    @Builder.Default
    private int importados = 0;

    @Builder.Default
    private int actualizados = 0;

    @Builder.Default
    private int rechazados = 0;

    @Builder.Default
    private List<ErrorFilaDTO> errores = new ArrayList<>();

    @Data
    @Builder
    public static class ErrorFilaDTO {
        private int fila;
        private String cedula;
        private String razon;
    }
}

