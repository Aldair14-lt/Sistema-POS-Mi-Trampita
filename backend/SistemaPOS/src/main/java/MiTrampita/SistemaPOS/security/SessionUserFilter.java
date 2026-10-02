package MiTrampita.SistemaPOS.security;

import MiTrampita.SistemaPOS.entity.EstadoUsuario;
import MiTrampita.SistemaPOS.repositorio.UsuarioRepository;
import MiTrampita.SistemaPOS.repositorio.UsuarioRolRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Aplica bloqueos y cambios de rol también a sesiones que ya estaban abiertas. */
@RequiredArgsConstructor
public class SessionUserFilter extends OncePerRequestFilter {
    private final UsuarioRepository usuarios;
    private final UsuarioRolRepository roles;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PosPrincipal principal) {
            var user = usuarios.findById(principal.id()).orElse(null);
            if (user == null || user.getEstado() != EstadoUsuario.activo) {
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) request.getSession(false).invalidate();
            } else {
                var currentRoles = roles.findAllByUsuario_Id(user.getId()).stream()
                        .map(r -> PosPrincipal.canonicalRole(r.getRol().getNombre())).distinct().toList();
                var current = new PosPrincipal(user.getId(), user.getUsuario(), user.getNombreCompleto(), currentRoles);
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(current, null,
                        currentRoles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
                SecurityContextHolder.setContext(context);
            }
        }
        chain.doFilter(request, response);
    }
}