package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import terminus.co.edu.ufps.identidad_validacion.ms1.model.Jugador;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolJugador;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JugadorDTO {

    private String cedula;
    private String nombre;
    private String codigoUniversitario;
    private RolJugador rolJugador;
    private Integer semestre;
    private Boolean activo;

    public static JugadorDTO fromEntity(Jugador jugador) {
        return JugadorDTO.builder()
                .cedula(jugador.getCedula())
                .nombre(jugador.getNombre())
                .codigoUniversitario(jugador.getCodigoUniversitario())
                .rolJugador(jugador.getRolJugador())
                .semestre(jugador.getSemestre())
                .activo(jugador.getActivo())
                .build();
    }
}

