package terminus.co.edu.ufps.identidad_validacion.ms1.config;

import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRegistro;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Perfil;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolSolicitado;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.PerfilRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.AppwriteUsersClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRunner implements CommandLineRunner {

    private final DataSource dataSource;
    private final PerfilRepository perfilRepository;
    private final AppwriteUsersClient appwriteUsersClient;

    @Value("${ADMIN_EMAIL:rauldavidbs@ufps.edu.co}")
    private String adminEmail;

    @Value("${ADMIN_PASSWORD:WAR C0MMANDER}")
    private String adminPassword;

    @Value("${ADMIN_CEDULA:1152383}")
    private String adminCedula;

    @Override
    public void run(String... args) throws Exception {
        try (var connection = dataSource.getConnection()) {
            if (!connection.isValid(3)) {
                throw new IllegalStateException("Database connection is not valid.");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Database connection failed. Check credentials and network.");
        }

        if (perfilRepository.existsByRolSolicitado(RolSolicitado.ADMINISTRADOR)) {
            return;
        }

        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("Skipping admin seed: ADMIN_EMAIL/ADMIN_PASSWORD not set.");
            return;
        }

        String cedula = (adminCedula == null || adminCedula.isBlank()) ? "0000000000" : adminCedula.trim();

        String adminUserId = appwriteUsersClient.crearUsuario(adminEmail, adminPassword, "Administrador");
        appwriteUsersClient.asignarLabels(adminUserId, List.of("administrador"));

        Perfil perfil = Perfil.builder()
                .appwriteUserId(adminUserId)
                .cedula(cedula)
                .rolSolicitado(RolSolicitado.ADMINISTRADOR)
                .estado(EstadoRegistro.APROBADO)
                .fechaSolicitud(LocalDateTime.now())
                .fechaResolucion(LocalDateTime.now())
                .correo(adminEmail)
                .nombre("Administrador")
                .build();
        perfilRepository.save(perfil);
    }
}

