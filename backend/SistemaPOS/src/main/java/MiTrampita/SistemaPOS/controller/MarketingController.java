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
    @GetMapping("/proximos-cumpleaneros")
    public List<MarketingService.ClienteResumen> proximos(@RequestParam(defaultValue = "30") @Min(1) @Max(366) int dias) { return service.proximosCumpleaneros(dias); }
    @GetMapping("/inactivos")
    public List<MarketingService.ClienteResumen> inactivos(@RequestParam(defaultValue = "60") @Min(1) @Max(3650) int dias) { return service.inactivos(dias); }
    @GetMapping("/mejores") public List<MarketingService.ClienteResumen> mejores() { return service.mejores(); }
    @GetMapping(value = "/exportar.csv", produces = "text/csv;charset=UTF-8")
    public org.springframework.http.ResponseEntity<byte[]> csv(@RequestParam(defaultValue = "todos") String segmento) {
        return org.springframework.http.ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=mi-trampita-meta.csv")
            .header("Cache-Control", "no-store")
            .body(service.exportarCsv(segmento).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @GetMapping public MarketingService.Dashboard dashboard() { return service.dashboard(); }
    @GetMapping("/frecuentes") public List<MarketingService.ClienteResumen> frecuentes() { return service.frecuentes(); }
    @GetMapping("/cumpleaneros")
    public List<MarketingService.ClienteResumen> cumpleaneros(@RequestParam(required = false) @Min(1) @Max(12) Integer mes) {
        return service.cumpleaneros(mes == null ? LocalDate.now(ZoneId.of("America/Lima")).getMonthValue() : mes);
    }
    @GetMapping("/clientes/{id}/consumo")
    public List<DetalleVentaRepository.ConsumoProducto> consumo(@PathVariable @Min(1) Integer id) { return service.consumo(id); }
}
