package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolSistema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CrearUsuarioRequestDTO {

    @Email
    @NotBlank
    private String correo;

    @NotBlank
    private String nombre;

    @NotBlank
    @Size(min = 6, max = 20)
    private String cedula;

    @NotNull
    private RolSistema rolSistema;

}

