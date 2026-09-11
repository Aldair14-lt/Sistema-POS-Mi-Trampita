package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.entity.EstadoMesa;
import MiTrampita.SistemaPOS.entity.Mesa;
import MiTrampita.SistemaPOS.entity.EstadoVenta;
import MiTrampita.SistemaPOS.repositorio.MesaRepository;
import MiTrampita.SistemaPOS.repositorio.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MesaService {
    private final MesaRepository mesaRepository;
    private final VentaRepository ventaRepository;

    @Transactional(readOnly = true)
    public List<Mesa> listar() {
        return mesaRepository.findAllByOrderByNumeroAsc();
    }

    @Transactional
    public Mesa crear(Mesa mesa) {
        mesa.setId(null);
        if (mesa.getEstado() == null) mesa.setEstado(EstadoMesa.LIBRE);
        validarNumeroDisponible(mesa.getNumero(), null);
        return mesaRepository.save(mesa);
    }

    @Transactional
    public Mesa actualizar(Integer id, Mesa request) {
        Mesa actual = mesaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> noEncontrado("Mesa"));
        validarNumeroDisponible(request.getNumero(), id);
        if (actual.getEstado() == EstadoMesa.OCUPADA && request.getEstado() != EstadoMesa.OCUPADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cierra primero la venta abierta para liberar la mesa");
        }
        if (actual.getEstado() != EstadoMesa.OCUPADA && request.getEstado() == EstadoMesa.OCUPADA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Una mesa solo pasa a ocupada al abrir una comanda");
        }
        actual.setNumero(request.getNumero());
        actual.setCapacidad(request.getCapacidad());
        if (request.getEstado() != null) actual.setEstado(request.getEstado());
        return mesaRepository.save(actual);
    }

    @Transactional
    public void eliminar(Integer id) {
        Mesa mesa = mesaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> noEncontrado("Mesa"));
        if (mesa.getEstado() == EstadoMesa.OCUPADA
                || ventaRepository.existsByMesa_IdAndEstado(id, EstadoVenta.ABIERTA)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar una mesa con una comanda abierta");
        }
        mesaRepository.delete(mesa);
    }

    private void validarNumeroDisponible(Integer numero, Integer id) {
        if (numero == null || numero < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El número de mesa debe ser mayor que cero");
        }
        mesaRepository.findByNumero(numero).filter(existing -> !existing.getId().equals(id)).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe la mesa " + numero);
        });
    }

    private ResponseStatusException noEncontrado(String recurso) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, recurso + " no encontrado");
    }
}
