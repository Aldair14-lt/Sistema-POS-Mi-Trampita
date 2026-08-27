package MiTrampita.SistemaPOS.config;

import MiTrampita.SistemaPOS.entity.EstadoUsuario;
import MiTrampita.SistemaPOS.entity.Rol;
import MiTrampita.SistemaPOS.entity.Usuario;
import MiTrampita.SistemaPOS.entity.UsuarioRol;
import MiTrampita.SistemaPOS.entity.TipoComprobante;
import MiTrampita.SistemaPOS.entity.Categoria;
import MiTrampita.SistemaPOS.entity.Marca;
import MiTrampita.SistemaPOS.repositorio.CategoriaRepository;
import MiTrampita.SistemaPOS.repositorio.MarcaRepository;
import MiTrampita.SistemaPOS.repositorio.RolRepository;
import MiTrampita.SistemaPOS.repositorio.TipoComprobanteRepository;
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
    private final TipoComprobanteRepository comprobantes;
    private final CategoriaRepository categorias;
    private final MarcaRepository marcas;

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

        ensureReceipt("BOLETA", "B001", "Boleta de venta");
        ensureReceipt("FACTURA", "F001", "Factura de venta");
        ensureReceipt("NOTA DE VENTA", "NV01", "Comprobante interno");
        ensureCategory("Comidas", "Platos, combos y alimentos preparados");
        ensureCategory("Bebidas", "Bebidas frías y calientes");
        ensureBrand("Sin marca");
    }

    private void ensureReceipt(String name, String series, String description) {
        if (comprobantes.findByNombreIgnoreCaseAndSerie(name, series).isEmpty()) {
            TipoComprobante receipt = new TipoComprobante();
            receipt.setNombre(name);
            receipt.setSerie(series);
            receipt.setDescripcion(description);
            comprobantes.save(receipt);
        }
    }

    private void ensureCategory(String name, String description) {
        if (categorias.findByNombreIgnoreCase(name).isEmpty()) {
            Categoria category = new Categoria();
            category.setNombre(name);
            category.setDescripcion(description);
            categorias.save(category);
        }
    }

    private void ensureBrand(String name) {
        if (marcas.findByNombreIgnoreCase(name).isEmpty()) {
            Marca brand = new Marca();
            brand.setNombre(name);
            marcas.save(brand);
        }
    }
}
