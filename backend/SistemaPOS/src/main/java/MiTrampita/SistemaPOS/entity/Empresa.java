package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "empresa")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Empresa {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_empresa") private Integer id;
    @NotBlank @Size(max = 20) @Column(nullable = false, unique = true, length = 20) private String ruc;
    @NotBlank @Size(max = 150) @Column(name = "razon_social", nullable = false, length = 150) private String razonSocial;
    @Size(max = 150) @Column(name = "nombre_comercial", length = 150) private String nombreComercial;
    @NotBlank @Size(max = 1000) @Column(nullable = false, columnDefinition = "TEXT") private String direccion;
    @Size(max = 20) @Column(length = 20) private String telefono;
    @Email @Size(max = 100) @Column(length = 100) private String correo;
    @Column(name = "fecha_registro", insertable = false, updatable = false) private OffsetDateTime fechaRegistro;
}
