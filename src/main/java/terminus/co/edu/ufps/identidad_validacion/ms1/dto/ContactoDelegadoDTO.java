package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContactoDelegadoDTO {
    private String cedulaDelegado;
    private String correoDelegado;
    private String nombreDelegado;
}
