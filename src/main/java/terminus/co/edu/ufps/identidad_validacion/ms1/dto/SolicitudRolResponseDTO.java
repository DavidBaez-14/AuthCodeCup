package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SolicitudRolResponseDTO {

    private String estado;

    private String mensaje;

    private TokenResponseDTO token;
}
