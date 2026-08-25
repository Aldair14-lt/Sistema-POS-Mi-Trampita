package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.MetodoPago;
import MiTrampita.SistemaPOS.entity.Venta;
import MiTrampita.SistemaPOS.service.VentaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
public class VentaController {
    private final VentaService service;
    @GetMapping public List<Venta> listar() { return service.listar(); }
    @GetMapping("/{id}") public Venta obtener(@PathVariable Integer id) { return service.obtener(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Venta registrar(@Valid @RequestBody VentaRequest request) {
        return service.registrar(request.empresaId(), request.usuarioId(), request.clienteId(), request.tipoComprobanteId(), request.numeroComprobante(), request.metodoPago(), request.itemVentaList());
    }
    public record VentaRequest(@NotNull Integer empresaId, @NotNull Integer usuarioId, @NotNull Integer clienteId,
                               @NotNull Integer tipoComprobanteId, @NotBlank String numeroComprobante,
                               MetodoPago metodoPago, @NotEmpty List<ItemRequest> items) {
        List<VentaService.ItemVenta> itemVentaList() { return items.stream().map(item -> new VentaService.ItemVenta(item.productoId(), item.cantidad())).toList(); }
    }
    public record ItemRequest(@NotNull Integer productoId, @NotNull @Min(1) Integer cantidad) { }
}
