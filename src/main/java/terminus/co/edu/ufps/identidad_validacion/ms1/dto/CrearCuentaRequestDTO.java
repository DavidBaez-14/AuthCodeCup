package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Rol;

@Data
public class CrearCuentaRequestDTO {

    @NotBlank
    private String nombre;

    @NotBlank
    @Email
    private String correo;

    @NotBlank
    private String cedula;

    @NotNull
    private Rol rolInicial;

    @NotBlank
    private String motivo;
}
