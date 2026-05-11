package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.CuentaRol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Jugador;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Rol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolJugador;

@Data
@Builder
public class CuentaRolPendienteDTO {

    private UUID id;
    private String cedula;
    private Rol rol;
    private EstadoRol estado;
    private LocalDateTime fechaSolicitud;
    private String nombre;
    private String correo;
    private String motivoSolicitud;
    private RolJugador rolJugador;
    private String codigoUniversitario;
    private Integer semestre;

    public static CuentaRolPendienteDTO from(CuentaRol cr, Jugador padron) {
        var cuenta = cr.getCuenta();
        String nombre = cuenta.getNombre();
        if ((nombre == null || nombre.isBlank()) && padron != null) {
            nombre = padron.getNombre();
        }
        return CuentaRolPendienteDTO.builder()
                .id(cr.getId())
                .cedula(cuenta.getCedula())
                .rol(cr.getRol())
                .estado(cr.getEstado())
                .fechaSolicitud(cr.getFechaSolicitud())
                .nombre(nombre)
                .correo(cuenta.getCorreo())
                .motivoSolicitud(cr.getMotivoSolicitud())
                .rolJugador(cr.getRolJugador())
                .codigoUniversitario(cr.getCodigoUniversitario())
                .semestre(cr.getSemestre())
                .build();
    }
}
