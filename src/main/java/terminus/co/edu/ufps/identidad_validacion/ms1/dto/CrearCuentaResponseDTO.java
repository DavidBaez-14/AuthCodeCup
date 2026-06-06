package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CrearCuentaResponseDTO {

    private UUID cuentaId;
    private String cedula;
    private String correo;
    private String passwordTemporal;
    private String mensaje;
}
