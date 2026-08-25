package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria")
    private Integer id;

    @NotBlank(message = "El nombre de la categoría es obligatorio")
    @Size(max = 100, message = "La categoría no puede superar 100 caracteres")
    @Column(name = "nombre_categoria", nullable = false, length = 100)
    private String nombre;

    @Size(max = 255)
    @Column(length = 255)
    private String descripcion;
}
