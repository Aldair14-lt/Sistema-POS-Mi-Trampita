package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.TipoComprobante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoComprobanteRepository extends JpaRepository<TipoComprobante, Integer> {
    Optional<TipoComprobante> findByNombreIgnoreCaseAndSerie(String nombre, String serie);
}
