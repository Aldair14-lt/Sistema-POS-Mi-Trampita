package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.EstadoUsuario;
import MiTrampita.SistemaPOS.repositorio.*;
import MiTrampita.SistemaPOS.security.PosPrincipal;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UsuarioRepository usuarios;
    private final UsuarioRolRepository usuarioRoles;
    private final PasswordEncoder passwords;
    private final SecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrf;

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(), token.getToken()); }

    @GetMapping("/me")
    public PosPrincipal me(@AuthenticationPrincipal PosPrincipal principal) { return principal; }

    @PostMapping("/login")
    @Transactional
    public PosPrincipal login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        var user = usuarios.findByUsuarioIgnoreCase(req.usuario().trim()).orElseThrow(this::invalid);
        String stored = user.getContrasena();
        boolean hashed = stored.startsWith("$2");
        // Compatibilidad con el esquema antiguo: convertir a BCrypt al primer login válido.
        boolean matches = hashed ? passwords.matches(req.contrasena(), stored)
                : MessageDigest.isEqual(req.contrasena().getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
        if (!matches || user.getEstado() != EstadoUsuario.activo) throw invalid();
        var roles = usuarioRoles.findAllByUsuario_Id(user.getId()).stream()
                .map(r -> PosPrincipal.canonicalRole(r.getRol().getNombre())).distinct().toList();
        if (roles.stream().allMatch(r -> r.equals("SIN_ACCESO")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solicita al administrador un rol de acceso");
        if (!hashed) user.setContrasena(passwords.encode(req.contrasena()));
        var principal = new PosPrincipal(user.getId(), user.getUsuario(), user.getNombreCompleto(), roles);
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
        request.getSession();
        request.changeSessionId();
        csrf.saveToken(null, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return principal;
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos, o usuario inactivo");
    }
    public record CsrfResponse(String headerName, String token) { }
    public record LoginRequest(@NotBlank @Size(max = 50) String usuario,
            @NotBlank @Size(max = 255) String contrasena) { }
}