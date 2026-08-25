package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "empresa")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Empresa {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_empresa") private Integer id;
    @NotBlank @Column(nullable = false, unique = true, length = 20) private String ruc;
    @NotBlank @Column(name = "razon_social", nullable = false, length = 150) private String razonSocial;
    @Column(name = "nombre_comercial", length = 150) private String nombreComercial;
    @NotBlank @Column(nullable = false, columnDefinition = "TEXT") private String direccion;
    @Column(length = 20) private String telefono;
    @Column(length = 100) private String correo;
    @Column(name = "fecha_registro", insertable = false, updatable = false) private OffsetDateTime fechaRegistro;
}
