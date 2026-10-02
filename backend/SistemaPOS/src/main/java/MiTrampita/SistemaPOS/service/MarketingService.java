package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.entity.Cliente;
import MiTrampita.SistemaPOS.repositorio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class MarketingService {
    private final ClienteRepository clientes;
    private final DetalleVentaRepository detalles;

    public Dashboard dashboard() {
        int mes = LocalDate.now(ZoneId.of("America/Lima")).getMonthValue();
        return new Dashboard(mes, clientes.count(), frecuentes(), cumpleaneros(mes));
    }
    public List<ClienteResumen> frecuentes() {
        return clientes.findTop10ByFrecuenciaVisitasGreaterThanOrderByFrecuenciaVisitasDescTotalGastadoDescIdAsc(0L)
                .stream().map(ClienteResumen::from).toList();
    }
    public List<ClienteResumen> cumpleaneros(int mes) {
        return clientes.cumpleaneros(mes).stream().map(ClienteResumen::from).toList();
    }
    public List<DetalleVentaRepository.ConsumoProducto> consumo(Integer id) {
        if (!clientes.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado");
        return detalles.consumoCliente(id, PageRequest.of(0, 10));
    }
    public record Dashboard(int mes, long totalClientes, List<ClienteResumen> frecuentes, List<ClienteResumen> cumpleaneros) { }
    public record ClienteResumen(Integer id, String nombre, String telefono, String correo,
            LocalDate fechaNacimiento, Long frecuenciaVisitas, BigDecimal totalGastado) {
        static ClienteResumen from(Cliente c) {
            return new ClienteResumen(c.getId(), c.getNombresRazonSocial(), c.getTelefono(), c.getCorreo(),
                    c.getFechaNacimiento(), c.getFrecuenciaVisitas(), c.getTotalGastado());
        }
    }
}