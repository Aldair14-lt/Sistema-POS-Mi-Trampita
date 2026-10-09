package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.repositorio.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api/usuarios") @RequiredArgsConstructor
@Transactional
public class UsuarioController {
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final UsuarioRolRepository relaciones;
    private final PasswordEncoder passwords;

    @GetMapping @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() { return usuarios.findAll().stream().map(this::response).toList(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crear(@Valid @RequestBody UsuarioRequest request) { return guardar(null, request); }
    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable Integer id, @Valid @RequestBody UsuarioRequest request) { return guardar(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) {
        roles.lockAdministratorRole().orElseThrow();
        var user = usuarios.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        protectLastAdministrator(user, false);
        relaciones.deleteAllByUsuario_Id(id);
        usuarios.delete(user);
    }

    private UsuarioResponse guardar(Integer id, UsuarioRequest req) {
        roles.lockAdministratorRole().orElseThrow();
        var user = id == null ? new Usuario() : usuarios.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        var requestedRoles = roles.findAllById(req.rolIds());
        usuarios.findByUsuarioIgnoreCase(req.usuario().trim()).filter(u -> !u.getId().equals(id)).ifPresent(u -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese nombre");
        });
        if (id != null) protectLastAdministrator(user, req.estado() == EstadoUsuario.activo
            && requestedRoles.stream().anyMatch(r -> MiTrampita.SistemaPOS.security.PosPrincipal.canonicalRole(r.getNombre()).equals("ADMIN")));
        if (requestedRoles.size() != new HashSet<>(req.rolIds()).size()
                || requestedRoles.stream().anyMatch(r -> !Set.of("ADMIN", "MOZO", "CAJA", "COCINERO", "BARTENDER").contains(r.getNombre())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona roles ADMIN, MOZO, CAJA, COCINERO o BARTENDER válidos");
        if (requestedRoles.size() > 1 && requestedRoles.stream().anyMatch(r -> Set.of("COCINERO", "BARTENDER").contains(r.getNombre())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COCINERO y BARTENDER deben asignarse como roles exclusivos");
        if (req.contrasena() != null && !req.contrasena().isBlank()) {
            if (req.contrasena().length() < 8 || req.contrasena().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener entre 8 y 72 caracteres");
            user.setContrasena(passwords.encode(req.contrasena()));
        } else if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña es obligatoria");
        user.setUsuario(req.usuario().trim());
        user.setNombreCompleto(req.nombreCompleto().trim());
        user.setCorreoElectronico(req.correoElectronico());
        user.setEstado(req.estado());
        usuarios.saveAndFlush(user);
        if (id != null) { relaciones.deleteAllByUsuario_Id(id); relaciones.flush(); }
        for (Rol rol : requestedRoles) {
            var relation = new UsuarioRol();
            relation.setUsuario(user);
            relation.setRol(rol);
            relaciones.save(relation);
        }
        return response(user);
    }
    private UsuarioResponse response(Usuario u) {
        var assigned = relaciones.findAllByUsuario_Id(u.getId()).stream()
                .filter(r -> Set.of("ADMIN", "MOZO", "CAJA", "COCINERO", "BARTENDER").contains(r.getRol().getNombre())).toList();
        return new UsuarioResponse(u.getId(), u.getUsuario(), u.getNombreCompleto(), u.getCorreoElectronico(), u.getEstado(),
                assigned.stream().map(r -> r.getRol().getId()).toList(), assigned.stream().map(r -> r.getRol().getNombre()).toList());
    }
    private void protectLastAdministrator(Usuario user, boolean remainsAdministrator) {
        boolean currentAdministrator = user.getEstado() == EstadoUsuario.activo && relaciones.findAllByUsuario_Id(user.getId()).stream()
            .anyMatch(r -> MiTrampita.SistemaPOS.security.PosPrincipal.canonicalRole(r.getRol().getNombre()).equals("ADMIN"));
        if (currentAdministrator && !remainsAdministrator && relaciones.countActiveAdministrators() <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conserva al menos un administrador activo");
    }
    public record UsuarioRequest(@NotBlank @Size(max = 50) String usuario, @Size(max = 72) String contrasena,
            @NotBlank @Size(max = 150) String nombreCompleto, @Email @Size(max = 100) String correoElectronico,
            @NotNull EstadoUsuario estado, @NotEmpty List<@NotNull @Min(1) Integer> rolIds) { }
    public record UsuarioResponse(Integer id, String usuario, String nombreCompleto, String correoElectronico,
            EstadoUsuario estado, List<Integer> rolIds, List<String> roles) { }
}
