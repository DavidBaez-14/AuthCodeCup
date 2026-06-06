package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RolEstadoDTO {

    private UUID id;
    private String rol;
    private String estado;
    private LocalDateTime fechaSolicitud;
    private LocalDateTime fechaResolucion;
}
