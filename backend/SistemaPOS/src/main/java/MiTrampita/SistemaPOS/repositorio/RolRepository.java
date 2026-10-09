package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Integer> {
    Optional<Rol> findByNombreIgnoreCase(String nombre);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Rol r where r.nombre = 'ADMIN'")
    Optional<Rol> lockAdministratorRole();
}
