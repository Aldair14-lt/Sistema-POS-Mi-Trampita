package MiTrampita.SistemaPOS.config;

import MiTrampita.SistemaPOS.entity.EstadoUsuario;
import MiTrampita.SistemaPOS.entity.Rol;
import MiTrampita.SistemaPOS.entity.Usuario;
import MiTrampita.SistemaPOS.entity.UsuarioRol;
import MiTrampita.SistemaPOS.repositorio.RolRepository;
import MiTrampita.SistemaPOS.repositorio.UsuarioRepository;
import MiTrampita.SistemaPOS.repositorio.UsuarioRolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Crea los datos mínimos de desarrollo sin sobrescribir usuarios existentes. */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final UsuarioRolRepository usuarioRoles;

    @Override
    @Transactional
    public void run(String... args) {
        Rol adminRole = roles.findByNombreIgnoreCase("ADMIN").orElseGet(() -> {
            Rol role = new Rol();
            role.setNombre("ADMIN");
            role.setDescripcion("Administrador");
            return roles.save(role);
        });

        Usuario admin = usuarios.findByUsuarioIgnoreCase("admin").orElseGet(() -> {
            Usuario user = new Usuario();
            user.setUsuario("admin");
            user.setContrasena("admin123");
            user.setNombreCompleto("Administrador");
            user.setEstado(EstadoUsuario.activo);
            return usuarios.save(user);
        });

        if (!usuarioRoles.existsByUsuario_IdAndRol_Id(admin.getId(), adminRole.getId())) {
            UsuarioRol relation = new UsuarioRol();
            relation.setUsuario(admin);
            relation.setRol(adminRole);
            usuarioRoles.save(relation);
        }
    }
}
