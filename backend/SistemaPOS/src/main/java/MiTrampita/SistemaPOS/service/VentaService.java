package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.repositorio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VentaService {
    private static final BigDecimal IGV = new BigDecimal("0.18");
    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final TipoComprobanteRepository comprobanteRepository;

    @Transactional(readOnly = true)
    public List<Venta> listar() { return ventaRepository.findAll(); }

    @Transactional(readOnly = true)
    public Venta obtener(Integer id) { return ventaRepository.findById(id).orElseThrow(() -> noEncontrado("Venta")); }

    @Transactional
    public Venta registrar(Integer empresaId, Integer usuarioId, Integer clienteId, Integer comprobanteId,
                           String numeroComprobante, MetodoPago metodoPago, List<ItemVenta> items) {
        if (items == null || items.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta requiere productos");
        Venta venta = new Venta();
        venta.setEmpresa(empresaRepository.findById(empresaId).orElseThrow(() -> noEncontrado("Empresa")));
        venta.setUsuario(usuarioRepository.findById(usuarioId).orElseThrow(() -> noEncontrado("Usuario")));
        venta.setCliente(clienteRepository.findById(clienteId).orElseThrow(() -> noEncontrado("Cliente")));
        venta.setTipoComprobante(comprobanteRepository.findById(comprobanteId).orElseThrow(() -> noEncontrado("Tipo de comprobante")));
        venta.setNumeroComprobante(numeroComprobante);
        venta.setMetodoPago(metodoPago == null ? MetodoPago.efectivo : metodoPago);
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemVenta item : items) {
            Producto producto = productoRepository.findByIdForUpdate(item.productoId()).orElseThrow(() -> noEncontrado("Producto"));
            if (producto.getStockActual() < item.cantidad()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente para " + producto.getNombre());
            BigDecimal linea = producto.getPrecioVenta().multiply(BigDecimal.valueOf(item.cantidad()));
            producto.setStockActual(producto.getStockActual() - item.cantidad());
            venta.getDetalles().add(new DetalleVenta(null, venta, producto, item.cantidad(), producto.getPrecioVenta(), linea));
            subtotal = subtotal.add(linea);
        }
        venta.setSubtotal(subtotal);
        BigDecimal igv = subtotal.multiply(IGV).setScale(2, RoundingMode.HALF_UP);
        venta.setIgv(igv);
        venta.setTotal(subtotal.add(igv).setScale(2, RoundingMode.HALF_UP));
        return ventaRepository.save(venta);
    }

    private ResponseStatusException noEncontrado(String recurso) { return new ResponseStatusException(HttpStatus.NOT_FOUND, recurso + " no encontrado"); }
    public record ItemVenta(Integer productoId, Integer cantidad) { }
}
