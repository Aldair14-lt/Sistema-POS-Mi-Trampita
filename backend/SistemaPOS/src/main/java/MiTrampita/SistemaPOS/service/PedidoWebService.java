package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.dto.PedidoWebDtos.*;
import MiTrampita.SistemaPOS.dto.VentaDtos.*;
import MiTrampita.SistemaPOS.dto.VentaResponse;
import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.repositorio.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.io.*;
import java.security.*;

/** Entrada pública aislada: ninguna solicitud anónima cobra, reserva stock o modifica clientes. */
@Service @RequiredArgsConstructor
public class PedidoWebService {
    private static final BigDecimal IGV = new BigDecimal("0.18");
    private final TiendaWebRepository tiendas;
    private final SolicitudWebRepository solicitudes;
    private final ProductoRepository productos;
    private final EmpresaRepository empresas;
    private final TipoComprobanteRepository tipos;
    private final VentaService ventas;
    private final EntityManager entityManager;
    private final OperationEvents events;

    @Transactional(readOnly = true)
    public List<Configuracion> configuraciones() {
        return empresas.findAll().stream().map(e -> tiendas.findById(e.getId()).map(this::config)
            .orElseGet(() -> new Configuracion(e.getId(), e.getRazonSocial(), "", false, true, true, ""))).toList();
    }
    @Transactional
    public Configuracion configurar(Integer empresaId, Configurar request) {
        var empresa = empresas.findById(empresaId).orElseThrow(this::missing);
        entityManager.refresh(empresa, LockModeType.PESSIMISTIC_WRITE);
        var tienda = tiendas.findById(empresaId).orElseGet(TiendaWeb::new);
        boolean nueva = tienda.getId() == null;
        if (tienda.getSlug() != null && !tienda.getSlug().equals(request.slug()))
            throw conflict("El enlace ya publicado no puede cambiar; conserva el identificador original");
        var otra = tiendas.findBySlug(request.slug());
        if (otra.isPresent() && !otra.get().getId().equals(empresaId)) throw conflict("Ese enlace pertenece a otra empresa");
        tienda.setEmpresa(empresa); tienda.setSlug(request.slug());
        tienda.setActiva(request.activa()); tienda.setRecojo(request.recojo()); tienda.setDelivery(request.delivery());
        tienda.setMensaje(text(request.mensaje()));
        if (nueva) entityManager.persist(tienda);
        entityManager.flush();
        return config(tienda);
    }
    private Configuracion config(TiendaWeb t) {
        return new Configuracion(t.getId(), t.getEmpresa().getRazonSocial(), t.getSlug(), t.isActiva(), t.isRecojo(), t.isDelivery(), t.getMensaje());
    }
    @Transactional(readOnly = true)
    public Menu menu(String slug) {
        var tienda = tienda(slug);
        if (!tienda.isActiva()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El local no está recibiendo pedidos web");
        var e = tienda.getEmpresa();
        var catalogo = productos.findByVisibleWebTrueOrderByNombreAsc().stream().map(p -> new ProductoMenu(p.getId(), p.getNombre(),
            p.getDescripcion(), p.getCategoria().getNombre(), p.getPrecioVenta(), p.getPrecioVenta().multiply(BigDecimal.ONE.add(IGV)).setScale(2, RoundingMode.HALF_UP),
            Math.min(20, p.getStockActual()))).toList();
        return new Menu(e.getNombreComercial() == null || e.getNombreComercial().isBlank() ? e.getRazonSocial() : e.getNombreComercial(),
            e.getDireccion(), e.getTelefono(), tienda.getMensaje(), tienda.isRecojo(), tienda.isDelivery(), catalogo);
    }
    @Transactional
    public Seguimiento enviar(String slug, Enviar request) {
        var tienda = tienda(slug);
        // Serializa la deduplicación y los límites por local, también entre instancias del backend.
        entityManager.refresh(tienda, LockModeType.PESSIMISTIC_WRITE);
        String huella = huella(request);
        var anterior = solicitudes.findByEmpresa_IdAndClaveOperacion(tienda.getId(), request.claveOperacion().toString());
        if (anterior.isPresent()) {
            if (!anterior.get().getHuella().equals(huella)) throw conflict("Ese envío ya corresponde a otro pedido");
            return seguimiento(anterior.get());
        }
        if (!tienda.isActiva()) throw conflict("El local no está recibiendo pedidos web");
        if ((request.tipoEntrega() == TipoEntrega.RECOJO && !tienda.isRecojo()) || (request.tipoEntrega() == TipoEntrega.DELIVERY && !tienda.isDelivery()))
            throw conflict("La modalidad de entrega no está disponible");
        if (!request.isEntregaValida()) throw bad("Datos de entrega incompletos");
        String telefono = telefono(request.telefono());
        OffsetDateTime desde = OffsetDateTime.now().minusHours(24);
        if (solicitudes.countByEmpresa_IdAndEstadoAndFechaCreacionAfter(tienda.getId(), SolicitudWeb.Estado.PENDIENTE, desde) >= 100
            || solicitudes.countByEmpresa_IdAndTelefonoAndEstadoAndFechaCreacionAfter(tienda.getId(), telefono, SolicitudWeb.Estado.PENDIENTE, desde) >= 3)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Hay pedidos pendientes de confirmar. Comunícate con el local");
        var cantidades = new TreeMap<Integer, Integer>();
        for (var i : request.items()) cantidades.merge(i.productoId(), i.cantidad(), Integer::sum);
        if (cantidades.values().stream().mapToInt(Integer::intValue).sum() > 60 || cantidades.values().stream().anyMatch(c -> c > 20))
            throw bad("Máximo 20 unidades por producto y 60 por pedido");
        var solicitud = new SolicitudWeb();
        solicitud.setEmpresa(tienda.getEmpresa()); solicitud.setClaveOperacion(request.claveOperacion().toString());
        solicitud.setCodigoSeguimiento(UUID.randomUUID().toString()); solicitud.setHuella(huella);
        solicitud.setNombre(request.nombre().trim()); solicitud.setDni(request.dni()); solicitud.setTelefono(telefono);
        solicitud.setTipoEntrega(request.tipoEntrega()); solicitud.setDireccion(request.tipoEntrega() == TipoEntrega.DELIVERY ? text(request.direccion()) : "");
        solicitud.setObservaciones(text(request.observaciones())); solicitud.setFechaCreacion(OffsetDateTime.now());
        var disponibles = new HashMap<Integer, Producto>();
        for (var entry : cantidades.entrySet()) {
            var producto = productos.findById(entry.getKey()).orElseThrow(() -> bad("Un producto ya no está disponible"));
            if (!producto.isVisibleWeb() || producto.getStockActual() < entry.getValue()) throw conflict("Actualiza el menú: un producto no está disponible");
            disponibles.put(entry.getKey(), producto);
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var i : request.items()) {
            var p = disponibles.get(i.productoId()); var linea = new SolicitudWebItem();
            linea.setSolicitud(solicitud); linea.setProducto(p); linea.setNombre(p.getNombre()); linea.setCantidad(i.cantidad());
            linea.setPrecioBase(p.getPrecioVenta()); linea.setObservaciones(text(i.observaciones())); solicitud.getItems().add(linea);
            subtotal = subtotal.add(p.getPrecioVenta().multiply(BigDecimal.valueOf(i.cantidad())));
        }
        BigDecimal igv = subtotal.multiply(IGV).setScale(2, RoundingMode.HALF_UP);
        if (subtotal.add(igv).compareTo(new BigDecimal("99999999.99")) > 0) throw bad("El pedido supera el importe permitido");
        if (subtotal.add(igv).compareTo(request.totalEsperado()) != 0)
            throw conflict("Cambió el importe de tu carrito. Actualiza la carta y revisa el total antes de enviar");
        solicitud.setSubtotal(subtotal); solicitud.setIgv(igv); solicitud.setTotal(subtotal.add(igv));
        solicitudes.saveAndFlush(solicitud); events.changed(false);
        return seguimiento(solicitud);
    }
    @Transactional(readOnly = true)
    public Seguimiento consultar(String slug, UUID codigo) {
        var t = tienda(slug);
        return seguimiento(solicitudes.findByEmpresa_IdAndCodigoSeguimiento(t.getId(), codigo.toString()).orElseThrow(this::missing));
    }
    @Transactional(readOnly = true)
    public List<Solicitud> pendientes() {
        return solicitudes.findTop100ByEstadoAndFechaCreacionAfterOrderByFechaCreacionAsc(SolicitudWeb.Estado.PENDIENTE, OffsetDateTime.now().minusHours(24))
            .stream().map(s -> new Solicitud(s.getId(), s.getEmpresa().getId(), s.getEmpresa().getRazonSocial(), "WEB-" + s.getId(), s.getNombre(), s.getDni(),
                s.getTelefono(), s.getTipoEntrega(), s.getDireccion(), s.getObservaciones(), s.getFechaCreacion(), s.getSubtotal(), s.getIgv(), s.getTotal(),
                s.getItems().stream().map(i -> new ItemResumen(i.getProducto().getId(), i.getNombre(), i.getCantidad(), i.getPrecioBase(), i.getObservaciones())).toList())).toList();
    }
    @Transactional
    public VentaResponse aceptar(Integer id, Aceptar request, Integer usuarioId) {
        var s = bloquear(id);
        if (s.getEstado() == SolicitudWeb.Estado.ACEPTADO) {
            if (!s.getVenta().getTipoComprobante().getId().equals(request.tipoComprobanteId())) throw conflict("El pedido ya se confirmó con otro comprobante");
            return VentaResponse.from(ventas.obtener(s.getVenta().getId()));
        }
        if (s.getEstado() != SolicitudWeb.Estado.PENDIENTE || expirada(s)) throw conflict("El pedido fue rechazado o expiró");
        var t = tiendas.findById(s.getEmpresa().getId()).orElseThrow(this::missing);
        entityManager.refresh(t, LockModeType.PESSIMISTIC_WRITE);
        if (!t.isActiva()) throw conflict("El menú web está pausado");
        var tipo = tipos.findById(request.tipoComprobanteId()).orElseThrow(this::missing);
        if (tipo.getNombre().toUpperCase(Locale.ROOT).contains("FACTURA")) throw bad("Confirma con nota o boleta; los datos de factura se completan al cobrar");
        var ids = s.getItems().stream().map(i -> i.getProducto().getId()).distinct().sorted().toList();
        for (var productoId : ids) {
            var p = productos.findById(productoId).orElseThrow(this::missing);
            entityManager.refresh(p, LockModeType.PESSIMISTIC_WRITE);
            if (!p.isVisibleWeb() || s.getItems().stream().filter(i -> i.getProducto().getId().equals(productoId)).anyMatch(i -> i.getPrecioBase().compareTo(p.getPrecioVenta()) != 0))
                throw conflict("Cambió el menú o el precio. Contacta al cliente y solicita un nuevo pedido");
        }
        var items = s.getItems().stream().map(i -> new ItemRequest(i.getProducto().getId(), i.getCantidad(), i.getObservaciones())).toList();
        var venta = ventas.registrar(new VentaRequest(s.getEmpresa().getId(), null,
            new ClienteRequest(s.getDni(), s.getNombre(), null, s.getTelefono(), null, null), tipo.getId(), "WEB-" + s.getCodigoSeguimiento(), null,
            items, OrigenPedido.WEB, s.getTipoEntrega(), s.getDireccion(), s.getTelefono(), null, false), usuarioId);
        if (venta.getTotal().compareTo(s.getTotal()) != 0) throw conflict("El importe cambió; solicita un nuevo pedido");
        venta.setObservacionesPedido(s.getObservaciones());
        s.setVenta(venta); s.setEstado(SolicitudWeb.Estado.ACEPTADO); events.changed(false);
        return VentaResponse.from(venta);
    }
    @Transactional
    public void rechazar(Integer id, Rechazar request) {
        var s = bloquear(id);
        if (s.getEstado() == SolicitudWeb.Estado.ACEPTADO) throw conflict("El pedido ya se envió a preparación; usa las anulaciones de Caja");
        if (s.getEstado() == SolicitudWeb.Estado.RECHAZADO && !s.getMotivo().equals(request.motivo().trim())) throw conflict("El rechazo ya fue registrado");
        s.setEstado(SolicitudWeb.Estado.RECHAZADO); s.setMotivo(request.motivo().trim()); events.changed(false);
    }
    private SolicitudWeb bloquear(Integer id) {
        var s = solicitudes.findByIdForUpdate(id).orElseThrow(this::missing);
        entityManager.refresh(s, LockModeType.PESSIMISTIC_WRITE); return s;
    }
    private TiendaWeb tienda(String slug) { return tiendas.findBySlug(slug).orElseThrow(this::missing); }
    private boolean expirada(SolicitudWeb s) { return s.getFechaCreacion().isBefore(OffsetDateTime.now().minusHours(24)); }
    private Seguimiento seguimiento(SolicitudWeb s) {
        String estado = s.getEstado().name();
        if (s.getEstado() == SolicitudWeb.Estado.PENDIENTE && expirada(s)) estado = "EXPIRADO";
        if (s.getEstado() == SolicitudWeb.Estado.ACEPTADO) {
            var activos = s.getVenta().getDetalles().stream().filter(i -> i.getEstadoPreparacion() != EstadoPreparacion.CANCELADO).toList();
            if (activos.isEmpty()) estado = "CANCELADO";
            else if (activos.stream().allMatch(i -> i.getEstadoPreparacion() == EstadoPreparacion.SERVIDO)) estado = "ENTREGADO";
            else if (activos.stream().allMatch(i -> i.getEstadoPreparacion() == EstadoPreparacion.LISTO || i.getEstadoPreparacion() == EstadoPreparacion.SERVIDO)) estado = "LISTO";
            else if (activos.stream().anyMatch(i -> i.getEstadoPreparacion() != EstadoPreparacion.PENDIENTE)) estado = "PREPARANDO";
        }
        return new Seguimiento(s.getCodigoSeguimiento(), "WEB-" + s.getId(), estado, s.getVenta() == null ? s.getTotal() : s.getVenta().getTotal(), s.getMotivo());
    }
    private String huella(Enviar r) {
        try {
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            for (String s : List.of(r.nombre().trim(), r.dni(), telefono(r.telefono()), r.tipoEntrega().name(), text(r.direccion()), text(r.observaciones()))) out.writeUTF(s);
            out.writeUTF(r.totalEsperado().stripTrailingZeros().toPlainString());
            for (var i : r.items()) { out.writeInt(i.productoId()); out.writeInt(i.cantidad()); out.writeUTF(text(i.observaciones())); }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException ex) { throw new IllegalStateException("No se pudo identificar el pedido", ex); }
    }
    private String telefono(String value) {
        String normal = value.replaceAll("[ ()-]", "");
        if (!normal.matches("\\+?\\d{6,15}")) throw bad("Teléfono de contacto inválido");
        normal = normal.replace("+", ""); return normal.length() == 9 ? "51" + normal : normal;
    }
    private String text(String s) { return s == null ? "" : s.trim(); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido o menú no encontrado"); }
    private ResponseStatusException bad(String s) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, s); }
    private ResponseStatusException conflict(String s) { return new ResponseStatusException(HttpStatus.CONFLICT, s); }
}
