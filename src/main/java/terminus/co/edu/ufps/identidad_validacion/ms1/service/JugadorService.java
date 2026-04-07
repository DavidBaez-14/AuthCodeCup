package terminus.co.edu.ufps.identidad_validacion.ms1.service;

import terminus.co.edu.ufps.identidad_validacion.ms1.dto.JugadorDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.CedulaNotFoundException;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolJugador;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.JugadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JugadorService {

    private final JugadorRepository jugadorRepository;

    public Page<JugadorDTO> listar(RolJugador rolJugador, Boolean activo, int page, int size) {
        var pageable = PageRequest.of(page, size);
        if (rolJugador != null && activo != null) {
            return jugadorRepository.findByRolJugadorAndActivo(rolJugador, activo, pageable).map(JugadorDTO::fromEntity);
        }
        if (rolJugador != null) {
            return jugadorRepository.findByRolJugador(rolJugador, pageable).map(JugadorDTO::fromEntity);
        }
        if (activo != null) {
            return jugadorRepository.findByActivo(activo, pageable).map(JugadorDTO::fromEntity);
        }
        return jugadorRepository.findAll(pageable).map(JugadorDTO::fromEntity);
    }

    public JugadorDTO buscarPorCedula(String cedula) {
        return jugadorRepository.findByCedula(cedula)
                .map(JugadorDTO::fromEntity)
                .orElseThrow(() -> new CedulaNotFoundException("Cedula no encontrada en la base de la facultad."));
    }
}

