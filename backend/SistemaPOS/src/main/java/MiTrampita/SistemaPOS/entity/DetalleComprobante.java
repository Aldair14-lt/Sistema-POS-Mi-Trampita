package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "detalle_comprobante") @Getter @Setter @NoArgsConstructor
public class DetalleComprobante {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_detalle_comprobante") private Integer id;
    @ManyToOne(optional = false) @JoinColumn(name = "id_comprobante", nullable = false, updatable = false) private Comprobante comprobante;
    @Column(nullable = false, length = 150, updatable = false) private String producto;
    @Column(nullable = false, updatable = false) private Integer cantidad;
    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2, updatable = false) private BigDecimal precioUnitario;
    @Column(nullable = false, precision = 10, scale = 2, updatable = false) private BigDecimal subtotal;
}
