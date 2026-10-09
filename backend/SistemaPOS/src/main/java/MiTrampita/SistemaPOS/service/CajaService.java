package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.dto.SesionCajaDtos.*;
import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.repositorio.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class CajaService {
    private final SesionCajaRepository sesiones;
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final PagoVentaRepository pagos;
    private final EntityManager em;
    private final OperationEvents events;

    @Transactional
    public Resumen abrir(Abrir request, Integer usuarioId) {
        Empresa empresa = empresas.findById(request.empresaId()).orElseThrow(() -> missing());
        // Serializar aperturas incluso cuando todavía no existe una sesión que bloquear.
        em.refresh(empresa, LockModeType.PESSIMISTIC_WRITE);
        SesionCaja anterior = sesiones.findByEmpresa_IdAndClaveOperacion(empresa.getId(), request.claveOperacion()).orElse(null);
        if (anterior != null) {
            if (anterior.getMontoInicial().compareTo(request.montoInicial()) != 0)
                throw conflict("La clave de apertura corresponde a otro importe");
            return resumen(anterior);
        }
        if (sesiones.findByEmpresaAbierta(empresa.getId()).isPresent()) throw conflict("La empresa ya tiene un turno abierto");
        var sesion = new SesionCaja();
        sesion.setEmpresa(empresa); sesion.setEmpresaAbierta(empresa.getId());
        sesion.setAbiertoPor(usuarios.findById(usuarioId).orElseThrow(() -> missing()));
        sesion.setFechaApertura(OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)); sesion.setMontoInicial(request.montoInicial());
        sesion.setClaveOperacion(request.claveOperacion()); sesiones.saveAndFlush(sesion);
        events.changed(false);
        return resumen(sesion);
    }

    /** Se mantiene el bloqueo hasta confirmar o revertir el pago de la venta. */
    @Transactional(propagation = Propagation.MANDATORY)
    public SesionCaja bloquearParaPago(Integer empresaId) {
        SesionCaja actual = sesiones.findByEmpresaAbierta(empresaId).orElseThrow(() -> conflict("Abre un turno de caja antes de registrar pagos"));
        SesionCaja bloqueada = sesiones.findByIdForUpdate(actual.getId()).orElseThrow(() -> missing());
        em.refresh(bloqueada, LockModeType.PESSIMISTIC_WRITE);
        if (bloqueada.getFechaCierre() != null) throw conflict("El turno acaba de cerrarse. Abre o selecciona un turno vigente");
        return bloqueada;
    }

    @Transactional
    public Resumen cerrar(Integer id, Cerrar request, Integer usuarioId) {
        SesionCaja sesion = sesiones.findByIdForUpdate(id).orElseThrow(() -> missing());
        em.refresh(sesion, LockModeType.PESSIMISTIC_WRITE);
        String nota = request.observaciones() == null ? "" : request.observaciones().trim();
        if (sesion.getFechaCierre() != null) {
            if (sesion.getEfectivoDeclarado().compareTo(request.efectivoDeclarado()) != 0 || !sesion.getObservaciones().equals(nota))
                throw conflict("Este turno ya fue cerrado con otro arqueo");
            return resumen(sesion);
        }
        Map<MetodoPago, BigDecimal> montos = montos(sesion);
        sesion.setTotalEfectivo(montos.get(MetodoPago.EFECTIVO)); sesion.setTotalYape(montos.get(MetodoPago.YAPE));
        sesion.setTotalPlin(montos.get(MetodoPago.PLIN)); sesion.setTotalTarjeta(montos.get(MetodoPago.TARJETA));
        sesion.setTotalTransferencia(montos.get(MetodoPago.TRANSFERENCIA)); sesion.setTotalYapePlin(montos.get(MetodoPago.YAPE_PLIN));
        sesion.setEfectivoDeclarado(request.efectivoDeclarado()); sesion.setObservaciones(nota);
        sesion.setCerradoPor(usuarios.findById(usuarioId).orElseThrow(() -> missing()));
        sesion.setFechaCierre(OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)); sesion.setEmpresaAbierta(null);
        sesiones.flush(); events.changed(false);
        return resumen(sesion);
    }

    @Transactional(readOnly = true)
    public Resumen actual(Integer empresaId) { return sesiones.findByEmpresaAbierta(empresaId).map(this::resumen).orElse(null); }
    @Transactional(readOnly = true)
    public Resumen obtener(Integer id) { return resumen(sesiones.findById(id).orElseThrow(() -> missing())); }
    @Transactional(readOnly = true)
    public List<Resumen> historial(Integer empresaId) { return sesiones.findTop20ByEmpresa_IdOrderByFechaAperturaDesc(empresaId).stream().map(this::resumen).toList(); }

    private Map<MetodoPago, BigDecimal> montos(SesionCaja s) {
        var result = new EnumMap<MetodoPago, BigDecimal>(MetodoPago.class);
        for (MetodoPago metodo : MetodoPago.values()) result.put(metodo, BigDecimal.ZERO.setScale(2));
        if (s.getFechaCierre() == null) {
            for (var row : pagos.totalesSesion(s.getId())) result.put(row.getMetodo(), row.getTotal());
        } else {
            result.put(MetodoPago.EFECTIVO, s.getTotalEfectivo()); result.put(MetodoPago.YAPE, s.getTotalYape());
            result.put(MetodoPago.PLIN, s.getTotalPlin()); result.put(MetodoPago.TARJETA, s.getTotalTarjeta());
            result.put(MetodoPago.TRANSFERENCIA, s.getTotalTransferencia()); result.put(MetodoPago.YAPE_PLIN, s.getTotalYapePlin());
        }
        return result;
    }
    private Resumen resumen(SesionCaja s) {
        var montos = montos(s);
        BigDecimal esperado = s.getMontoInicial().add(montos.get(MetodoPago.EFECTIVO));
        return new Resumen(s.getId(), s.getEmpresa().getId(), s.getEmpresa().getRazonSocial(), s.getEmpresa().getRuc(),
            s.getAbiertoPor().getNombreCompleto(), s.getCerradoPor() == null ? null : s.getCerradoPor().getNombreCompleto(),
            s.getFechaApertura(), s.getFechaCierre(), s.getMontoInicial(), Map.copyOf(montos),
            montos.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add), esperado, s.getEfectivoDeclarado(),
            s.getEfectivoDeclarado() == null ? null : s.getEfectivoDeclarado().subtract(esperado), s.getObservaciones());
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa, turno o usuario no encontrado"); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
