package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.TiendaWeb;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TiendaWebRepository extends JpaRepository<TiendaWeb, Integer> {
    Optional<TiendaWeb> findBySlug(String slug);
}
