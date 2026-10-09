package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.SesionCaja;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface SesionCajaRepository extends JpaRepository<SesionCaja, Integer> {
    Optional<SesionCaja> findByEmpresaAbierta(Integer empresaId);
    Optional<SesionCaja> findByEmpresa_IdAndClaveOperacion(Integer empresaId, String claveOperacion);
    List<SesionCaja> findTop20ByEmpresa_IdOrderByFechaAperturaDesc(Integer empresaId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SesionCaja s where s.id = :id")
    Optional<SesionCaja> findByIdForUpdate(Integer id);
}
