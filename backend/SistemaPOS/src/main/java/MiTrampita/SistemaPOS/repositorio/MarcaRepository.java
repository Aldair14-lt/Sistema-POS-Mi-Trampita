package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Marca;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarcaRepository extends JpaRepository<Marca, Integer> {
    java.util.Optional<Marca> findByNombreIgnoreCase(String nombre);
}
