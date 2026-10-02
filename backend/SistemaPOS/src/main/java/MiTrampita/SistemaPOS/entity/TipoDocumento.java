package MiTrampita.SistemaPOS.entity;

import java.util.Locale;

public enum TipoDocumento {
    NOTA_VENTA, BOLETA, FACTURA;

    public static TipoDocumento desdeCatalogo(String nombre) {
        return switch (nombre.trim().toUpperCase(Locale.ROOT).replace(' ', '_')) {
            case "NOTA_DE_VENTA", "NOTA_VENTA" -> NOTA_VENTA;
            case "BOLETA" -> BOLETA;
            case "FACTURA" -> FACTURA;
            default -> throw new IllegalArgumentException("Tipo de comprobante no soportado");
        };
    }
}
