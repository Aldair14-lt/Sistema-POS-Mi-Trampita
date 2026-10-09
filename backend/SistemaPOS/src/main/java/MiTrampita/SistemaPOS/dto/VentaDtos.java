package MiTrampita.SistemaPOS.dto;

import jakarta.validation.Valid;
import MiTrampita.SistemaPOS.entity.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

/** Contratos validados compartidos entre controlador y servicio de ventas. */
public final class VentaDtos {
    private VentaDtos() { }
    public record VentaRequest(@NotNull @Min(1) Integer empresaId,
            @Min(1) Integer clienteId, @Valid ClienteRequest cliente,
            @NotNull @Min(1) Integer tipoComprobanteId,
            @NotBlank @Size(max = 50) String numeroComprobante,
            @Min(1) Integer mesaId,
            @NotEmpty @Size(max = 200) List<@NotNull @Valid ItemRequest> items,
            OrigenPedido origenPedido, TipoEntrega tipoEntrega,
            @Size(max = 255) String direccion, @Size(max = 20) String telefono,
            @Valid PagoParcialRequest pagoInicial, Boolean pagoTotal) {
        public VentaRequest(Integer empresaId, Integer clienteId, ClienteRequest cliente, Integer tipoComprobanteId,
                String numeroComprobante, Integer mesaId, List<ItemRequest> items) {
            this(empresaId, clienteId, cliente, tipoComprobanteId, numeroComprobante, mesaId, items, null, null, null, null, null, null);
        }
        @AssertTrue(message = "Local requiere mesa; WhatsApp/Web requieren recojo o delivery, teléfono y dirección para delivery")
        public boolean isEntregaValida() {
            if (origenPedido == null || origenPedido == OrigenPedido.LOCAL)
                return mesaId != null && (tipoEntrega == null || tipoEntrega == TipoEntrega.MESA)
                    && (direccion == null || direccion.isBlank());
            return mesaId == null && (tipoEntrega == TipoEntrega.RECOJO || tipoEntrega == TipoEntrega.DELIVERY)
                && telefono != null && telefono.matches("[+0-9 ()-]{6,20}")
                && (tipoEntrega != TipoEntrega.DELIVERY || (direccion != null && !direccion.isBlank()));
        }
        @AssertTrue(message = "Selecciona un cliente o completa sus datos, no ambos")
        public boolean isClienteValido() { return (clienteId != null) != (cliente != null); }
    }
    public record ClienteRequest(
            @NotBlank @Pattern(regexp = "\\d{6,20}", message = "El documento debe tener de 6 a 20 dígitos") String numeroDocumento,
            @NotBlank @Size(max = 150) String nombresRazonSocial,
            @Size(max = 255) String direccion, @Size(max = 20) String telefono,
            @Email @Size(max = 100) String correo, @PastOrPresent LocalDate fechaNacimiento) { }
    public record ItemRequest(@NotNull @Min(1) Integer productoId,
            @NotNull @Min(1) @Max(100000) Integer cantidad, @Size(max = 255) String observaciones) {
        public ItemRequest(Integer productoId, Integer cantidad) { this(productoId, cantidad, null); }
    }
    public record ActualizarItemsRequest(@NotEmpty @Size(max = 200) List<@NotNull @Valid ItemRequest> items,
            @NotNull @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String claveOperacion) { }
}
