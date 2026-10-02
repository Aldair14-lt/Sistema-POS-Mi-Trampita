package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.dto.VentaDtos.*;
import MiTrampita.SistemaPOS.dto.*;
import MiTrampita.SistemaPOS.dto.RegistrarPagoRequest;
import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.exception.StockInsuficienteException;
import MiTrampita.SistemaPOS.repositorio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.OffsetDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class VentaService {
    private static final BigDecimal IGV = new BigDecimal("0.18");
    private static final BigDecimal MAX_IMPORTE = new BigDecimal("99999999.99");
    private final jakarta.persistence.EntityManager entityManager;
    private final VentaRepository ventas;
    private final ProductoRepository productos;
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final ClienteRepository clientes;
    private final TipoComprobanteRepository comprobantes;
    private final MesaRepository mesas;
    private final MesaService mesaService;
    private final PagoVentaRepository pagos;
    private final ComprobanteRepository documentos;
    private final OperationEvents events;

    @Transactional(readOnly = true)
    public List<Venta> listar(boolean abiertas) {
        var result = abiertas ? ventas.findAllByEstadoOrderByFechaVentaDesc(EstadoVenta.ABIERTA) : ventas.findAll();
        result.forEach(this::cargarCuenta);
        return result;
    }
    @Transactional(readOnly = true)
    public Venta obtener(Integer id) {
        Venta venta = ventas.findById(id).orElseThrow(() -> missing("Venta"));
        cargarCuenta(venta);
        return venta;
    }

    @Transactional(readOnly = true)
    public List<Venta> listarOnline() {
        // Incluye los pagados hasta entregar todos los ítems; pago y preparación son independientes.
        var result = ventas.findOnlineActivos();
        result.forEach(this::cargarCuenta);
        return result;
    }

    private void cargarCuenta(Venta venta) {
        venta.getDetalles().size();
        venta.getPagos().size();
        if (venta.getComprobante() != null) venta.getComprobante().getDetalles().size();
    }

    @Transactional
    public Venta registrar(VentaRequest request, Integer usuarioId) {
        OrigenPedido origen = request.origenPedido() == null ? OrigenPedido.LOCAL : request.origenPedido();
        if (!request.isEntregaValida()) throw bad("Datos de mesa o entrega incompletos");
        Mesa mesa = null;
        if (origen == OrigenPedido.LOCAL) {
            mesa = mesaService.bloquearParaAbrir(request.mesaId());
            if (mesa.getEstado() == EstadoMesa.ATENDIENDO || ventas.existsByMesa_IdAndEstado(mesa.getId(), EstadoVenta.ABIERTA))
                throw conflict("La mesa ya tiene una comanda abierta. Actualiza el salón");
        }
        Venta venta = new Venta();
        venta.setMesa(mesa);
        venta.setOrigenPedido(origen);
        venta.setTipoEntrega(origen == OrigenPedido.LOCAL ? TipoEntrega.MESA : request.tipoEntrega());
        venta.setDireccionEnvio(request.tipoEntrega() == TipoEntrega.DELIVERY ? request.direccion().trim() : null);
        venta.setTelefonoEntrega(request.telefono() == null ? null : request.telefono().trim());
        venta.setEmpresa(empresas.findById(request.empresaId()).orElseThrow(() -> missing("Configuración fiscal")));
        var usuario = usuarios.findById(usuarioId).orElseThrow(() -> missing("Usuario"));
        if (usuario.getEstado() != EstadoUsuario.activo) throw bad("Usuario inactivo");
        venta.setUsuario(usuario);
        venta.setCliente(resolverCliente(request.clienteId(), request.cliente()));
        venta.setTipoComprobante(comprobantes.findById(request.tipoComprobanteId()).orElseThrow(() -> missing("Comprobante")));
        validarCliente(venta);
        venta.setNumeroComprobante(request.numeroComprobante().trim());
        venta.setFechaVenta(OffsetDateTime.now());
        agregarLineas(venta, request.items());
        if (mesa != null) mesa.setEstado(EstadoMesa.ATENDIENDO);
        ventas.saveAndFlush(venta);
        if (Boolean.TRUE.equals(request.pagoTotal()) && (request.pagoInicial() == null ||
                request.pagoInicial().monto().compareTo(venta.getTotal()) != 0))
            throw bad("El pago total debe coincidir con el importe vigente del pedido");
        if (request.pagoInicial() != null) registrarAbono(venta, request.pagoInicial().toPago(), usuarioId);
        return venta;
    }

    @Transactional
    public Venta registrarOnline(PedidoOnlineRequest request, Integer usuarioId) {
        if (request.tipoEntrega() != TipoEntrega.RECOJO && request.tipoEntrega() != TipoEntrega.DELIVERY)
            throw bad("Selecciona recojo o delivery");
        if (request.tipoEntrega() == TipoEntrega.DELIVERY &&
                (request.direccionEnvio() == null || request.direccionEnvio().isBlank()))
            throw bad("Delivery requiere una dirección de envío");
        Venta venta = new Venta();
        venta.setEmpresa(empresas.findById(request.empresaId()).orElseThrow(() -> missing("Configuración fiscal")));
        venta.setUsuario(usuarios.findById(usuarioId).orElseThrow(() -> missing("Usuario")));
        venta.setCliente(resolverCliente(request.clienteId(), request.cliente()));
        venta.setTipoComprobante(comprobantes.findById(request.tipoComprobanteId()).orElseThrow(() -> missing("Comprobante")));
        validarCliente(venta);
        venta.setNumeroComprobante(request.numeroComprobante().trim());
        venta.setFechaVenta(OffsetDateTime.now());
        venta.setOrigenPedido(OrigenPedido.ONLINE);
        venta.setTelefonoEntrega(venta.getCliente().getTelefono());
        venta.setTipoEntrega(request.tipoEntrega());
        venta.setDireccionEnvio(request.tipoEntrega() == TipoEntrega.DELIVERY ? request.direccionEnvio().trim() : null);
        agregarLineas(venta, request.items());
        ventas.saveAndFlush(venta);
        if (Boolean.TRUE.equals(request.pagoTotal()) && (request.pagoInicial() == null ||
                request.pagoInicial().monto().compareTo(venta.getTotal()) != 0))
            throw bad("El pago total debe coincidir con el importe vigente del pedido. Actualiza el catálogo");
        // Pedido, stock y adelanto se confirman juntos. Un pago inválido revierte todo.
        if (request.pagoInicial() != null) registrarAbono(venta, request.pagoInicial(), usuarioId);
        return venta;
    }

    @Transactional
    public Venta agregarItems(Integer id, List<ItemRequest> items) {
        Venta venta = ventas.findByIdForUpdate(id).orElseThrow(() -> missing("Venta"));
        validarAbierta(venta);
        if (venta.getEstadoCuenta() == EstadoCuenta.CERRADA) throw conflict("La cuenta ya está pagada");
        agregarLineas(venta, items);
        return venta;
    }

    /** El stock sale al enviar cada tanda. Los IDs ordenados evitan bloqueos cruzados. */
    private void agregarLineas(Venta venta, List<ItemRequest> items) {
        var cantidades = consolidar(items);
        for (var entry : cantidades.entrySet()) {
            Producto producto = productos.findById(entry.getKey()).orElseThrow(() -> missing("Producto"));
            entityManager.refresh(producto, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (producto.getStockActual() < entry.getValue())
                throw new StockInsuficienteException(producto.getNombre(), producto.getStockActual(), entry.getValue());
            producto.setStockActual(producto.getStockActual() - entry.getValue());
            BigDecimal precio = producto.getPrecioVenta();
            // Una tanda adicional nunca se mezcla con platos que cocina ya comenzó o terminó.
            var detalle = new DetalleVenta();
            detalle.setVenta(venta);
            detalle.setProducto(producto);
            detalle.setCantidad(entry.getValue());
            detalle.setPrecioUnitario(precio);
            detalle.setSubtotal(precio.multiply(BigDecimal.valueOf(entry.getValue())));
            venta.getDetalles().add(detalle);
        }
        BigDecimal subtotal = venta.getDetalles().stream().map(DetalleVenta::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal igv = subtotal.multiply(IGV).setScale(2, RoundingMode.HALF_UP);
        if (subtotal.add(igv).compareTo(MAX_IMPORTE) > 0) throw bad("El importe supera el límite de una venta");
        venta.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        venta.setIgv(igv);
        venta.setTotal(subtotal.add(igv).setScale(2, RoundingMode.HALF_UP));
        recalcularCuenta(venta);
        venta.setCuentaSolicitada(false);
        venta.setFechaSolicitudCuenta(null);
        events.changed(true);
    }

    @Transactional
    public Venta solicitarCuenta(Integer id) {
        Venta venta = ventas.findByIdForUpdate(id).orElseThrow(() -> missing("Venta"));
        validarAbierta(venta);
        if (venta.getMesa() == null) throw conflict("Solo las mesas solicitan cuenta");
        validarEntrega(venta);
        venta.setCuentaSolicitada(true);
        if (venta.getFechaSolicitudCuenta() == null) venta.setFechaSolicitudCuenta(OffsetDateTime.now());
        cargarCuenta(venta);
        events.changed(false);
        return venta;
    }

    @Transactional
    public Venta cerrar(Integer id) {
        Venta venta = ventas.findByIdForUpdate(id).orElseThrow(() -> missing("Venta"));
        validarAbierta(venta);
        if (totalPagado(venta).compareTo(venta.getTotal()) != 0)
            throw conflict("Los pagos deben cubrir exactamente el total antes de cerrar");
        validarEntrega(venta);
        finalizar(venta);
        cargarCuenta(venta);
        return venta;
    }

    /** Pago, cierre, fidelización y numeración forman una única unidad atómica. */
    @Transactional
    public Venta cobrar(Integer id, CobrarVentaRequest request, Integer usuarioId) {
        Venta venta = ventas.findByIdForUpdate(id).orElseThrow(() -> missing("Venta"));
        if (request.pago() != null) registrarAbono(venta, request.pago().toPago(), usuarioId);
        if (venta.getEstado() == EstadoVenta.CERRADA) {
            if (venta.getComprobante() == null) throw conflict("La venta histórica ya está cerrada");
            cargarCuenta(venta);
            return venta; // Reintento confirmado: ni otro pago ni otro correlativo.
        }
        validarAbierta(venta);
        if (totalPagado(venta).compareTo(venta.getTotal()) == 0) {
            if (venta.getMesa() != null) validarEntrega(venta);
            finalizar(venta);
        } else if (request.pago() == null) throw conflict("Todavía existe saldo pendiente");
        cargarCuenta(venta);
        return venta;
    }

    private void validarEntrega(Venta venta) {
        if (venta.getDetalles().isEmpty()) throw bad("La comanda no tiene productos");
        if (venta.getDetalles().stream().anyMatch(d -> d.getEstadoPreparacion() != EstadoPreparacion.SERVIDO))
            throw conflict("Entrega todos los productos antes de finalizar la mesa");
    }

    private void finalizar(Venta venta) {
        recalcularCuenta(venta);
        if (totalPagado(venta).compareTo(venta.getTotal()) != 0)
            throw conflict("Los pagos deben cubrir exactamente el total antes de cerrar");
        if (venta.getDetalles().isEmpty()) throw bad("La comanda no tiene productos");
        Mesa mesa = venta.getMesa() == null ? null : mesas.findByIdForUpdate(venta.getMesa().getId()).orElseThrow(() -> missing("Mesa"));
        Cliente cliente = clientes.findByIdForUpdate(venta.getCliente().getId()).orElseThrow(() -> missing("Cliente"));
        entityManager.refresh(cliente, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        venta.setCliente(cliente);
        validarCliente(venta); // Revalidar la ficha fiscal vigente antes de emitir.
        cliente.setFrecuenciaVisitas(Math.addExact(cliente.getFrecuenciaVisitas(), 1L));
        cliente.setTotalGastado(cliente.getTotalGastado().add(venta.getTotal()));
        venta.setEstado(EstadoVenta.CERRADA);
        venta.setFechaCobro(OffsetDateTime.now());
        emitirComprobante(venta);
        if (mesa != null) mesa.setEstado(EstadoMesa.LIBRE);
        events.changed(false);
    }

    private void emitirComprobante(Venta venta) {
        if (venta.getComprobante() != null) return;
        TipoComprobante tipo = comprobantes.findByIdForUpdate(venta.getTipoComprobante().getId()).orElseThrow(() -> missing("Comprobante"));
        entityManager.refresh(tipo, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        long numero = Math.addExact(tipo.getUltimoCorrelativo(), 1L);
        tipo.setUltimoCorrelativo(numero);
        var c = new Comprobante();
        c.setVenta(venta); c.setTipoComprobante(tipo); c.setTipo(tipo.getNombre());
        c.setSerie(tipo.getSerie()); c.setCorrelativo(numero);
        c.setEmpresaRuc(venta.getEmpresa().getRuc()); c.setEmpresaNombre(venta.getEmpresa().getRazonSocial());
        c.setEmpresaDireccion(venta.getEmpresa().getDireccion());
        c.setClienteDocumento(venta.getCliente().getNumeroDocumento()); c.setClienteNombre(venta.getCliente().getNombresRazonSocial());
        c.setClienteDireccion(venta.getCliente().getDireccion()); c.setFechaEmision(venta.getFechaCobro());
        c.setSubtotal(venta.getSubtotal()); c.setIgv(venta.getIgv()); c.setTotal(venta.getTotal());
        venta.getDetalles().forEach(item -> {
            var d = new DetalleComprobante();
            d.setComprobante(c); d.setProducto(item.getProducto().getNombre()); d.setCantidad(item.getCantidad());
            d.setPrecioUnitario(item.getPrecioUnitario()); d.setSubtotal(item.getSubtotal()); c.getDetalles().add(d);
        });
        documentos.saveAndFlush(c);
        venta.setComprobante(c);
    }

    @Transactional(readOnly = true)
    public CajaResponse resumenCaja() {
        var abiertas = listar(true);
        var locales = abiertas.stream().filter(v -> v.getOrigenPedido() == OrigenPedido.LOCAL).toList();
        var recientes = ventas.findTop20ByEstadoOrderByFechaCobroDesc(EstadoVenta.CERRADA);
        recientes.forEach(this::cargarCuenta);
        return new CajaResponse(locales.stream().filter(Venta::isCuentaSolicitada).sorted(Comparator.comparing(Venta::getFechaSolicitudCuenta)).map(VentaResponse::from).toList(),
            locales.stream().filter(v -> !v.isCuentaSolicitada()).map(VentaResponse::from).toList(),
            listarOnline().stream().map(VentaResponse::from).toList(),
            recientes.stream().map(VentaResponse::from).toList());
    }

    @Transactional
    public Venta registrarPago(Integer id, RegistrarPagoRequest request, Integer usuarioId) {
        Venta venta = ventas.findByIdForUpdate(id).orElseThrow(() -> missing("Venta"));
        registrarAbono(venta, request, usuarioId);
        cargarCuenta(venta); // La respuesta se serializa con open-in-view=false, fuera de esta transacción.
        return venta;
    }

    private void registrarAbono(Venta venta, RegistrarPagoRequest request, Integer usuarioId) {
        BigDecimal monto = request.monto();
        BigDecimal recibido = request.metodoPago() == MetodoPago.efectivo ? request.montoRecibido() : monto;
        String referencia = request.referencia() == null ? "" : request.referencia().trim();
        var existente = venta.getPagos().stream()
                .filter(p -> p.getClaveOperacion().equals(request.claveOperacion())).findFirst().orElse(null);
        if (existente != null) {
            if (existente.getMonto().compareTo(monto) != 0 || existente.getMetodoPago() != request.metodoPago()
                    || existente.getMontoRecibido().compareTo(recibido) != 0 || !existente.getReferencia().equals(referencia))
                throw conflict("La clave de operación ya corresponde a otro abono");
            return; // Un reintento después de perder la respuesta nunca cobra dos veces.
        }
        validarAbierta(venta);
        if (monto == null || monto.signum() <= 0 || monto.scale() > 2 || request.metodoPago() == null)
            throw bad("El abono debe ser positivo y tener como máximo dos decimales");
        if (recibido == null || recibido.compareTo(monto) < 0) throw bad("El monto recibido no cubre el abono");
        if (monto.compareTo(venta.getTotal().subtract(totalPagado(venta))) > 0)
            throw conflict("El abono supera el saldo pendiente");
        var pago = new PagoVenta();
        pago.setVenta(venta);
        pago.setUsuario(usuarios.findById(usuarioId).orElseThrow(() -> missing("Usuario")));
        pago.setMonto(monto.setScale(2, RoundingMode.UNNECESSARY));
        pago.setMetodoPago(request.metodoPago());
        pago.setMontoRecibido(recibido);
        pago.setReferencia(referencia);
        pago.setClaveOperacion(request.claveOperacion());
        pagos.save(pago);
        venta.getPagos().add(pago);
        venta.setMetodoPago(request.metodoPago()); // Compatibilidad con el comprobante anterior; el libro conserva todos los métodos.
        recalcularCuenta(venta);
        events.changed(false);
    }

    private BigDecimal totalPagado(Venta venta) {
        return venta.getPagos().stream().map(PagoVenta::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** El saldo se deriva del libro; nunca se acepta un estado financiero enviado por el cliente. */
    private void recalcularCuenta(Venta venta) {
        BigDecimal abonado = totalPagado(venta);
        venta.setEstadoCuenta(abonado.compareTo(venta.getTotal()) == 0 ? EstadoCuenta.CERRADA :
                abonado.signum() > 0 ? EstadoCuenta.PAGADA_PARCIALMENTE : EstadoCuenta.ABIERTA);
    }

    private Cliente resolverCliente(Integer id, ClienteRequest data) {
        if (id != null) return clientes.findById(id).orElseThrow(() -> missing("Cliente"));
        if (data == null) throw bad("Los datos del cliente son obligatorios");
        var existing = clientes.findByNumeroDocumento(data.numeroDocumento().trim());
        // No sobreescribir la ficha ni las métricas por un pedido simultáneo o por datos antiguos del POS.
        if (existing.isPresent()) return existing.get();
        var cliente = new Cliente();
        cliente.setNumeroDocumento(data.numeroDocumento().trim());
        cliente.setNombresRazonSocial(data.nombresRazonSocial().trim());
        cliente.setDireccion(data.direccion());
        cliente.setTelefono(data.telefono());
        cliente.setCorreo(data.correo());
        cliente.setFechaNacimiento(data.fechaNacimiento());
        return clientes.save(cliente);
    }
    private void validarCliente(Venta venta) {
        if (venta.getTipoComprobante().getNombre().toUpperCase(Locale.ROOT).contains("FACTURA")
                && (!venta.getCliente().getNumeroDocumento().matches("\\d{11}")
                || venta.getCliente().getDireccion() == null || venta.getCliente().getDireccion().isBlank()))
            throw bad("Para una factura se requiere RUC de 11 dígitos y dirección fiscal");
    }
    private Map<Integer, Integer> consolidar(List<ItemRequest> items) {
        if (items == null || items.isEmpty()) throw bad("La comanda requiere productos");
        var result = new TreeMap<Integer, Integer>();
        for (var item : items) {
            if (item == null || item.productoId() == null || item.cantidad() == null || item.cantidad() < 1)
                throw bad("Cada producto debe tener una cantidad mayor que cero");
            result.merge(item.productoId(), item.cantidad(), this::sumar);
        }
        return result;
    }
    private int sumar(int a, int b) {
        try { return Math.addExact(a, b); }
        catch (ArithmeticException e) { throw bad("La cantidad acumulada es demasiado grande"); }
    }
    private void validarAbierta(Venta venta) {
        if (venta.getEstado() != EstadoVenta.ABIERTA) throw conflict("La venta ya está cerrada");
    }
    private ResponseStatusException missing(String entity) { return new ResponseStatusException(HttpStatus.NOT_FOUND, entity + " no encontrado"); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
