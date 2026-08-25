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

    @GetMapping("/categorias") public List<Categoria> categorias() { return categorias.findAll(); }
    @PostMapping("/categorias") @ResponseStatus(HttpStatus.CREATED) public Categoria crearCategoria(@Valid @RequestBody Categoria value) { return categorias.save(value); }
    @GetMapping("/marcas") public List<Marca> marcas() { return marcas.findAll(); }
    @PostMapping("/marcas") @ResponseStatus(HttpStatus.CREATED) public Marca crearMarca(@Valid @RequestBody Marca value) { return marcas.save(value); }
    @GetMapping("/proveedores") public List<Proveedor> proveedores() { return proveedores.findAll(); }
    @PostMapping("/proveedores") @ResponseStatus(HttpStatus.CREATED) public Proveedor crearProveedor(@Valid @RequestBody Proveedor value) { return proveedores.save(value); }
    @GetMapping("/clientes") public List<Cliente> clientes() { return clientes.findAll(); }
    @PostMapping("/clientes") @ResponseStatus(HttpStatus.CREATED) public Cliente crearCliente(@Valid @RequestBody Cliente value) { return clientes.save(value); }
    @GetMapping("/empresas") public List<Empresa> empresas() { return empresas.findAll(); }
    @PostMapping("/empresas") @ResponseStatus(HttpStatus.CREATED) public Empresa crearEmpresa(@Valid @RequestBody Empresa value) { return empresas.save(value); }
    @GetMapping("/tipos-comprobante") public List<TipoComprobante> comprobantes() { return comprobantes.findAll(); }
    @PostMapping("/tipos-comprobante") @ResponseStatus(HttpStatus.CREATED) public TipoComprobante crearComprobante(@Valid @RequestBody TipoComprobante value) { return comprobantes.save(value); }

    @DeleteMapping("/categorias/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminarCategoria(@PathVariable Integer id) { eliminar(categorias, id, "Categoría"); }
    @DeleteMapping("/marcas/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminarMarca(@PathVariable Integer id) { eliminar(marcas, id, "Marca"); }
    @DeleteMapping("/proveedores/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminarProveedor(@PathVariable Integer id) { eliminar(proveedores, id, "Proveedor"); }
    @DeleteMapping("/clientes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminarCliente(@PathVariable Integer id) { eliminar(clientes, id, "Cliente"); }
    @DeleteMapping("/empresas/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminarEmpresa(@PathVariable Integer id) { eliminar(empresas, id, "Empresa"); }
    @DeleteMapping("/tipos-comprobante/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminarComprobante(@PathVariable Integer id) { eliminar(comprobantes, id, "Tipo de comprobante"); }

    private void eliminar(org.springframework.data.jpa.repository.JpaRepository<?, Integer> repository, Integer id, String recurso) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, recurso + " no encontrado");
        repository.deleteById(id);
    }
}
