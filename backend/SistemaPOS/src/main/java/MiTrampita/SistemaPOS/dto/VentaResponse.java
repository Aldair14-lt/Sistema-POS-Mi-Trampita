package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Evita exponer contraseñas, contactos del personal o métricas de marketing en el POS. */
public record VentaResponse(Integer id, Empresa empresa, UsuarioResumen usuario, ClientePosResponse cliente,
        TipoComprobante tipoComprobante, Mesa mesa, String numeroComprobante, BigDecimal subtotal,
        BigDecimal igv, BigDecimal total, MetodoPago metodoPago, EstadoVenta estado,
        OffsetDateTime fechaVenta, OffsetDateTime fechaCobro, List<DetalleResponse> detalles,
        EstadoCuenta estadoCuenta, OrigenPedido origenPedido, TipoEntrega tipoEntrega, String direccionEnvio,
        BigDecimal totalPagado, BigDecimal saldoPendiente, List<PagoResponse> pagos,
        String telefonoEntrega, boolean cuentaSolicitada, OffsetDateTime fechaSolicitudCuenta, ComprobanteResponse comprobante, String observacionesPedido) {
    public static VentaResponse from(Venta v) {
        BigDecimal abonado = v.getPagos().stream().map(PagoVenta::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new VentaResponse(v.getId(), v.getEmpresa(),
                new UsuarioResumen(v.getUsuario().getId(), v.getUsuario().getUsuario(), v.getUsuario().getNombreCompleto()),
                ClientePosResponse.from(v.getCliente()), v.getTipoComprobante(), v.getMesa(),
                v.getNumeroComprobante(), v.getSubtotal(), v.getIgv(), v.getTotal(), v.getMetodoPago(), v.getEstado(),
                v.getFechaVenta(), v.getFechaCobro(), v.getDetalles().stream().map(d -> new DetalleResponse(d.getId(),
                    new ProductoResumen(d.getProducto().getId(), d.getProducto().getNombre()),
                    d.getCantidad(), d.getPrecioUnitario(), d.getSubtotal(), d.getEstadoPreparacion(),
                    d.getFechaPedido(), d.getFechaEstado(), d.getMotivoCancelacion(), d.getAreaDestino(), d.getObservaciones())).toList(),
                v.getEstadoCuenta(), v.getOrigenPedido(), v.getTipoEntrega(), v.getDireccionEnvio(),
                abonado, v.getTotal().subtract(abonado), v.getPagos().stream().map(p -> new PagoResponse(
                    p.getId(), p.getMonto(), p.getMetodoPago(), p.getMontoRecibido(),
                    p.getMontoRecibido().subtract(p.getMonto()), p.getReferencia(), p.getFechaPago(),
                    p.getUsuario().getNombreCompleto(), p.getClaveOperacion())).toList(),
                v.getTelefonoEntrega(), v.isCuentaSolicitada(), v.getFechaSolicitudCuenta(), ComprobanteResponse.from(v.getComprobante()), v.getObservacionesPedido());
    }
    public record UsuarioResumen(Integer id, String usuario, String nombreCompleto) { }
    public record ProductoResumen(Integer id, String nombre) { }
    public record DetalleResponse(Integer id, ProductoResumen producto, Integer cantidad,
            BigDecimal precioUnitario, BigDecimal subtotal, EstadoPreparacion estadoPreparacion,
            OffsetDateTime fechaPedido, OffsetDateTime fechaEstado, String motivoCancelacion, AreaDestino areaDestino, String observaciones) { }
    public record PagoResponse(Integer id, BigDecimal monto, MetodoPago metodoPago, BigDecimal montoRecibido,
            BigDecimal vuelto, String referencia, OffsetDateTime fechaPago, String registradoPor, String claveOperacion) { }
}
