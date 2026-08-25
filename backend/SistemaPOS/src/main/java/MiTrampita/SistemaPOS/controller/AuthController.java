package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.Usuario;
import MiTrampita.SistemaPOS.entity.EstadoUsuario;
import MiTrampita.SistemaPOS.repositorio.UsuarioRepository;
import MiTrampita.SistemaPOS.repositorio.UsuarioRolRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UsuarioRepository usuarios;
    private final UsuarioRolRepository usuarioRoles;

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        String username = req.usuario().trim();
        Usuario u = usuarios.findByUsuarioIgnoreCase(username)
            .filter(x -> req.contrasena().equals(x.getContrasena()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos"));

        if (u.getEstado() != EstadoUsuario.activo) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario inactivo o bloqueado");
        }

        List<String> roles = usuarioRoles.findAllByUsuario_Id(u.getId()).stream()
            .map(ur -> ur.getRol().getNombre())
            .toList();
            
        return new LoginResponse(u.getId(), u.getUsuario(), u.getNombreCompleto(), roles);
    }
    
    public record LoginRequest(
        @NotBlank(message = "El usuario es obligatorio")
        @Size(max = 50, message = "El usuario no puede superar 50 caracteres")
        String usuario,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 255, message = "La contraseña no puede superar 255 caracteres")
        String contrasena
    ) {}
    public record LoginResponse(Integer id, String usuario, String nombreCompleto, List<String> roles) {}
}
