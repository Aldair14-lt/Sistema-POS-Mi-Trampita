package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpresaRepository extends JpaRepository<Empresa, Integer> { }
