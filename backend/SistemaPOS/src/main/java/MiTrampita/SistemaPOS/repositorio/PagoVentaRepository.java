package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.PagoVenta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoVentaRepository extends JpaRepository<PagoVenta, Integer> {
    @org.springframework.data.jpa.repository.Query("select p.metodoPago as metodo, sum(p.monto) as total from PagoVenta p where p.sesionCaja.id = :id group by p.metodoPago")
    java.util.List<TotalMetodo> totalesSesion(Integer id);
    interface TotalMetodo {
        MiTrampita.SistemaPOS.entity.MetodoPago getMetodo();
        java.math.BigDecimal getTotal();
    }
}
