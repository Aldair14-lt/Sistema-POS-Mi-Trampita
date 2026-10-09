package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name = "solicitudes_web", uniqueConstraints = @UniqueConstraint(name = "uk_web_operacion", columnNames = {"id_empresa", "clave_operacion"}))
@Getter @Setter @NoArgsConstructor
public class SolicitudWeb {
    public enum Estado { PENDIENTE, ACEPTADO, RECHAZADO }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_solicitud") private Integer id;
    @ManyToOne(optional = false) @JoinColumn(name = "id_empresa", nullable = false) private Empresa empresa;
    @Column(name = "clave_operacion", nullable = false, length = 36) private String claveOperacion;
    @Column(name = "codigo_seguimiento", nullable = false, unique = true, length = 36) private String codigoSeguimiento;
    @Column(nullable = false, length = 64) private String huella;
    @Column(nullable = false, length = 150) private String nombre;
    @Column(nullable = false, length = 8) private String dni;
    @Column(nullable = false, length = 20) private String telefono;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "tipo_entrega", nullable = false, length = 10) private TipoEntrega tipoEntrega;
    @Column(nullable = false, length = 255) private String direccion = "";
    @Column(nullable = false, length = 255) private String observaciones = "";
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 15) private Estado estado = Estado.PENDIENTE;
    @Column(name = "fecha_creacion", nullable = false) private OffsetDateTime fechaCreacion;
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 8, fraction = 2)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal subtotal;
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 8, fraction = 2)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal igv;
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 8, fraction = 2)
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal total;
    @Column(nullable = false, length = 255) private String motivo = "";
    @OneToOne @JoinColumn(name = "id_venta", unique = true) private Venta venta;
    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC") private List<SolicitudWebItem> items = new ArrayList<>();
}
