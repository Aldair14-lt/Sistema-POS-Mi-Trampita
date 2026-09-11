package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.Mesa;
import MiTrampita.SistemaPOS.service.MesaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/mesas")
@RequiredArgsConstructor
public class MesaController {
    private final MesaService service;

    @GetMapping
    public List<Mesa> listar() {
        return service.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mesa crear(@Valid @RequestBody Mesa mesa) {
        return service.crear(mesa);
    }

    @PutMapping("/{id}")
    public Mesa actualizar(@PathVariable Integer id, @Valid @RequestBody Mesa mesa) {
        return service.actualizar(id, mesa);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) {
        service.eliminar(id);
    }
}
