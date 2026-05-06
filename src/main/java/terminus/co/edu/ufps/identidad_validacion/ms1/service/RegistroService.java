package terminus.co.edu.ufps.identidad_validacion.ms1.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.RegistroPendienteDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRegistro;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.JugadorRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.PerfilRepository;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.AppwriteUsersClient;

@Service
@RequiredArgsConstructor
public class RegistroService {

    private final PerfilRepository perfilRepository;
    private final AppwriteUsersClient appwriteUsersClient;
    private final JugadorRepository jugadorRepository;

    @Transactional(readOnly = true)
    public List<RegistroPendienteDTO> listarPendientes() {
        return perfilRepository.findByEstado(EstadoRegistro.PENDIENTE)
                .stream()
                .map(p -> RegistroPendienteDTO.from(p, jugadorRepository.findByCedula(p.getCedula()).orElse(null)))
                .toList();
    }

    @Transactional
    public void aprobar(String perfilId) {
        var perfil = perfilRepository.findById(perfilId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado."));

        var label = perfil.getRolSolicitado().name().toLowerCase();
        appwriteUsersClient.asignarLabels(perfil.getAppwriteUserId(), List.of(label));

        perfil.setEstado(EstadoRegistro.APROBADO);
        perfil.setFechaResolucion(LocalDateTime.now());
        perfilRepository.save(perfil);
    }

    @Transactional
    public void rechazar(String perfilId, String motivo) {
        var perfil = perfilRepository.findById(perfilId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado."));

        appwriteUsersClient.eliminarUsuario(perfil.getAppwriteUserId());

        perfil.setEstado(EstadoRegistro.RECHAZADO);
        perfil.setMotivoRechazo(motivo);
        perfil.setFechaResolucion(LocalDateTime.now());
        perfilRepository.save(perfil);
    }
}
