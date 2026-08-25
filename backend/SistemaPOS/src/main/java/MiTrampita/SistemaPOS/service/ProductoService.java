package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.entity.Producto;
import MiTrampita.SistemaPOS.repositorio.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoService {
    private final ProductoRepository repository;

    @Transactional(readOnly = true)
    public List<Producto> listar() { return repository.findAll(); }

    @Transactional(readOnly = true)
    public Producto obtener(Integer id) { return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado")); }

    @Transactional
    public Producto guardar(Producto producto) { return repository.save(producto); }

    @Transactional
    public void eliminar(Integer id) { repository.delete(obtener(id)); }
}
