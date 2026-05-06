package terminus.co.edu.ufps.identidad_validacion.ms1.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRegistro;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Perfil;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolSolicitado;

public interface PerfilRepository extends JpaRepository<Perfil, String> {

    Optional<Perfil> findByAppwriteUserId(String appwriteUserId);

    List<Perfil> findByEstado(EstadoRegistro estado);

    boolean existsByRolSolicitado(RolSolicitado rolSolicitado);
}
