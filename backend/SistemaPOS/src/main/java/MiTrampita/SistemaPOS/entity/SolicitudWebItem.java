package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "solicitud_web_items") @Getter @Setter @NoArgsConstructor
public class SolicitudWebItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_item") private Integer id;
    @ManyToOne(optional = false) @JoinColumn(name = "id_solicitud", nullable = false) private SolicitudWeb solicitud;
    @ManyToOne(optional = false) @JoinColumn(name = "id_producto", nullable = false) private Producto producto;
    @Column(nullable = false, length = 150) private String nombre;
    @Column(nullable = false) private Integer cantidad;
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 8, fraction = 2)
    @Column(name = "precio_base", nullable = false, precision = 10, scale = 2) private BigDecimal precioBase;
    @Column(nullable = false, length = 255) private String observaciones = "";
}
