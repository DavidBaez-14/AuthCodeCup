package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolSistema;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Usuario;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UsuarioDTO {

    private Long id;
    private String correo;
    private RolSistema rolSistema;
    private String nombre;
    private String cedula;
    private Boolean activo;
    private Boolean debeCambiarContrasena;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaUltimoAcceso;

    public static UsuarioDTO fromEntity(Usuario usuario) {
        return UsuarioDTO.builder()
                .id(usuario.getId())
                .correo(usuario.getCorreo())
                .rolSistema(usuario.getRolSistema())
                .nombre(usuario.getNombre())
                .cedula(usuario.getCedula())
                .activo(usuario.getActivo())
                .debeCambiarContrasena(usuario.getDebeCambiarContrasena())
                .fechaCreacion(usuario.getFechaCreacion())
                .fechaUltimoAcceso(usuario.getFechaUltimoAcceso())
                .build();
    }
}

