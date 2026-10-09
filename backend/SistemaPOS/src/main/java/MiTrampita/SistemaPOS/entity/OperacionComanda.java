package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity @Table(name = "operaciones_comanda", uniqueConstraints = @UniqueConstraint(name = "uk_comanda_operacion", columnNames = {"id_venta", "clave_operacion"}))
@Getter @Setter @NoArgsConstructor
public class OperacionComanda {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_operacion") private Integer id;
    @NotNull @ManyToOne(optional = false) @JoinColumn(name = "id_venta", nullable = false) private Venta venta;
    @NotBlank @Size(max = 36) @Column(name = "clave_operacion", nullable = false, length = 36) private String claveOperacion;
    @NotBlank @Size(min = 64, max = 64) @Column(nullable = false, length = 64) private String huella;
    @NotNull @Column(name = "fecha_creacion", nullable = false) private OffsetDateTime fechaCreacion = OffsetDateTime.now();
}
