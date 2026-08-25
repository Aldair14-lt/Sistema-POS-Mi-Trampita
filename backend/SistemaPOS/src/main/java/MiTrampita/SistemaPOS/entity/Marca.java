package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "marca")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Marca {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_marca") private Integer id;
    @NotBlank @Size(max = 100) @Column(name = "nombre_marca", nullable = false, length = 100) private String nombre;
}
