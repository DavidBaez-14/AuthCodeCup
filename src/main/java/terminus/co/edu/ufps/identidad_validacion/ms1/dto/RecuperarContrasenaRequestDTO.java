package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecuperarContrasenaRequestDTO {

    @Email
    @NotBlank
    private String correo;
}

