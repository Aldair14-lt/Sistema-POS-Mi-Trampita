package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Fotografía inmutable de la venta: cambios posteriores del catálogo no alteran la boleta. */
@Entity @Table(name = "comprobantes", uniqueConstraints = {
    @UniqueConstraint(name = "uk_comprobante_venta", columnNames = "id_venta"),
    @UniqueConstraint(name = "uk_comprobante_numero", columnNames = {"id_tipo_comprobante", "correlativo"})})
@Getter @Setter @NoArgsConstructor
public class Comprobante {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_comprobante")
    private Integer id;
    @OneToOne(optional = false) @JoinColumn(name = "id_venta", nullable = false, updatable = false)
    private Venta venta;
    @ManyToOne(optional = false) @JoinColumn(name = "id_tipo_comprobante", nullable = false, updatable = false)
    private TipoComprobante tipoComprobante;
    @Column(nullable = false, length = 50, updatable = false) private String tipo;
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "tipo_comprobante", nullable = false, length = 20, updatable = false)
    private TipoDocumento tipoDocumento;
    @Column(length = 11, updatable = false) private String ruc;
    @Column(name = "razon_social", length = 150, updatable = false) private String razonSocial;
    @Column(length = 8, updatable = false) private String dni;
    @Column(nullable = false, length = 10, updatable = false) private String serie;
    @Column(nullable = false, updatable = false) private Long correlativo;
    @Column(name = "empresa_ruc", nullable = false, length = 20, updatable = false) private String empresaRuc;
    @Column(name = "empresa_nombre", nullable = false, length = 150, updatable = false) private String empresaNombre;
    @Column(name = "empresa_direccion", nullable = false, columnDefinition = "TEXT", updatable = false) private String empresaDireccion;
    @Column(name = "cliente_documento", nullable = false, length = 20, updatable = false) private String clienteDocumento;
    @Column(name = "cliente_nombre", nullable = false, length = 150, updatable = false) private String clienteNombre;
    @Column(name = "cliente_direccion", length = 255, updatable = false) private String clienteDireccion;
    @Column(nullable = false, precision = 10, scale = 2, updatable = false) private BigDecimal subtotal;
    @Column(nullable = false, precision = 10, scale = 2, updatable = false) private BigDecimal igv;
    @Column(nullable = false, precision = 10, scale = 2, updatable = false) private BigDecimal total;
    @Column(name = "fecha_emision", nullable = false, updatable = false) private OffsetDateTime fechaEmision;
    @OneToMany(mappedBy = "comprobante", cascade = CascadeType.PERSIST) @OrderBy("id ASC")
    private List<DetalleComprobante> detalles = new ArrayList<>();
}
