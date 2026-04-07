package terminus.co.edu.ufps.identidad_validacion.ms1.exception;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiErrorResponse {

    private String timestamp;
    private int status;
    private String error;
    private String mensaje;
    private String path;
}

