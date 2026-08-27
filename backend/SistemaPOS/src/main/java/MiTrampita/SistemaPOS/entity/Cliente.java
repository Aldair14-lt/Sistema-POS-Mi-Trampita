package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "cliente", uniqueConstraints = @UniqueConstraint(name = "uk_cliente_documento", columnNames = "numero_documento"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Integer id;
    @NotBlank
    @Size(max = 20)
    @Column(name = "numero_documento", nullable = false, length = 20)
    private String numeroDocumento;
    @NotBlank
    @Size(max = 150)
    @Column(name = "nombres_razon_social", nullable = false, length = 150)
    private String nombresRazonSocial;
    @Size(max = 255)
    @Column(length = 255)
    private String direccion;
    @Size(max = 20)
    @Column(length = 20)
    private String telefono;
    @Email
    @Size(max = 100)
    @Column(length = 100)
    private String correo;
}
