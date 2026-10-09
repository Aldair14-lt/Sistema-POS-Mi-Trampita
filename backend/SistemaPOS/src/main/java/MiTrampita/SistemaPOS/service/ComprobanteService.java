package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.dto.FacturacionRequest;
import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.repositorio.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Objects;

/** Emisión interna. El pago y el correlativo se confirman en la transacción de la venta. */
@Service @RequiredArgsConstructor
public class ComprobanteService {
    public static final BigDecimal UMBRAL_DNI_BOLETA = new BigDecimal("700.00");
    private final TipoComprobanteRepository tipos;
    private final ComprobanteRepository documentos;
    private final EntityManager entityManager;
    private final Validator validator;

    @Transactional(propagation = Propagation.MANDATORY)
    public void emitir(Venta venta, FacturacionRequest request) {
        FacturacionRequest fiscal = request == null ? desdeCliente(venta) : request;
        validar(fiscal, venta.getTotal());
        TipoComprobante elegido = tipos.findAll().stream()
            .filter(t -> compatible(t, fiscal.tipoComprobante()))
            .sorted(java.util.Comparator.comparing(TipoComprobante::getId))
            .findFirst().orElseThrow(() -> bad("Configura la serie de " + fiscal.tipoComprobante()));
        if (compatible(venta.getTipoComprobante(), fiscal.tipoComprobante())) elegido = venta.getTipoComprobante();
        TipoComprobante tipo = tipos.findByIdForUpdate(elegido.getId()).orElseThrow();
        entityManager.refresh(tipo, LockModeType.PESSIMISTIC_WRITE);
        long numero = Math.addExact(tipo.getUltimoCorrelativo(), 1L);
        tipo.setUltimoCorrelativo(numero);
        var c = new Comprobante();
        c.setVenta(venta); c.setTipoComprobante(tipo); c.setTipoDocumento(fiscal.tipoComprobante());
        c.setTipo(fiscal.tipoComprobante().name()); c.setSerie(tipo.getSerie()); c.setCorrelativo(numero);
        c.setEmpresaRuc(venta.getEmpresa().getRuc()); c.setEmpresaNombre(venta.getEmpresa().getRazonSocial());
        c.setEmpresaDireccion(venta.getEmpresa().getDireccion());
        c.setRuc(fiscal.factura() == null ? null : fiscal.factura().ruc());
        c.setRazonSocial(fiscal.factura() == null ? null : fiscal.factura().razonSocial().trim());
        c.setDni(fiscal.dni());
        c.setClienteDocumento(c.getRuc() != null ? c.getRuc() : Objects.toString(c.getDni(), ""));
        c.setClienteNombre(c.getRazonSocial() != null ? c.getRazonSocial() : nombre(fiscal));
        c.setClienteDireccion(fiscal.factura() == null ? null : fiscal.factura().direccionFiscal().trim());
        c.setFechaEmision(venta.getFechaCobro()); c.setSubtotal(venta.getSubtotal()); c.setIgv(venta.getIgv()); c.setTotal(venta.getTotal());
        venta.getDetalles().stream().filter(d -> d.getEstadoPreparacion() != EstadoPreparacion.CANCELADO).forEach(item -> {
            var d = new DetalleComprobante(); d.setComprobante(c); d.setProducto(item.getProducto().getNombre());
            d.setCantidad(item.getCantidad()); d.setPrecioUnitario(item.getPrecioUnitario()); d.setSubtotal(item.getSubtotal());
            c.getDetalles().add(d);
        });
        documentos.saveAndFlush(c);
        venta.setTipoComprobante(tipo); venta.setComprobante(c);
    }

    public void validarReintento(Comprobante c, FacturacionRequest f) {
        if (f == null) return;
        validar(f, c.getTotal());
        boolean igual = c.getTipoDocumento() == f.tipoComprobante() && Objects.equals(c.getDni(), f.dni());
        if (f.factura() != null) igual &= Objects.equals(c.getRuc(), f.factura().ruc())
            && Objects.equals(c.getRazonSocial(), f.factura().razonSocial().trim())
            && Objects.equals(c.getClienteDireccion(), f.factura().direccionFiscal().trim());
        else igual &= Objects.equals(c.getClienteNombre(), nombre(f));
        if (!igual) throw new ResponseStatusException(HttpStatus.CONFLICT, "El comprobante ya fue emitido con otros datos fiscales");
    }

    private void validar(FacturacionRequest f, BigDecimal total) {
        var errores = validator.validate(f);
        if (!errores.isEmpty()) throw new ConstraintViolationException(errores);
        // El umbral se evalúa contra el total de la venta, nunca contra el último abono.
        if (f.tipoComprobante() == TipoDocumento.BOLETA && (total.compareTo(UMBRAL_DNI_BOLETA) > 0 || f.dni() != null
                || (f.nombreCliente() != null && !f.nombreCliente().isBlank()))
                && (f.dni() == null || f.nombreCliente() == null || f.nombreCliente().isBlank()))
            throw bad("Boleta mayor a S/ 700 o identificada requiere DNI y nombre del cliente");
    }

    private FacturacionRequest desdeCliente(Venta venta) {
        TipoDocumento tipo;
        try { tipo = TipoDocumento.desdeCatalogo(venta.getTipoComprobante().getNombre()); }
        catch (IllegalArgumentException ex) { throw bad(ex.getMessage()); }
        Cliente c = venta.getCliente();
        if (c == null) {
            if (tipo == TipoDocumento.FACTURA) throw bad("Factura requiere RUC, razón social y dirección fiscal");
            return new FacturacionRequest(tipo, null, null, null);
        }
        if (tipo == TipoDocumento.FACTURA) {
            if (c.getDireccion() == null || c.getDireccion().isBlank()) throw bad("Factura requiere RUC de 11 dígitos y dirección fiscal");
            return new FacturacionRequest(tipo, new FacturacionRequest.Factura(c.getNumeroDocumento(), c.getNombresRazonSocial(), c.getDireccion()), null, null);
        }
        String dni = tipo == TipoDocumento.BOLETA && c.getNumeroDocumento().matches("[0-9]{8}") ? c.getNumeroDocumento() : null;
        // Una ficha con otro documento no se convierte en una Boleta parcialmente identificada.
        return new FacturacionRequest(tipo, null, dni, tipo == TipoDocumento.BOLETA && dni == null ? null : c.getNombresRazonSocial());
    }
    private boolean compatible(TipoComprobante t, TipoDocumento tipo) {
        try { return TipoDocumento.desdeCatalogo(t.getNombre()) == tipo; }
        catch (IllegalArgumentException ex) { return false; }
    }
    private String nombre(FacturacionRequest f) {
        return f.nombreCliente() == null || f.nombreCliente().isBlank() ? "CONSUMIDOR FINAL" : f.nombreCliente().trim();
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
