package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.*;
import java.time.OffsetDateTime;

/** La cocina solo recibe producto, cantidad y destino; no recibe importes ni datos personales. */
public record KitchenItemResponse(Integer id, Integer ventaId, Integer mesaNumero,
        OrigenPedido origenPedido, TipoEntrega tipoEntrega, String producto, Integer cantidad,
        EstadoPreparacion estadoPreparacion, OffsetDateTime fechaPedido, OffsetDateTime fechaEstado,
        AreaDestino areaDestino, String observaciones, String mozo) {
    public static KitchenItemResponse from(DetalleVenta d) {
        var venta = d.getVenta();
        return new KitchenItemResponse(d.getId(), venta.getId(),
                venta.getMesa() == null ? null : venta.getMesa().getNumero(),
                venta.getOrigenPedido(), venta.getTipoEntrega(), d.getProducto().getNombre(),
                d.getCantidad(), d.getEstadoPreparacion(), d.getFechaPedido(), d.getFechaEstado(),
                d.getAreaDestino(), d.getObservaciones(), venta.getUsuario().getNombreCompleto());
    }
}
