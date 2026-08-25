package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "cliente", uniqueConstraints = @UniqueConstraint(name = "uk_cliente_documento", columnNames = "numero_documento"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Cliente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente") private Integer id;
    @NotBlank @Column(name = "numero_documento", nullable = false, length = 20) private String numeroDocumento;
    @NotBlank @Column(name = "nombres_razon_social", nullable = false, length = 150) private String nombresRazonSocial;
    @Column(length = 255) private String direccion;
    @Column(length = 20) private String telefono;
    @Column(length = 100) private String correo;
}
