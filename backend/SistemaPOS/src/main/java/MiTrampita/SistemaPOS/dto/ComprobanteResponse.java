package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.Comprobante;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ComprobanteResponse(Integer id, String tipo, String numero, String empresaRuc,
        String empresaNombre, String empresaDireccion, String clienteDocumento, String clienteNombre,
        String clienteDireccion, BigDecimal subtotal, BigDecimal igv, BigDecimal total,
        OffsetDateTime fechaEmision, List<Linea> detalles) {
    public static ComprobanteResponse from(Comprobante c) {
        if (c == null) return null;
        return new ComprobanteResponse(c.getId(), c.getTipo(), c.getSerie() + "-" + String.format("%08d", c.getCorrelativo()),
            c.getEmpresaRuc(), c.getEmpresaNombre(), c.getEmpresaDireccion(), c.getClienteDocumento(),
            c.getClienteNombre(), c.getClienteDireccion(), c.getSubtotal(), c.getIgv(), c.getTotal(),
            c.getFechaEmision(), c.getDetalles().stream().map(d -> new Linea(d.getId(), d.getProducto(),
                d.getCantidad(), d.getPrecioUnitario(), d.getSubtotal())).toList());
    }
    public record Linea(Integer id, String producto, Integer cantidad, BigDecimal precioUnitario, BigDecimal subtotal) { }
}
