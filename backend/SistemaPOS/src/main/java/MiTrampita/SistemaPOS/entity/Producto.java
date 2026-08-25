package MiTrampita.SistemaPOS.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "producto")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Producto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto") private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_categoria", nullable = false) @JsonIgnore private Categoria categoria;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_marca", nullable = false) @JsonIgnore private Marca marca;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_proveedor", nullable = false) @JsonIgnore private Proveedor proveedor;
    @NotBlank @Column(name = "codigo_barras", nullable = false, unique = true, length = 50) private String codigoBarras;
    @NotBlank @Column(name = "nombre_producto", nullable = false, length = 150) private String nombre;
    @Column(columnDefinition = "TEXT") private String descripcion;
    @PositiveOrZero @Column(name = "precio_compra", nullable = false, precision = 10, scale = 2) private BigDecimal precioCompra = BigDecimal.ZERO;
    @PositiveOrZero @Column(name = "precio_venta", nullable = false, precision = 10, scale = 2) private BigDecimal precioVenta = BigDecimal.ZERO;
    @Min(0) @Column(name = "stock_actual", nullable = false) private Integer stockActual = 0;
    @Min(0) @Column(name = "stock_minimo", nullable = false) private Integer stockMinimo = 5;
    @Column(name = "fecha_registro", insertable = false, updatable = false) private OffsetDateTime fechaRegistro;

    public void setId(Integer id) {
        this.id = id;
    }
}
