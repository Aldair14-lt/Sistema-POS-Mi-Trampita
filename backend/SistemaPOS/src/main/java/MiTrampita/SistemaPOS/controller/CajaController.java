package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.dto.CajaResponse;
import MiTrampita.SistemaPOS.service.VentaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/caja") @RequiredArgsConstructor
public class CajaController {
    private final VentaService ventas;
    @GetMapping("/resumen") public CajaResponse resumen() { return ventas.resumenCaja(); }
}
