package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.dto.*;
import MiTrampita.SistemaPOS.entity.AreaDestino;
import MiTrampita.SistemaPOS.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

@RestController @RequestMapping("/api/bar") @RequiredArgsConstructor
public class BarController {
    private final KitchenService service;
    private final OperationEvents events;
    @GetMapping("/items") public List<KitchenItemResponse> cola() { return service.cola(AreaDestino.BAR); }
    @PatchMapping("/items/{id}/estado") public KitchenItemResponse actualizar(@PathVariable Integer id, @Valid @RequestBody UpdateItemStatusRequest request) {
        return service.actualizar(id, request, false, AreaDestino.BAR);
    }
    @GetMapping(value = "/eventos", produces = "text/event-stream") public SseEmitter eventos() { return events.subscribe(true); }
}
