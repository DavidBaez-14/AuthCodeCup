package terminus.co.edu.ufps.identidad_validacion.ms1.service;

import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CambiarEstadoRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CrearUsuarioRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CrearUsuarioResponseDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.UsuarioDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.AuthException;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolSistema;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Usuario;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.UsuarioRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public CrearUsuarioResponseDTO crear(CrearUsuarioRequestDTO request) {
        if (usuarioRepository.existsByCorreo(request.getCorreo())) {
            throw new AuthException("Ya existe una cuenta con ese correo.");
        }

        String cedulaNormalizada = request.getCedula().trim();
        String hash = passwordEncoder.encode(cedulaNormalizada);

        Usuario usuario = Usuario.builder()
                .correo(request.getCorreo())
                .nombre(request.getNombre())
                .cedula(cedulaNormalizada)
                .rolSistema(request.getRolSistema())
                .contrasena(hash)
                .debeCambiarContrasena(true)
                .activo(true)
                .intentosFallidos(0)
                .fechaCreacion(LocalDateTime.now())
                .build();

        Usuario guardado = usuarioRepository.save(usuario);
        return CrearUsuarioResponseDTO.builder()
                .usuario(UsuarioDTO.fromEntity(guardado))
            .mensaje("Usuario creado. La contrasena inicial es la cedula y debe cambiarse en el primer ingreso.")
                .build();
    }

    public Page<UsuarioDTO> listar(RolSistema rolSistema, Boolean activo, String buscar, int page, int size) {
        var pageable = PageRequest.of(page, size);
        return usuarioRepository.buscarConFiltros(rolSistema, activo, buscar, pageable).map(UsuarioDTO::fromEntity);
    }

    public UsuarioDTO buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .map(UsuarioDTO::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
    }

    public UsuarioDTO cambiarEstado(Long id, CambiarEstadoRequestDTO request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        usuario.setActivo(request.getActivo());
        Usuario guardado = usuarioRepository.save(usuario);
        return UsuarioDTO.fromEntity(guardado);
    }

}

