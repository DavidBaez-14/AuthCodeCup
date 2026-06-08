package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Rol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolJugador;

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

    // Solo requeridos cuando rolInicial == DELEGADO (regla: todo delegado
    // tambien es jugador). El service los valida segun el contexto.
    private RolJugador rolJugador;
    private String codigoUniversitario;
    private Integer semestre;
}
