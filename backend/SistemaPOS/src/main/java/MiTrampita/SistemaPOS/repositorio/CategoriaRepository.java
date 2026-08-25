package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> { }
