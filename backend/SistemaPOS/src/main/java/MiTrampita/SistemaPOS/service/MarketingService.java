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
    public List<ClienteResumen> mejores() {
        return clientes.findTop10ByTotalGastadoGreaterThanOrderByTotalGastadoDescFrecuenciaVisitasDescIdAsc(BigDecimal.ZERO)
            .stream().map(ClienteResumen::from).toList();
    }
    public List<ClienteResumen> inactivos(int dias) {
        return clientes.inactivos(OffsetDateTime.now(ZoneId.of("America/Lima")).minusDays(dias)).stream().map(ClienteResumen::from).toList();
    }
    public List<ClienteResumen> proximosCumpleaneros(int dias) {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
        return clientes.findAll().stream().filter(c -> c.getFechaNacimiento() != null)
            .filter(c -> !proximo(c, hoy).isAfter(hoy.plusDays(dias)))
            .sorted(java.util.Comparator.comparing((Cliente c) -> proximo(c, hoy)).thenComparing(Cliente::getId))
            .map(ClienteResumen::from).toList();
    }
    private LocalDate proximo(Cliente c, LocalDate hoy) {
        MonthDay dia = MonthDay.from(c.getFechaNacimiento());
        LocalDate fecha = dia.atYear(hoy.getYear()); // 29 de febrero se celebra el 28 en años no bisiestos.
        return fecha.isBefore(hoy) ? dia.atYear(hoy.getYear() + 1) : fecha;
    }
    public String exportarCsv(String segmento) {
        List<ClienteResumen> lista = switch (segmento) {
            case "todos" -> clientes.findAll().stream().map(ClienteResumen::from).toList();
            case "cumpleaneros" -> proximosCumpleaneros(30);
            case "inactivos" -> inactivos(60);
            case "mejores" -> mejores();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Segmento de exportación inválido");
        };
        var filas = new java.util.LinkedHashSet<String>();
        for (var c : lista) {
            String correo = c.correo() == null ? "" : c.correo().trim().toLowerCase(java.util.Locale.ROOT);
            // Exportar solo identificadores normalizados; no inferir nombres desde una razón social.
            if (!correo.matches("[a-z0-9][a-z0-9._+%-]*@[a-z0-9.-]+\\.[a-z]{2,}")) correo = "";
            String telefono = telefonoMeta(c.telefono());
            if (!correo.isEmpty() || !telefono.isEmpty()) filas.add(correo + "," + telefono + "," + (telefono.startsWith("51") ? "pe" : ""));
        }
        return "email,phone,country\r\n" + String.join("\r\n", filas) + (filas.isEmpty() ? "" : "\r\n");
    }
    private String telefonoMeta(String valor) {
        if (valor == null || !valor.matches("[+0-9 ()-]+")) return "";
        String digitos = valor.replaceAll("[^0-9]", "");
        if (digitos.matches("9[0-9]{8}")) return "51" + digitos;
        if (digitos.matches("519[0-9]{8}")) return digitos;
        return valor.trim().startsWith("+") && digitos.matches("[1-9][0-9]{7,14}") ? digitos : "";
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
