package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.dto.VentaDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record RegistrarPedidoWhatsAppRequest(
        @NotNull @Min(1) Integer empresaId, @Min(1) Integer clienteId, @Valid ClienteRequest cliente,
        @NotNull @Min(1) Integer tipoComprobanteId, @NotBlank @Size(max = 50) String numeroComprobante,
        @NotNull TipoEntrega tipoEntrega, @Size(max = 255) String direccion,
        @NotBlank @Pattern(regexp = "[+0-9 ()-]{6,20}") String telefono,
        @NotEmpty @Size(max = 200) List<@NotNull @Valid ItemRequest> items,
        @Valid PagoParcialRequest pagoInicial, Boolean pagoTotal) {
    @AssertTrue(message = "Selecciona un cliente o completa sus datos, no ambos")
    public boolean isClienteValido() { return (clienteId != null) != (cliente != null); }
    @AssertTrue(message = "Selecciona recojo o delivery con dirección")
    public boolean isEntregaValida() {
        return tipoEntrega == TipoEntrega.RECOJO || (tipoEntrega == TipoEntrega.DELIVERY && direccion != null && !direccion.isBlank());
    }
    public VentaRequest toVenta() {
        return new VentaRequest(empresaId, clienteId, cliente, tipoComprobanteId, numeroComprobante, null,
            items, OrigenPedido.WHATSAPP, tipoEntrega, direccion, telefono, pagoInicial, pagoTotal);
    }
}
