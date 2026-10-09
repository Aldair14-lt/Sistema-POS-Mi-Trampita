package MiTrampita.SistemaPOS;

import MiTrampita.SistemaPOS.dto.VentaDtos.*;
import MiTrampita.SistemaPOS.dto.*;
import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.exception.StockInsuficienteException;
import MiTrampita.SistemaPOS.repositorio.*;
import MiTrampita.SistemaPOS.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class PosFlowTests {
    @Autowired MockMvc mvc;
    @Autowired VentaService service;
    @Autowired KitchenService cocina;
    @Autowired ProductoService productoService;
    @Autowired MarketingService marketing;
    @Autowired MesaService mesaService;
    @Autowired CajaService cajaService;
    @Autowired PedidoWebService web;
    @Autowired SolicitudWebRepository solicitudesWeb;
    @Autowired ProductoRepository productos;
    @Autowired MesaRepository mesas;
    @Autowired ClienteRepository clientes;
    @Autowired VentaRepository ventas;
    @Autowired EmpresaRepository empresas;
    @Autowired UsuarioRepository usuarios;
    @Autowired UsuarioRolRepository relaciones;
    @Autowired RolRepository roles;
    @Autowired CategoriaRepository categorias;
    @Autowired MarcaRepository marcas;
    @Autowired ProveedorRepository proveedores;
    @Autowired AreaRepository areas;
    @Autowired TipoComprobanteRepository comprobantes;
    private static final AtomicInteger sequence = new AtomicInteger(1000);

    @Test void ticketLocalSinClienteCompletaCuentaSinCrearFichaNiAlterarMarketing() throws Exception {
        var f = fixture(5); long fichas = clientes.count();
        var tipo = comprobantes.findByNombreIgnoreCaseAndSerie("NOTA DE VENTA", "NV01").orElseThrow();
        var v = service.registrar(new VentaRequest(f.empresa().getId(), null, null, tipo.getId(),
            "ANON-" + UUID.randomUUID(), f.mesa().getId(), List.of(new ItemRequest(f.primero().getId(), 1))), f.usuario().getId());
        assertThat(v.getCliente()).isNull();
        mvc.perform(get("/api/ventas/" + v.getId()).session(login(f.usuario())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cliente").isEmpty());
        service.registrarPago(v.getId(), abono("5.00"), f.usuario().getId());
        service.agregarItems(v.getId(), List.of(new ItemRequest(f.primero().getId(), 1)), UUID.randomUUID().toString());
        entregar(v);
        var request = new CobrarVentaRequest(parcial("18.60", MetodoPago.YAPE), new FacturacionRequest(TipoDocumento.NOTA_VENTA, null, null, null));
        var cerrado = service.cobrar(v.getId(), request, f.usuario().getId());
        assertThat(cerrado.getComprobante().getClienteDocumento()).isEmpty();
        assertThat(cerrado.getComprobante().getClienteNombre()).isEqualTo("CONSUMIDOR FINAL");
        assertThat(service.cobrar(v.getId(), request, f.usuario().getId()).getPagos()).hasSize(2);
        assertThat(mesas.findById(f.mesa().getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.LIBRE);
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(3);
        assertThat(clientes.count()).isEqualTo(fichas);
        assertThat(clientes.findById(f.cliente().getId()).orElseThrow().getFrecuenciaVisitas()).isZero();
        mvc.perform(get("/api/caja/resumen").session(login(user("CAJA")))).andExpect(status().isOk());
    }

    @Test void facturaYEntregaExternaSinClienteNoAbrenMesaNiDescuentanStock() throws Exception {
        var f = fixture(4); long total = ventas.count();
        var factura = comprobantes.findByNombreIgnoreCaseAndSerie("FACTURA", "F001").orElseThrow();
        String body = "{\"empresaId\":" + f.empresa().getId() + ",\"tipoComprobanteId\":" + factura.getId()
            + ",\"numeroComprobante\":\"ANON-" + UUID.randomUUID() + "\",\"mesaId\":" + f.mesa().getId()
            + ",\"items\":[{\"productoId\":" + f.primero().getId() + ",\"cantidad\":1}]}";
        mvc.perform(post("/api/ventas").session(login(f.usuario())).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isBadRequest());
        var externo = new VentaRequest(f.empresa().getId(), null, null, factura.getId(), "ANON-" + UUID.randomUUID(), null,
            List.of(new ItemRequest(f.primero().getId(), 1)), OrigenPedido.WHATSAPP, TipoEntrega.RECOJO, null, "999888777", null, false);
        assertThatThrownBy(() -> service.registrar(externo, f.usuario().getId())).hasMessageContaining("cliente");
        assertThat(ventas.count()).isEqualTo(total);
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(4);
        assertThat(mesas.findById(f.mesa().getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.LIBRE);
    }

    @Test void boletaLocalSinIdentificarRespetaLimiteEnRegistroYEnEmision() {
        var f = fixture(3); var p = productos.findById(f.primero().getId()).orElseThrow();
        var tipo = comprobantes.findByNombreIgnoreCaseAndSerie("BOLETA", "B001").orElseThrow();
        p.setPrecioVenta(new BigDecimal("593.23")); productos.save(p);
        var request = new VentaRequest(f.empresa().getId(), null, null, tipo.getId(), "ANON-" + UUID.randomUUID(),
            f.mesa().getId(), List.of(new ItemRequest(p.getId(), 1)));
        assertThatThrownBy(() -> service.registrar(request, f.usuario().getId())).hasMessageContaining("700");
        assertThat(productos.findById(p.getId()).orElseThrow().getStockActual()).isEqualTo(3);
        assertThat(mesas.findById(f.mesa().getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.LIBRE);
        // El rollback puede incrementar la versión del objeto en memoria; recargar antes de editarlo.
        p = productos.findById(p.getId()).orElseThrow();
        p.setPrecioVenta(new BigDecimal("593.22")); productos.save(p);
        var v = service.registrar(request, f.usuario().getId()); entregar(v);
        var c = service.cobrar(v.getId(), new CobrarVentaRequest(parcial("700.00", MetodoPago.YAPE)), f.usuario().getId()).getComprobante();
        assertThat(c.getDni()).isNull(); assertThat(c.getClienteNombre()).isEqualTo("CONSUMIDOR FINAL");
    }

    @Test void ticketAnonimoPermiteFacturaFiscalSinInventarClienteYRechazaBoletaIncompleta() {
        var f = fixture(3); var tipo = comprobantes.findByNombreIgnoreCaseAndSerie("NOTA DE VENTA", "NV01").orElseThrow();
        long fichas = clientes.count();
        var v = service.registrar(new VentaRequest(f.empresa().getId(), null, null, tipo.getId(), "ANON-" + UUID.randomUUID(),
            f.mesa().getId(), List.of(new ItemRequest(f.primero().getId(), 1))), f.usuario().getId()); entregar(v);
        var pago = parcial("11.80", MetodoPago.PLIN);
        assertThatThrownBy(() -> service.cobrar(v.getId(), new CobrarVentaRequest(pago,
            new FacturacionRequest(TipoDocumento.BOLETA, null, null, "Cliente sin documento")), f.usuario().getId()))
            .hasMessageContaining("DNI");
        assertThat(service.obtener(v.getId()).getPagos()).isEmpty();
        var fiscal = new FacturacionRequest(TipoDocumento.FACTURA,
            new FacturacionRequest.Factura("20123456789", "Empresa SAC", "Av. Principal 123"), null, null);
        var c = service.cobrar(v.getId(), new CobrarVentaRequest(pago, fiscal), f.usuario().getId()).getComprobante();
        assertThat(c.getRuc()).isEqualTo("20123456789"); assertThat(c.getClienteNombre()).isEqualTo("Empresa SAC");
        assertThat(clientes.count()).isEqualTo(fichas);
    }

    @Test void catalogoRechazaTiposDeComprobanteQueNoSePuedenEmitir() throws Exception {
        mvc.perform(post("/api/tipos-comprobante").session(login(user("ADMIN"))).with(csrf()).contentType("application/json")
            .content("{\"nombre\":\"RECIBO_NO_SOPORTADO\",\"serie\":\"X001\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test void comprobanteHistoricoInvalidoNoAbreCuentaNiDescuentaStock() {
        var f = fixture(4); var tipo = new TipoComprobante();
        tipo.setNombre("RECIBO_NO_SOPORTADO"); tipo.setSerie("X" + sequence.incrementAndGet()); comprobantes.save(tipo);
        long count = ventas.count();
        assertThatThrownBy(() -> service.registrar(new VentaRequest(f.empresa().getId(), f.cliente().getId(), null,
            tipo.getId(), "QA-" + UUID.randomUUID(), f.mesa().getId(), List.of(new ItemRequest(f.primero().getId(), 1))), f.usuario().getId()))
            .hasMessageContaining("comprobante soportado");
        assertThat(ventas.count()).isEqualTo(count);
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(4);
        assertThat(mesas.findById(f.mesa().getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.LIBRE);
    }

    @Test void mozoNoPuedeAgregarProductosAUnaCuentaExternaPorId() throws Exception {
        var f = fixture(5);
        var venta = service.registrar(new VentaRequest(f.empresa().getId(), f.cliente().getId(), null,
            boletaWeb().tipoComprobanteId(), "QA-" + UUID.randomUUID(), null, List.of(new ItemRequest(f.primero().getId(), 1)),
            OrigenPedido.WHATSAPP, TipoEntrega.RECOJO, null, "999888777", null, false), f.usuario().getId());
        mvc.perform(patch("/api/ventas/" + venta.getId() + "/items").session(login(f.usuario())).with(csrf()).contentType("application/json")
            .content("{\"claveOperacion\":\"" + UUID.randomUUID() + "\",\"items\":[{\"productoId\":" + f.primero().getId() + ",\"cantidad\":1}]}"))
            .andExpect(status().isForbidden());
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(4);
        assertThat(service.obtener(venta.getId()).getDetalles()).hasSize(1);
    }
    @Test void crearCatalogoConIdNoSobrescribeRegistrosExistentes() throws Exception {
        var admin = login(user("ADMIN")); var categoria = categorias.findAll().getFirst(); String anterior = categoria.getNombre();
        mvc.perform(post("/api/categorias").session(admin).with(csrf()).contentType("application/json")
            .content("{\"id\":" + categoria.getId() + ",\"nombre\":\"Creación aislada " + sequence.incrementAndGet() + "\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(categoria.getId())));
        assertThat(categorias.findById(categoria.getId()).orElseThrow().getNombre()).isEqualTo(anterior);
    }
    @Test void productoRechazaReferenciasAnidadasInvalidasSinErrorInterno() throws Exception {
        var f = fixture(3); var admin = login(user("ADMIN"));
        String body = "{\"categoria\":{},\"proveedor\":{\"id\":" + f.primero().getProveedor().getId()
            + "},\"codigoBarras\":\"QA-invalid\",\"nombre\":\"Prueba\",\"precioCompra\":0,\"precioVenta\":1.25,\"stockActual\":3,\"stockMinimo\":1}";
        mvc.perform(post("/api/productos").session(admin).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/productos").session(admin).with(csrf()).contentType("application/json")
            .content(body.replace("\"categoria\":{}", "\"categoria\":{\"id\":2147483647}"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/productos").session(admin).with(csrf()).contentType("application/json")
            .content(body.replace("\"categoria\":{}", "\"categoria\":{\"id\":" + f.primero().getCategoria().getId() + "}")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.precioVenta").value(1.25)).andExpect(jsonPath("$.visibleWeb").value(false));
    }
    @Test void cambiarContrasenaRevocaSesionAnteriorSinExponerCredenciales() throws Exception {
        var user = user("MOZO"); var session = login(user);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.contrasena").doesNotExist()).andExpect(jsonPath("$.credential").doesNotExist());
        var saved = usuarios.findById(user.getId()).orElseThrow(); saved.setContrasena("newpass123"); usuarios.save(saved);
        mvc.perform(get("/api/mesas").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content("{\"usuario\":\"" + user.getUsuario() + "\",\"contrasena\":\"testpass123\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void loginNoAceptaAliasPorTruncamientoBcryptNiDevuelve500() throws Exception {
        var user = user("MOZO"); login(user);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content("{\"usuario\":\"" + user.getUsuario() + "\",\"contrasena\":\"" + "á".repeat(50) + "\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void loginLimitaFallosPorUsuarioYDireccionSinBloqueoPermanente() {
        var limiter = new MiTrampita.SistemaPOS.security.LoginAttemptLimiter();
        for (int i = 0; i < 5; i++) { limiter.check("192.0.2.1", "CaseUser"); limiter.failed("192.0.2.1", "CaseUser"); }
        assertThatThrownBy(() -> limiter.check("192.0.2.2", "caseuser")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("429");
        limiter.succeeded("CASEUSER"); limiter.check("192.0.2.1", "CaseUser");
        for (int i = 0; i < 30; i++) limiter.failed("192.0.2.3", "user" + i);
        assertThatThrownBy(() -> limiter.check("192.0.2.3", "new-user")).hasMessageContaining("429");
    }
    @Test void cuerposGrandesSeRechazanAntesDeJacksonTambienEnLogin() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(" ".repeat(4097))).andExpect(status().is(413));
        mvc.perform(post("/api/productos").with(csrf()).contentType("application/json").content(" ".repeat(262145))).andExpect(status().is(413));
    }
    @Test void conexionesSseSonAcotadasPorUsuarioYRenuevanLaSesion() {
        var events = new OperationEvents();
        for (int i = 0; i < 6; i++) assertThat(events.subscribe(false, 1).getTimeout()).isEqualTo(300000L);
        assertThatThrownBy(() -> events.subscribe(true, 1)).hasMessageContaining("429");
        assertThat(events.subscribe(true, 2)).isNotNull();
    }
    @Test void adicionalIdempotenteNoDuplicaLineasStockYRechazaCambiosDeContenido() {
        var f = fixture(5); var sale = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        String key = UUID.randomUUID().toString(); var items = List.of(new ItemRequest(f.primero().getId(), 2, "Sin sal"));
        service.agregarItems(sale.getId(), items, key); service.agregarItems(sale.getId(), items, key);
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(2);
        assertThat(service.obtener(sale.getId()).getDetalles()).hasSize(2).filteredOn(d -> key.equals(d.getClaveComanda())).hasSize(1);
        assertThatThrownBy(() -> service.agregarItems(sale.getId(), List.of(new ItemRequest(f.primero().getId(), 1)), key)).hasMessageContaining("otros productos");
    }
    @Test void adicionalConcurrenteSeConfirmaUnaVezYRollbackNoConsumeSuClave() throws Exception {
        var f = fixture(5); var sale = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        String key = UUID.randomUUID().toString(); var items = List.of(new ItemRequest(f.primero().getId(), 2));
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); return service.agregarItems(sale.getId(), items, key).getId(); });
            var second = pool.submit(() -> { start.await(); return service.agregarItems(sale.getId(), items, key).getId(); }); start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
            assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(2);
            String retry = UUID.randomUUID().toString(); var tooMany = List.of(new ItemRequest(f.primero().getId(), 3));
            assertThatThrownBy(() -> service.agregarItems(sale.getId(), tooMany, retry)).isInstanceOf(StockInsuficienteException.class);
            var product = productos.findById(f.primero().getId()).orElseThrow(); product.setStockActual(3); productos.save(product);
            service.agregarItems(sale.getId(), tooMany, retry);
            assertThat(productos.findById(product.getId()).orElseThrow().getStockActual()).isZero();
        } finally { pool.shutdownNow(); }
    }

    String tiendaWeb(Fixture f) {
        var p = productos.findById(f.primero().getId()).orElseThrow(); p.setVisibleWeb(true); productos.save(p);
        var bebida = productos.findById(f.segundo().getId()).orElseThrow(); bebida.setVisibleWeb(true); bebida.setAreaDestino(AreaDestino.BAR); productos.save(bebida);
        String slug = "web-" + f.empresa().getId();
        web.configurar(f.empresa().getId(), new PedidoWebDtos.Configurar(slug, true, true, true, "Horario QA")); return slug;
    }
    PedidoWebDtos.Enviar carrito(Fixture f, UUID clave, List<PedidoWebDtos.Item> items) {
        var total = BigDecimal.TEN.multiply(BigDecimal.valueOf(items.stream().mapToInt(PedidoWebDtos.Item::cantidad).sum())).multiply(new BigDecimal("1.18")).setScale(2);
        return new PedidoWebDtos.Enviar(clave, "Nombre del comprador", f.cliente().getNumeroDocumento(), "999888777", TipoEntrega.DELIVERY, "Av. Prueba 123", "Llamar al llegar", items, total);
    }
    PedidoWebDtos.Aceptar boletaWeb() {
        return new PedidoWebDtos.Aceptar(comprobantes.findByNombreIgnoreCaseAndSerie("BOLETA", "B001").orElseThrow().getId());
    }
    @Test void carritoPublicoNoModificaStockPagosNiFichaYConfirmacionEsIdempotente() {
        var f = fixture(5); String slug = tiendaWeb(f); long prevVentas = ventas.count(); long prevClientes = clientes.count();
        var request = carrito(f, UUID.randomUUID(), List.of(new PedidoWebDtos.Item(f.primero().getId(), 2, "Sin ají"), new PedidoWebDtos.Item(f.segundo().getId(), 1, "Sin hielo")));
        var recibido = web.enviar(slug, request); assertThat(recibido.estado()).isEqualTo("PENDIENTE"); assertThat(recibido.total()).isEqualByComparingTo("35.40");
        assertThat(web.enviar(slug, request).codigoSeguimiento()).isEqualTo(recibido.codigoSeguimiento());
        assertThat(ventas.count()).isEqualTo(prevVentas); assertThat(clientes.count()).isEqualTo(prevClientes);
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(5);
        var solicitud = solicitudesWeb.findByEmpresa_IdAndCodigoSeguimiento(f.empresa().getId(), recibido.codigoSeguimiento()).orElseThrow();
        var venta = web.aceptar(solicitud.getId(), boletaWeb(), f.usuario().getId());
        assertThat(venta.origenPedido()).isEqualTo(OrigenPedido.WEB); assertThat(venta.saldoPendiente()).isEqualByComparingTo("35.40");
        assertThat(venta.pagos()).isEmpty(); assertThat(venta.observacionesPedido()).isEqualTo("Llamar al llegar");
        assertThat(web.aceptar(solicitud.getId(), boletaWeb(), f.usuario().getId()).id()).isEqualTo(venta.id());
        assertThat(ventas.count()).isEqualTo(prevVentas + 1); assertThat(clientes.findById(f.cliente().getId()).orElseThrow().getNombresRazonSocial()).isEqualTo(f.cliente().getNombresRazonSocial());
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(3);
        var pedido = service.obtener(venta.id()); assertThat(pedido.getDetalles()).extracting(DetalleVenta::getAreaDestino).containsExactlyInAnyOrder(AreaDestino.COCINA, AreaDestino.BAR);
        assertThat(web.consultar(slug, UUID.fromString(recibido.codigoSeguimiento())).estado()).isEqualTo("ACEPTADO");
        cocina.actualizar(pedido.getDetalles().getFirst().getId(), new UpdateItemStatusRequest(EstadoPreparacion.PREPARANDO, EstadoPreparacion.PENDIENTE), false, pedido.getDetalles().getFirst().getAreaDestino());
        assertThat(web.consultar(slug, UUID.fromString(recibido.codigoSeguimiento())).estado()).isEqualTo("PREPARANDO");
    }
    @Test void confirmacionWebConcurrenteNoDuplicaVentaNiStock() throws Exception {
        var f = fixture(3); String slug = tiendaWeb(f); var recibido = web.enviar(slug, carrito(f, UUID.randomUUID(), List.of(new PedidoWebDtos.Item(f.primero().getId(), 1, ""))));
        int id = solicitudesWeb.findByEmpresa_IdAndCodigoSeguimiento(f.empresa().getId(), recibido.codigoSeguimiento()).orElseThrow().getId();
        var inicio = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try { List<Future<Integer>> resultados = new ArrayList<>(); for (int i = 0; i < 2; i++) resultados.add(pool.submit(() -> { inicio.await(); return web.aceptar(id, boletaWeb(), f.usuario().getId()).id(); }));
            inicio.countDown(); assertThat(resultados.get(0).get(20, TimeUnit.SECONDS)).isEqualTo(resultados.get(1).get(20, TimeUnit.SECONDS));
            assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(2);
        } finally { pool.shutdownNow(); }
    }
    @Test void cambioDePrecioYFaltaDeStockRevierteConfirmacionCompleta() {
        var f = fixture(3); String slug = tiendaWeb(f); long prevVentas = ventas.count();
        var recibido = web.enviar(slug, carrito(f, UUID.randomUUID(), List.of(new PedidoWebDtos.Item(f.primero().getId(), 1, ""), new PedidoWebDtos.Item(f.segundo().getId(), 1, ""))));
        int id = solicitudesWeb.findByEmpresa_IdAndCodigoSeguimiento(f.empresa().getId(), recibido.codigoSeguimiento()).orElseThrow().getId();
        var segundo = productos.findById(f.segundo().getId()).orElseThrow(); segundo.setPrecioVenta(new BigDecimal("11.00")); productos.save(segundo);
        assertThatThrownBy(() -> web.aceptar(id, boletaWeb(), f.usuario().getId())).hasMessageContaining("Cambió el menú");
        segundo = productos.findById(f.segundo().getId()).orElseThrow(); segundo.setPrecioVenta(BigDecimal.TEN); segundo.setStockActual(0); productos.save(segundo);
        assertThatThrownBy(() -> web.aceptar(id, boletaWeb(), f.usuario().getId())).isInstanceOf(StockInsuficienteException.class);
        assertThat(ventas.count()).isEqualTo(prevVentas); assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(3);
        assertThat(solicitudesWeb.findById(id).orElseThrow().getEstado()).isEqualTo(SolicitudWeb.Estado.PENDIENTE);
    }
    @Test void webRechazaProductosPrivadosCantidadesAcumuladasYClavesAlteradas() {
        var f = fixture(50); String slug = tiendaWeb(f); var request = carrito(f, UUID.randomUUID(), List.of(new PedidoWebDtos.Item(f.primero().getId(), 1, "")));
        web.enviar(slug, request);
        assertThatThrownBy(() -> web.enviar(slug, carrito(f, request.claveOperacion(), List.of(new PedidoWebDtos.Item(f.primero().getId(), 2, ""))))).hasMessageContaining("otro pedido");
        assertThatThrownBy(() -> web.enviar(slug, carrito(f, UUID.randomUUID(), List.of(new PedidoWebDtos.Item(f.primero().getId(), 15, "A"), new PedidoWebDtos.Item(f.primero().getId(), 6, "B"))))).hasMessageContaining("Máximo 20");
        var p = productos.findById(f.segundo().getId()).orElseThrow(); p.setVisibleWeb(false); productos.save(p);
        assertThatThrownBy(() -> web.enviar(slug, carrito(f, UUID.randomUUID(), List.of(new PedidoWebDtos.Item(p.getId(), 1, ""))))).hasMessageContaining("no está disponible");
    }
    @Test void webRespetaPausaEntregaLimitesExpiracionYRechazoSinReponerStock() {
        var f = fixture(5); String slug = tiendaWeb(f);
        web.configurar(f.empresa().getId(), new PedidoWebDtos.Configurar(slug, true, true, false, ""));
        assertThatThrownBy(() -> web.enviar(slug, carrito(f, UUID.randomUUID(), List.of(new PedidoWebDtos.Item(f.primero().getId(), 1, ""))))).hasMessageContaining("modalidad");
        web.configurar(f.empresa().getId(), new PedidoWebDtos.Configurar(slug, true, true, true, ""));
        var recibido = web.enviar(slug, carrito(f, UUID.randomUUID(), List.of(new PedidoWebDtos.Item(f.primero().getId(), 1, ""))));
        var s = solicitudesWeb.findByEmpresa_IdAndCodigoSeguimiento(f.empresa().getId(), recibido.codigoSeguimiento()).orElseThrow();
        web.rechazar(s.getId(), new PedidoWebDtos.Rechazar("Fuera de cobertura"));
        assertThat(web.consultar(slug, UUID.fromString(recibido.codigoSeguimiento())).motivo()).isEqualTo("Fuera de cobertura");
        assertThatThrownBy(() -> web.aceptar(s.getId(), boletaWeb(), f.usuario().getId())).hasMessageContaining("rechazado");
        var nuevo = web.enviar(slug, carrito(f, UUID.randomUUID(), List.of(new PedidoWebDtos.Item(f.primero().getId(), 1, ""))));
        var viejo = solicitudesWeb.findByEmpresa_IdAndCodigoSeguimiento(f.empresa().getId(), nuevo.codigoSeguimiento()).orElseThrow(); viejo.setFechaCreacion(java.time.OffsetDateTime.now().minusHours(25)); solicitudesWeb.save(viejo);
        assertThat(web.consultar(slug, UUID.fromString(nuevo.codigoSeguimiento())).estado()).isEqualTo("EXPIRADO");
        assertThat(web.pendientes()).extracting(PedidoWebDtos.Solicitud::id).doesNotContain(viejo.getId());
        web.configurar(f.empresa().getId(), new PedidoWebDtos.Configurar(slug, false, true, true, ""));
        assertThatThrownBy(() -> web.menu(slug)).hasMessageContaining("no está recibiendo");
        assertThat(web.consultar(slug, UUID.fromString(recibido.codigoSeguimiento())).estado()).isEqualTo("RECHAZADO");
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(5);
    }
    @Test void webAnonimaSoloExponeMenuYSeguimientoYConservaCsrfInterno() throws Exception {
        var f = fixture(3); String slug = tiendaWeb(f);
        mvc.perform(get("/api/public/tiendas/" + slug + "/menu")).andExpect(status().isOk()).andExpect(jsonPath("$.productos[0].precioCompra").doesNotExist())
            .andExpect(jsonPath("$.productos[0].stockActual").doesNotExist()).andExpect(jsonPath("$.productos[0].proveedor").doesNotExist());
        var path = "/api/public/tiendas/" + slug + "/pedidos";
        String body = """
            {"claveOperacion":"%s","nombre":"Comprador público","dni":"%s","telefono":"999888777","tipoEntrega":"RECOJO","items":[{"productoId":%d,"cantidad":1}],"totalEsperado":11.80,"total":0,"pagoInicial":{"monto":1000},"usuarioId":1}"""
            .formatted(UUID.randomUUID(), f.cliente().getNumeroDocumento(), f.primero().getId());
        mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(11.80))
            .andExpect(jsonPath("$.nombre").doesNotExist()).andExpect(jsonPath("$.dni").doesNotExist());
        mvc.perform(post(path).contentType("application/json").content(body.replace("\"cantidad\":1", "\"cantidad\":0"))).andExpect(status().isBadRequest());
        mvc.perform(post(path).contentType("application/json").content(body.replace("\"tipoEntrega\":\"RECOJO\"", "\"tipoEntrega\":\"MESA\""))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/web/solicitudes")).andExpect(status().isUnauthorized());
        var mozo = login(user("MOZO")); var caja = login(user("CAJA"));
        mvc.perform(get("/api/web/solicitudes").session(mozo)).andExpect(status().isForbidden());
        mvc.perform(get("/api/web/solicitudes").session(caja)).andExpect(status().isOk());
        mvc.perform(get("/api/web/configuracion").session(caja)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/web/solicitudes/1/aceptar").session(caja).contentType("application/json").content("{\"tipoComprobanteId\":1}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/public/tiendas/" + slug + "/pedidos/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
    @Test void limitePublicoEsAcotadoYNoPermiteDiezEnviosMasUno() {
        var limite = new MiTrampita.SistemaPOS.security.PublicOrderRateLimiter();
        for (int i = 0; i < 10; i++) limite.validar("127.0.0.1");
        assertThatThrownBy(() -> limite.validar("127.0.0.1")).hasMessageContaining("Demasiados intentos");
        limite.validar("127.0.0.2");
    }
    @Test void webPideRevisarElCarritoSiCambioElPrecioAntesDeEnviar() {
        var f = fixture(3); String slug = tiendaWeb(f);
        var request = carrito(f, UUID.randomUUID(), List.of(new PedidoWebDtos.Item(f.primero().getId(), 1, "")));
        var p = productos.findById(f.primero().getId()).orElseThrow(); p.setPrecioVenta(new BigDecimal("12.00")); productos.save(p);
        long prev = solicitudesWeb.count();
        assertThatThrownBy(() -> web.enviar(slug, request)).hasMessageContaining("Actualiza la carta");
        assertThat(solicitudesWeb.count()).isEqualTo(prev);
        assertThat(productos.findById(p.getId()).orElseThrow().getStockActual()).isEqualTo(3);
    }
    @Test void limiteHttpPublicoSeAplicaAntesDeDeserializarYAcotaElCuerpo() throws Exception {
        String path = "/api/public/tiendas/prueba/pedidos";
        mvc.perform(post(path).with(r -> { r.setRemoteAddr("192.0.2.10"); return r; }).contentType("application/json").content(" ".repeat(32769)))
            .andExpect(status().is(413));
        for (int i = 0; i < 10; i++) mvc.perform(post(path).with(r -> { r.setRemoteAddr("192.0.2.11"); return r; }).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(path).with(r -> { r.setRemoteAddr("192.0.2.11"); return r; }).contentType("application/json").content("{}"))
            .andExpect(status().is(429)).andExpect(header().string("Retry-After", "60"));
    }
    @Test void pedidosPendientesPorTelefonoNoPermitenAcapararYNoReservanStock() {
        var f = fixture(5); String slug = tiendaWeb(f);
        for (int i = 0; i < 3; i++) web.enviar(slug, carrito(f, UUID.randomUUID(), List.of(new PedidoWebDtos.Item(f.primero().getId(), 1, ""))));
        assertThatThrownBy(() -> web.enviar(slug, carrito(f, UUID.randomUUID(), List.of(new PedidoWebDtos.Item(f.primero().getId(), 1, ""))))).hasMessageContaining("pendientes");
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(5);
    }

    record Fixture(Producto primero, Producto segundo, Cliente cliente, Mesa mesa, Empresa empresa, Usuario usuario) { }
    Fixture fixture(int stock) {
        int seq = sequence.incrementAndGet();
        var empresa = new Empresa(); empresa.setRuc("20" + String.format("%09d", seq)); empresa.setRazonSocial("Prueba"); empresa.setDireccion("Prueba");
        empresa = empresas.save(empresa);
        var proveedor = new Proveedor(); proveedor.setRucDni(String.valueOf(seq)); proveedor.setRazonSocial("Prueba"); proveedor = proveedores.save(proveedor);
        var cliente = new Cliente(); cliente.setNumeroDocumento(String.valueOf(10000000 + seq)); cliente.setNombresRazonSocial("Cliente " + seq);
        cliente.setFechaNacimiento(LocalDate.now().minusYears(25)); cliente = clientes.save(cliente);
        var user = user("MOZO");
        cajaService.abrir(new SesionCajaDtos.Abrir(empresa.getId(), new BigDecimal("100.00"), UUID.randomUUID().toString()), user("CAJA").getId());
        var mesa = mesa();
        var p1 = product("P" + seq, stock, proveedor);
        var p2 = product("Q" + seq, stock, proveedor);
        return new Fixture(p1, p2, cliente, mesa, empresa, user);
    }
    Producto product(String codigo, int stock, Proveedor proveedor) {
        var p = new Producto(); p.setCodigoBarras(codigo); p.setNombre(codigo);
        p.setCategoria(categorias.findAll().getFirst()); p.setMarca(marcas.findAll().getFirst()); p.setProveedor(proveedor);
        p.setPrecioVenta(new BigDecimal("10.00")); p.setStockActual(stock); return productos.save(p);
    }
    Mesa mesa() {
        var mesa = new Mesa(); mesa.setNumero(sequence.incrementAndGet()); mesa.setArea(areas.findAll().getFirst());
        return mesas.save(mesa);
    }
    Usuario user(String role) {
        var u = new Usuario(); u.setUsuario("test" + sequence.incrementAndGet()); u.setNombreCompleto("Prueba " + role);
        u.setContrasena("testpass123"); u = usuarios.save(u);
        var ur = new UsuarioRol(); ur.setUsuario(u); ur.setRol(roles.findByNombreIgnoreCase(role).orElseThrow()); relaciones.save(ur);
        return u;
    }
    Venta abrir(Fixture f, Mesa mesa, List<ItemRequest> items) {
        return service.registrar(new VentaRequest(f.empresa().getId(), f.cliente().getId(), null,
            comprobantes.findByNombreIgnoreCaseAndSerie("BOLETA", "B001").orElseThrow().getId(),
            "T-" + UUID.randomUUID(), mesa.getId(), items), f.usuario().getId());
    }
    MockHttpSession login(Usuario u) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"usuario\":\"" + u.getUsuario() + "\",\"contrasena\":\"testpass123\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    void entregar(Venta v) {
        for (var item : service.obtener(v.getId()).getDetalles()) {
            cocina.actualizar(item.getId(), new UpdateItemStatusRequest(EstadoPreparacion.PREPARANDO, EstadoPreparacion.PENDIENTE), false);
            cocina.actualizar(item.getId(), new UpdateItemStatusRequest(EstadoPreparacion.LISTO, EstadoPreparacion.PREPARANDO), false);
            cocina.actualizar(item.getId(), new UpdateItemStatusRequest(EstadoPreparacion.SERVIDO, EstadoPreparacion.LISTO), true);
        }
    }
    RegistrarPagoRequest abono(String monto) {
        return new RegistrarPagoRequest(new BigDecimal(monto), MetodoPago.TARJETA, null, "", UUID.randomUUID().toString());
    }
    void pagarYEntregar(Venta v, Integer usuarioId) {
        service.registrarPago(v.getId(), abono(v.getTotal().toPlainString()), usuarioId);
        entregar(v);
    }
    @Test void pedidoDescuentaYCierreActualizaFidelizacionUnaVez() {
        var f = fixture(10);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 2), new ItemRequest(f.primero().getId(), 1)));
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(7);
        assertThat(v.getDetalles()).hasSize(1);
        service.agregarItems(v.getId(), List.of(new ItemRequest(f.primero().getId(), 1)));
        v = service.obtener(v.getId());
        assertThat(v.getDetalles()).hasSize(2);
        pagarYEntregar(v, f.usuario().getId());
        service.cerrar(v.getId());
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(6);
        assertThat(mesas.findById(f.mesa().getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.LIBRE);
        var c = clientes.findById(f.cliente().getId()).orElseThrow();
        assertThat(c.getFrecuenciaVisitas()).isEqualTo(1);
        assertThat(c.getTotalGastado()).isEqualByComparingTo("47.20");
        Integer id = v.getId();
        assertThatThrownBy(() -> service.cerrar(id)).hasMessageContaining("ya está cerrada");
        assertThat(marketing.consumo(c.getId()).getFirst().getUnidades()).isEqualTo(4);
    }
    @Test void stockInsuficienteEnSegundaLineaRevierteElPrimerDescuento() {
        var f = fixture(2);
        assertThatThrownBy(() -> abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1), new ItemRequest(f.segundo().getId(), 3))))
                .isInstanceOf(StockInsuficienteException.class);
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(2);
        assertThat(productos.findById(f.segundo().getId()).orElseThrow().getStockActual()).isEqualTo(2);
        assertThat(mesas.findById(f.mesa().getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.LIBRE);
        assertThat(clientes.findById(f.cliente().getId()).orElseThrow().getFrecuenciaVisitas()).isZero();
    }
    @Test void pedidosSimultaneosNoPermitenStockNegativo() throws Exception {
        var f = fixture(1);
        var secondMesa = mesa();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> tasks = new ArrayList<>();
            for (var table : List.of(f.mesa(), secondMesa)) tasks.add(executor.submit(() -> {
                start.await();
                try { abrir(f, table, List.of(new ItemRequest(f.primero().getId(), 1))); return true; }
                catch (StockInsuficienteException ex) { return false; }
            }));
            start.countDown();
            int successful = 0;
            for (var task : tasks) if (task.get(20, TimeUnit.SECONDS)) successful++;
            assertThat(successful).isEqualTo(1);
        }
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isZero();
        assertThat(clientes.findById(f.cliente().getId()).orElseThrow().getFrecuenciaVisitas()).isZero();
    }
    @Test void efectivoInsuficienteNoRegistraAbonoNiVuelveADescontarStock() {
        var f = fixture(5);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        assertThatThrownBy(() -> service.registrarPago(v.getId(), new RegistrarPagoRequest(v.getTotal(), MetodoPago.EFECTIVO,
                BigDecimal.ONE, "", UUID.randomUUID().toString()), f.usuario().getId())).hasMessageContaining("monto recibido");
        assertThat(service.obtener(v.getId()).getPagos()).isEmpty();
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(4);
    }
    @Test void noPermiteDosComandasNiLiberarUnaMesaConPedido() {
        var f = fixture(10);
        abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        assertThatThrownBy(() -> abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)))).hasMessageContaining("comanda abierta");
        assertThatThrownBy(() -> mesaService.liberar(f.mesa().getId())).hasMessageContaining("Cobra la comanda");
    }
    @Test void rolesSeValidanEnServidorYLosCambiosSeAplicanASesionesExistentes() throws Exception {
        mvc.perform(get("/api/mesas")).andExpect(status().isUnauthorized());
        var mozo = user("MOZO"); var session = login(mozo);
        mvc.perform(get("/api/marketing").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/configuracion").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ventas").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ventas/abiertas").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/clientes").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pos/clientes").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].frecuenciaVisitas").isEmpty());
        mvc.perform(patch("/api/ventas/1/cerrar").session(session).with(csrf()).contentType("application/json")
            .content("{\"metodoPago\":\"tarjeta\"}")).andExpect(status().isForbidden());
        mozo = usuarios.findById(mozo.getId()).orElseThrow(); mozo.setEstado(EstadoUsuario.bloqueado); usuarios.save(mozo);
        mvc.perform(get("/api/mesas").session(session)).andExpect(status().isUnauthorized());
        var caja = login(user("CAJA"));
        mvc.perform(post("/api/ventas").session(caja).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/ventas/1/items").session(caja).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/marketing").session(caja)).andExpect(status().isForbidden());
        mvc.perform(get("/api/marketing").session(login(user("ADMIN")))).andExpect(status().isOk());
    }
    @Test void faltaMesaYElementosNulosDevuelven400SinCrearVenta() throws Exception {
        var f = fixture(5);
        var session = login(f.usuario());
        String body = "{\"empresaId\":" + f.empresa().getId() + ",\"clienteId\":" + f.cliente().getId()
            + ",\"tipoComprobanteId\":" + comprobantes.findAll().getFirst().getId() + ",\"numeroComprobante\":\"VALIDAR\",\"items\":[null]}";
        mvc.perform(post("/api/ventas").session(session).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ventas").session(session).contentType("application/json").content(body))
            .andExpect(status().isForbidden());
    }
    @Test void cumpleanerosIncluyeElMesActualYConsumoIgnoraPedidosAbiertos() {
        var f = fixture(5);
        abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        assertThat(marketing.consumo(f.cliente().getId())).isEmpty();
        assertThat(marketing.cumpleaneros(LocalDate.now().getMonthValue())).extracting(MarketingService.ClienteResumen::id)
                .contains(f.cliente().getId());
    }
    @Test void usuarioEnJsonNoPuedeSuplantarAlMozoAutenticado() throws Exception {
        var f = fixture(5);
        String body = "{\"empresaId\":" + f.empresa().getId() + ",\"usuarioId\":999999,\"clienteId\":" + f.cliente().getId()
            + ",\"tipoComprobanteId\":" + comprobantes.findByNombreIgnoreCaseAndSerie("BOLETA", "B001").orElseThrow().getId()
            + ",\"numeroComprobante\":\"IDENTIDAD-" + sequence.incrementAndGet() + "\",\"mesaId\":" + f.mesa().getId()
            + ",\"items\":[{\"productoId\":" + f.primero().getId() + ",\"cantidad\":1}]}";
        mvc.perform(post("/api/ventas").session(login(f.usuario())).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.usuario.id").value(f.usuario().getId()));
    }

    @Test void editarUnProductoDesactualizadoNoPuedeReponerStockCobrado() {
        var f = fixture(5);
        Producto stale = productos.findById(f.primero().getId()).orElseThrow();
        var sale = abrir(f, f.mesa(), List.of(new ItemRequest(stale.getId(), 2)));
        pagarYEntregar(sale, f.usuario().getId());
        service.cerrar(sale.getId());
        stale.setNombre("Nombre editado con datos antiguos");
        assertThatThrownBy(() -> productoService.actualizar(stale.getId(), stale))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(productos.findById(stale.getId()).orElseThrow().getStockActual()).isEqualTo(3);
    }

    @Test void abonosRecalculanSaldoYAdicionalesSinAlterarUnaTandaLista() {
        var f = fixture(10);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 2)));
        var id = v.getDetalles().getFirst().getId();
        cocina.actualizar(id, new UpdateItemStatusRequest(EstadoPreparacion.PREPARANDO, EstadoPreparacion.PENDIENTE), false);
        cocina.actualizar(id, new UpdateItemStatusRequest(EstadoPreparacion.LISTO, EstadoPreparacion.PREPARANDO), false);
        var payment = abono("5.00");
        service.registrarPago(v.getId(), payment, f.usuario().getId());
        service.registrarPago(v.getId(), payment, f.usuario().getId());
        var updated = service.agregarItems(v.getId(), List.of(new ItemRequest(f.primero().getId(), 1)));
        assertThat(updated.getEstadoCuenta()).isEqualTo(EstadoCuenta.PAGADA_PARCIALMENTE);
        assertThat(updated.getDetalles()).hasSize(2);
        assertThat(updated.getDetalles().getFirst().getEstadoPreparacion()).isEqualTo(EstadoPreparacion.LISTO);
        assertThat(updated.getDetalles().getLast().getEstadoPreparacion()).isEqualTo(EstadoPreparacion.PENDIENTE);
        var response = VentaResponse.from(service.obtener(v.getId()));
        assertThat(response.totalPagado()).isEqualByComparingTo("5.00");
        assertThat(response.saldoPendiente()).isEqualByComparingTo("30.40");
        assertThat(response.pagos()).hasSize(1);
        assertThatThrownBy(() -> service.cerrar(v.getId())).hasMessageContaining("cubrir exactamente");
        assertThatThrownBy(() -> service.registrarPago(v.getId(), abono("30.41"), f.usuario().getId())).hasMessageContaining("supera el saldo");
        service.registrarPago(v.getId(), abono("30.40"), f.usuario().getId());
        assertThat(service.obtener(v.getId()).getEstadoCuenta()).isEqualTo(EstadoCuenta.CERRADA);
        assertThatThrownBy(() -> service.agregarItems(v.getId(), List.of(new ItemRequest(f.primero().getId(), 1)))).hasMessageContaining("ya está pagada");
        assertThatThrownBy(() -> service.cerrar(v.getId())).hasMessageContaining("Entrega todos");
    }

    @Test void abonosSimultaneosNoSuperanSaldoYReintentosNoDuplicanPagos() throws Exception {
        var f = fixture(5);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 2; i++) tasks.add(executor.submit(() -> {
                start.await();
                try { service.registrarPago(v.getId(), abono("7.00"), f.usuario().getId()); return true; }
                catch (org.springframework.web.server.ResponseStatusException ex) { return false; }
            }));
            start.countDown();
            int count = 0;
            for (var task : tasks) if (task.get(20, TimeUnit.SECONDS)) count++;
            assertThat(count).isEqualTo(1);
        }
        var duplicate = abono("4.80");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(executor.submit(() -> service.registrarPago(v.getId(), duplicate, f.usuario().getId())),
                    executor.submit(() -> service.registrarPago(v.getId(), duplicate, f.usuario().getId())));
            for (var task : tasks) task.get(20, TimeUnit.SECONDS);
        }
        assertThat(service.obtener(v.getId()).getPagos()).hasSize(2);
        assertThat(VentaResponse.from(service.obtener(v.getId())).saldoPendiente()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> service.registrarPago(v.getId(), new RegistrarPagoRequest(BigDecimal.ONE, MetodoPago.TARJETA,
                null, "", duplicate.claveOperacion()), f.usuario().getId())).hasMessageContaining("otro abono");
    }

    PedidoOnlineRequest online(Fixture f, RegistrarPagoRequest payment, boolean total) {
        return new PedidoOnlineRequest(f.empresa().getId(), f.cliente().getId(), null,
                comprobantes.findByNombreIgnoreCaseAndSerie("BOLETA", "B001").orElseThrow().getId(),
                "ON-" + UUID.randomUUID(), TipoEntrega.DELIVERY, "Av. Prueba 123",
                List.of(new ItemRequest(f.primero().getId(), 1)), payment, total);
    }

    @Test void onlineAdmitePagoTotalAdelantoYContraEntregaSinSalirDeCocina() {
        for (String amount : List.of("0", "5.00", "11.80")) {
            var f = fixture(5);
            var v = service.registrarOnline(online(f, amount.equals("0") ? null : abono(amount), amount.equals("11.80")), f.usuario().getId());
            assertThat(v.getMesa()).isNull();
            assertThat(v.getOrigenPedido()).isEqualTo(OrigenPedido.ONLINE);
            assertThat(v.getEstado()).isEqualTo(EstadoVenta.ABIERTA);
            assertThat(v.getEstadoCuenta()).isEqualTo(amount.equals("0") ? EstadoCuenta.ABIERTA :
                    amount.equals("5.00") ? EstadoCuenta.PAGADA_PARCIALMENTE : EstadoCuenta.CERRADA);
            assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(4);
            assertThat(cocina.cola()).extracting(KitchenItemResponse::ventaId).contains(v.getId());
            assertThat(service.listarOnline()).extracting(Venta::getId).contains(v.getId());
            if (amount.equals("11.80")) {
                entregar(v); service.cerrar(v.getId());
                assertThat(service.listarOnline()).extracting(Venta::getId).doesNotContain(v.getId());
            }
        }
    }

    @Test void pagoOnlineInvalidoReviertePedidoYStock() {
        var f = fixture(3);
        long before = ventas.count();
        assertThatThrownBy(() -> service.registrarOnline(online(f, abono("12.00"), false), f.usuario().getId())).hasMessageContaining("supera el saldo");
        assertThatThrownBy(() -> service.registrarOnline(online(f, abono("5.00"), true), f.usuario().getId())).hasMessageContaining("pago total");
        assertThat(ventas.count()).isEqualTo(before);
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(3);
    }

    @Test void cocineroSoloTieneCocinaYMozoSoloEntregaLosListos() throws Exception {
        var f = fixture(5);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        var cook = login(user("COCINERO"));
        var mozo = login(f.usuario());
        for (String path : List.of("/api/ventas/abiertas", "/api/pedidos-online", "/api/mesas", "/api/productos", "/api/marketing"))
            mvc.perform(get(path).session(cook)).andExpect(status().isForbidden());
        mvc.perform(post("/api/ventas/" + v.getId() + "/pagos").session(cook).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/cocina/items").session(cook)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].total").isEmpty()).andExpect(jsonPath("$[*].cliente").isEmpty());
        mvc.perform(get("/api/cocina/items").session(mozo)).andExpect(status().isForbidden());
        int id = v.getDetalles().getFirst().getId();
        mvc.perform(patch("/api/cocina/items/" + id + "/estado").session(cook).with(csrf()).contentType("application/json")
                .content("{\"estado\":\"LISTO\",\"estadoActual\":\"PENDIENTE\"}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/cocina/items/" + id + "/estado").session(cook).with(csrf()).contentType("application/json")
                .content("{\"estado\":\"PREPARANDO\",\"estadoActual\":\"PENDIENTE\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/cocina/items/" + id + "/estado").session(cook).with(csrf()).contentType("application/json")
                .content("{\"estado\":\"SERVIDO\",\"estadoActual\":\"PREPARANDO\"}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/cocina/items/" + id + "/estado").session(cook).with(csrf()).contentType("application/json")
                .content("{\"estado\":\"LISTO\",\"estadoActual\":\"PENDIENTE\"}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/cocina/items/" + id + "/estado").session(cook).with(csrf()).contentType("application/json")
                .content("{\"estado\":\"LISTO\",\"estadoActual\":\"PREPARANDO\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/ventas/" + v.getId()).session(mozo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles[0].estadoPreparacion").value("LISTO"));
        mvc.perform(patch("/api/ventas/items/" + id + "/servir").session(mozo).with(csrf()).contentType("application/json")
                .content("{\"estado\":\"SERVIDO\",\"estadoActual\":\"LISTO\"}")).andExpect(status().isOk());
    }

    @Test void contratosPagoYOnlineRechazanImportesYEntregasInvalidos() throws Exception {
        var f = fixture(5);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        var caja = login(user("CAJA"));
        for (String amount : List.of("0", "-1", "1.001")) {
            mvc.perform(post("/api/ventas/" + v.getId() + "/pagos").session(caja).with(csrf()).contentType("application/json")
                .content("{\"monto\":" + amount + ",\"metodoPago\":\"tarjeta\",\"claveOperacion\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/ventas/" + v.getId() + "/pagos").session(login(f.usuario())).with(csrf())
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        String validPayment = "{\"monto\":5,\"metodoPago\":\"efectivo\",\"montoRecibido\":10,\"claveOperacion\":\"" + UUID.randomUUID() + "\"}";
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/ventas/" + v.getId() + "/pagos").session(caja).with(csrf())
                .contentType("application/json").content(validPayment)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.detalles[0].cantidad").value(1))
                .andExpect(jsonPath("$.pagos.length()").value(1))
                .andExpect(jsonPath("$.pagos[0].vuelto").value(5))
                .andExpect(jsonPath("$.saldoPendiente").value(6.80));
        mvc.perform(post("/api/pedidos-online").session(caja).with(csrf()).contentType("application/json")
                .content("{\"empresaId\":" + f.empresa().getId() + ",\"clienteId\":" + f.cliente().getId()
                    + ",\"tipoComprobanteId\":" + comprobantes.findAll().getFirst().getId()
                    + ",\"numeroComprobante\":\"INVALIDO\",\"tipoEntrega\":\"DELIVERY\",\"items\":[{\"productoId\":"
                    + f.primero().getId() + ",\"cantidad\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    PagoParcialRequest parcial(String amount, MetodoPago method) {
        return new PagoParcialRequest(new BigDecimal(amount), method, null, "", UUID.randomUUID().toString());
    }

    @Test void cobroAtomicoReviertePagoSinEntregaYEmiteUnaSolaBoleta() {
        var f = fixture(5);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        var typeId = v.getTipoComprobante().getId();
        long sequenceBefore = comprobantes.findById(typeId).orElseThrow().getUltimoCorrelativo();
        var request = new CobrarVentaRequest(parcial("11.80", MetodoPago.YAPE));
        assertThatThrownBy(() -> service.cobrar(v.getId(), request, f.usuario().getId())).hasMessageContaining("Entrega todos");
        assertThat(service.obtener(v.getId()).getPagos()).isEmpty();
        assertThat(comprobantes.findById(typeId).orElseThrow().getUltimoCorrelativo()).isEqualTo(sequenceBefore);
        entregar(v);
        var closed = VentaResponse.from(service.cobrar(v.getId(), request, f.usuario().getId()));
        assertThat(closed.estado()).isEqualTo(EstadoVenta.CERRADA);
        assertThat(closed.comprobante().numero()).endsWith(String.format("%08d", sequenceBefore + 1));
        var retry = VentaResponse.from(service.cobrar(v.getId(), request, f.usuario().getId()));
        assertThat(retry.comprobante().id()).isEqualTo(closed.comprobante().id());
        assertThat(retry.pagos()).hasSize(1);
        assertThat(clientes.findById(f.cliente().getId()).orElseThrow().getFrecuenciaVisitas()).isEqualTo(1);
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(4);
        var product = productos.findById(f.primero().getId()).orElseThrow();
        product.setNombre("Nombre cambiado"); productos.save(product);
        var company = empresas.findById(f.empresa().getId()).orElseThrow();
        company.setRazonSocial("Empresa cambiada"); empresas.save(company);
        var snapshot = VentaResponse.from(service.obtener(v.getId())).comprobante();
        assertThat(snapshot.detalles().getFirst().producto()).isEqualTo(f.primero().getNombre());
        assertThat(snapshot.empresaNombre()).isEqualTo("Prueba");
    }

    @Test void solicitudCuentaNotificaCajaYAdicionalReabreElConsumo() {
        var f = fixture(5);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        assertThatThrownBy(() -> service.solicitarCuenta(v.getId())).hasMessageContaining("Entrega todos");
        entregar(v);
        service.solicitarCuenta(v.getId());
        assertThat(service.resumenCaja().mesasPorCobrar()).extracting(VentaResponse::id).contains(v.getId());
        service.agregarItems(v.getId(), List.of(new ItemRequest(f.primero().getId(), 1)));
        var updated = service.obtener(v.getId());
        assertThat(updated.isCuentaSolicitada()).isFalse();
        assertThat(updated.getFechaSolicitudCuenta()).isNull();
        assertThat(service.resumenCaja().mesasPorCobrar()).extracting(VentaResponse::id).doesNotContain(v.getId());
    }

    @Test void cobrosSimultaneosReutilizanComprobanteYCorrelativo() throws Exception {
        var f = fixture(5);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        entregar(v);
        var request = new CobrarVentaRequest(parcial("11.80", MetodoPago.PLIN));
        long before = comprobantes.findById(v.getTipoComprobante().getId()).orElseThrow().getUltimoCorrelativo();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> task = () -> { start.await(); return service.cobrar(v.getId(), request, f.usuario().getId()).getComprobante().getId(); };
            var a = executor.submit(task); var b = executor.submit(task);
            start.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(b.get(20, TimeUnit.SECONDS));
        }
        assertThat(comprobantes.findById(v.getTipoComprobante().getId()).orElseThrow().getUltimoCorrelativo()).isEqualTo(before + 1);
        assertThat(service.obtener(v.getId()).getPagos()).hasSize(1);
    }

    @Test void whatsappPagadoPermaneceEnCocinaYRecepcionHastaDespacho() {
        var f = fixture(5);
        var request = new RegistrarPedidoWhatsAppRequest(f.empresa().getId(), f.cliente().getId(), null,
            comprobantes.findByNombreIgnoreCaseAndSerie("BOLETA", "B001").orElseThrow().getId(),
            "WA-" + UUID.randomUUID(), TipoEntrega.DELIVERY, "Av. Prueba", "999888777",
            List.of(new ItemRequest(f.primero().getId(), 1)), parcial("5.00", MetodoPago.YAPE), false);
        var v = service.registrar(request.toVenta(), f.usuario().getId());
        var partial = service.cobrar(v.getId(), new CobrarVentaRequest(parcial("1.00", MetodoPago.PLIN)), f.usuario().getId());
        assertThat(partial.getEstado()).isEqualTo(EstadoVenta.ABIERTA);
        assertThat(partial.getComprobante()).isNull();
        service.cobrar(v.getId(), new CobrarVentaRequest(parcial("5.80", MetodoPago.YAPE)), f.usuario().getId());
        assertThat(service.resumenCaja().pedidosOnline()).extracting(VentaResponse::id).contains(v.getId());
        assertThat(cocina.cola()).extracting(KitchenItemResponse::ventaId).contains(v.getId());
        entregar(v);
        assertThat(service.listarOnline()).extracting(Venta::getId).doesNotContain(v.getId());
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(4);
    }

    @Test void nuevosContratosYPermisosProtegenCajaYSse() throws Exception {
        var f = fixture(5);
        var mozo = login(f.usuario()); var caja = login(user("CAJA")); var cook = login(user("COCINERO"));
        mvc.perform(get("/api/caja/resumen").session(caja)).andExpect(status().isOk());
        for (var session : List.of(mozo, cook))
            mvc.perform(get("/api/caja/resumen").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/operacion/eventos").session(cook)).andExpect(status().isForbidden());
        mvc.perform(get("/api/cocina/eventos").session(caja)).andExpect(status().isForbidden());
        String body = """
            {"empresaId":%d,"clienteId":%d,"tipoComprobanteId":%d,"numeroComprobante":"WA-%d",
             "origenPedido":"WHATSAPP","tipoEntrega":"DELIVERY","direccion":"Destino","telefono":"999888777",
             "items":[{"productoId":%d,"cantidad":1}]}
            """.formatted(f.empresa().getId(), f.cliente().getId(), comprobantes.findAll().getFirst().getId(),
                sequence.incrementAndGet(), f.primero().getId());
        mvc.perform(post("/api/ventas").session(mozo).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/ventas/whatsapp").session(cook).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/ventas/whatsapp").session(caja).with(csrf()).contentType("application/json")
                .content(body.replace("Destino", "")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ventas/whatsapp").session(caja).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.origenPedido").value("WHATSAPP"))
            .andExpect(jsonPath("$.telefonoEntrega").value("999888777"));
    }

    @Test void ventasDistintasEmitenCorrelativosUnicosConcurrentemente() throws Exception {
        var a = fixture(2); var b = fixture(2);
        var va = abrir(a, a.mesa(), List.of(new ItemRequest(a.primero().getId(), 1)));
        var vb = abrir(b, b.mesa(), List.of(new ItemRequest(b.primero().getId(), 1)));
        entregar(va); entregar(vb);
        long before = comprobantes.findById(va.getTipoComprobante().getId()).orElseThrow().getUltimoCorrelativo();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> { start.await(); return service.cobrar(va.getId(), new CobrarVentaRequest(parcial("11.80", MetodoPago.YAPE)), a.usuario().getId()).getComprobante().getCorrelativo(); });
            var second = executor.submit(() -> { start.await(); return service.cobrar(vb.getId(), new CobrarVentaRequest(parcial("11.80", MetodoPago.PLIN)), b.usuario().getId()).getComprobante().getCorrelativo(); });
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(before + 1, before + 2);
        }
    }

    @Test void facturaEnCajaValidaContratoYConservaSnapshotSinModificarCliente() throws Exception {
        var f = fixture(4);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        entregar(v);
        var caja = login(user("CAJA"));
        var clave = UUID.randomUUID().toString();
        String template = """
            {"pago":{"monto":11.80,"metodoPago":"EFECTIVO","montoRecibido":20,"claveOperacion":"%s"},
             "facturacion":{"tipoComprobante":"FACTURA","factura":{"ruc":"%s","razonSocial":"%s","direccionFiscal":"Av. Principal 123"}}}
            """;
        for (String[] invalid : List.of(new String[]{"", "Restaurante SAC"}, new String[]{"20123456789", " "}, new String[]{"123", "Restaurante SAC"})) {
            mvc.perform(post("/api/ventas/" + v.getId() + "/cobrar").session(caja).with(csrf()).contentType("application/json")
                .content(template.formatted(clave, invalid[0], invalid[1]))).andExpect(status().isBadRequest());
            assertThat(service.obtener(v.getId()).getPagos()).isEmpty();
        }
        String valid = template.formatted(clave, "20123456789", "Restaurante SAC");
        mvc.perform(post("/api/ventas/" + v.getId() + "/cobrar").session(login(f.usuario())).with(csrf()).contentType("application/json").content(valid))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/ventas/" + v.getId() + "/cobrar").session(caja).with(csrf()).contentType("application/json").content(valid))
            .andExpect(status().isOk()).andExpect(jsonPath("$.comprobante.tipoComprobante").value("FACTURA"))
            .andExpect(jsonPath("$.comprobante.ruc").value("20123456789"))
            .andExpect(jsonPath("$.pagos[0].metodoPago").value("EFECTIVO"))
            .andExpect(jsonPath("$.pagos[0].vuelto").value(8.2));
        mvc.perform(post("/api/ventas/" + v.getId() + "/cobrar").session(caja).with(csrf()).contentType("application/json").content(valid))
            .andExpect(status().isOk()).andExpect(jsonPath("$.pagos.length()").value(1));
        mvc.perform(post("/api/ventas/" + v.getId() + "/cobrar").session(caja).with(csrf()).contentType("application/json")
            .content(valid.replace("Restaurante SAC", "Otro negocio"))).andExpect(status().isConflict());
        assertThat(clientes.findById(f.cliente().getId()).orElseThrow().getNumeroDocumento()).isEqualTo(f.cliente().getNumeroDocumento());
    }

    @Test void boletaExigeDniSegunTotalNoSegunAbonoYTicketNoLoExige() {
        for (String price : List.of("593.22", "593.23")) {
            var f = fixture(3);
            var p = productos.findById(f.primero().getId()).orElseThrow(); p.setPrecioVenta(new BigDecimal(price)); productos.save(p);
            var v = abrir(f, f.mesa(), List.of(new ItemRequest(p.getId(), 1))); entregar(v);
            service.registrarPago(v.getId(), abono("690.00"), f.usuario().getId());
            String rest = v.getTotal().subtract(new BigDecimal("690.00")).toPlainString();
            var request = new CobrarVentaRequest(parcial(rest, MetodoPago.YAPE), new FacturacionRequest(TipoDocumento.BOLETA, null, null, null));
            if (price.equals("593.22")) {
                assertThat(v.getTotal()).isEqualByComparingTo("700.00");
                assertThat(service.cobrar(v.getId(), request, f.usuario().getId()).getComprobante().getDni()).isNull();
            } else {
                assertThat(v.getTotal()).isEqualByComparingTo("700.01");
                assertThatThrownBy(() -> service.cobrar(v.getId(), request, f.usuario().getId())).hasMessageContaining("DNI");
                assertThat(service.obtener(v.getId()).getPagos()).hasSize(1);
                var valid = new CobrarVentaRequest(request.pago(), new FacturacionRequest(TipoDocumento.BOLETA, null, "12345678", "Cliente identificado"));
                assertThat(service.cobrar(v.getId(), valid, f.usuario().getId()).getComprobante().getDni()).isEqualTo("12345678");
            }
        }
        var f = fixture(2); var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1))); entregar(v);
        var ticket = service.cobrar(v.getId(), new CobrarVentaRequest(parcial("11.80", MetodoPago.PLIN),
            new FacturacionRequest(TipoDocumento.NOTA_VENTA, null, null, null)), f.usuario().getId()).getComprobante();
        assertThat(ticket.getTipoDocumento()).isEqualTo(TipoDocumento.NOTA_VENTA);
        assertThat(ticket.getRuc()).isNull(); assertThat(ticket.getDni()).isNull();
    }

    @Test void cancelarPendienteReponeUnaVezYExcluyeDeReciboYMarketing() {
        var f = fixture(4);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1), new ItemRequest(f.segundo().getId(), 1)));
        var item = v.getDetalles().getFirst();
        service.registrarPago(v.getId(), abono("5.00"), f.usuario().getId());
        var request = new CancelarItemRequest("Cliente cambió su pedido");
        service.cancelarItem(item.getId(), request, f.usuario().getId());
        var updated = service.cancelarItem(item.getId(), request, f.usuario().getId());
        assertThat(updated.getTotal()).isEqualByComparingTo("11.80");
        assertThat(updated.getEstadoCuenta()).isEqualTo(EstadoCuenta.PAGADA_PARCIALMENTE);
        assertThat(productos.findById(item.getProducto().getId()).orElseThrow().getStockActual()).isEqualTo(4);
        var active = updated.getDetalles().stream().filter(d -> d.getEstadoPreparacion() != EstadoPreparacion.CANCELADO).findFirst().orElseThrow();
        cocina.actualizar(active.getId(), new UpdateItemStatusRequest(EstadoPreparacion.PREPARANDO, EstadoPreparacion.PENDIENTE), false);
        cocina.actualizar(active.getId(), new UpdateItemStatusRequest(EstadoPreparacion.LISTO, EstadoPreparacion.PREPARANDO), false);
        assertThat(cocina.cola()).extracting(KitchenItemResponse::id).contains(active.getId()).doesNotContain(item.getId());
        cocina.actualizar(active.getId(), new UpdateItemStatusRequest(EstadoPreparacion.SERVIDO, EstadoPreparacion.LISTO), true);
        var closed = service.cobrar(v.getId(), new CobrarVentaRequest(parcial("6.80", MetodoPago.TARJETA)), f.usuario().getId());
        assertThat(closed.getComprobante().getDetalles()).hasSize(1);
        assertThat(marketing.consumo(f.cliente().getId())).hasSize(1);
    }

    @Test void cancelarUltimoPlatoAnulaCuentaSinComprobanteYLiberaMesa() {
        var f = fixture(2); var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        var id = v.getDetalles().getFirst().getId();
        service.cancelarItem(id, new CancelarItemRequest("Cambio de planes"), f.usuario().getId());
        var result = service.cancelarItem(id, new CancelarItemRequest("Reintento"), f.usuario().getId());
        assertThat(result.getEstado()).isEqualTo(EstadoVenta.ANULADA);
        assertThat(result.getTotal()).isEqualByComparingTo("0.00");
        assertThat(result.getComprobante()).isNull();
        assertThat(mesas.findById(f.mesa().getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.LIBRE);
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(2);
        assertThat(clientes.findById(f.cliente().getId()).orElseThrow().getFrecuenciaVisitas()).isZero();
    }

    @Test void cancelacionConAdelantoExcedenteRevierteEstadoSaldoYStock() {
        var f = fixture(2); var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        var id = v.getDetalles().getFirst().getId();
        service.registrarPago(v.getId(), abono("5.00"), f.usuario().getId());
        assertThatThrownBy(() -> service.cancelarItem(id, new CancelarItemRequest("Cancelación"), f.usuario().getId())).hasMessageContaining("devolución");
        var updated = service.obtener(v.getId());
        assertThat(updated.getDetalles().getFirst().getEstadoPreparacion()).isEqualTo(EstadoPreparacion.PENDIENTE);
        assertThat(updated.getDetalles().getFirst().getMotivoCancelacion()).isNull();
        assertThat(updated.getTotal()).isEqualByComparingTo("11.80");
        assertThat(updated.getPagos()).hasSize(1);
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(1);
    }

    @Test void cocinaYCancelacionConcurrentesSoloPermitenUnGanador() throws Exception {
        var f = fixture(2); var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        var id = v.getDetalles().getFirst().getId(); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cancel = executor.submit(() -> {
                start.await();
                try { service.cancelarItem(id, new CancelarItemRequest("Cliente cancela"), f.usuario().getId()); return true; }
                catch (org.springframework.web.server.ResponseStatusException ex) { assertThat(ex.getStatusCode().value()).isEqualTo(409); return false; }
            });
            var prepare = executor.submit(() -> {
                start.await();
                try { cocina.actualizar(id, new UpdateItemStatusRequest(EstadoPreparacion.PREPARANDO, EstadoPreparacion.PENDIENTE), false); return true; }
                catch (org.springframework.web.server.ResponseStatusException ex) { assertThat(ex.getStatusCode().value()).isEqualTo(409); return false; }
            });
            start.countDown();
            boolean canceled = cancel.get(20, TimeUnit.SECONDS);
            assertThat(prepare.get(20, TimeUnit.SECONDS)).isNotEqualTo(canceled);
            assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(canceled ? 2 : 1);
        }
    }

    @Test void cancelacionValidaMotivoSesionCsrfYRolYMozoNoVeOnline() throws Exception {
        var f = fixture(2); var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        var path = "/api/ventas/items/" + v.getDetalles().getFirst().getId() + "/cancelar";
        var mozo = login(f.usuario()); var cook = login(user("COCINERO")); var cashier = login(user("CAJA"));
        mvc.perform(patch(path).session(cook).with(csrf()).contentType("application/json").content("{\"motivo\":\"Cancelar\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch(path).session(mozo).contentType("application/json").content("{\"motivo\":\"Cancelar\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch(path).session(mozo).with(csrf()).contentType("application/json").content("{\"motivo\":\" \"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch(path).session(cashier).with(csrf()).contentType("application/json").content("{\"motivo\":\" \"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/pedidos-online").session(mozo)).andExpect(status().isForbidden());
        mvc.perform(patch(path).session(mozo).with(csrf()).contentType("application/json").content("{\"motivo\":\"Cambio del cliente\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch(path).session(cashier).with(csrf()).contentType("application/json").content("{\"motivo\":\"Cambio del cliente\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ANULADA"));
        var external = service.registrarOnline(online(f, null, false), f.usuario().getId());
        var externalId = external.getDetalles().getFirst().getId();
        mvc.perform(get("/api/ventas/" + external.getId()).session(mozo)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/ventas/items/" + externalId + "/cancelar").session(mozo).with(csrf()).contentType("application/json")
            .content("{\"motivo\":\"Intento sobre pedido externo\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/ventas/items/" + externalId + "/servir").session(mozo).with(csrf()).contentType("application/json")
            .content("{\"estado\":\"SERVIDO\",\"estadoActual\":\"LISTO\"}")).andExpect(status().isForbidden());
    }

    @Test void turnoArqueaSoloCobrosNetosYPermiteAbonosEntreTurnos() {
        var f = fixture(4); var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 2)));
        var actual = cajaService.actual(f.empresa().getId());
        var efectivo = new RegistrarPagoRequest(new BigDecimal("5.00"), MetodoPago.EFECTIVO, new BigDecimal("20.00"), "", UUID.randomUUID().toString());
        service.registrarPago(v.getId(), efectivo, f.usuario().getId());
        service.registrarPago(v.getId(), abono("4.00"), f.usuario().getId());
        var cierre = new SesionCajaDtos.Cerrar(new BigDecimal("104.00"), "Falta un sol");
        var cerrado = cajaService.cerrar(actual.id(), cierre, f.usuario().getId());
        assertThat(cerrado.totalCobrado()).isEqualByComparingTo("9.00");
        assertThat(cerrado.efectivoEsperado()).isEqualByComparingTo("105.00");
        assertThat(cerrado.diferencia()).isEqualByComparingTo("-1.00");
        assertThat(cajaService.cerrar(actual.id(), cierre, f.usuario().getId()).fechaCierre()).isEqualTo(cerrado.fechaCierre());
        assertThatThrownBy(() -> cajaService.cerrar(actual.id(), new SesionCajaDtos.Cerrar(BigDecimal.ZERO, ""), f.usuario().getId())).hasMessageContaining("otro arqueo");
        // Un reintento confirmado del turno anterior no depende de que haya un turno abierto.
        service.registrarPago(v.getId(), efectivo, f.usuario().getId());
        assertThatThrownBy(() -> service.registrarPago(v.getId(), abono("1.00"), f.usuario().getId())).hasMessageContaining("Abre un turno");
        var apertura = new SesionCajaDtos.Abrir(f.empresa().getId(), new BigDecimal("50.00"), UUID.randomUUID().toString());
        var nuevo = cajaService.abrir(apertura, f.usuario().getId());
        assertThat(cajaService.abrir(apertura, f.usuario().getId()).id()).isEqualTo(nuevo.id());
        service.registrarPago(v.getId(), abono("14.60"), f.usuario().getId());
        assertThat(cajaService.actual(f.empresa().getId()).totalCobrado()).isEqualByComparingTo("14.60");
        assertThat(cajaService.obtener(actual.id()).totalCobrado()).isEqualByComparingTo("9.00");
        assertThat(service.obtener(v.getId()).getPagos()).hasSize(3);
    }

    @Test void pagoInicialSinTurnoReviertePedidoYStock() {
        var f = fixture(4); var actual = cajaService.actual(f.empresa().getId());
        cajaService.cerrar(actual.id(), new SesionCajaDtos.Cerrar(new BigDecimal("100.00"), ""), f.usuario().getId());
        long cantidad = ventas.count();
        assertThatThrownBy(() -> service.registrarOnline(online(f, abono("1.00"), false), f.usuario().getId())).hasMessageContaining("Abre un turno");
        assertThat(ventas.count()).isEqualTo(cantidad);
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(4);
    }

    @Test void aperturaConcurrentePermiteUnSoloTurno() throws Exception {
        var f = fixture(1); var actual = cajaService.actual(f.empresa().getId());
        cajaService.cerrar(actual.id(), new SesionCajaDtos.Cerrar(new BigDecimal("100.00"), ""), f.usuario().getId());
        var inicio = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) results.add(pool.submit(() -> {
                inicio.await();
                try { cajaService.abrir(new SesionCajaDtos.Abrir(f.empresa().getId(), BigDecimal.TEN, UUID.randomUUID().toString()), f.usuario().getId()); return true; }
                catch (org.springframework.web.server.ResponseStatusException ex) { assertThat(ex.getStatusCode().value()).isEqualTo(409); return false; }
            }));
            inicio.countDown();
            int ganadores = 0; for (var r : results) if (r.get(20, TimeUnit.SECONDS)) ganadores++;
            assertThat(ganadores).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void cierreYPagoConcurrentesNoPierdenCobrosEnArqueo() throws Exception {
        var f = fixture(3); var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        var actual = cajaService.actual(f.empresa().getId()); var inicio = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var pago = pool.submit(() -> {
                inicio.await();
                try { service.registrarPago(v.getId(), abono("1.00"), f.usuario().getId()); return true; }
                catch (org.springframework.web.server.ResponseStatusException ex) { assertThat(ex.getStatusCode().value()).isEqualTo(409); return false; }
            });
            var cierre = pool.submit(() -> { inicio.await(); return cajaService.cerrar(actual.id(), new SesionCajaDtos.Cerrar(new BigDecimal("100.00"), ""), f.usuario().getId()); });
            inicio.countDown(); boolean aceptado = pago.get(20, TimeUnit.SECONDS); var resultado = cierre.get(20, TimeUnit.SECONDS);
            assertThat(resultado.totalCobrado()).isEqualByComparingTo(aceptado ? "1.00" : "0.00");
            assertThat(service.obtener(v.getId()).getPagos()).hasSize(aceptado ? 1 : 0);
        } finally { pool.shutdownNow(); }
    }

    @Test void barYCocinaSeparanColasYRechazanIdsDeOtraEstacion() throws Exception {
        var f = fixture(3); var bebida = productos.findById(f.segundo().getId()).orElseThrow();
        bebida.setAreaDestino(AreaDestino.BAR); productos.save(bebida);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1, "Sin ají"), new ItemRequest(bebida.getId(), 1, "Sin hielo")));
        var plato = v.getDetalles().stream().filter(d -> d.getAreaDestino() == AreaDestino.COCINA).findFirst().orElseThrow();
        var trago = v.getDetalles().stream().filter(d -> d.getAreaDestino() == AreaDestino.BAR).findFirst().orElseThrow();
        assertThat(cocina.cola()).extracting(KitchenItemResponse::id).contains(plato.getId()).doesNotContain(trago.getId());
        assertThat(cocina.cola(AreaDestino.BAR)).extracting(KitchenItemResponse::id).contains(trago.getId()).doesNotContain(plato.getId());
        var bartender = login(user("BARTENDER")); var cook = login(user("COCINERO"));
        mvc.perform(get("/api/bar/items").session(bartender)).andExpect(status().isOk());
        mvc.perform(get("/api/cocina/items").session(bartender)).andExpect(status().isForbidden());
        mvc.perform(get("/api/bar/items").session(cook)).andExpect(status().isForbidden());
        for (var path : List.of("/api/ventas/abiertas", "/api/caja/resumen", "/api/marketing"))
            mvc.perform(get(path).session(bartender)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/bar/items/" + plato.getId() + "/estado").session(bartender).with(csrf()).contentType("application/json")
            .content("{\"estado\":\"PREPARANDO\",\"estadoActual\":\"PENDIENTE\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/cocina/items/" + trago.getId() + "/estado").session(cook).with(csrf()).contentType("application/json")
            .content("{\"estado\":\"PREPARANDO\",\"estadoActual\":\"PENDIENTE\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/bar/items/" + trago.getId() + "/estado").session(bartender).with(csrf()).contentType("application/json")
            .content("{\"estado\":\"PREPARANDO\",\"estadoActual\":\"PENDIENTE\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.observaciones").value("Sin hielo"));
        bebida = productos.findById(bebida.getId()).orElseThrow(); bebida.setAreaDestino(AreaDestino.COCINA); productos.save(bebida);
        assertThat(cocina.cola(AreaDestino.BAR)).extracting(KitchenItemResponse::id).contains(trago.getId());
    }

    @Test void notasDistintasNoSeMezclanYElStockSeDescuentaUnaVez() {
        var f = fixture(10);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1, "Sin sal"), new ItemRequest(f.primero().getId(), 2, "Sin sal"), new ItemRequest(f.primero().getId(), 1, "Con ají")));
        assertThat(v.getDetalles()).hasSize(2);
        assertThat(v.getDetalles().stream().filter(d -> d.getObservaciones().equals("Sin sal")).findFirst().orElseThrow().getCantidad()).isEqualTo(3);
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(6);
    }

    @Test void marketingUsaUltimaVentaCobradaYNormalizaIdentificadores() throws Exception {
        var f = fixture(4); var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        pagarYEntregar(v, f.usuario().getId()); service.cerrar(v.getId());
        var c = clientes.findById(f.cliente().getId()).orElseThrow();
        c.setTelefono("+51 999-888-777"); c.setCorreo("Cliente.QA@ejemplo.com");
        c.setFechaNacimiento(LocalDate.now(java.time.ZoneId.of("America/Lima")).plusDays(10).minusYears(30)); clientes.save(c);
        var vieja = ventas.findById(v.getId()).orElseThrow(); vieja.setFechaCobro(java.time.OffsetDateTime.now().minusDays(61)); ventas.save(vieja);
        assertThat(marketing.inactivos(60)).extracting(MarketingService.ClienteResumen::id).contains(c.getId());
        assertThat(marketing.proximosCumpleaneros(30)).extracting(MarketingService.ClienteResumen::id).contains(c.getId());
        assertThat(marketing.exportarCsv("todos")).contains("email,phone,country\r\n", "cliente.qa@ejemplo.com,51999888777,pe\r\n");
        mvc.perform(get("/api/marketing/exportar.csv").session(login(user("CAJA")))).andExpect(status().isForbidden());
        var reciente = abrir(f, mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        pagarYEntregar(reciente, f.usuario().getId()); service.cerrar(reciente.getId());
        assertThat(marketing.inactivos(60)).extracting(MarketingService.ClienteResumen::id).doesNotContain(c.getId());
        assertThat(marketing.exportarCsv("todos")).doesNotContain(c.getNumeroDocumento());
    }

    @Test void turnosRequierenRolCajaCsrfYMontosValidos() throws Exception {
        var f = fixture(1); var cashier = login(user("CAJA")); var mozo = login(f.usuario());
        String body = "{\"empresaId\":" + f.empresa().getId() + ",\"montoInicial\":-1,\"claveOperacion\":\"" + UUID.randomUUID() + "\"}";
        mvc.perform(post("/api/caja/sesiones/abrir").session(cashier).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/caja/sesiones/abrir").session(mozo).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/caja/sesiones/abrir").session(cashier).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/api/caja/sesiones/actual").param("empresaId", f.empresa().getId().toString()).session(cashier))
            .andExpect(status().isOk()).andExpect(jsonPath("$.montoInicial").value(100.0));
    }

    @Test void facturaRevalidaClienteAlCobrarYRevierteTodoSiPerdioDatosFiscales() {
        var f = fixture(2);
        var cliente = clientes.findById(f.cliente().getId()).orElseThrow();
        cliente.setNumeroDocumento("20" + cliente.getNumeroDocumento() + "1"); cliente.setDireccion("Dirección fiscal"); clientes.save(cliente);
        var v = service.registrar(new VentaRequest(f.empresa().getId(), cliente.getId(), null,
            comprobantes.findByNombreIgnoreCaseAndSerie("FACTURA", "F001").orElseThrow().getId(),
            "FACT-" + UUID.randomUUID(), f.mesa().getId(), List.of(new ItemRequest(f.primero().getId(), 1))), f.usuario().getId());
        entregar(v);
        cliente.setDireccion(""); clientes.save(cliente);
        assertThatThrownBy(() -> service.cobrar(v.getId(), new CobrarVentaRequest(parcial("11.80", MetodoPago.PLIN)), f.usuario().getId()))
            .hasMessageContaining("RUC");
        var updated = service.obtener(v.getId());
        assertThat(updated.getPagos()).isEmpty();
        assertThat(updated.getComprobante()).isNull();
        assertThat(updated.getEstado()).isEqualTo(EstadoVenta.ABIERTA);
        assertThat(mesas.findById(f.mesa().getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.ATENDIENDO);
        assertThat(clientes.findById(cliente.getId()).orElseThrow().getFrecuenciaVisitas()).isZero();
    }

}
