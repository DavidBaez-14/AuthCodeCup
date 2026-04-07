package terminus.co.edu.ufps.identidad_validacion.ms1.repository;

import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolSistema;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Usuario;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    boolean existsByRolSistema(RolSistema rolSistema);

    Page<Usuario> findByRolSistemaAndActivo(RolSistema rolSistema, Boolean activo, Pageable pageable);

    Page<Usuario> findByRolSistema(RolSistema rolSistema, Pageable pageable);

    Page<Usuario> findByActivo(Boolean activo, Pageable pageable);

        @Query("""
            SELECT u FROM Usuario u
            WHERE (:rolSistema IS NULL OR u.rolSistema = :rolSistema)
              AND (:activo IS NULL OR u.activo = :activo)
              AND (
                :buscar IS NULL OR :buscar = ''
                OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :buscar, '%'))
                OR LOWER(COALESCE(u.cedula, '')) LIKE LOWER(CONCAT('%', :buscar, '%'))
             )
            """)
        Page<Usuario> buscarConFiltros(
            @Param("rolSistema") RolSistema rolSistema,
            @Param("activo") Boolean activo,
            @Param("buscar") String buscar,
            Pageable pageable);
}

