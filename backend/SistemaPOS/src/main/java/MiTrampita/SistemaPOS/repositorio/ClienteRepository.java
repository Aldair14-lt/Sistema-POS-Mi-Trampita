package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {
    Optional<Cliente> findByNumeroDocumento(String numeroDocumento);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Cliente c where c.id = :id")
    Optional<Cliente> findByIdForUpdate(Integer id);

    java.util.List<Cliente> findTop10ByFrecuenciaVisitasGreaterThanOrderByFrecuenciaVisitasDescTotalGastadoDescIdAsc(Long minimo);

    @org.springframework.data.jpa.repository.Query("select c from Cliente c where month(c.fechaNacimiento) = :mes order by day(c.fechaNacimiento), c.id")
    java.util.List<Cliente> cumpleaneros(int mes);
}
