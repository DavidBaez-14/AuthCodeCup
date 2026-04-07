package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CrearUsuarioResponseDTO {

    private UsuarioDTO usuario;
    private String mensaje;
}

