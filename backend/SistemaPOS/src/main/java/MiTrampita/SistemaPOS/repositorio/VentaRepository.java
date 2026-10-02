package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Venta;
import MiTrampita.SistemaPOS.entity.EstadoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface VentaRepository extends JpaRepository<Venta, Integer> {
    boolean existsByMesa_IdAndEstado(Integer mesaId, EstadoVenta estado);

    List<Venta> findAllByEstadoOrderByFechaVentaDesc(EstadoVenta estado);

    List<Venta> findTop20ByEstadoOrderByFechaCobroDesc(EstadoVenta estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venta v where v.id = :id")
    Optional<Venta> findByIdForUpdate(Integer id);

    @Query("""
        select v from Venta v where v.origenPedido <> MiTrampita.SistemaPOS.entity.OrigenPedido.LOCAL
          and v.estado <> MiTrampita.SistemaPOS.entity.EstadoVenta.ANULADA
          and (v.estado = MiTrampita.SistemaPOS.entity.EstadoVenta.ABIERTA
            or exists (select d.id from DetalleVenta d where d.venta = v
              and d.estadoPreparacion not in (MiTrampita.SistemaPOS.entity.EstadoPreparacion.SERVIDO,
                MiTrampita.SistemaPOS.entity.EstadoPreparacion.CANCELADO)))
        order by v.fechaVenta asc, v.id asc
        """)
    List<Venta> findOnlineActivos();
}
