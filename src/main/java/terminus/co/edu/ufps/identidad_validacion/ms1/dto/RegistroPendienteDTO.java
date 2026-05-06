package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRegistro;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Jugador;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Perfil;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolSolicitado;

@Data
@Builder
public class RegistroPendienteDTO {
    private String id;
    private String cedula;
    private RolSolicitado rolSolicitado;
    private EstadoRegistro estado;
    private LocalDateTime fechaSolicitud;
    private String nombre;
    private String correo;

    public static RegistroPendienteDTO from(Perfil perfil, Jugador jugador) {
        String nombre = perfil.getNombre();
        if ((nombre == null || nombre.isBlank()) && jugador != null) {
            nombre = jugador.getNombre();
        }

        return RegistroPendienteDTO.builder()
                .id(perfil.getId())
                .cedula(perfil.getCedula())
                .rolSolicitado(perfil.getRolSolicitado())
                .estado(perfil.getEstado())
                .fechaSolicitud(perfil.getFechaSolicitud())
                .nombre(nombre)
                .correo(perfil.getCorreo())
                .build();
    }
}
