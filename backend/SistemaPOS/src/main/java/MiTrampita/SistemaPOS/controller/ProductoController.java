package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.Producto;
import MiTrampita.SistemaPOS.service.ProductoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {
    private final ProductoService service;

    @GetMapping public List<Producto> listar() { return service.listar(); }
    @GetMapping("/{id}") public Producto obtener(@PathVariable Integer id) { return service.obtener(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Producto crear(@Valid @RequestBody Producto producto) { producto.setId(null); return service.guardar(producto); }
    @PutMapping("/{id}") public Producto actualizar(@PathVariable Integer id, @Valid @RequestBody Producto producto) { producto.setId(id); return service.guardar(producto); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void eliminar(@PathVariable Integer id) { service.eliminar(id); }
}
