package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CuentaAdminDTO {

    private UUID id;
    private String cedula;
    private String nombre;
    private String correo;
    private LocalDateTime fechaCreacion;
    private List<RolEstadoDTO> roles;
}
