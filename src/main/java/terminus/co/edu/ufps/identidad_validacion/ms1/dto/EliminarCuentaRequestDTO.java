package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EliminarCuentaRequestDTO {

    @NotBlank
    private String motivo;
}
