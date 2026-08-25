package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {
    Optional<Producto> findByCodigoBarras(String codigoBarras);
}
