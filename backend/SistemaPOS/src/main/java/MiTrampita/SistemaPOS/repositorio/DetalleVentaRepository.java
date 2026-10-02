package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.DetalleVenta;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import MiTrampita.SistemaPOS.entity.EstadoPreparacion;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Integer> {
    @Query("""
        select d from DetalleVenta d join fetch d.venta v join fetch d.producto
        left join fetch v.mesa
        where d.estadoPreparacion in :estados
          and v.estado <> MiTrampita.SistemaPOS.entity.EstadoVenta.ANULADA
        order by d.fechaPedido asc, d.id asc
        """)
    List<DetalleVenta> findColaCocina(Collection<EstadoPreparacion> estados);

    @Query("select d.venta.id from DetalleVenta d where d.id = :id")
    Optional<Integer> findVentaId(Integer id);

    @Query("""
        select d.producto.id as productoId, d.producto.nombre as nombre,
               d.producto.categoria.nombre as categoria, sum(d.cantidad) as unidades,
               sum(d.subtotal) as importe
        from DetalleVenta d where d.venta.cliente.id = :clienteId
          and d.venta.estado = MiTrampita.SistemaPOS.entity.EstadoVenta.CERRADA
        group by d.producto.id, d.producto.nombre, d.producto.categoria.nombre
        order by sum(d.cantidad) desc, d.producto.nombre asc
        """)
    List<ConsumoProducto> consumoCliente(Integer clienteId, Pageable pageable);

    interface ConsumoProducto {
        Integer getProductoId();
        String getNombre();
        String getCategoria();
        Long getUnidades();
        BigDecimal getImporte();
    }
}
