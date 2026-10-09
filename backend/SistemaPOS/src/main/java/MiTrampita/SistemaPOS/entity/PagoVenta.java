package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Libro de abonos: cada pago conserva el responsable y la clave de reintento. */
@Entity
@Table(name = "pagos_venta", uniqueConstraints = @UniqueConstraint(
        name = "uk_pago_operacion", columnNames = {"id_venta", "clave_operacion"}))
@Getter @Setter @NoArgsConstructor
public class PagoVenta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Integer id;
    @ManyToOne(optional = false) @JoinColumn(name = "id_venta", nullable = false)
    private Venta venta;
    @ManyToOne(optional = false) @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;
    @ManyToOne @JoinColumn(name = "id_sesion_caja")
    private SesionCaja sesionCaja;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;
    @Enumerated(EnumType.STRING) @Column(name = "metodo_pago", nullable = false, length = 20)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private MetodoPago metodoPago;
    @Column(name = "monto_recibido", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoRecibido;
    @Column(nullable = false, length = 100)
    private String referencia = "";
    @Column(name = "clave_operacion", nullable = false, length = 36)
    private String claveOperacion;
    @Column(name = "fecha_pago", nullable = false)
    private OffsetDateTime fechaPago = OffsetDateTime.now();
}
