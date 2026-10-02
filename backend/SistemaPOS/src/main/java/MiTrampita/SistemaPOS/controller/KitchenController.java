package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.dto.*;
import MiTrampita.SistemaPOS.service.KitchenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/cocina") @RequiredArgsConstructor
public class KitchenController {
    private final KitchenService service;
    @GetMapping("/items")
    public List<KitchenItemResponse> cola() { return service.cola(); }
    @PatchMapping("/items/{id}/estado")
    public KitchenItemResponse actualizar(@PathVariable Integer id, @Valid @RequestBody UpdateItemStatusRequest request) {
        return service.actualizar(id, request, false);
    }
}
