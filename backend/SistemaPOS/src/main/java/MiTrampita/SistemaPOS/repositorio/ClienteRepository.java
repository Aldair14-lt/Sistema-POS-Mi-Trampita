package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> { }
