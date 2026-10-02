package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.dto.VentaDtos.*;
import MiTrampita.SistemaPOS.entity.TipoEntrega;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** El precio y el saldo se calculan en el servidor, nunca se aceptan desde el navegador. */
public record PedidoOnlineRequest(
        @NotNull @Min(1) Integer empresaId, @Min(1) Integer clienteId, @Valid ClienteRequest cliente,
        @NotNull @Min(1) Integer tipoComprobanteId, @NotBlank @Size(max = 50) String numeroComprobante,
        @NotNull TipoEntrega tipoEntrega, @Size(max = 255) String direccionEnvio,
        @NotEmpty @Size(max = 200) List<@NotNull @Valid ItemRequest> items,
        @Valid RegistrarPagoRequest pagoInicial, Boolean pagoTotal) {
    @AssertTrue(message = "Selecciona un cliente o completa sus datos, no ambos")
    public boolean isClienteValido() { return (clienteId != null) != (cliente != null); }
    @AssertTrue(message = "Un pedido online requiere RECOJO o DELIVERY; delivery requiere dirección")
    public boolean isEntregaValida() {
        return tipoEntrega == TipoEntrega.RECOJO ||
                (tipoEntrega == TipoEntrega.DELIVERY && direccionEnvio != null && !direccionEnvio.isBlank());
    }
    @AssertTrue(message = "El pago total requiere un pago inicial")
    public boolean isPagoTotalValido() { return !Boolean.TRUE.equals(pagoTotal) || pagoInicial != null; }
}
