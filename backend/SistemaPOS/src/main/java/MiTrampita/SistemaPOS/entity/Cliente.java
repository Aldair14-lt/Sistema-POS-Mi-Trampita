package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;
import java.math.BigDecimal;

@Entity
@Table(name = "clientes", uniqueConstraints = @UniqueConstraint(name = "uk_cliente_documento", columnNames = "numero_documento"))
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

    @PastOrPresent(message = "La fecha de nacimiento no puede ser futura")
    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "frecuencia_visitas", nullable = false)
    private Long frecuenciaVisitas = 0L;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "total_gastado", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalGastado = BigDecimal.ZERO;
}
