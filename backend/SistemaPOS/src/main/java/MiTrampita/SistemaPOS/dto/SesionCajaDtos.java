package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.MetodoPago;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public final class SesionCajaDtos {
    private SesionCajaDtos() { }
    public record Abrir(@NotNull @Min(1) Integer empresaId,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal montoInicial,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String claveOperacion) { }
    public record Cerrar(@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal efectivoDeclarado,
            @Size(max = 500) String observaciones) { }
    public record Resumen(Integer id, Integer empresaId, String empresaNombre, String empresaRuc,
            String abiertoPor, String cerradoPor, OffsetDateTime fechaApertura, OffsetDateTime fechaCierre,
            BigDecimal montoInicial, Map<MetodoPago, BigDecimal> montosPorMetodo, BigDecimal totalCobrado,
            BigDecimal efectivoEsperado, BigDecimal efectivoDeclarado, BigDecimal diferencia, String observaciones) { }
}
