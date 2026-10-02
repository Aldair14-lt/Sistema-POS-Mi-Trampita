package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface MesaRepository extends JpaRepository<Mesa, Integer> {
    Optional<Mesa> findByNumero(Integer numero);

    List<Mesa> findAllByOrderByNumeroAsc();
    List<Mesa> findAllByArea_IdOrderByNumeroAsc(Integer areaId);
    boolean existsByArea_Id(Integer areaId);
    boolean existsByArea_IdAndEstadoNot(Integer areaId, MiTrampita.SistemaPOS.entity.EstadoMesa estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mesa m where m.id = :id")
    Optional<Mesa> findByIdForUpdate(Integer id);
}
