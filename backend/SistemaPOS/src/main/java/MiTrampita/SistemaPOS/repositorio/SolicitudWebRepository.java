package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.SolicitudWeb;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.*;

public interface SolicitudWebRepository extends JpaRepository<SolicitudWeb, Integer> {
    Optional<SolicitudWeb> findByEmpresa_IdAndClaveOperacion(Integer empresaId, String claveOperacion);
    Optional<SolicitudWeb> findByEmpresa_IdAndCodigoSeguimiento(Integer empresaId, String codigoSeguimiento);
    List<SolicitudWeb> findTop100ByEstadoAndFechaCreacionAfterOrderByFechaCreacionAsc(SolicitudWeb.Estado estado, OffsetDateTime desde);
    long countByEmpresa_IdAndEstadoAndFechaCreacionAfter(Integer empresaId, SolicitudWeb.Estado estado, OffsetDateTime desde);
    long countByEmpresa_IdAndTelefonoAndEstadoAndFechaCreacionAfter(Integer empresaId, String telefono, SolicitudWeb.Estado estado, OffsetDateTime desde);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SolicitudWeb s where s.id = :id")
    Optional<SolicitudWeb> findByIdForUpdate(Integer id);
}
