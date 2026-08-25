package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "proveedor")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Proveedor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proveedor") private Integer id;
    @NotBlank @Column(name = "ruc_dni", nullable = false, length = 20) private String rucDni;
    @NotBlank @Column(name = "razon_social", nullable = false, length = 150) private String razonSocial;
    @Column(length = 20) private String telefono;
    @Column(length = 100) private String correo;
}
