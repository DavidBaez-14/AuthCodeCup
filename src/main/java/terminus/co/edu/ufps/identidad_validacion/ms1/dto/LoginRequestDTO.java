package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequestDTO {

    @Email
    @NotBlank
    private String correo;

    @NotBlank
    private String contrasena;
}

