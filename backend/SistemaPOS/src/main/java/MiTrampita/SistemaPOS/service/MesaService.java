package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.dto.MesaRequest;
import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.repositorio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.TreeSet;

@Service @RequiredArgsConstructor
public class MesaService {
    private final jakarta.persistence.EntityManager entityManager;
    private final MesaRepository mesas;
    private final AreaRepository areas;
    private final VentaRepository ventas;
    private final OperationEvents events;

    @Transactional(readOnly = true)
    public List<Mesa> listar(Integer areaId) {
        return areaId == null ? mesas.findAllByOrderByNumeroAsc() : mesas.findAllByArea_IdOrderByNumeroAsc(areaId);
    }

    @Transactional
    public Mesa guardar(Integer id, MesaRequest request) {
        // Bloqueos de áreas en orden estable; evita competir con su desactivación.
        var ids = new TreeSet<Integer>();
        ids.add(request.areaId());
        if (id != null) ids.add(obtener(id).getArea().getId());
        for (Integer areaId : ids) areaActiva(areaId);
        Mesa mesa = id == null ? new Mesa() : bloquear(id);
        if (id != null && !ids.contains(mesa.getArea().getId())) throw conflict("La mesa cambió de área; actualiza el salón");
        if (mesa.getEstado() != EstadoMesa.LIBRE) throw conflict("Solo se puede editar una mesa libre");
        mesas.findByNumero(request.numero()).filter(m -> !m.getId().equals(id)).ifPresent(m -> {
            throw conflict("Ya existe la mesa " + request.numero());
        });
        mesa.setNumero(request.numero());
        mesa.setCapacidad(request.capacidad());
        mesa.setArea(areaActiva(request.areaId()));
        return mesas.save(mesa);
    }

    /** El área se bloquea antes que la mesa en todos los flujos de apertura. */
    @Transactional
    public Mesa bloquearParaAbrir(Integer id) {
        var areaId = obtener(id).getArea().getId();
        areaActiva(areaId);
        Mesa mesa = bloquear(id);
        if (!mesa.getArea().getId().equals(areaId)) throw conflict("La mesa cambió de área; actualiza el salón");
        return mesa;
    }

    @Transactional
    public Mesa abrir(Integer id) {
        Mesa mesa = bloquearParaAbrir(id);
        if (mesa.getEstado() != EstadoMesa.LIBRE) throw conflict("La mesa ya está ocupada");
        mesa.setEstado(EstadoMesa.OCUPADA);
        events.changed(false);
        return mesa;
    }

    @Transactional
    public Mesa liberar(Integer id) {
        Mesa mesa = bloquear(id);
        if (ventas.existsByMesa_IdAndEstado(id, EstadoVenta.ABIERTA))
            throw conflict("Cobra la comanda antes de liberar la mesa");
        mesa.setEstado(EstadoMesa.LIBRE);
        events.changed(false);
        return mesa;
    }

    @Transactional
    public void eliminar(Integer id) {
        Mesa mesa = bloquear(id);
        if (mesa.getEstado() != EstadoMesa.LIBRE) throw conflict("Solo se puede eliminar una mesa libre");
        mesas.delete(mesa);
    }

    private Mesa obtener(Integer id) {
        return mesas.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mesa no encontrada"));
    }
    private Mesa bloquear(Integer id) {
        var mesa = mesas.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mesa no encontrada"));
        entityManager.refresh(mesa, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return mesa;
    }
    private Area areaActiva(Integer id) {
        Area area = areas.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Área no encontrada"));
        entityManager.refresh(area, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (area.getEstado() != Area.EstadoArea.ACTIVA) throw conflict("El área está inactiva");
        return area;
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}