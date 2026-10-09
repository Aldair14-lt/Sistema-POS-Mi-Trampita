package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Un turno por empresa. Los pagos históricos pueden conservarse sin turno. */
@Entity @Table(name = "sesiones_caja", uniqueConstraints = {
    @UniqueConstraint(name = "uk_caja_empresa_abierta", columnNames = "empresa_abierta"),
    @UniqueConstraint(name = "uk_caja_apertura", columnNames = {"id_empresa", "clave_operacion"})
})
@Getter @Setter @NoArgsConstructor
public class SesionCaja {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_sesion")
    private Integer id;
    @ManyToOne(optional = false) @JoinColumn(name = "id_empresa", nullable = false)
    private Empresa empresa;
    @Column(name = "empresa_abierta") private Integer empresaAbierta;
    @ManyToOne(optional = false) @JoinColumn(name = "abierto_por", nullable = false)
    private Usuario abiertoPor;
    @ManyToOne @JoinColumn(name = "cerrado_por") private Usuario cerradoPor;
    @Column(name = "fecha_apertura", nullable = false) private OffsetDateTime fechaApertura;
    @Column(name = "fecha_cierre") private OffsetDateTime fechaCierre;
    @Column(name = "clave_operacion", nullable = false, length = 36) private String claveOperacion;
    @Column(name = "monto_inicial", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoInicial;
    @Column(name = "total_efectivo", nullable = false, precision = 14, scale = 2) private BigDecimal totalEfectivo = BigDecimal.ZERO;
    @Column(name = "total_yape", nullable = false, precision = 14, scale = 2) private BigDecimal totalYape = BigDecimal.ZERO;
    @Column(name = "total_plin", nullable = false, precision = 14, scale = 2) private BigDecimal totalPlin = BigDecimal.ZERO;
    @Column(name = "total_tarjeta", nullable = false, precision = 14, scale = 2) private BigDecimal totalTarjeta = BigDecimal.ZERO;
    @Column(name = "total_transferencia", nullable = false, precision = 14, scale = 2) private BigDecimal totalTransferencia = BigDecimal.ZERO;
    @Column(name = "total_yape_plin", nullable = false, precision = 14, scale = 2) private BigDecimal totalYapePlin = BigDecimal.ZERO;
    @Column(name = "efectivo_declarado", precision = 14, scale = 2) private BigDecimal efectivoDeclarado;
    @Column(name = "observaciones", nullable = false, length = 500) private String observaciones = "";
}
