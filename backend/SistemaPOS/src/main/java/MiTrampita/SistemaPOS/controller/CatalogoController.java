package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.repositorio.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogoController {
    private final CategoriaRepository categorias;
    private final MarcaRepository marcas;
    private final ProveedorRepository proveedores;
    private final ClienteRepository clientes;
    private final EmpresaRepository empresas;
    private final TipoComprobanteRepository comprobantes;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;

    @GetMapping("/categorias") public List<Categoria> categorias() { return categorias.findAll(); }
    @PostMapping("/categorias") @ResponseStatus(HttpStatus.CREATED) public Categoria crearCategoria(@Valid @RequestBody Categoria value) { return categorias.save(value); }
    @PutMapping("/categorias/{id}") public Categoria actualizarCategoria(@PathVariable Integer id, @Valid @RequestBody Categoria value) { value.setId(id); return actualizar(categorias, id, value, "Categoría"); }
    @GetMapping("/marcas") public List<Marca> marcas() { return marcas.findAll(); }
    @PostMapping("/marcas") @ResponseStatus(HttpStatus.CREATED) public Marca crearMarca(@Valid @RequestBody Marca value) { return marcas.save(value); }
    @PutMapping("/marcas/{id}") public Marca actualizarMarca(@PathVariable Integer id, @Valid @RequestBody Marca value) { value.setId(id); return actualizar(marcas, id, value, "Marca"); }
    @GetMapping("/proveedores") public List<Proveedor> proveedores() { return proveedores.findAll(); }
    @PostMapping("/proveedores") @ResponseStatus(HttpStatus.CREATED) public Proveedor crearProveedor(@Valid @RequestBody Proveedor value) { return proveedores.save(value); }
    @PutMapping("/proveedores/{id}") public Proveedor actualizarProveedor(@PathVariable Integer id, @Valid @RequestBody Proveedor value) { value.setId(id); return actualizar(proveedores, id, value, "Proveedor"); }
    @GetMapping("/clientes") public List<Cliente> clientes() { return clientes.findAll(); }
    @PostMapping("/clientes") @ResponseStatus(HttpStatus.CREATED) public Cliente crearCliente(@Valid @RequestBody Cliente value) { return clientes.save(value); }
    @PutMapping("/clientes/{id}") public Cliente actualizarCliente(@PathVariable Integer id, @Valid @RequestBody Cliente value) { value.setId(id); return actualizar(clientes, id, value, "Cliente"); }
    @GetMapping("/empresas") public List<Empresa> empresas() { return empresas.findAll(); }
    @PostMapping("/empresas") @ResponseStatus(HttpStatus.CREATED) public Empresa crearEmpresa(@Valid @RequestBody Empresa value) { return empresas.save(value); }
    @PutMapping("/empresas/{id}") public Empresa actualizarEmpresa(@PathVariable Integer id, @Valid @RequestBody Empresa value) { value.setId(id); return actualizar(empresas, id, value, "Empresa"); }
    @GetMapping("/tipos-comprobante") public List<TipoComprobante> comprobantes() { return comprobantes.findAll(); }
    @PostMapping("/tipos-comprobante") @ResponseStatus(HttpStatus.CREATED) public TipoComprobante crearComprobante(@Valid @RequestBody TipoComprobante value) { return comprobantes.save(value); }
    @PutMapping("/tipos-comprobante/{id}") public TipoComprobante actualizarComprobante(@PathVariable Integer id, @Valid @RequestBody TipoComprobante value) { value.setId(id); return actualizar(comprobantes, id, value, "Tipo de comprobante"); }
    @GetMapping("/usuarios") public List<Usuario> usuarios() { return usuarios.findAll(); }
    @PostMapping("/usuarios") @ResponseStatus(HttpStatus.CREATED) public Usuario crearUsuario(@Valid @RequestBody Usuario value) { return usuarios.save(value); }
    @PutMapping("/usuarios/{id}") public Usuario actualizarUsuario(@PathVariable Integer id, @RequestBody Usuario value) {
        Usuario current = usuarios.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (value.getContrasena() == null || value.getContrasena().isBlank()) value.setContrasena(current.getContrasena());
        value.setId(id);
        return usuarios.save(value);
    }
    @GetMapping("/roles") public List<Rol> roles() { return roles.findAll(); }
    @PostMapping("/roles") @ResponseStatus(HttpStatus.CREATED) public Rol crearRol(@Valid @RequestBody Rol value) { return roles.save(value); }
    @PutMapping("/roles/{id}") public Rol actualizarRol(@PathVariable Integer id, @Valid @RequestBody Rol value) { value.setId(id); return actualizar(roles, id, value, "Rol"); }

    @DeleteMapping("/categorias/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminarCategoria(@PathVariable Integer id) { eliminar(categorias, id, "Categoría"); }
    @DeleteMapping("/marcas/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminarMarca(@PathVariable Integer id) { eliminar(marcas, id, "Marca"); }
    @DeleteMapping("/proveedores/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminarProveedor(@PathVariable Integer id) { eliminar(proveedores, id, "Proveedor"); }
    @DeleteMapping("/clientes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminarCliente(@PathVariable Integer id) { eliminar(clientes, id, "Cliente"); }
    @DeleteMapping("/empresas/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminarEmpresa(@PathVariable Integer id) { eliminar(empresas, id, "Empresa"); }
    @DeleteMapping("/tipos-comprobante/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminarComprobante(@PathVariable Integer id) { eliminar(comprobantes, id, "Tipo de comprobante"); }
    @DeleteMapping("/usuarios/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminarUsuario(@PathVariable Integer id) { eliminar(usuarios, id, "Usuario"); }
    @DeleteMapping("/roles/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminarRol(@PathVariable Integer id) { eliminar(roles, id, "Rol"); }

    private void eliminar(org.springframework.data.jpa.repository.JpaRepository<?, Integer> repository, Integer id, String recurso) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, recurso + " no encontrado");
        repository.deleteById(id);
    }

    private <T> T actualizar(org.springframework.data.jpa.repository.JpaRepository<T, Integer> repository, Integer id, T value, String recurso) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, recurso + " no encontrado");
        return repository.save(value);
    }
}
