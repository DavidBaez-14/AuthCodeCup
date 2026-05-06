package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolSolicitado;

@Data
public class RegistroRequestDTO {

    @NotBlank
    private String appwriteJwt;

    @NotBlank
    private String cedula;

    @NotNull
    private RolSolicitado rolSolicitado;
}
