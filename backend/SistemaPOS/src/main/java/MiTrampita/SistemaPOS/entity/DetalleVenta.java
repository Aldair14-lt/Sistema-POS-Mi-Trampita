package MiTrampita.SistemaPOS.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "detalle_venta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DetalleVenta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_venta")
    private Integer id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_venta", nullable = false)
    @JsonIgnore
    private Venta venta;
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;
    @Min(1)
    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Integer cantidad = 1;
    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00")
    @jakarta.validation.constraints.Digits(integer = 8, fraction = 2)
    private BigDecimal precioUnitario = BigDecimal.ZERO;
    @Column(nullable = false, precision = 10, scale = 2)
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00")
    @jakarta.validation.constraints.Digits(integer = 8, fraction = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "area_destino", nullable = false, length = 10)
    private AreaDestino areaDestino = AreaDestino.COCINA;
    @Column(name = "observaciones", nullable = false, length = 255)
    private String observaciones = "";
    @Column(name = "clave_comanda", length = 36)
    private String claveComanda;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_preparacion", nullable = false, length = 20)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private EstadoPreparacion estadoPreparacion = EstadoPreparacion.PENDIENTE;
    @Column(name = "fecha_pedido", nullable = false, updatable = false)
    private OffsetDateTime fechaPedido = OffsetDateTime.now();
    @Column(name = "fecha_estado", nullable = false)
    private OffsetDateTime fechaEstado = OffsetDateTime.now();
    @Column(name = "motivo_cancelacion", length = 255)
    private String motivoCancelacion;
    @ManyToOne @JoinColumn(name = "cancelado_por") @JsonIgnore
    private Usuario canceladoPor;
}
