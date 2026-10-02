package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.service.MarketingService;
import MiTrampita.SistemaPOS.repositorio.DetalleVentaRepository;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.List;

@RestController @RequestMapping("/api/marketing") @RequiredArgsConstructor
public class MarketingController {
    private final MarketingService service;
    @GetMapping public MarketingService.Dashboard dashboard() { return service.dashboard(); }
    @GetMapping("/frecuentes") public List<MarketingService.ClienteResumen> frecuentes() { return service.frecuentes(); }
    @GetMapping("/cumpleaneros")
    public List<MarketingService.ClienteResumen> cumpleaneros(@RequestParam(required = false) @Min(1) @Max(12) Integer mes) {
        return service.cumpleaneros(mes == null ? LocalDate.now(ZoneId.of("America/Lima")).getMonthValue() : mes);
    }
    @GetMapping("/clientes/{id}/consumo")
    public List<DetalleVentaRepository.ConsumoProducto> consumo(@PathVariable @Min(1) Integer id) { return service.consumo(id); }
}