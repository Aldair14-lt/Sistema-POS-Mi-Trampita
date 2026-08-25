package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "marca")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Marca {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_marca") private Integer id;
    @NotBlank @Column(name = "nombre_marca", nullable = false, length = 100) private String nombre;
}
