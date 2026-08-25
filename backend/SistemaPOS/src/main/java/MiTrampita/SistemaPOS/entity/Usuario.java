package MiTrampita.SistemaPOS.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Table(name = "usuario")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario") private Integer id;
    @NotBlank @Size(max = 50) @Column(nullable = false, unique = true, length = 50) private String usuario;
    @NotBlank @Size(min = 4, max = 255) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Column(name = "contraseña", nullable = false, length = 255) private String contrasena;
    @NotBlank @Size(max = 150) @Column(name = "nombre_completo", nullable = false, length = 150) private String nombreCompleto;
    @Email @Size(max = 100) @Column(name = "correo_electronico", length = 100) private String correoElectronico;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Column(name = "pin_caja", length = 255) private String pinCaja;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(nullable = false, length = 20) private EstadoUsuario estado = EstadoUsuario.activo;
    @Column(name = "fecha_creacion", insertable = false, updatable = false) private OffsetDateTime fechaCreacion;
}
