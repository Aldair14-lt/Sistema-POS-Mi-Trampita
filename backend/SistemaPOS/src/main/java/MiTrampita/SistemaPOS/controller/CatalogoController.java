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

    @GetMapping("/categorias")
    public List<Categoria> categorias() {
        return categorias.findAll();
    }

    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    public Categoria crearCategoria(@Valid @RequestBody Categoria value) {
        return categorias.save(value);
    }

    @PutMapping("/categorias/{id}")
    public Categoria actualizarCategoria(@PathVariable Integer id, @Valid @RequestBody Categoria value) {
        value.setId(id);
        return actualizar(categorias, id, value, "Categoría");
    }

    @GetMapping("/marcas")
    public List<Marca> marcas() {
        return marcas.findAll();
    }

    @PostMapping("/marcas")
    @ResponseStatus(HttpStatus.CREATED)
    public Marca crearMarca(@Valid @RequestBody Marca value) {
        return marcas.save(value);
    }

    @PutMapping("/marcas/{id}")
    public Marca actualizarMarca(@PathVariable Integer id, @Valid @RequestBody Marca value) {
        value.setId(id);
        return actualizar(marcas, id, value, "Marca");
    }

    @GetMapping("/proveedores")
    public List<Proveedor> proveedores() {
        return proveedores.findAll();
    }

    @PostMapping("/proveedores")
    @ResponseStatus(HttpStatus.CREATED)
    public Proveedor crearProveedor(@Valid @RequestBody Proveedor value) {
        return proveedores.save(value);
    }

    @PutMapping("/proveedores/{id}")
    public Proveedor actualizarProveedor(@PathVariable Integer id, @Valid @RequestBody Proveedor value) {
        value.setId(id);
        return actualizar(proveedores, id, value, "Proveedor");
    }

    @GetMapping("/pos/clientes")
    public List<MiTrampita.SistemaPOS.dto.ClientePosResponse> clientesPos() {
        return clientes.findAll().stream().map(MiTrampita.SistemaPOS.dto.ClientePosResponse::from).toList();
    }

    @GetMapping("/clientes")
    public List<Cliente> clientes() {
        return clientes.findAll();
    }

    @PostMapping("/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    public Cliente crearCliente(@Valid @RequestBody Cliente value) {
        value.setId(null);
        value.setFrecuenciaVisitas(0L);
        value.setTotalGastado(java.math.BigDecimal.ZERO);
        return clientes.save(value);
    }

    @PutMapping("/clientes/{id}")
    @org.springframework.transaction.annotation.Transactional
    public Cliente actualizarCliente(@PathVariable Integer id, @Valid @RequestBody Cliente value) {
        Cliente current = clientes.findByIdForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        current.setNumeroDocumento(value.getNumeroDocumento());
        current.setNombresRazonSocial(value.getNombresRazonSocial());
        current.setDireccion(value.getDireccion());
        current.setTelefono(value.getTelefono());
        current.setCorreo(value.getCorreo());
        current.setFechaNacimiento(value.getFechaNacimiento());
        return current;
    }

    @GetMapping({"/configuracion", "/empresas", "/pos/configuracion"})
    public List<Empresa> empresas() {
        return empresas.findAll();
    }

    @PostMapping({"/configuracion", "/empresas"})
    @ResponseStatus(HttpStatus.CREATED)
    public Empresa crearEmpresa(@Valid @RequestBody Empresa value) {
        return empresas.save(value);
    }

    @PutMapping({"/configuracion/{id}", "/empresas/{id}"})
    public Empresa actualizarEmpresa(@PathVariable Integer id, @Valid @RequestBody Empresa value) {
        value.setId(id);
        return actualizar(empresas, id, value, "Empresa");
    }

    @GetMapping("/tipos-comprobante")
    public List<TipoComprobante> comprobantes() {
        return comprobantes.findAll();
    }

    @PostMapping("/tipos-comprobante")
    @ResponseStatus(HttpStatus.CREATED)
    public TipoComprobante crearComprobante(@Valid @RequestBody TipoComprobante value) {
        value.setId(null);
        value.setUltimoCorrelativo(0L);
        return comprobantes.save(value);
    }

    @PutMapping("/tipos-comprobante/{id}")
    @org.springframework.transaction.annotation.Transactional
    public TipoComprobante actualizarComprobante(@PathVariable Integer id, @Valid @RequestBody TipoComprobante value) {
        var current = comprobantes.findByIdForUpdate(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de comprobante no encontrado"));
        if (current.getUltimoCorrelativo() > 0 &&
                (!current.getSerie().equals(value.getSerie()) || !current.getNombre().equals(value.getNombre())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Una serie emitida no se puede modificar; crea otra");
        current.setNombre(value.getNombre());
        current.setSerie(value.getSerie());
        current.setDescripcion(value.getDescripcion());
        return current;
    }

    @GetMapping("/roles")
    public List<Rol> roles() {
        return roles.findAll();
    }

    @DeleteMapping("/categorias/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCategoria(@PathVariable Integer id) {
        eliminar(categorias, id, "Categoría");
    }

    @DeleteMapping("/marcas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarMarca(@PathVariable Integer id) {
        eliminar(marcas, id, "Marca");
    }

    @DeleteMapping("/proveedores/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarProveedor(@PathVariable Integer id) {
        eliminar(proveedores, id, "Proveedor");
    }

    @DeleteMapping("/clientes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCliente(@PathVariable Integer id) {
        eliminar(clientes, id, "Cliente");
    }

    @DeleteMapping({"/configuracion/{id}", "/empresas/{id}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarEmpresa(@PathVariable Integer id) {
        eliminar(empresas, id, "Empresa");
    }

    @DeleteMapping("/tipos-comprobante/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarComprobante(@PathVariable Integer id) {
        eliminar(comprobantes, id, "Tipo de comprobante");
    }

    private void eliminar(org.springframework.data.jpa.repository.JpaRepository<?, Integer> repository, Integer id,
            String recurso) {
        if (!repository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, recurso + " no encontrado");
        repository.deleteById(id);
    }

    private <T> T actualizar(org.springframework.data.jpa.repository.JpaRepository<T, Integer> repository, Integer id,
            T value, String recurso) {
        if (!repository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, recurso + " no encontrado");
        return repository.save(value);
    }
}
