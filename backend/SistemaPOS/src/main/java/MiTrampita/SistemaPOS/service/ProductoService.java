package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.entity.Producto;
import MiTrampita.SistemaPOS.entity.Marca;
import MiTrampita.SistemaPOS.repositorio.ProductoRepository;
import MiTrampita.SistemaPOS.repositorio.MarcaRepository;
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
    private final MarcaRepository marcas;

    @Transactional(readOnly = true)
    public List<Producto> listar() { return repository.findAll(); }

    @Transactional(readOnly = true)
    public Producto obtener(Integer id) { return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado")); }

    @Transactional
    public Producto guardar(Producto producto) {
        if (producto.getMarca() == null || producto.getMarca().getId() == null || producto.getMarca().getId() <= 0) {
            producto.setMarca(marcas.findByNombreIgnoreCase("Sin marca").orElseGet(() -> {
                Marca marca = new Marca();
                marca.setNombre("Sin marca");
                return marcas.save(marca);
            }));
        }
        return repository.save(producto);
    }

    @Transactional
    public Producto actualizar(Integer id, Producto producto) {
        obtener(id);
        producto.setId(id);
        return guardar(producto);
    }

    @Transactional
    public void eliminar(Integer id) { repository.delete(obtener(id)); }
}
