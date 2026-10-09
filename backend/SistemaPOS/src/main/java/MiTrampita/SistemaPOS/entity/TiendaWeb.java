package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "tiendas_web") @Getter @Setter @NoArgsConstructor
public class TiendaWeb {
    @Id @Column(name = "id_empresa") private Integer id;
    @MapsId @OneToOne(optional = false) @JoinColumn(name = "id_empresa") private Empresa empresa;
    @Column(nullable = false, unique = true, length = 60) private String slug;
    @Column(nullable = false) private boolean activa;
    @Column(nullable = false) private boolean recojo = true;
    @Column(nullable = false) private boolean delivery = true;
    @Column(nullable = false, length = 300) private String mensaje = "";
}
