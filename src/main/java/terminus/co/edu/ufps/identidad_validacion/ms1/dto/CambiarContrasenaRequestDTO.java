package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CambiarContrasenaRequestDTO {

    @NotBlank
    private String contrasenaActual;

    @NotBlank
    @Size(min = 8, max = 100)
    private String contrasenaNueva;
}
