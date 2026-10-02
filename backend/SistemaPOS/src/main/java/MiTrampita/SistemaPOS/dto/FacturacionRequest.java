package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.TipoDocumento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** La factura tiene su propio contrato: sus campos obligatorios no afectan a una boleta. */
public record FacturacionRequest(
        @NotNull TipoDocumento tipoComprobante,
        @Valid Factura factura,
        @Pattern(regexp = "[0-9]{8}", message = "El DNI debe tener 8 dígitos") String dni,
        @Size(max = 150) String nombreCliente) {
    public record Factura(
            @NotBlank @Pattern(regexp = "[0-9]{11}", message = "El RUC debe tener 11 dígitos") String ruc,
            @NotBlank @Size(max = 150) String razonSocial,
            @NotBlank @Size(max = 255) String direccionFiscal) { }

    @AssertTrue(message = "Factura requiere datos fiscales; DNI corresponde únicamente a Boleta")
    public boolean isTipoValido() {
        return tipoComprobante != null
            && (tipoComprobante == TipoDocumento.FACTURA ? factura != null && dni == null : factura == null)
            && (tipoComprobante == TipoDocumento.BOLETA || dni == null);
    }
}
