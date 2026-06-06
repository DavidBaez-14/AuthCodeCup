package terminus.co.edu.ufps.identidad_validacion.ms1.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Cuenta;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Rol;

public interface CuentaRepository extends JpaRepository<Cuenta, UUID> {

    Optional<Cuenta> findByAppwriteUserId(String appwriteUserId);

    Optional<Cuenta> findByCedula(String cedula);

    boolean existsByCedula(String cedula);

    boolean existsByCorreo(String correo);

    @Query("""
            SELECT DISTINCT c FROM Cuenta c
            WHERE (:q IS NULL
                   OR LOWER(c.cedula) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(c.correo) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
              AND (:rol IS NULL OR EXISTS (
                    SELECT 1 FROM CuentaRol cr
                    WHERE cr.cuenta = c
                      AND cr.rol = :rol
                      AND cr.estado = terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRol.APROBADO))
            """)
    Page<Cuenta> buscar(@Param("q") String q, @Param("rol") Rol rol, Pageable pageable);
}
