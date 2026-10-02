package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Comprobante;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComprobanteRepository extends JpaRepository<Comprobante, Integer> { }
