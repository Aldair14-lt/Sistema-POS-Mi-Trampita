package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "proveedor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Proveedor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proveedor")
    private Integer id;
    @NotBlank
    @Size(max = 20)
    @Column(name = "ruc_dni", nullable = false, length = 20)
    private String rucDni;
    @NotBlank
    @Size(max = 150)
    @Column(name = "razon_social", nullable = false, length = 150)
    private String razonSocial;
    @Size(max = 20)
    @Column(length = 20)
    private String telefono;
    @Email
    @Size(max = 100)
    @Column(length = 100)
    private String correo;
}
