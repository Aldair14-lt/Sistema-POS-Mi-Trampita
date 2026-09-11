package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mesa", uniqueConstraints = @UniqueConstraint(name = "uk_mesa_numero", columnNames = "numero_mesa"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Mesa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mesa")
    private Integer id;

    @NotNull
    @Min(1)
    @Column(name = "numero_mesa", nullable = false)
    private Integer numero;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer capacidad = 4;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "estado_mesa", nullable = false, length = 20)
    private EstadoMesa estado = EstadoMesa.LIBRE;
}
