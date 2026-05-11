package terminus.co.edu.ufps.identidad_validacion.ms1.config;

import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Cuenta;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.CuentaRol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Rol;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.CuentaRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.CuentaRolRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.AppwriteUsersClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRunner implements CommandLineRunner {

    private final DataSource dataSource;
    private final CuentaRepository cuentaRepository;
    private final CuentaRolRepository cuentaRolRepository;
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

        if (cuentaRolRepository.existsByRol(Rol.ADMINISTRADOR)) {
            return;
        }

        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("Skipping admin seed: ADMIN_EMAIL/ADMIN_PASSWORD not set.");
            return;
        }

        String cedula = (adminCedula == null || adminCedula.isBlank()) ? "0000000000" : adminCedula.trim();

        String adminUserId = appwriteUsersClient.crearUsuario(adminEmail, adminPassword, "Administrador");
        appwriteUsersClient.setLabels(adminUserId, List.of("administrador"));

        var cuenta = cuentaRepository.save(Cuenta.builder()
                .appwriteUserId(adminUserId)
                .cedula(cedula)
                .correo(adminEmail)
                .nombre("Administrador")
                .fechaCreacion(LocalDateTime.now())
                .build());

        cuentaRolRepository.save(CuentaRol.builder()
                .cuenta(cuenta)
                .rol(Rol.ADMINISTRADOR)
                .estado(EstadoRol.APROBADO)
                .fechaSolicitud(LocalDateTime.now())
                .fechaResolucion(LocalDateTime.now())
                .build());
    }
}
