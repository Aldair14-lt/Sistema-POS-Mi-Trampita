package MiTrampita.SistemaPOS.config;

import MiTrampita.SistemaPOS.entity.EstadoUsuario;
import MiTrampita.SistemaPOS.entity.Rol;
import MiTrampita.SistemaPOS.entity.Usuario;
import MiTrampita.SistemaPOS.entity.UsuarioRol;
import MiTrampita.SistemaPOS.entity.TipoComprobante;
import MiTrampita.SistemaPOS.entity.Categoria;
import MiTrampita.SistemaPOS.entity.Marca;
import MiTrampita.SistemaPOS.entity.Mesa;
import MiTrampita.SistemaPOS.repositorio.CategoriaRepository;
import MiTrampita.SistemaPOS.repositorio.MarcaRepository;
import MiTrampita.SistemaPOS.repositorio.RolRepository;
import MiTrampita.SistemaPOS.repositorio.TipoComprobanteRepository;
import MiTrampita.SistemaPOS.repositorio.UsuarioRepository;
import MiTrampita.SistemaPOS.repositorio.UsuarioRolRepository;
import MiTrampita.SistemaPOS.repositorio.MesaRepository;
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
    private final MesaRepository mesas;
    private final MiTrampita.SistemaPOS.repositorio.AreaRepository areas;
    private final org.springframework.security.crypto.password.PasswordEncoder passwords;
    @org.springframework.beans.factory.annotation.Value("${app.bootstrap.password:}")
    private String bootstrapPassword;
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

        for (String name : java.util.List.of("MOZO", "CAJA", "COCINERO")) {
            if (roles.findByNombreIgnoreCase(name).isEmpty()) {
                Rol role = new Rol();
                role.setNombre(name);
                role.setDescripcion(switch (name) {
                    case "MOZO" -> "Pedidos y comandas";
                    case "CAJA" -> "Cobros y comprobantes";
                    default -> "Preparación de pedidos; acceso exclusivo a cocina";
                });
                roles.save(role);
            }
        }
        if (usuarios.count() == 0 && !bootstrapPassword.isBlank()) {
            Usuario admin = new Usuario();
            admin.setUsuario("admin");
            admin.setContrasena(passwords.encode(bootstrapPassword));
            admin.setNombreCompleto("Administrador");
            admin.setEstado(EstadoUsuario.activo);
            usuarios.save(admin);
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
        ensureTables();
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

    private void ensureTables() {
        var names = java.util.List.of("Sal\u00f3n Principal", "Terraza", "Zona Recreacional", "Piscina");
        var available = names.stream().map(name -> areas.findByNombreIgnoreCase(name).orElseGet(() -> {
            var area = new MiTrampita.SistemaPOS.entity.Area();
            area.setNombre(name);
            return areas.save(area);
        })).toList();
        for (int number = 1; number <= 10; number++) {
            final int tableNumber = number;
            if (mesas.findByNumero(tableNumber).isEmpty()) {
                Mesa mesa = new Mesa();
                mesa.setNumero(tableNumber);
                mesa.setCapacidad(4);
                mesa.setArea(available.get(Math.min((tableNumber - 1) / 3, 3)));
                mesas.save(mesa);
            }
        }
    }
}
