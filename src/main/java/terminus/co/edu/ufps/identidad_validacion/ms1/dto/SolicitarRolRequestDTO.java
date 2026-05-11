package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Rol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolJugador;

@Data
public class SolicitarRolRequestDTO {

    @NotBlank
    private String appwriteJwt;

    @NotBlank
    private String cedula;

    @NotNull
    private Rol rol;

    private RolJugador rolJugador;

    private String codigoUniversitario;

    private Integer semestre;

    private String motivoSolicitud;
}
