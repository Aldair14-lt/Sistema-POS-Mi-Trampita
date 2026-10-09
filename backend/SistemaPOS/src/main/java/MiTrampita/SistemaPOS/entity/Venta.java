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
@Table(name = "ventas", uniqueConstraints = @UniqueConstraint(name = "uk_venta_comprobante", columnNames = {
        "id_tipo_comprobante", "numero_comprobante" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Venta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_venta")
    private Integer id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_empresa", nullable = false)
    private Empresa empresa;
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_tipo_comprobante", nullable = false)
    private TipoComprobante tipoComprobante;
    @ManyToOne
    @JoinColumn(name = "mesa_id")
    private Mesa mesa;
    @NotBlank
    @Size(max = 50)
    @Column(name = "numero_comprobante", nullable = false, length = 50)
    private String numeroComprobante;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(name = "igv_impuesto", nullable = false, precision = 10, scale = 2)
    private BigDecimal igv = BigDecimal.ZERO;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "metodo_pago", nullable = false, length = 20)
    private MetodoPago metodoPago = MetodoPago.EFECTIVO;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "estado_venta", nullable = false, length = 20)
    private EstadoVenta estado = EstadoVenta.ABIERTA;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_cuenta", nullable = false, length = 25)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private EstadoCuenta estadoCuenta = EstadoCuenta.ABIERTA;
    @Enumerated(EnumType.STRING)
    @Column(name = "origen_pedido", nullable = false, length = 10)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private OrigenPedido origenPedido = OrigenPedido.LOCAL;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_entrega", nullable = false, length = 10)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private TipoEntrega tipoEntrega = TipoEntrega.MESA;
    @Column(name = "direccion_envio", length = 255)
    private String direccionEnvio;
    @Column(name = "telefono_entrega", length = 20)
    private String telefonoEntrega;
    @Column(name = "observaciones_pedido", nullable = false, length = 255)
    private String observacionesPedido = "";
    @Column(name = "cuenta_solicitada", nullable = false)
    private boolean cuentaSolicitada;
    @Column(name = "fecha_solicitud_cuenta")
    private OffsetDateTime fechaSolicitudCuenta;
    @OneToOne(mappedBy = "venta", cascade = CascadeType.PERSIST)
    private Comprobante comprobante;
    @Column(name = "fecha_venta", nullable = false, updatable = false)
    private OffsetDateTime fechaVenta;
    @Column(name = "fecha_cobro")
    private OffsetDateTime fechaCobro;
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleVenta> detalles = new ArrayList<>();
    @OneToMany(mappedBy = "venta", cascade = CascadeType.PERSIST)
    @OrderBy("fechaPago ASC, id ASC")
    private List<PagoVenta> pagos = new ArrayList<>();
}
