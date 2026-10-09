package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.OperacionComanda;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OperacionComandaRepository extends JpaRepository<OperacionComanda, Integer> {
    Optional<OperacionComanda> findByVenta_IdAndClaveOperacion(Integer ventaId, String claveOperacion);
}
