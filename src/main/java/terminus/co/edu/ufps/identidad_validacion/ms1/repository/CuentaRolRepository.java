package terminus.co.edu.ufps.identidad_validacion.ms1.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.CuentaRol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRol;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Rol;

public interface CuentaRolRepository extends JpaRepository<CuentaRol, UUID> {

    List<CuentaRol> findByCuentaId(UUID cuentaId);

    List<CuentaRol> findByCuentaIdAndEstado(UUID cuentaId, EstadoRol estado);

    Optional<CuentaRol> findByCuentaIdAndRol(UUID cuentaId, Rol rol);

    boolean existsByCuentaIdAndRol(UUID cuentaId, Rol rol);

    List<CuentaRol> findByEstadoIn(List<EstadoRol> estados);

    List<CuentaRol> findByEstadoAndRol(EstadoRol estado, Rol rol);

    boolean existsByRol(Rol rol);
}
