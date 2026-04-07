package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import terminus.co.edu.ufps.identidad_validacion.ms1.model.ProveedorAuth;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolSistema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CrearUsuarioRequestDTO {

    @Email
    @NotBlank
    private String correo;

    @NotBlank
    private String nombre;

    private String cedula;

    @NotNull
    private RolSistema rolSistema;

    @NotNull
    private ProveedorAuth proveedorAuth;
}

