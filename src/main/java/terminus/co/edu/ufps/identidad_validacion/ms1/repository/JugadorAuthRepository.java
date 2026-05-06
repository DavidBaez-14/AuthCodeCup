package terminus.co.edu.ufps.identidad_validacion.ms1.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.JugadorAuth;

public interface JugadorAuthRepository extends JpaRepository<JugadorAuth, String> {

    Optional<JugadorAuth> findByAppwriteUserId(String appwriteUserId);

    Optional<JugadorAuth> findByCedula(String cedula);

    boolean existsByAppwriteUserId(String appwriteUserId);

    boolean existsByCedula(String cedula);
}
