package terminus.co.edu.ufps.identidad_validacion.ms1.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Resultado público (sin auth) del lookup de cédula contra el padrón.
 * Sirve al frontend de signup para esconder los campos académicos cuando
 * la persona ya está registrada oficialmente.
 *
 * No expone codigo_universitario ni semestre por privacidad: si la persona
 * ya está en padrón, esos datos los toma el backend al crear el CuentaRol.
 */
@Data
@Builder
public class PadronPreviewDTO {

    private boolean enPadron;
    private boolean esEstudiante;
    private String nombre;
}
