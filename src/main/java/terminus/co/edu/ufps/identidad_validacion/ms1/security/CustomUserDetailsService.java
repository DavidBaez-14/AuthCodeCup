package terminus.co.edu.ufps.identidad_validacion.ms1.security;

import terminus.co.edu.ufps.identidad_validacion.ms1.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var usuario = usuarioRepository.findByCorreo(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado."));

        return User.withUsername(usuario.getCorreo())
                .password(usuario.getContrasena() == null ? "" : usuario.getContrasena())
                .authorities(new SimpleGrantedAuthority("ROLE_" + usuario.getRolSistema().name()))
                .accountLocked(usuario.getBloqueadoHasta() != null)
                .disabled(Boolean.FALSE.equals(usuario.getActivo()))
                .build();
    }
}

