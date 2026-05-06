package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegistroResponseDTO {
    private String estado;
    private String mensaje;
}
