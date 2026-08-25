package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> { }
