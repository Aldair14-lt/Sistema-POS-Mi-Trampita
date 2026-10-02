package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface AreaRepository extends JpaRepository<Area, Integer> {
    List<Area> findAllByOrderByIdAsc();
    Optional<Area> findByNombreIgnoreCase(String nombre);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Area a where a.id = :id")
    Optional<Area> findByIdForUpdate(Integer id);
}
