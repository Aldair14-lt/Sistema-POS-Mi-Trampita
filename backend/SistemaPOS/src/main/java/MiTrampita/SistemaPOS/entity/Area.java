package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "areas", uniqueConstraints = @UniqueConstraint(name = "uk_area_nombre", columnNames = "nombre"))
@Getter @Setter @NoArgsConstructor
public class Area {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, length = 100)
    private String nombre;
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EstadoArea estado = EstadoArea.ACTIVA;

    public enum EstadoArea { ACTIVA, INACTIVA }
}
