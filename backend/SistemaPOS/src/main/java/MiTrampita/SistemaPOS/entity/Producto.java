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
    @NotNull @ManyToOne(optional = false) @JoinColumn(name = "id_categoria", nullable = false) private Categoria categoria;
    @NotNull @ManyToOne(optional = false) @JoinColumn(name = "id_marca", nullable = false) private Marca marca;
    @NotNull @ManyToOne(optional = false) @JoinColumn(name = "id_proveedor", nullable = false) private Proveedor proveedor;
    @NotBlank @Size(max = 50) @Column(name = "codigo_barras", nullable = false, unique = true, length = 50) private String codigoBarras;
    @NotBlank @Size(max = 150) @Column(name = "nombre_producto", nullable = false, length = 150) private String nombre;
    @Column(columnDefinition = "TEXT") private String descripcion;
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) @Column(name = "precio_compra", nullable = false, precision = 10, scale = 2) private BigDecimal precioCompra = BigDecimal.ZERO;
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) @Column(name = "precio_venta", nullable = false, precision = 10, scale = 2) private BigDecimal precioVenta = BigDecimal.ZERO;
    @NotNull @Min(0) @Column(name = "stock_actual", nullable = false) private Integer stockActual = 0;
    @NotNull @Min(0) @Column(name = "stock_minimo", nullable = false) private Integer stockMinimo = 5;
    @Column(name = "fecha_registro", insertable = false, updatable = false) private OffsetDateTime fechaRegistro;

    public void setId(Integer id) {
        this.id = id;
    }
}
