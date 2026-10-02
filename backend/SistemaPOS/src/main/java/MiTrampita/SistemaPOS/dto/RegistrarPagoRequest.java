package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.MetodoPago;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record RegistrarPagoRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal monto,
        @NotNull MetodoPago metodoPago,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal montoRecibido,
        @Size(max = 100) String referencia,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String claveOperacion) {
    @AssertTrue(message = "En efectivo el monto recibido debe cubrir el abono")
    public boolean isEfectivoValido() {
        return metodoPago != MetodoPago.EFECTIVO ||
                (monto != null && montoRecibido != null && montoRecibido.compareTo(monto) >= 0);
    }
}
