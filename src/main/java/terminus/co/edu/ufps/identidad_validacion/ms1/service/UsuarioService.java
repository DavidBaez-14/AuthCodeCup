package terminus.co.edu.ufps.identidad_validacion.ms1.service;

import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CambiarEstadoRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CrearUsuarioRequestDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.CrearUsuarioResponseDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.UsuarioDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.AuthException;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.ProveedorAuth;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.RolSistema;
import terminus.co.edu.ufps.identidad_validacion.ms1.model.Usuario;
import terminus.co.edu.ufps.identidad_validacion.ms1.repository.UsuarioRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
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

        String contrasenaTemporal = null;
        String hash = null;
        if (request.getProveedorAuth() == ProveedorAuth.LOCAL) {
            contrasenaTemporal = generarContrasenaTemporal();
            hash = passwordEncoder.encode(contrasenaTemporal);
        }

        Usuario usuario = Usuario.builder()
                .correo(request.getCorreo())
                .nombre(request.getNombre())
                .cedula(request.getCedula())
                .rolSistema(request.getRolSistema())
                .proveedorAuth(request.getProveedorAuth())
                .contrasena(hash)
                .activo(true)
                .intentosFallidos(0)
                .fechaCreacion(LocalDateTime.now())
                .build();

        Usuario guardado = usuarioRepository.save(usuario);
        return CrearUsuarioResponseDTO.builder()
                .usuario(UsuarioDTO.fromEntity(guardado))
                .contrasenaTemporal(contrasenaTemporal)
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

    private String generarContrasenaTemporal() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

