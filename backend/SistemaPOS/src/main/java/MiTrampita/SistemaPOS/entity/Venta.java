package MiTrampita.SistemaPOS.entity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "venta", uniqueConstraints = @UniqueConstraint(name = "uk_venta_comprobante", columnNames = {"id_tipo_comprobante", "numero_comprobante"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Venta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_venta") private Integer id;
    @ManyToOne(optional = false) @JoinColumn(name = "id_empresa", nullable = false) private Empresa empresa;
    @ManyToOne(optional = false) @JoinColumn(name = "id_usuario", nullable = false) private Usuario usuario;
    @ManyToOne(optional = false) @JoinColumn(name = "id_cliente", nullable = false) private Cliente cliente;
    @ManyToOne(optional = false) @JoinColumn(name = "id_tipo_comprobante", nullable = false) private TipoComprobante tipoComprobante;
    @NotBlank @Size(max = 50) @Column(name = "numero_comprobante", nullable = false, length = 50) private String numeroComprobante;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(name = "igv_impuesto", nullable = false, precision = 10, scale = 2) private BigDecimal igv = BigDecimal.ZERO;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal total = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(name = "metodo_pago", nullable = false, length = 20) private MetodoPago metodoPago = MetodoPago.efectivo;
    @Column(name = "fecha_venta", insertable = false, updatable = false) private OffsetDateTime fechaVenta;
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleVenta> detalles = new ArrayList<>();
}
