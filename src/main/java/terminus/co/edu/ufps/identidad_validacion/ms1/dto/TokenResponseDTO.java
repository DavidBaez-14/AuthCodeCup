package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenResponseDTO {
    private String token;
    private long expiraEn;
    private List<String> roles;
    private String nombre;
    private String correo;
    private String cedula;
}
