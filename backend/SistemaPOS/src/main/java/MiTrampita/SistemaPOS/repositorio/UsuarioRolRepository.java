package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.UsuarioRol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, Integer> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "rol")
    List<UsuarioRol> findAllByUsuario_Id(Integer usuarioId);
    void deleteAllByUsuario_Id(Integer usuarioId);
    boolean existsByUsuario_IdAndRol_Id(Integer usuarioId, Integer rolId);
}
