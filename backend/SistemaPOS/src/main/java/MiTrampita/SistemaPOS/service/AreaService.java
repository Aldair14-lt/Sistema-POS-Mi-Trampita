package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.dto.AreaRequest;
import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.repositorio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service @RequiredArgsConstructor
public class AreaService {
    private final AreaRepository areas;
    private final MesaRepository mesas;

    @Transactional(readOnly = true)
    public List<Area> listar() { return areas.findAllByOrderByIdAsc(); }

    @Transactional
    public Area guardar(Integer id, AreaRequest request) {
        Area area = id == null ? new Area() : areas.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Área no encontrada"));
        areas.findByNombreIgnoreCase(request.nombre().trim()).filter(a -> !a.getId().equals(id)).ifPresent(a -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un área con ese nombre");
        });
        if (id != null && request.estado() == Area.EstadoArea.INACTIVA
                && mesas.existsByArea_IdAndEstadoNot(id, EstadoMesa.LIBRE))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Libera las mesas antes de desactivar el área");
        area.setNombre(request.nombre().trim());
        area.setEstado(request.estado());
        return areas.save(area);
    }

    @Transactional
    public void eliminar(Integer id) {
        var area = areas.findByIdForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Área no encontrada"));
        if (mesas.existsByArea_Id(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reasigna las mesas antes de eliminar el área");
        areas.delete(area);
    }
}