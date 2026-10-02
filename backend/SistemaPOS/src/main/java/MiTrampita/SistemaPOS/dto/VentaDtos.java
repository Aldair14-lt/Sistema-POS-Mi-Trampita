package MiTrampita.SistemaPOS.dto;

import jakarta.validation.Valid;
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
            @NotNull(message = "Faltan datos de la mesa") @Min(1) Integer mesaId,
            @NotEmpty @Size(max = 200) List<@NotNull @Valid ItemRequest> items) {
        @AssertTrue(message = "Selecciona un cliente o completa sus datos, no ambos")
        public boolean isClienteValido() { return (clienteId != null) != (cliente != null); }
    }
    public record ClienteRequest(
            @NotBlank @Pattern(regexp = "\\d{6,20}", message = "El documento debe tener de 6 a 20 dígitos") String numeroDocumento,
            @NotBlank @Size(max = 150) String nombresRazonSocial,
            @Size(max = 255) String direccion, @Size(max = 20) String telefono,
            @Email @Size(max = 100) String correo, @PastOrPresent LocalDate fechaNacimiento) { }
    public record ItemRequest(@NotNull @Min(1) Integer productoId,
            @NotNull @Min(1) @Max(100000) Integer cantidad) { }
    public record ActualizarItemsRequest(@NotEmpty @Size(max = 200) List<@NotNull @Valid ItemRequest> items) { }
}
