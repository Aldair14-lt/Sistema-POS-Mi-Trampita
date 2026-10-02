package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.Mesa;
import MiTrampita.SistemaPOS.dto.MesaRequest;
import MiTrampita.SistemaPOS.service.MesaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/mesas") @RequiredArgsConstructor
public class MesaController {
    private final MesaService service;
    @GetMapping public List<Mesa> listar(@RequestParam(required = false) Integer areaId) { return service.listar(areaId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Mesa crear(@Valid @RequestBody MesaRequest request) { return service.guardar(null, request); }
    @PutMapping("/{id}")
    public Mesa actualizar(@PathVariable Integer id, @Valid @RequestBody MesaRequest request) { return service.guardar(id, request); }
    @PatchMapping("/{id}/abrir") public Mesa abrir(@PathVariable Integer id) { return service.abrir(id); }
    @PatchMapping("/{id}/liberar") public Mesa liberar(@PathVariable Integer id) { return service.liberar(id); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) { service.eliminar(id); }

}
