package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.dto.*;
import MiTrampita.SistemaPOS.dto.RegistrarPagoRequest;
import MiTrampita.SistemaPOS.dto.UpdateItemStatusRequest;
import MiTrampita.SistemaPOS.dto.KitchenItemResponse;
import MiTrampita.SistemaPOS.dto.VentaDtos.*;
import MiTrampita.SistemaPOS.security.PosPrincipal;
import MiTrampita.SistemaPOS.service.VentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController @RequestMapping("/api/ventas") @RequiredArgsConstructor
public class VentaController {
    private final VentaService service;
    private final MiTrampita.SistemaPOS.service.KitchenService cocina;
    @GetMapping public List<VentaResponse> listar() { return service.listar(false).stream().map(VentaResponse::from).toList(); }
    @GetMapping("/abiertas") public List<VentaResponse> abiertas(@AuthenticationPrincipal PosPrincipal principal) {
        return service.listar(true).stream().filter(v -> puedeVer(principal, v)).map(VentaResponse::from).toList();
    }
    @GetMapping("/{id}")
    public VentaResponse obtener(@PathVariable Integer id, @AuthenticationPrincipal PosPrincipal principal) {
        Venta venta = service.obtener(id);
        if (!puedeVer(principal, venta))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administración y caja pueden consultar comprobantes");
        return VentaResponse.from(venta);
    }

    private boolean puedeVer(PosPrincipal principal, Venta venta) {
        return principal.roles().contains("ADMIN") || principal.roles().contains("CAJA")
            || (venta.getEstado() == EstadoVenta.ABIERTA && venta.getOrigenPedido() == OrigenPedido.LOCAL);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse registrar(@Valid @RequestBody VentaRequest request, @AuthenticationPrincipal PosPrincipal principal) {
        if (!principal.roles().contains("ADMIN") && !principal.roles().contains("CAJA")
                && ((request.origenPedido() != null && request.origenPedido() != OrigenPedido.LOCAL) || request.pagoInicial() != null))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo Caja registra pedidos externos y pagos");
        return VentaResponse.from(service.registrar(request, principal.id()));
    }
    @PostMapping("/whatsapp") @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse whatsapp(@Valid @RequestBody RegistrarPedidoWhatsAppRequest request,
            @AuthenticationPrincipal PosPrincipal principal) {
        return VentaResponse.from(service.registrar(request.toVenta(), principal.id()));
    }
    @PostMapping("/{id}/cobrar")
    public VentaResponse cobrar(@PathVariable Integer id, @Valid @RequestBody CobrarVentaRequest request,
            @AuthenticationPrincipal PosPrincipal principal) {
        return VentaResponse.from(service.cobrar(id, request, principal.id()));
    }
    @PatchMapping("/{id}/solicitar-cuenta")
    public VentaResponse solicitarCuenta(@PathVariable Integer id) { return VentaResponse.from(service.solicitarCuenta(id)); }
    @PatchMapping("/{id}/items")
    public VentaResponse agregarItems(@PathVariable Integer id, @Valid @RequestBody ActualizarItemsRequest request) {
        return VentaResponse.from(service.agregarItems(id, request.items()));
    }
    @PatchMapping("/{id}/cerrar")
    public VentaResponse cerrar(@PathVariable Integer id) {
        return VentaResponse.from(service.cerrar(id));
    }
    @PostMapping("/{id}/pagos") @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse pagar(@PathVariable Integer id, @Valid @RequestBody RegistrarPagoRequest request,
            @AuthenticationPrincipal PosPrincipal principal) {
        return VentaResponse.from(service.registrarPago(id, request, principal.id()));
    }
    @GetMapping("/{id}/pagos")
    public List<VentaResponse.PagoResponse> pagos(@PathVariable Integer id) {
        return VentaResponse.from(service.obtener(id)).pagos();
    }
    @PatchMapping("/items/{id}/servir")
    public KitchenItemResponse servir(@PathVariable Integer id, @Valid @RequestBody UpdateItemStatusRequest request,
            @AuthenticationPrincipal PosPrincipal principal) {
        validarItemLocal(id, principal);
        return cocina.actualizar(id, request, true);
    }

    @PatchMapping("/items/{id}/cancelar")
    public VentaResponse cancelar(@PathVariable Integer id, @Valid @RequestBody CancelarItemRequest request,
            @AuthenticationPrincipal PosPrincipal principal) {
        validarItemLocal(id, principal);
        return VentaResponse.from(service.cancelarItem(id, request, principal.id()));
    }

    private void validarItemLocal(Integer id, PosPrincipal principal) {
        if (!principal.roles().contains("ADMIN") && !principal.roles().contains("CAJA")) service.validarItemLocal(id);
    }

}
