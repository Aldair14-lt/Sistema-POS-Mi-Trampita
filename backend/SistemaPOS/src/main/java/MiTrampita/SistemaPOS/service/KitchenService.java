package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.dto.KitchenItemResponse;
import MiTrampita.SistemaPOS.dto.UpdateItemStatusRequest;
import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.repositorio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;

@Service @RequiredArgsConstructor
public class KitchenService {
    private final DetalleVentaRepository detalles;
    private final VentaRepository ventas;
    private final OperationEvents events;

    @Transactional(readOnly = true)
    public List<KitchenItemResponse> cola() {
        return cola(AreaDestino.COCINA);
    }

    @Transactional(readOnly = true)
    public List<KitchenItemResponse> cola(AreaDestino area) {
        return detalles.findColaCocina(List.of(EstadoPreparacion.PENDIENTE, EstadoPreparacion.PREPARANDO, EstadoPreparacion.LISTO), area)
                .stream().map(KitchenItemResponse::from).toList();
    }

    @Transactional
    public KitchenItemResponse actualizar(Integer id, UpdateItemStatusRequest request, boolean entrega) {
        return actualizar(id, request, entrega, entrega ? null : AreaDestino.COCINA);
    }

    @Transactional
    public KitchenItemResponse actualizar(Integer id, UpdateItemStatusRequest request, boolean entrega, AreaDestino area) {
        Integer ventaId = detalles.findVentaId(id).orElseThrow(() -> missing());
        // Todos los cambios de la comanda toman primero este bloqueo, igual que pagos y cierre.
        Venta venta = ventas.findByIdForUpdate(ventaId).orElseThrow(() -> missing());
        if (venta.getEstado() == EstadoVenta.ANULADA) throw conflict("La venta está anulada");
        DetalleVenta detalle = detalles.findById(id).orElseThrow(() -> missing());
        if (!entrega && detalle.getAreaDestino() != area)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El ítem pertenece a otra estación de preparación");
        EstadoPreparacion actual = detalle.getEstadoPreparacion();
        EstadoPreparacion siguiente = request.estado();
        if (entrega && siguiente != EstadoPreparacion.SERVIDO)
            throw conflict("Recepción y mozo solo pueden marcar la entrega");
        if (!entrega && siguiente != EstadoPreparacion.PREPARANDO && siguiente != EstadoPreparacion.LISTO)
            throw conflict("Cocina solo puede marcar preparando o listo");
        if (actual == siguiente) return KitchenItemResponse.from(detalle);
        if (actual != request.estadoActual()) throw conflict("El plato cambió de estado. Actualiza el tablero");
        // Máquina de estados monotónica: nunca retroceder, saltar preparación ni servir antes de LISTO.
        boolean valido = switch (actual) {
            case PENDIENTE -> siguiente == EstadoPreparacion.PREPARANDO;
            case PREPARANDO -> siguiente == EstadoPreparacion.LISTO;
            case LISTO -> siguiente == EstadoPreparacion.SERVIDO;
            case SERVIDO, CANCELADO -> false;
        };
        if (!valido) throw conflict("Transición de cocina no permitida");
        detalle.setEstadoPreparacion(siguiente);
        detalle.setFechaEstado(OffsetDateTime.now());
        events.changed(true);
        return KitchenItemResponse.from(detalle);
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Ítem no encontrado");
    }
    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
