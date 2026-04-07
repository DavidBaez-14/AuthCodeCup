package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolSistema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponseDTO {

    private String token;
    private String tipo;
    private long expiraEn;
    private RolSistema rol;
    private String nombre;
    private String correo;
}

