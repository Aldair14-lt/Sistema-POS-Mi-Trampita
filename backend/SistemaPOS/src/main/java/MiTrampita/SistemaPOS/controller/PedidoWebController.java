package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.dto.PedidoWebDtos.*;
import MiTrampita.SistemaPOS.dto.VentaResponse;
import MiTrampita.SistemaPOS.service.PedidoWebService;
import MiTrampita.SistemaPOS.security.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor
public class PedidoWebController {
    private final PedidoWebService service;
    @GetMapping("/api/public/tiendas/{slug}/menu")
    public ResponseEntity<Menu> menu(@PathVariable String slug) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.menu(slug)); }
    @PostMapping("/api/public/tiendas/{slug}/pedidos")
    public ResponseEntity<Seguimiento> enviar(@PathVariable String slug, @Valid @RequestBody Enviar request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.enviar(slug, request));
    }
    @GetMapping("/api/public/tiendas/{slug}/pedidos/{codigo}")
    public ResponseEntity<Seguimiento> estado(@PathVariable String slug, @PathVariable UUID codigo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.consultar(slug, codigo));
    }
    @GetMapping("/api/web/configuracion") public List<Configuracion> configuraciones() { return service.configuraciones(); }
    @PutMapping("/api/web/configuracion/{empresaId}")
    public Configuracion configurar(@PathVariable Integer empresaId, @Valid @RequestBody Configurar request) { return service.configurar(empresaId, request); }
    @GetMapping("/api/web/solicitudes") public List<Solicitud> pendientes() { return service.pendientes(); }
    @PatchMapping("/api/web/solicitudes/{id}/aceptar")
    public VentaResponse aceptar(@PathVariable Integer id, @Valid @RequestBody Aceptar request, @AuthenticationPrincipal PosPrincipal principal) {
        return service.aceptar(id, request, principal.id());
    }
    @PatchMapping("/api/web/solicitudes/{id}/rechazar") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rechazar(@PathVariable Integer id, @Valid @RequestBody Rechazar request) { service.rechazar(id, request); }
}
