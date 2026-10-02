package MiTrampita.SistemaPOS.config;

import MiTrampita.SistemaPOS.repositorio.UsuarioRepository;
import MiTrampita.SistemaPOS.repositorio.UsuarioRolRepository;
import MiTrampita.SistemaPOS.security.SessionUserFilter;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean HttpSessionCsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }

    @Bean
    SecurityFilterChain security(HttpSecurity http, SecurityContextRepository contexts,
            HttpSessionCsrfTokenRepository csrf, UsuarioRepository usuarios, UsuarioRolRepository roles) throws Exception {
        http.securityContext(c -> c.securityContextRepository(contexts))
            .csrf(c -> c.csrfTokenRepository(csrf))
            .addFilterAfter(new SessionUserFilter(usuarios, roles), SecurityContextHolderFilter.class)
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/login", "/api/auth/csrf", "/error").permitAll()
                .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/cocina/items").hasAnyRole("ADMIN", "COCINERO")
                .requestMatchers(HttpMethod.PATCH, "/api/cocina/items/*/estado").hasAnyRole("ADMIN", "COCINERO")
                .requestMatchers(HttpMethod.GET, "/api/pedidos-online").hasAnyRole("ADMIN", "MOZO", "CAJA")
                .requestMatchers(HttpMethod.POST, "/api/pedidos-online", "/api/ventas/*/pagos").hasAnyRole("ADMIN", "CAJA")
                .requestMatchers(HttpMethod.GET, "/api/ventas/*/pagos").hasAnyRole("ADMIN", "CAJA")
                .requestMatchers(HttpMethod.PATCH, "/api/ventas/items/*/servir").hasAnyRole("ADMIN", "MOZO", "CAJA")
                .requestMatchers(HttpMethod.GET, "/api/marketing/**", "/api/configuracion/**", "/api/empresas/**")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/ventas").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/ventas/*/cerrar", "/api/mesas/*/liberar")
                    .hasAnyRole("ADMIN", "CAJA")
                .requestMatchers(HttpMethod.POST, "/api/ventas").hasAnyRole("ADMIN", "MOZO")
                .requestMatchers(HttpMethod.PATCH, "/api/ventas/*/items", "/api/mesas/*/abrir")
                    .hasAnyRole("ADMIN", "MOZO")
                .requestMatchers(HttpMethod.GET, "/api/ventas/**", "/api/mesas", "/api/areas",
                    "/api/productos", "/api/categorias", "/api/marcas", "/api/pos/clientes",
                    "/api/tipos-comprobante", "/api/pos/configuracion").hasAnyRole("ADMIN", "MOZO", "CAJA")
                .anyRequest().hasRole("ADMIN"))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"status\":401,\"message\":\"Inicia sesión para continuar\"}");
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"status\":403,\"message\":\"No tienes permiso para esta acción o tu sesión debe renovarse\"}");
                }))
            .logout(l -> l.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)));
        return http.build();
    }
}
