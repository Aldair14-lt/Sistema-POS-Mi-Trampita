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

    record Fixture(Producto primero, Producto segundo, Cliente cliente, Mesa mesa, Empresa empresa, Usuario usuario) { }
    Fixture fixture(int stock) {
        int seq = sequence.incrementAndGet();
        var empresa = new Empresa(); empresa.setRuc("20" + String.format("%09d", seq)); empresa.setRazonSocial("Prueba"); empresa.setDireccion("Prueba");
        empresa = empresas.save(empresa);
        var proveedor = new Proveedor(); proveedor.setRucDni(String.valueOf(seq)); proveedor.setRazonSocial("Prueba"); proveedor = proveedores.save(proveedor);
        var cliente = new Cliente(); cliente.setNumeroDocumento(String.valueOf(10000000 + seq)); cliente.setNombresRazonSocial("Cliente " + seq);
        cliente.setFechaNacimiento(LocalDate.now().minusYears(25)); cliente = clientes.save(cliente);
        var user = user("MOZO");
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
            cocina.actualizar(item.getId(), new UpdateItemStatusRequest(EstadoPreparacion.EN_PREPARACION, EstadoPreparacion.PENDIENTE), false);
            cocina.actualizar(item.getId(), new UpdateItemStatusRequest(EstadoPreparacion.LISTO, EstadoPreparacion.EN_PREPARACION), false);
            cocina.actualizar(item.getId(), new UpdateItemStatusRequest(EstadoPreparacion.SERVIDO, EstadoPreparacion.LISTO), true);
        }
    }
    RegistrarPagoRequest abono(String monto) {
        return new RegistrarPagoRequest(new BigDecimal(monto), MetodoPago.tarjeta, null, "", UUID.randomUUID().toString());
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
        assertThatThrownBy(() -> service.registrarPago(v.getId(), new RegistrarPagoRequest(v.getTotal(), MetodoPago.efectivo,
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
        mvc.perform(post("/api/ventas").session(caja).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
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
        cocina.actualizar(id, new UpdateItemStatusRequest(EstadoPreparacion.EN_PREPARACION, EstadoPreparacion.PENDIENTE), false);
        cocina.actualizar(id, new UpdateItemStatusRequest(EstadoPreparacion.LISTO, EstadoPreparacion.EN_PREPARACION), false);
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
        assertThatThrownBy(() -> service.registrarPago(v.getId(), new RegistrarPagoRequest(BigDecimal.ONE, MetodoPago.tarjeta,
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
                .content("{\"estado\":\"EN_PREPARACION\",\"estadoActual\":\"PENDIENTE\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/cocina/items/" + id + "/estado").session(cook).with(csrf()).contentType("application/json")
                .content("{\"estado\":\"SERVIDO\",\"estadoActual\":\"EN_PREPARACION\"}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/cocina/items/" + id + "/estado").session(cook).with(csrf()).contentType("application/json")
                .content("{\"estado\":\"LISTO\",\"estadoActual\":\"PENDIENTE\"}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/cocina/items/" + id + "/estado").session(cook).with(csrf()).contentType("application/json")
                .content("{\"estado\":\"LISTO\",\"estadoActual\":\"EN_PREPARACION\"}")).andExpect(status().isOk());
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
}
