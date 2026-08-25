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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        if (numeroComprobante == null || numeroComprobante.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de comprobante es obligatorio");
        if (numeroComprobante.length() > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de comprobante no puede superar 50 caracteres");
        Venta venta = new Venta();
        venta.setEmpresa(empresaRepository.findById(empresaId).orElseThrow(() -> noEncontrado("Empresa")));
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow(() -> noEncontrado("Usuario"));
        if (usuario.getEstado() != EstadoUsuario.activo) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario no está activo");
        venta.setUsuario(usuario);
        venta.setCliente(clienteRepository.findById(clienteId).orElseThrow(() -> noEncontrado("Cliente")));
        venta.setTipoComprobante(comprobanteRepository.findById(comprobanteId).orElseThrow(() -> noEncontrado("Tipo de comprobante")));
        venta.setNumeroComprobante(numeroComprobante.trim());
        venta.setMetodoPago(metodoPago == null ? MetodoPago.efectivo : metodoPago);
        BigDecimal subtotal = BigDecimal.ZERO;
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        for (ItemVenta item : items) {
            if (item == null || item.productoId() == null || item.cantidad() == null || item.cantidad() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada producto debe tener una cantidad mayor que cero");
            }
            quantities.merge(item.productoId(), item.cantidad(), Math::addExact);
        }
        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            Integer productId = entry.getKey();
            Integer quantity = entry.getValue();
            Producto producto = productoRepository.findByIdForUpdate(productId).orElseThrow(() -> noEncontrado("Producto"));
            if (producto.getStockActual() < quantity) throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente para " + producto.getNombre());
            BigDecimal linea = producto.getPrecioVenta().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            producto.setStockActual(producto.getStockActual() - quantity);
            venta.getDetalles().add(new DetalleVenta(null, venta, producto, quantity, producto.getPrecioVenta(), linea));
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
