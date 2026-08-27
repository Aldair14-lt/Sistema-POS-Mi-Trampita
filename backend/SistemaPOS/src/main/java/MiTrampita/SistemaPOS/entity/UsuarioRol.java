package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuario_rol", uniqueConstraints = @UniqueConstraint(name = "uk_usuario_rol", columnNames = {
        "id_usuario", "id_rol" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioRol {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario_rol")
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;
}
