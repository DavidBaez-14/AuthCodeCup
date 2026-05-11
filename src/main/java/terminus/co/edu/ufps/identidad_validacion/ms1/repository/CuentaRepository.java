package terminus.co.edu.ufps.identidad_validacion.ms1.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Cuenta;

public interface CuentaRepository extends JpaRepository<Cuenta, UUID> {

    Optional<Cuenta> findByAppwriteUserId(String appwriteUserId);

    Optional<Cuenta> findByCedula(String cedula);

    boolean existsByCedula(String cedula);

    boolean existsByCorreo(String correo);
}
