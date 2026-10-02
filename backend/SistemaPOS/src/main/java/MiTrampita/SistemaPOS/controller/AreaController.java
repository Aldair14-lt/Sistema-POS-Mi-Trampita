package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.Area;
import MiTrampita.SistemaPOS.dto.AreaRequest;
import MiTrampita.SistemaPOS.service.AreaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/areas") @RequiredArgsConstructor
public class AreaController {
    private final AreaService service;
    @GetMapping public List<Area> listar() { return service.listar(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Area crear(@Valid @RequestBody AreaRequest request) { return service.guardar(null, request); }
    @PutMapping("/{id}")
    public Area actualizar(@PathVariable Integer id, @Valid @RequestBody AreaRequest request) { return service.guardar(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) { service.eliminar(id); }

}
