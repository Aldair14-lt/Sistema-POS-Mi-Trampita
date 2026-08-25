package MiTrampita.SistemaPOS.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "detalle_venta")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class DetalleVenta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_venta") private Integer id;
    @ManyToOne(optional = false) @JoinColumn(name = "id_venta", nullable = false) @JsonIgnore private Venta venta;
    @ManyToOne(optional = false) @JoinColumn(name = "id_producto", nullable = false) private Producto producto;
    @Min(1) @Column(nullable = false) private Integer cantidad = 1;
    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2) private BigDecimal precioUnitario = BigDecimal.ZERO;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal subtotal = BigDecimal.ZERO;
}
