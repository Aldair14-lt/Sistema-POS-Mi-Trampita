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

    @GetMapping
    public List<Venta> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public Venta obtener(@PathVariable Integer id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Venta registrar(@Valid @RequestBody VentaRequest request) {
        VentaService.ClienteData client = request.cliente() == null ? null
                : new VentaService.ClienteData(
                        request.cliente().numeroDocumento(), request.cliente().nombresRazonSocial(),
                        request.cliente().direccion(),
                        request.cliente().telefono(), request.cliente().correo());
        return service.registrar(request.empresaId(), request.usuarioId(), request.clienteId(), client,
                request.tipoComprobanteId(), request.numeroComprobante(), request.metodoPago(),
                request.itemVentaList());
    }

    public record VentaRequest(@NotNull(message = "La empresa es obligatoria") Integer empresaId,
            @NotNull(message = "El usuario es obligatorio") Integer usuarioId,
            Integer clienteId,
            @Valid ClienteRequest cliente,
            @NotNull(message = "El tipo de comprobante es obligatorio") Integer tipoComprobanteId,
            @NotBlank(message = "El número de comprobante es obligatorio") @Size(max = 50, message = "El número de comprobante no puede superar 50 caracteres") String numeroComprobante,
            @NotNull(message = "El método de pago es obligatorio") MetodoPago metodoPago,
            @NotEmpty(message = "La venta requiere al menos un producto") @Valid List<ItemRequest> items) {
        @AssertTrue(message = "Selecciona un cliente o completa sus datos")
        public boolean tieneCliente() {
            return clienteId != null || cliente != null;
        }

        List<VentaService.ItemVenta> itemVentaList() {
            return items.stream().map(item -> new VentaService.ItemVenta(item.productoId(), item.cantidad())).toList();
        }
    }

    public record ClienteRequest(
            @NotBlank(message = "El documento del cliente es obligatorio") @Size(max = 20, message = "El documento no puede superar 20 caracteres") String numeroDocumento,
            @NotBlank(message = "El nombre o razón social es obligatorio") @Size(max = 150, message = "El nombre no puede superar 150 caracteres") String nombresRazonSocial,
            @Size(max = 255, message = "La dirección no puede superar 255 caracteres") String direccion,
            @Size(max = 20, message = "El teléfono no puede superar 20 caracteres") String telefono,
            @Email(message = "El correo del cliente no es válido") @Size(max = 100, message = "El correo no puede superar 100 caracteres") String correo) {
    }

    public record ItemRequest(@NotNull(message = "El producto es obligatorio") Integer productoId,
            @NotNull(message = "La cantidad es obligatoria") @Min(value = 1, message = "La cantidad debe ser mayor que cero") @Max(value = 100000, message = "La cantidad es demasiado grande") Integer cantidad) {
    }
}
