package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.TipoEntrega;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class PedidoWebDtos {
    private PedidoWebDtos() { }
    public record Configurar(@NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(min = 3, max = 60) String slug,
            @NotNull Boolean activa, @NotNull Boolean recojo, @NotNull Boolean delivery, @Size(max = 300) String mensaje) {
        @AssertTrue(message = "Habilita recojo o delivery") public boolean isEntregaValida() { return Boolean.TRUE.equals(recojo) || Boolean.TRUE.equals(delivery); }
    }
    public record Configuracion(Integer empresaId, String empresa, String slug, boolean activa, boolean recojo, boolean delivery, String mensaje) { }
    public record ProductoMenu(Integer id, String nombre, String descripcion, String categoria, BigDecimal precioBase, BigDecimal precio, int limiteCantidad) { }
    public record Menu(String nombre, String direccion, String telefono, String mensaje, boolean recojo, boolean delivery, List<ProductoMenu> productos) { }
    public record Item(@NotNull @Min(1) Integer productoId, @NotNull @Min(1) @Max(20) Integer cantidad, @Size(max = 255) String observaciones) { }
    public record Enviar(@NotNull java.util.UUID claveOperacion, @NotBlank @Size(max = 150) String nombre,
            @NotBlank @Pattern(regexp = "\\d{8}", message = "Ingresa un DNI de 8 dígitos") String dni,
            @NotBlank @Pattern(regexp = "[+0-9 ()-]{6,20}") String telefono,
            @NotNull TipoEntrega tipoEntrega, @Size(max = 255) String direccion, @Size(max = 255) String observaciones,
            @NotEmpty @Size(max = 30) List<@NotNull @Valid Item> items,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal totalEsperado) {
        @AssertTrue(message = "Selecciona recojo o delivery y completa la dirección de delivery")
        public boolean isEntregaValida() { return tipoEntrega == TipoEntrega.RECOJO || (tipoEntrega == TipoEntrega.DELIVERY && direccion != null && !direccion.isBlank()); }
    }
    public record Aceptar(@NotNull @Min(1) Integer tipoComprobanteId) { }
    public record Rechazar(@NotBlank @Size(max = 255) String motivo) { }
    public record ItemResumen(Integer productoId, String nombre, int cantidad, BigDecimal precioBase, String observaciones) { }
    public record Seguimiento(String codigoSeguimiento, String referencia, String estado, BigDecimal total, String motivo) { }
    public record Solicitud(Integer id, Integer empresaId, String empresa, String referencia, String nombre, String dni, String telefono,
            TipoEntrega tipoEntrega, String direccion, String observaciones, OffsetDateTime fechaCreacion,
            BigDecimal subtotal, BigDecimal igv, BigDecimal total, List<ItemResumen> items) { }
}
