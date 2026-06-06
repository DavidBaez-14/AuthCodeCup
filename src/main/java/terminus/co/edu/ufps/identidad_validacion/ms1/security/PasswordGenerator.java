package terminus.co.edu.ufps.identidad_validacion.ms1.security;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class PasswordGenerator {

    private static final String ALFABETO =
            "ABCDEFGHJKLMNPQRSTUVWXYZ" +
            "abcdefghijkmnpqrstuvwxyz" +
            "23456789";

    private static final int LONGITUD = 16;

    private final SecureRandom random = new SecureRandom();

    public String generar() {
        var sb = new StringBuilder(LONGITUD);
        for (int i = 0; i < LONGITUD; i++) {
            sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
