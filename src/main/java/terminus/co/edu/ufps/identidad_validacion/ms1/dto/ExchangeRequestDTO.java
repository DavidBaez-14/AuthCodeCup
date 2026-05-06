package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExchangeRequestDTO {

    @NotBlank
    private String appwriteJwt;
}
