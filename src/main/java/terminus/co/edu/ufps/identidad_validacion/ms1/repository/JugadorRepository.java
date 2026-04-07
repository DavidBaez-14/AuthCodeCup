package terminus.co.edu.ufps.identidad_validacion.ms1.repository;

import terminus.co.edu.ufps.identidad_validacion.ms1.model.Jugador;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolJugador;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JugadorRepository extends JpaRepository<Jugador, UUID> {

    Optional<Jugador> findByCedula(String cedula);

    Page<Jugador> findByRolJugadorAndActivo(RolJugador rolJugador, Boolean activo, Pageable pageable);

    Page<Jugador> findByRolJugador(RolJugador rolJugador, Pageable pageable);

    Page<Jugador> findByActivo(Boolean activo, Pageable pageable);
}

