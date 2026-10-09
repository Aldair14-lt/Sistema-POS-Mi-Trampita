package MiTrampita.SistemaPOS;

import MiTrampita.SistemaPOS.controller.UsuarioController;
import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.repositorio.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin_safety;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class AdministratorSafetyTests {
    @Autowired UsuarioController controller;
    @Autowired UsuarioRepository users;
    @Autowired RolRepository roles;
    @Autowired UsuarioRolRepository assignments;

    UsuarioController.UsuarioRequest request(Usuario u, EstadoUsuario estado, String role) {
        return new UsuarioController.UsuarioRequest(u.getUsuario(), null, u.getNombreCompleto(), null, estado,
            List.of(roles.findByNombreIgnoreCase(role).orElseThrow().getId()));
    }
    @BeforeEach void restoreAdministrator() {
        var admin = users.findByUsuarioIgnoreCase("admin").orElseThrow(); admin.setEstado(EstadoUsuario.activo); users.save(admin);
        var role = roles.findByNombreIgnoreCase("ADMIN").orElseThrow();
        if (!assignments.existsByUsuario_IdAndRol_Id(admin.getId(), role.getId())) {
            var link = new UsuarioRol(); link.setUsuario(admin); link.setRol(role); assignments.save(link);
        }
        users.findByUsuarioIgnoreCase("second-admin").ifPresent(u -> { u.setEstado(EstadoUsuario.inactivo); users.save(u); });
    }
    @Test void ultimoAdministradorNoSeEliminaBloqueaNiPierdeSuRol() {
        var admin = users.findByUsuarioIgnoreCase("admin").orElseThrow(); String hash = admin.getContrasena();
        assertThatThrownBy(() -> controller.eliminar(admin.getId())).hasMessageContaining("administrador activo");
        assertThatThrownBy(() -> controller.actualizar(admin.getId(), request(admin, EstadoUsuario.bloqueado, "ADMIN"))).hasMessageContaining("administrador activo");
        assertThatThrownBy(() -> controller.actualizar(admin.getId(), request(admin, EstadoUsuario.activo, "MOZO"))).hasMessageContaining("administrador activo");
        assertThat(users.findById(admin.getId()).orElseThrow().getContrasena()).isEqualTo(hash);
        assertThat(assignments.countActiveAdministrators()).isEqualTo(1);
    }
    @Test void dosDesactivacionesConcurrentesConservanUnAdministrador() throws Exception {
        var first = users.findByUsuarioIgnoreCase("admin").orElseThrow();
        var second = users.findByUsuarioIgnoreCase("second-admin").orElse(null);
        if (second == null) {
            var created = controller.crear(new UsuarioController.UsuarioRequest("second-admin", "AuditTest#123", "Administrador QA", null,
                EstadoUsuario.activo, List.of(roles.findByNombreIgnoreCase("ADMIN").orElseThrow().getId())));
            second = users.findById(created.id()).orElseThrow();
        } else controller.actualizar(second.getId(), request(second, EstadoUsuario.activo, "ADMIN"));
        final var other = second;
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> deactivate(first, start)); var b = pool.submit(() -> deactivate(other, start)); start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(assignments.countActiveAdministrators()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    boolean deactivate(Usuario u, CountDownLatch start) throws InterruptedException {
        start.await();
        try { controller.actualizar(u.getId(), request(u, EstadoUsuario.inactivo, "ADMIN")); return true; }
        catch (org.springframework.web.server.ResponseStatusException ex) { assertThat(ex.getStatusCode().value()).isEqualTo(409); return false; }
    }
}
