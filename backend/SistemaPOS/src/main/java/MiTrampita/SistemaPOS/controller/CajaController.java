package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.dto.CajaResponse;
import MiTrampita.SistemaPOS.service.VentaService;
import MiTrampita.SistemaPOS.service.CajaService;
import MiTrampita.SistemaPOS.dto.SesionCajaDtos.*;
import MiTrampita.SistemaPOS.security.PosPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/caja") @RequiredArgsConstructor
public class CajaController {
    private final VentaService ventas;
    private final CajaService caja;
    @GetMapping("/resumen") public CajaResponse resumen() { return ventas.resumenCaja(); }
    @GetMapping("/sesiones/actual") public Resumen actual(@RequestParam @Min(1) Integer empresaId) { return caja.actual(empresaId); }
    @GetMapping("/sesiones") public List<Resumen> historial(@RequestParam @Min(1) Integer empresaId) { return caja.historial(empresaId); }
    @GetMapping("/sesiones/{id}") public Resumen turno(@PathVariable @Min(1) Integer id) { return caja.obtener(id); }
    @PostMapping("/sesiones/abrir") public Resumen abrir(@Valid @RequestBody Abrir request, @AuthenticationPrincipal PosPrincipal principal) {
        return caja.abrir(request, principal.id());
    }
    @PostMapping("/sesiones/{id}/cerrar") public Resumen cerrar(@PathVariable @Min(1) Integer id, @Valid @RequestBody Cerrar request,
            @AuthenticationPrincipal PosPrincipal principal) { return caja.cerrar(id, request, principal.id()); }
}
