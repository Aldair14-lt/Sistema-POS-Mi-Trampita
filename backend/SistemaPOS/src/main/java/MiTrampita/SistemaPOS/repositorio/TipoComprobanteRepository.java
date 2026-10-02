package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.TipoComprobante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoComprobanteRepository extends JpaRepository<TipoComprobante, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from TipoComprobante t where t.id = :id")
    Optional<TipoComprobante> findByIdForUpdate(Integer id);
    Optional<TipoComprobante> findByNombreIgnoreCaseAndSerie(String nombre, String serie);
}
