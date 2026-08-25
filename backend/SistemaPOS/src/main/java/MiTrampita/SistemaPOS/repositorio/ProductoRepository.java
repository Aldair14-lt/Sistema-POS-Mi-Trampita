package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id = :id")
    Optional<Producto> findByIdForUpdate(Integer id);

    Optional<Producto> findByCodigoBarras(String codigoBarras);
}
