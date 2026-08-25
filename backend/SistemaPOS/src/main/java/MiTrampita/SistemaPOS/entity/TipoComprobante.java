package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "tipo_comprobante", uniqueConstraints = @UniqueConstraint(name = "uk_tipo_comprobante_serie", columnNames = {"nombre_tipo", "serie"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TipoComprobante {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_comprobante") private Integer id;
    @NotBlank @Size(max = 50) @Column(name = "nombre_tipo", nullable = false, length = 50) private String nombre;
    @NotBlank @Size(max = 10) @Column(nullable = false, length = 10) private String serie;
    @Size(max = 255) @Column(length = 255) private String descripcion;
}
