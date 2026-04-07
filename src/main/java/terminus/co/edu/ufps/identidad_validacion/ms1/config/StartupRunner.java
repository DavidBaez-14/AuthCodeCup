package terminus.co.edu.ufps.identidad_validacion.ms1.config;

import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolSistema;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Usuario;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.UsuarioRepository;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartupRunner implements CommandLineRunner {

    private final DataSource dataSource;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_EMAIL:}")
    private String adminEmail;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        try (var connection = dataSource.getConnection()) {
            if (!connection.isValid(3)) {
                throw new IllegalStateException("No hay conexiÃ³n vÃ¡lida con la base de datos.");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Fallo la conexiÃ³n a la base de datos. Revisa credenciales y red.");
        }

        if (!usuarioRepository.existsByRolSistema(RolSistema.ADMINISTRADOR)) {
            if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
                throw new IllegalStateException("Faltan ADMIN_EMAIL y ADMIN_PASSWORD para crear el administrador inicial.");
            }

            Usuario admin = Usuario.builder()
                    .correo(adminEmail)
                    .nombre("Administrador")
                    .rolSistema(RolSistema.ADMINISTRADOR)
                    .contrasena(passwordEncoder.encode(adminPassword))
                    .debeCambiarContrasena(false)
                    .activo(true)
                    .intentosFallidos(0)
                    .fechaCreacion(LocalDateTime.now())
                    .build();
            usuarioRepository.save(admin);
        }
    }
}

