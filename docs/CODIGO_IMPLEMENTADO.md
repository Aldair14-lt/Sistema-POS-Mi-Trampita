# Código implementado por archivo

Los bloques SQL de este documento son una instantánea histórica y no deben ejecutarse. Los scripts vigentes, consolidados por motor, son `database/scripts/pos_postgresql.sql` y `database/scripts/pos_mysql.sql`. Consultar [la guía](IMPLEMENTACION_ESCALAMIENTO.md) para su uso y para el orden de las migraciones, los permisos y la validación.

## `backend/SistemaPOS/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>4.1.1</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>
	<groupId>MiTrampita</groupId>
	<artifactId>SistemaPOS</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name/>
	<description/>
	<url/>
	<licenses>
		<license/>
	</licenses>
	<developers>
		<developer/>
	</developers>
	<scm>
		<connection/>
		<developerConnection/>
		<tag/>
		<url/>
	</scm>
	<properties>
		<java.version>21</java.version>
		<lombok.version>1.18.38</lombok.version>
	</properties>
	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.security</groupId>
			<artifactId>spring-security-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc</artifactId>
		</dependency>
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<optional>true</optional>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-devtools</artifactId>
			<scope>runtime</scope>
			<optional>true</optional>
		</dependency>
		<dependency>
			<groupId>com.mysql</groupId>
			<artifactId>mysql-connector-j</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>com.h2database</groupId>
			<artifactId>h2</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
				<configuration>
					<annotationProcessorPaths>
						<path>
							<groupId>org.projectlombok</groupId>
							<artifactId>lombok</artifactId>
							<version>${lombok.version}</version>
						</path>
					</annotationProcessorPaths>
				</configuration>
			</plugin>
		</plugins>
	</build>

</project>
```

## `backend/SistemaPOS/scripts/verify_http_flow.py`

```python
"""Prueba HTTP con datos desechables. Ejecutar SOLO sobre una base de pruebas.
Usa POS_TEST_PASSWORD y --url; conserva los registros creados para inspección.
No requiere dependencias externas.
"""
import argparse
import datetime
import http.cookiejar
import json
import os
import urllib.error
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument("--url", required=True)
parser.add_argument("--user", default="admin")
args = parser.parse_args()
password = os.environ["POS_TEST_PASSWORD"]

class Client:
    def __init__(self):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(self, method, path, data=None, expected=200):
        headers = {"Content-Type": "application/json"}
        if method != "GET":
            token = self.call("GET", "/api/auth/csrf")
            headers[token["headerName"]] = token["token"]
        request = urllib.request.Request(args.url + path, headers=headers, method=method,
            data=None if data is None else json.dumps(data).encode("utf-8"))
        try:
            with self.opener.open(request, timeout=20) as response:
                status, body = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, body = error.code, error.read()
        assert status == expected, (method, path, status, body.decode("utf-8"))
        return json.loads(body) if body else None

    def login(self, username, secret):
        return self.call("POST", "/api/auth/login", {"usuario": username, "contrasena": secret})

suffix = uuid.uuid4().hex[:10]
admin = Client()
admin.login(args.user, password)
assert Client().call("GET", "/api/mesas", expected=401)["status"] == 401
role_ids = {role["nombre"]: role["id"] for role in admin.call("GET", "/api/roles")}
users = {}
for role in ("MOZO", "CAJA"):
    username = "qa_" + role.lower() + "_" + suffix
    admin.call("POST", "/api/usuarios", {"usuario": username, "contrasena": password,
        "nombreCompleto": "QA " + role, "estado": "activo", "rolIds": [role_ids[role]]}, expected=201)
    users[role] = Client()
    users[role].login(username, password)
mozo, caja = users["MOZO"], users["CAJA"]
mozo.call("GET", "/api/marketing", expected=403)
caja.call("GET", "/api/configuracion", expected=403)
caja.call("POST", "/api/ventas", {}, expected=403)

area = admin.call("POST", "/api/areas", {"nombre": "QA " + suffix, "estado": "ACTIVA"}, expected=201)
mesa = admin.call("POST", "/api/mesas", {"numero": max(m["numero"] for m in admin.call("GET", "/api/mesas")) + 1,
    "capacidad": 4, "areaId": area["id"]}, expected=201)
assert mozo.call("PATCH", f"/api/mesas/{mesa['id']}/abrir", {})["estado"] == "OCUPADA"
company = admin.call("POST", "/api/configuracion", {"ruc": str(int(suffix, 16)), "razonSocial": "Empresa QA " + suffix,
    "direccion": "Dirección de prueba"}, expected=201)
client = admin.call("POST", "/api/clientes", {"numeroDocumento": str(int(suffix, 16)),
    "nombresRazonSocial": "Cliente QA " + suffix, "fechaNacimiento": datetime.date.today().replace(year=2000).isoformat()}, expected=201)
provider = admin.call("POST", "/api/proveedores", {"rucDni": str(int(suffix, 16)), "razonSocial": "Proveedor QA"}, expected=201)
category = admin.call("GET", "/api/categorias")[0]
brand = admin.call("GET", "/api/marcas")[0]
products = []
for index in range(2):
    products.append(admin.call("POST", "/api/productos", {"categoria": {"id": category["id"]}, "marca": {"id": brand["id"]},
        "proveedor": {"id": provider["id"]}, "codigoBarras": f"QA-{suffix}-{index}", "nombre": f"Producto QA {index}",
        "precioCompra": 1, "precioVenta": 10, "stockActual": 2, "stockMinimo": 0}, expected=201))
receipt = next(r for r in admin.call("GET", "/api/tipos-comprobante") if r["nombre"] == "BOLETA")
payload = {"empresaId": company["id"], "clienteId": client["id"], "tipoComprobanteId": receipt["id"],
    "numeroComprobante": "QA-" + suffix, "mesaId": mesa["id"], "items": [{"productoId": p["id"], "cantidad": 2} for p in products]}
sale = mozo.call("POST", "/api/ventas", payload, expected=201)
assert sale["mesa"]["estado"] == "ATENDIENDO"
mozo.call("PATCH", f"/api/ventas/{sale['id']}/cerrar", {"metodoPago": "tarjeta"}, expected=403)
caja.call("PATCH", f"/api/mesas/{mesa['id']}/liberar", {}, expected=409)
products[1]["stockActual"] = 1
products[1] = admin.call("PUT", f"/api/productos/{products[1]['id']}", products[1])
failed = caja.call("PATCH", f"/api/ventas/{sale['id']}/cerrar", {"metodoPago": "tarjeta"}, expected=409)
assert "Stock insuficiente" in failed["message"]
assert next(p for p in admin.call("GET", "/api/productos") if p["id"] == products[0]["id"])["stockActual"] == 2
assert admin.call("GET", f"/api/marketing/clientes/{client['id']}/consumo") == []
products[1]["stockActual"] = 2
products[1] = admin.call("PUT", f"/api/productos/{products[1]['id']}", products[1])
caja.call("PATCH", f"/api/ventas/{sale['id']}/cerrar", {"metodoPago": "efectivo", "montoRecibido": 1}, expected=400)
closed = caja.call("PATCH", f"/api/ventas/{sale['id']}/cerrar", {"metodoPago": "tarjeta"})
assert closed["estado"] == "CERRADA" and closed["mesa"]["estado"] == "LIBRE"
caja.call("PATCH", f"/api/ventas/{sale['id']}/cerrar", {"metodoPago": "tarjeta"}, expected=409)
stats = next(c for c in admin.call("GET", "/api/clientes") if c["id"] == client["id"])
assert stats["frecuenciaVisitas"] == 1 and float(stats["totalGastado"]) == 47.2
assert len(admin.call("GET", f"/api/marketing/clientes/{client['id']}/consumo")) == 2
assert any(c["id"] == client["id"] for c in admin.call("GET", "/api/marketing/cumpleaneros"))
# Editar la ficha no puede borrar ni falsear las métricas acumuladas.
stats["frecuenciaVisitas"] = 999
stats["totalGastado"] = 999
updated = admin.call("PUT", f"/api/clientes/{client['id']}", stats)
assert updated["frecuenciaVisitas"] == 1 and float(updated["totalGastado"]) == 47.2
admin.call("POST", "/api/auth/logout", {}, expected=204)
admin.call("GET", "/api/mesas", expected=401)
print("PASS: sesión/CSRF, permisos, áreas/mesas, comanda, rollback HTTP 409, cobro, doble cobro, marketing y logout.")
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/config/ApiExceptionHandler.java`

```java
package MiTrampita.SistemaPOS.config;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> staleUpdate(Exception exception) {
        return error(HttpStatus.CONFLICT, "El registro cambió mientras lo editabas. Recarga los datos antes de guardar.");
    }
    @ExceptionHandler(MiTrampita.SistemaPOS.exception.StockInsuficienteException.class)
    public ResponseEntity<ApiError> stock(MiTrampita.SistemaPOS.exception.StockInsuficienteException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    public ResponseEntity<ApiError> concurrentUpdate(Exception exception) {
        return error(HttpStatus.CONFLICT, "Otro usuario está actualizando estos datos. Actualiza e inténtalo de nuevo.");
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .distinct()
            .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, message.isBlank() ? "Datos inválidos" : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraint(ConstraintViolationException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformed(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException exception) {
        return error(HttpStatus.CONFLICT, "El registro duplica un dato existente o tiene relaciones inválidas");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException exception) {
        return error(exception.getStatusCode(), exception.getReason() == null ? "Solicitud inválida" : exception.getReason());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message));
    }

    private ResponseEntity<ApiError> error(org.springframework.http.HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message));
    }

    public record ApiError(int status, String message) { }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/config/DataInitializer.java`

```java
package MiTrampita.SistemaPOS.config;

import MiTrampita.SistemaPOS.entity.EstadoUsuario;
import MiTrampita.SistemaPOS.entity.Rol;
import MiTrampita.SistemaPOS.entity.Usuario;
import MiTrampita.SistemaPOS.entity.UsuarioRol;
import MiTrampita.SistemaPOS.entity.TipoComprobante;
import MiTrampita.SistemaPOS.entity.Categoria;
import MiTrampita.SistemaPOS.entity.Marca;
import MiTrampita.SistemaPOS.entity.Mesa;
import MiTrampita.SistemaPOS.repositorio.CategoriaRepository;
import MiTrampita.SistemaPOS.repositorio.MarcaRepository;
import MiTrampita.SistemaPOS.repositorio.RolRepository;
import MiTrampita.SistemaPOS.repositorio.TipoComprobanteRepository;
import MiTrampita.SistemaPOS.repositorio.UsuarioRepository;
import MiTrampita.SistemaPOS.repositorio.UsuarioRolRepository;
import MiTrampita.SistemaPOS.repositorio.MesaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Crea los datos mínimos de desarrollo sin sobrescribir usuarios existentes. */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final UsuarioRolRepository usuarioRoles;
    private final MesaRepository mesas;
    private final MiTrampita.SistemaPOS.repositorio.AreaRepository areas;
    private final org.springframework.security.crypto.password.PasswordEncoder passwords;
    @org.springframework.beans.factory.annotation.Value("${app.bootstrap.password:}")
    private String bootstrapPassword;
    private final TipoComprobanteRepository comprobantes;
    private final CategoriaRepository categorias;
    private final MarcaRepository marcas;

    @Override
    @Transactional
    public void run(String... args) {
        Rol adminRole = roles.findByNombreIgnoreCase("ADMIN").orElseGet(() -> {
            Rol role = new Rol();
            role.setNombre("ADMIN");
            role.setDescripcion("Administrador");
            return roles.save(role);
        });

        for (String name : java.util.List.of("MOZO", "CAJA")) {
            if (roles.findByNombreIgnoreCase(name).isEmpty()) {
                Rol role = new Rol();
                role.setNombre(name);
                role.setDescripcion(name.equals("MOZO") ? "Pedidos y comandas" : "Cobros y comprobantes");
                roles.save(role);
            }
        }
        if (usuarios.count() == 0 && !bootstrapPassword.isBlank()) {
            Usuario admin = new Usuario();
            admin.setUsuario("admin");
            admin.setContrasena(passwords.encode(bootstrapPassword));
            admin.setNombreCompleto("Administrador");
            admin.setEstado(EstadoUsuario.activo);
            usuarios.save(admin);
            UsuarioRol relation = new UsuarioRol();
            relation.setUsuario(admin);
            relation.setRol(adminRole);
            usuarioRoles.save(relation);
        }

        ensureReceipt("BOLETA", "B001", "Boleta de venta");
        ensureReceipt("FACTURA", "F001", "Factura de venta");
        ensureReceipt("NOTA DE VENTA", "NV01", "Comprobante interno");
        ensureCategory("Comidas", "Platos, combos y alimentos preparados");
        ensureCategory("Bebidas", "Bebidas frías y calientes");
        ensureBrand("Sin marca");
        ensureTables();
    }

    private void ensureReceipt(String name, String series, String description) {
        if (comprobantes.findByNombreIgnoreCaseAndSerie(name, series).isEmpty()) {
            TipoComprobante receipt = new TipoComprobante();
            receipt.setNombre(name);
            receipt.setSerie(series);
            receipt.setDescripcion(description);
            comprobantes.save(receipt);
        }
    }

    private void ensureCategory(String name, String description) {
        if (categorias.findByNombreIgnoreCase(name).isEmpty()) {
            Categoria category = new Categoria();
            category.setNombre(name);
            category.setDescripcion(description);
            categorias.save(category);
        }
    }

    private void ensureBrand(String name) {
        if (marcas.findByNombreIgnoreCase(name).isEmpty()) {
            Marca brand = new Marca();
            brand.setNombre(name);
            marcas.save(brand);
        }
    }

    private void ensureTables() {
        var names = java.util.List.of("Sal\u00f3n Principal", "Terraza", "Zona Recreacional", "Piscina");
        var available = names.stream().map(name -> areas.findByNombreIgnoreCase(name).orElseGet(() -> {
            var area = new MiTrampita.SistemaPOS.entity.Area();
            area.setNombre(name);
            return areas.save(area);
        })).toList();
        for (int number = 1; number <= 10; number++) {
            final int tableNumber = number;
            if (mesas.findByNumero(tableNumber).isEmpty()) {
                Mesa mesa = new Mesa();
                mesa.setNumero(tableNumber);
                mesa.setCapacidad(4);
                mesa.setArea(available.get(Math.min((tableNumber - 1) / 3, 3)));
                mesas.save(mesa);
            }
        }
    }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/config/SecurityConfig.java`

```java
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
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/controller/AreaController.java`

```java
package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.Area;
import MiTrampita.SistemaPOS.dto.AreaRequest;
import MiTrampita.SistemaPOS.service.AreaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/areas") @RequiredArgsConstructor
public class AreaController {
    private final AreaService service;
    @GetMapping public List<Area> listar() { return service.listar(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Area crear(@Valid @RequestBody AreaRequest request) { return service.guardar(null, request); }
    @PutMapping("/{id}")
    public Area actualizar(@PathVariable Integer id, @Valid @RequestBody AreaRequest request) { return service.guardar(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) { service.eliminar(id); }

}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/controller/AuthController.java`

```java
package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.EstadoUsuario;
import MiTrampita.SistemaPOS.repositorio.*;
import MiTrampita.SistemaPOS.security.PosPrincipal;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UsuarioRepository usuarios;
    private final UsuarioRolRepository usuarioRoles;
    private final PasswordEncoder passwords;
    private final SecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrf;

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(), token.getToken()); }

    @GetMapping("/me")
    public PosPrincipal me(@AuthenticationPrincipal PosPrincipal principal) { return principal; }

    @PostMapping("/login")
    @Transactional
    public PosPrincipal login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        var user = usuarios.findByUsuarioIgnoreCase(req.usuario().trim()).orElseThrow(this::invalid);
        String stored = user.getContrasena();
        boolean hashed = stored.startsWith("$2");
        // Compatibilidad con el esquema antiguo: convertir a BCrypt al primer login válido.
        boolean matches = hashed ? passwords.matches(req.contrasena(), stored)
                : MessageDigest.isEqual(req.contrasena().getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
        if (!matches || user.getEstado() != EstadoUsuario.activo) throw invalid();
        var roles = usuarioRoles.findAllByUsuario_Id(user.getId()).stream()
                .map(r -> PosPrincipal.canonicalRole(r.getRol().getNombre())).distinct().toList();
        if (roles.stream().allMatch(r -> r.equals("SIN_ACCESO")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solicita al administrador un rol de acceso");
        if (!hashed) user.setContrasena(passwords.encode(req.contrasena()));
        var principal = new PosPrincipal(user.getId(), user.getUsuario(), user.getNombreCompleto(), roles);
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
        request.getSession();
        request.changeSessionId();
        csrf.saveToken(null, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return principal;
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos, o usuario inactivo");
    }
    public record CsrfResponse(String headerName, String token) { }
    public record LoginRequest(@NotBlank @Size(max = 50) String usuario,
            @NotBlank @Size(max = 255) String contrasena) { }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/controller/CatalogoController.java`

```java
package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.repositorio.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogoController {
    private final CategoriaRepository categorias;
    private final MarcaRepository marcas;
    private final ProveedorRepository proveedores;
    private final ClienteRepository clientes;
    private final EmpresaRepository empresas;
    private final TipoComprobanteRepository comprobantes;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;

    @GetMapping("/categorias")
    public List<Categoria> categorias() {
        return categorias.findAll();
    }

    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    public Categoria crearCategoria(@Valid @RequestBody Categoria value) {
        return categorias.save(value);
    }

    @PutMapping("/categorias/{id}")
    public Categoria actualizarCategoria(@PathVariable Integer id, @Valid @RequestBody Categoria value) {
        value.setId(id);
        return actualizar(categorias, id, value, "Categoría");
    }

    @GetMapping("/marcas")
    public List<Marca> marcas() {
        return marcas.findAll();
    }

    @PostMapping("/marcas")
    @ResponseStatus(HttpStatus.CREATED)
    public Marca crearMarca(@Valid @RequestBody Marca value) {
        return marcas.save(value);
    }

    @PutMapping("/marcas/{id}")
    public Marca actualizarMarca(@PathVariable Integer id, @Valid @RequestBody Marca value) {
        value.setId(id);
        return actualizar(marcas, id, value, "Marca");
    }

    @GetMapping("/proveedores")
    public List<Proveedor> proveedores() {
        return proveedores.findAll();
    }

    @PostMapping("/proveedores")
    @ResponseStatus(HttpStatus.CREATED)
    public Proveedor crearProveedor(@Valid @RequestBody Proveedor value) {
        return proveedores.save(value);
    }

    @PutMapping("/proveedores/{id}")
    public Proveedor actualizarProveedor(@PathVariable Integer id, @Valid @RequestBody Proveedor value) {
        value.setId(id);
        return actualizar(proveedores, id, value, "Proveedor");
    }

    @GetMapping("/pos/clientes")
    public List<MiTrampita.SistemaPOS.dto.ClientePosResponse> clientesPos() {
        return clientes.findAll().stream().map(MiTrampita.SistemaPOS.dto.ClientePosResponse::from).toList();
    }

    @GetMapping("/clientes")
    public List<Cliente> clientes() {
        return clientes.findAll();
    }

    @PostMapping("/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    public Cliente crearCliente(@Valid @RequestBody Cliente value) {
        value.setId(null);
        value.setFrecuenciaVisitas(0L);
        value.setTotalGastado(java.math.BigDecimal.ZERO);
        return clientes.save(value);
    }

    @PutMapping("/clientes/{id}")
    @org.springframework.transaction.annotation.Transactional
    public Cliente actualizarCliente(@PathVariable Integer id, @Valid @RequestBody Cliente value) {
        Cliente current = clientes.findByIdForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        current.setNumeroDocumento(value.getNumeroDocumento());
        current.setNombresRazonSocial(value.getNombresRazonSocial());
        current.setDireccion(value.getDireccion());
        current.setTelefono(value.getTelefono());
        current.setCorreo(value.getCorreo());
        current.setFechaNacimiento(value.getFechaNacimiento());
        return current;
    }

    @GetMapping({"/configuracion", "/empresas", "/pos/configuracion"})
    public List<Empresa> empresas() {
        return empresas.findAll();
    }

    @PostMapping({"/configuracion", "/empresas"})
    @ResponseStatus(HttpStatus.CREATED)
    public Empresa crearEmpresa(@Valid @RequestBody Empresa value) {
        return empresas.save(value);
    }

    @PutMapping({"/configuracion/{id}", "/empresas/{id}"})
    public Empresa actualizarEmpresa(@PathVariable Integer id, @Valid @RequestBody Empresa value) {
        value.setId(id);
        return actualizar(empresas, id, value, "Empresa");
    }

    @GetMapping("/tipos-comprobante")
    public List<TipoComprobante> comprobantes() {
        return comprobantes.findAll();
    }

    @PostMapping("/tipos-comprobante")
    @ResponseStatus(HttpStatus.CREATED)
    public TipoComprobante crearComprobante(@Valid @RequestBody TipoComprobante value) {
        return comprobantes.save(value);
    }

    @PutMapping("/tipos-comprobante/{id}")
    public TipoComprobante actualizarComprobante(@PathVariable Integer id, @Valid @RequestBody TipoComprobante value) {
        value.setId(id);
        return actualizar(comprobantes, id, value, "Tipo de comprobante");
    }

    @GetMapping("/roles")
    public List<Rol> roles() {
        return roles.findAll();
    }

    @DeleteMapping("/categorias/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCategoria(@PathVariable Integer id) {
        eliminar(categorias, id, "Categoría");
    }

    @DeleteMapping("/marcas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarMarca(@PathVariable Integer id) {
        eliminar(marcas, id, "Marca");
    }

    @DeleteMapping("/proveedores/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarProveedor(@PathVariable Integer id) {
        eliminar(proveedores, id, "Proveedor");
    }

    @DeleteMapping("/clientes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCliente(@PathVariable Integer id) {
        eliminar(clientes, id, "Cliente");
    }

    @DeleteMapping({"/configuracion/{id}", "/empresas/{id}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarEmpresa(@PathVariable Integer id) {
        eliminar(empresas, id, "Empresa");
    }

    @DeleteMapping("/tipos-comprobante/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarComprobante(@PathVariable Integer id) {
        eliminar(comprobantes, id, "Tipo de comprobante");
    }

    private void eliminar(org.springframework.data.jpa.repository.JpaRepository<?, Integer> repository, Integer id,
            String recurso) {
        if (!repository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, recurso + " no encontrado");
        repository.deleteById(id);
    }

    private <T> T actualizar(org.springframework.data.jpa.repository.JpaRepository<T, Integer> repository, Integer id,
            T value, String recurso) {
        if (!repository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, recurso + " no encontrado");
        return repository.save(value);
    }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/controller/MarketingController.java`

```java
package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.service.MarketingService;
import MiTrampita.SistemaPOS.repositorio.DetalleVentaRepository;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.List;

@RestController @RequestMapping("/api/marketing") @RequiredArgsConstructor
public class MarketingController {
    private final MarketingService service;
    @GetMapping public MarketingService.Dashboard dashboard() { return service.dashboard(); }
    @GetMapping("/frecuentes") public List<MarketingService.ClienteResumen> frecuentes() { return service.frecuentes(); }
    @GetMapping("/cumpleaneros")
    public List<MarketingService.ClienteResumen> cumpleaneros(@RequestParam(required = false) @Min(1) @Max(12) Integer mes) {
        return service.cumpleaneros(mes == null ? LocalDate.now(ZoneId.of("America/Lima")).getMonthValue() : mes);
    }
    @GetMapping("/clientes/{id}/consumo")
    public List<DetalleVentaRepository.ConsumoProducto> consumo(@PathVariable @Min(1) Integer id) { return service.consumo(id); }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/controller/MesaController.java`

```java
package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.Mesa;
import MiTrampita.SistemaPOS.dto.MesaRequest;
import MiTrampita.SistemaPOS.service.MesaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/mesas") @RequiredArgsConstructor
public class MesaController {
    private final MesaService service;
    @GetMapping public List<Mesa> listar(@RequestParam(required = false) Integer areaId) { return service.listar(areaId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Mesa crear(@Valid @RequestBody MesaRequest request) { return service.guardar(null, request); }
    @PutMapping("/{id}")
    public Mesa actualizar(@PathVariable Integer id, @Valid @RequestBody MesaRequest request) { return service.guardar(id, request); }
    @PatchMapping("/{id}/abrir") public Mesa abrir(@PathVariable Integer id) { return service.abrir(id); }
    @PatchMapping("/{id}/liberar") public Mesa liberar(@PathVariable Integer id) { return service.liberar(id); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) { service.eliminar(id); }

}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/controller/UsuarioController.java`

```java
package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.repositorio.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api/usuarios") @RequiredArgsConstructor
@Transactional
public class UsuarioController {
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final UsuarioRolRepository relaciones;
    private final PasswordEncoder passwords;

    @GetMapping @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() { return usuarios.findAll().stream().map(this::response).toList(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crear(@Valid @RequestBody UsuarioRequest request) { return guardar(null, request); }
    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable Integer id, @Valid @RequestBody UsuarioRequest request) { return guardar(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) {
        var user = usuarios.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        relaciones.deleteAllByUsuario_Id(id);
        usuarios.delete(user);
    }

    private UsuarioResponse guardar(Integer id, UsuarioRequest req) {
        var user = id == null ? new Usuario() : usuarios.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        var requestedRoles = roles.findAllById(req.rolIds());
        if (requestedRoles.size() != new HashSet<>(req.rolIds()).size()
                || requestedRoles.stream().anyMatch(r -> !Set.of("ADMIN", "MOZO", "CAJA").contains(r.getNombre())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona roles ADMIN, MOZO o CAJA válidos");
        if (req.contrasena() != null && !req.contrasena().isBlank()) {
            if (req.contrasena().length() < 8 || req.contrasena().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener entre 8 y 72 caracteres");
            user.setContrasena(passwords.encode(req.contrasena()));
        } else if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña es obligatoria");
        user.setUsuario(req.usuario().trim());
        user.setNombreCompleto(req.nombreCompleto().trim());
        user.setCorreoElectronico(req.correoElectronico());
        user.setEstado(req.estado());
        usuarios.saveAndFlush(user);
        if (id != null) { relaciones.deleteAllByUsuario_Id(id); relaciones.flush(); }
        for (Rol rol : requestedRoles) {
            var relation = new UsuarioRol();
            relation.setUsuario(user);
            relation.setRol(rol);
            relaciones.save(relation);
        }
        return response(user);
    }
    private UsuarioResponse response(Usuario u) {
        var assigned = relaciones.findAllByUsuario_Id(u.getId()).stream()
                .filter(r -> Set.of("ADMIN", "MOZO", "CAJA").contains(r.getRol().getNombre())).toList();
        return new UsuarioResponse(u.getId(), u.getUsuario(), u.getNombreCompleto(), u.getCorreoElectronico(), u.getEstado(),
                assigned.stream().map(r -> r.getRol().getId()).toList(), assigned.stream().map(r -> r.getRol().getNombre()).toList());
    }
    public record UsuarioRequest(@NotBlank @Size(max = 50) String usuario, @Size(max = 72) String contrasena,
            @NotBlank @Size(max = 150) String nombreCompleto, @Email @Size(max = 100) String correoElectronico,
            @NotNull EstadoUsuario estado, @NotEmpty List<@NotNull @Min(1) Integer> rolIds) { }
    public record UsuarioResponse(Integer id, String usuario, String nombreCompleto, String correoElectronico,
            EstadoUsuario estado, List<Integer> rolIds, List<String> roles) { }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/controller/VentaController.java`

```java
package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.dto.VentaResponse;
import MiTrampita.SistemaPOS.dto.VentaDtos.*;
import MiTrampita.SistemaPOS.security.PosPrincipal;
import MiTrampita.SistemaPOS.service.VentaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/api/ventas") @RequiredArgsConstructor
public class VentaController {
    private final VentaService service;
    @GetMapping public List<VentaResponse> listar() { return service.listar(false).stream().map(VentaResponse::from).toList(); }
    @GetMapping("/abiertas") public List<VentaResponse> abiertas() { return service.listar(true).stream().map(VentaResponse::from).toList(); }
    @GetMapping("/{id}")
    public VentaResponse obtener(@PathVariable Integer id, @AuthenticationPrincipal PosPrincipal principal) {
        Venta venta = service.obtener(id);
        if (venta.getEstado() != EstadoVenta.ABIERTA && !principal.roles().contains("ADMIN") && !principal.roles().contains("CAJA"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administración y caja pueden consultar comprobantes");
        return VentaResponse.from(venta);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse registrar(@Valid @RequestBody VentaRequest request, @AuthenticationPrincipal PosPrincipal principal) {
        return VentaResponse.from(service.registrar(request, principal.id()));
    }
    @PatchMapping("/{id}/items")
    public VentaResponse agregarItems(@PathVariable Integer id, @Valid @RequestBody ActualizarItemsRequest request) {
        return VentaResponse.from(service.agregarItems(id, request.items()));
    }
    @PatchMapping("/{id}/cerrar")
    public VentaResponse cerrar(@PathVariable Integer id, @Valid @RequestBody CerrarVentaRequest request) {
        return VentaResponse.from(service.cerrar(id, request.metodoPago(), request.montoRecibido()));
    }

}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/dto/AreaRequest.java`

```java
package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.Area;
import jakarta.validation.constraints.*;

public record AreaRequest(@NotBlank @Size(max = 100) String nombre, @NotNull Area.EstadoArea estado) { }
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/dto/ClientePosResponse.java`

```java
package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.Cliente;
import java.time.LocalDate;

/** Información necesaria para atender al cliente, sin sus métricas administrativas. */
public record ClientePosResponse(Integer id, String numeroDocumento, String nombresRazonSocial,
        String direccion, String telefono, String correo, LocalDate fechaNacimiento) {
    public static ClientePosResponse from(Cliente c) {
        return new ClientePosResponse(c.getId(), c.getNumeroDocumento(), c.getNombresRazonSocial(),
                c.getDireccion(), c.getTelefono(), c.getCorreo(), c.getFechaNacimiento());
    }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/dto/MesaRequest.java`

```java
package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.Mesa;
import jakarta.validation.constraints.*;

public record MesaRequest(@NotNull @Min(1) Integer numero, @NotNull @Min(1) @Max(100) Integer capacidad,
            @NotNull(message = "El área es obligatoria") @Min(1) Integer areaId) { }
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/dto/VentaDtos.java`

```java
package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.MetodoPago;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contratos validados compartidos entre controlador y servicio de ventas. */
public final class VentaDtos {
    private VentaDtos() { }
    public record VentaRequest(@NotNull @Min(1) Integer empresaId,
            @Min(1) Integer clienteId, @Valid ClienteRequest cliente,
            @NotNull @Min(1) Integer tipoComprobanteId,
            @NotBlank @Size(max = 50) String numeroComprobante,
            @NotNull(message = "Faltan datos de la mesa") @Min(1) Integer mesaId,
            @NotEmpty @Size(max = 200) List<@NotNull @Valid ItemRequest> items) {
        @AssertTrue(message = "Selecciona un cliente o completa sus datos, no ambos")
        public boolean isClienteValido() { return (clienteId != null) != (cliente != null); }
    }
    public record ClienteRequest(
            @NotBlank @Pattern(regexp = "\\d{6,20}", message = "El documento debe tener de 6 a 20 dígitos") String numeroDocumento,
            @NotBlank @Size(max = 150) String nombresRazonSocial,
            @Size(max = 255) String direccion, @Size(max = 20) String telefono,
            @Email @Size(max = 100) String correo, @PastOrPresent LocalDate fechaNacimiento) { }
    public record ItemRequest(@NotNull @Min(1) Integer productoId,
            @NotNull @Min(1) @Max(100000) Integer cantidad) { }
    public record ActualizarItemsRequest(@NotEmpty @Size(max = 200) List<@NotNull @Valid ItemRequest> items) { }
    public record CerrarVentaRequest(@NotNull(message = "El método de pago es obligatorio") MetodoPago metodoPago,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal montoRecibido) { }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/dto/VentaResponse.java`

```java
package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Evita exponer contraseñas, contactos del personal o métricas de marketing en el POS. */
public record VentaResponse(Integer id, Empresa empresa, UsuarioResumen usuario, ClientePosResponse cliente,
        TipoComprobante tipoComprobante, Mesa mesa, String numeroComprobante, BigDecimal subtotal,
        BigDecimal igv, BigDecimal total, MetodoPago metodoPago, EstadoVenta estado,
        OffsetDateTime fechaVenta, OffsetDateTime fechaCobro, List<DetalleResponse> detalles) {
    public static VentaResponse from(Venta v) {
        return new VentaResponse(v.getId(), v.getEmpresa(),
                new UsuarioResumen(v.getUsuario().getId(), v.getUsuario().getUsuario(), v.getUsuario().getNombreCompleto()),
                ClientePosResponse.from(v.getCliente()), v.getTipoComprobante(), v.getMesa(),
                v.getNumeroComprobante(), v.getSubtotal(), v.getIgv(), v.getTotal(), v.getMetodoPago(), v.getEstado(),
                v.getFechaVenta(), v.getFechaCobro(), v.getDetalles().stream().map(d -> new DetalleResponse(d.getId(),
                    new ProductoResumen(d.getProducto().getId(), d.getProducto().getNombre()),
                    d.getCantidad(), d.getPrecioUnitario(), d.getSubtotal())).toList());
    }
    public record UsuarioResumen(Integer id, String usuario, String nombreCompleto) { }
    public record ProductoResumen(Integer id, String nombre) { }
    public record DetalleResponse(Integer id, ProductoResumen producto, Integer cantidad,
            BigDecimal precioUnitario, BigDecimal subtotal) { }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/entity/Area.java`

```java
package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "areas", uniqueConstraints = @UniqueConstraint(name = "uk_area_nombre", columnNames = "nombre"))
@Getter @Setter @NoArgsConstructor
public class Area {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, length = 100)
    private String nombre;
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EstadoArea estado = EstadoArea.ACTIVA;

    public enum EstadoArea { ACTIVA, INACTIVA }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/entity/Cliente.java`

```java
package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;
import java.math.BigDecimal;

@Entity
@Table(name = "clientes", uniqueConstraints = @UniqueConstraint(name = "uk_cliente_documento", columnNames = "numero_documento"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Integer id;
    @NotBlank
    @Size(max = 20)
    @Column(name = "numero_documento", nullable = false, length = 20)
    private String numeroDocumento;
    @NotBlank
    @Size(max = 150)
    @Column(name = "nombres_razon_social", nullable = false, length = 150)
    private String nombresRazonSocial;
    @Size(max = 255)
    @Column(length = 255)
    private String direccion;
    @Size(max = 20)
    @Column(length = 20)
    private String telefono;
    @Email
    @Size(max = 100)
    @Column(length = 100)
    private String correo;

    @PastOrPresent(message = "La fecha de nacimiento no puede ser futura")
    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "frecuencia_visitas", nullable = false)
    private Long frecuenciaVisitas = 0L;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "total_gastado", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalGastado = BigDecimal.ZERO;
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/entity/EstadoMesa.java`

```java
package MiTrampita.SistemaPOS.entity;

public enum EstadoMesa {
    LIBRE,
    OCUPADA,
    ATENDIENDO
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/entity/Mesa.java`

```java
package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mesas", uniqueConstraints = @UniqueConstraint(name = "uk_mesa_numero", columnNames = "numero"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Mesa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @NotNull
    @Min(1)
    @Column(name = "numero", nullable = false)
    private Integer numero;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer capacidad = 4;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoMesa estado = EstadoMesa.LIBRE;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/entity/Producto.java`

```java
package MiTrampita.SistemaPOS.entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Integer id;
    @Version
    @Column(nullable = false)
    private Long version;
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_categoria", nullable = false)
    private Categoria categoria;
    @ManyToOne
    @JoinColumn(name = "id_marca")
    private Marca marca;
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;
    @NotBlank
    @Size(max = 50)
    @Column(name = "codigo_barras", nullable = false, unique = true, length = 50)
    private String codigoBarras;
    @NotBlank
    @Size(max = 150)
    @Column(name = "nombre_producto", nullable = false, length = 150)
    private String nombre;
    @Column(columnDefinition = "TEXT")
    private String descripcion;
    @NotNull
    @PositiveOrZero
    @Digits(integer = 8, fraction = 2)
    @Column(name = "precio_compra", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioCompra = BigDecimal.ZERO;
    @NotNull
    @PositiveOrZero
    @Digits(integer = 8, fraction = 2)
    @Column(name = "precio_venta", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioVenta = BigDecimal.ZERO;
    @NotNull
    @Min(0)
    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual = 0;
    @NotNull
    @Min(0)
    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo = 5;
    @Column(name = "fecha_registro", insertable = false, updatable = false)
    private OffsetDateTime fechaRegistro;

    public void setId(Integer id) {
        this.id = id;
    }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/entity/Usuario.java`

```java
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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer id;
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String usuario;
    @NotBlank
    @Size(min = 4, max = 255)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "contraseña", nullable = false, length = 255)
    private String contrasena;
    @NotBlank
    @Size(max = 150)
    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;
    @Email
    @Size(max = 100)
    @Column(name = "correo_electronico", length = 100)
    private String correoElectronico;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "pin_caja", length = 255)
    private String pinCaja;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EstadoUsuario estado = EstadoUsuario.activo;
    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/entity/Venta.java`

```java
package MiTrampita.SistemaPOS.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ventas", uniqueConstraints = @UniqueConstraint(name = "uk_venta_comprobante", columnNames = {
        "id_tipo_comprobante", "numero_comprobante" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Venta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_venta")
    private Integer id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_empresa", nullable = false)
    private Empresa empresa;
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_tipo_comprobante", nullable = false)
    private TipoComprobante tipoComprobante;
    @ManyToOne
    @JoinColumn(name = "mesa_id")
    private Mesa mesa;
    @NotBlank
    @Size(max = 50)
    @Column(name = "numero_comprobante", nullable = false, length = 50)
    private String numeroComprobante;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(name = "igv_impuesto", nullable = false, precision = 10, scale = 2)
    private BigDecimal igv = BigDecimal.ZERO;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "metodo_pago", nullable = false, length = 20)
    private MetodoPago metodoPago = MetodoPago.efectivo;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "estado_venta", nullable = false, length = 20)
    private EstadoVenta estado = EstadoVenta.ABIERTA;
    @Column(name = "fecha_venta", insertable = false, updatable = false)
    private OffsetDateTime fechaVenta;
    @Column(name = "fecha_cobro")
    private OffsetDateTime fechaCobro;
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleVenta> detalles = new ArrayList<>();
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/exception/StockInsuficienteException.java`

```java
package MiTrampita.SistemaPOS.exception;

/** RuntimeException: Spring revierte todo el cobro, incluido cualquier descuento previo. */
public class StockInsuficienteException extends RuntimeException {
    public StockInsuficienteException(String producto, int disponible, int solicitado) {
        super("Stock insuficiente para " + producto + ". Disponible: " + disponible + "; solicitado: " + solicitado);
    }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/repositorio/AreaRepository.java`

```java
package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface AreaRepository extends JpaRepository<Area, Integer> {
    List<Area> findAllByOrderByIdAsc();
    Optional<Area> findByNombreIgnoreCase(String nombre);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Area a where a.id = :id")
    Optional<Area> findByIdForUpdate(Integer id);
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/repositorio/ClienteRepository.java`

```java
package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {
    Optional<Cliente> findByNumeroDocumento(String numeroDocumento);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Cliente c where c.id = :id")
    Optional<Cliente> findByIdForUpdate(Integer id);

    java.util.List<Cliente> findTop10ByFrecuenciaVisitasGreaterThanOrderByFrecuenciaVisitasDescTotalGastadoDescIdAsc(Long minimo);

    @org.springframework.data.jpa.repository.Query("select c from Cliente c where month(c.fechaNacimiento) = :mes order by day(c.fechaNacimiento), c.id")
    java.util.List<Cliente> cumpleaneros(int mes);
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/repositorio/DetalleVentaRepository.java`

```java
package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.DetalleVenta;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Integer> {
    @Query("""
        select d.producto.id as productoId, d.producto.nombre as nombre,
               d.producto.categoria.nombre as categoria, sum(d.cantidad) as unidades,
               sum(d.subtotal) as importe
        from DetalleVenta d where d.venta.cliente.id = :clienteId
          and d.venta.estado = MiTrampita.SistemaPOS.entity.EstadoVenta.CERRADA
        group by d.producto.id, d.producto.nombre, d.producto.categoria.nombre
        order by sum(d.cantidad) desc, d.producto.nombre asc
        """)
    List<ConsumoProducto> consumoCliente(Integer clienteId, Pageable pageable);

    interface ConsumoProducto {
        Integer getProductoId();
        String getNombre();
        String getCategoria();
        Long getUnidades();
        BigDecimal getImporte();
    }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/repositorio/MesaRepository.java`

```java
package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface MesaRepository extends JpaRepository<Mesa, Integer> {
    Optional<Mesa> findByNumero(Integer numero);

    List<Mesa> findAllByOrderByNumeroAsc();
    List<Mesa> findAllByArea_IdOrderByNumeroAsc(Integer areaId);
    boolean existsByArea_Id(Integer areaId);
    boolean existsByArea_IdAndEstadoNot(Integer areaId, MiTrampita.SistemaPOS.entity.EstadoMesa estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mesa m where m.id = :id")
    Optional<Mesa> findByIdForUpdate(Integer id);
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/repositorio/UsuarioRolRepository.java`

```java
package MiTrampita.SistemaPOS.repositorio;

import MiTrampita.SistemaPOS.entity.UsuarioRol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, Integer> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "rol")
    List<UsuarioRol> findAllByUsuario_Id(Integer usuarioId);
    void deleteAllByUsuario_Id(Integer usuarioId);
    boolean existsByUsuario_IdAndRol_Id(Integer usuarioId, Integer rolId);
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/security/PosPrincipal.java`

```java
package MiTrampita.SistemaPOS.security;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

/** Identidad y permisos obtenidos del servidor, nunca del JSON del navegador. */
public record PosPrincipal(Integer id, String usuario, String nombreCompleto, List<String> roles)
        implements Serializable {
    public static String canonicalRole(String nombre) {
        return switch (nombre.trim().toUpperCase(Locale.ROOT)) {
            case "ADMIN", "ADMINISTRADOR" -> "ADMIN";
            case "MOZO", "VENTAS", "VENDEDOR", "MOZO/VENTAS" -> "MOZO";
            case "CAJA", "CAJERO" -> "CAJA";
            default -> "SIN_ACCESO";
        };
    }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/security/SessionUserFilter.java`

```java
package MiTrampita.SistemaPOS.security;

import MiTrampita.SistemaPOS.entity.EstadoUsuario;
import MiTrampita.SistemaPOS.repositorio.UsuarioRepository;
import MiTrampita.SistemaPOS.repositorio.UsuarioRolRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Aplica bloqueos y cambios de rol también a sesiones que ya estaban abiertas. */
@RequiredArgsConstructor
public class SessionUserFilter extends OncePerRequestFilter {
    private final UsuarioRepository usuarios;
    private final UsuarioRolRepository roles;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PosPrincipal principal) {
            var user = usuarios.findById(principal.id()).orElse(null);
            if (user == null || user.getEstado() != EstadoUsuario.activo) {
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) request.getSession(false).invalidate();
            } else {
                var currentRoles = roles.findAllByUsuario_Id(user.getId()).stream()
                        .map(r -> PosPrincipal.canonicalRole(r.getRol().getNombre())).distinct().toList();
                var current = new PosPrincipal(user.getId(), user.getUsuario(), user.getNombreCompleto(), currentRoles);
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(current, null,
                        currentRoles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
                SecurityContextHolder.setContext(context);
            }
        }
        chain.doFilter(request, response);
    }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/service/AreaService.java`

```java
package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.dto.AreaRequest;
import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.repositorio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service @RequiredArgsConstructor
public class AreaService {
    private final AreaRepository areas;
    private final MesaRepository mesas;

    @Transactional(readOnly = true)
    public List<Area> listar() { return areas.findAllByOrderByIdAsc(); }

    @Transactional
    public Area guardar(Integer id, AreaRequest request) {
        Area area = id == null ? new Area() : areas.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Área no encontrada"));
        areas.findByNombreIgnoreCase(request.nombre().trim()).filter(a -> !a.getId().equals(id)).ifPresent(a -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un área con ese nombre");
        });
        if (id != null && request.estado() == Area.EstadoArea.INACTIVA
                && mesas.existsByArea_IdAndEstadoNot(id, EstadoMesa.LIBRE))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Libera las mesas antes de desactivar el área");
        area.setNombre(request.nombre().trim());
        area.setEstado(request.estado());
        return areas.save(area);
    }

    @Transactional
    public void eliminar(Integer id) {
        var area = areas.findByIdForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Área no encontrada"));
        if (mesas.existsByArea_Id(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reasigna las mesas antes de eliminar el área");
        areas.delete(area);
    }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/service/MarketingService.java`

```java
package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.entity.Cliente;
import MiTrampita.SistemaPOS.repositorio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class MarketingService {
    private final ClienteRepository clientes;
    private final DetalleVentaRepository detalles;

    public Dashboard dashboard() {
        int mes = LocalDate.now(ZoneId.of("America/Lima")).getMonthValue();
        return new Dashboard(mes, clientes.count(), frecuentes(), cumpleaneros(mes));
    }
    public List<ClienteResumen> frecuentes() {
        return clientes.findTop10ByFrecuenciaVisitasGreaterThanOrderByFrecuenciaVisitasDescTotalGastadoDescIdAsc(0L)
                .stream().map(ClienteResumen::from).toList();
    }
    public List<ClienteResumen> cumpleaneros(int mes) {
        return clientes.cumpleaneros(mes).stream().map(ClienteResumen::from).toList();
    }
    public List<DetalleVentaRepository.ConsumoProducto> consumo(Integer id) {
        if (!clientes.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado");
        return detalles.consumoCliente(id, PageRequest.of(0, 10));
    }
    public record Dashboard(int mes, long totalClientes, List<ClienteResumen> frecuentes, List<ClienteResumen> cumpleaneros) { }
    public record ClienteResumen(Integer id, String nombre, String telefono, String correo,
            LocalDate fechaNacimiento, Long frecuenciaVisitas, BigDecimal totalGastado) {
        static ClienteResumen from(Cliente c) {
            return new ClienteResumen(c.getId(), c.getNombresRazonSocial(), c.getTelefono(), c.getCorreo(),
                    c.getFechaNacimiento(), c.getFrecuenciaVisitas(), c.getTotalGastado());
        }
    }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/service/MesaService.java`

```java
package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.dto.MesaRequest;
import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.repositorio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.TreeSet;

@Service @RequiredArgsConstructor
public class MesaService {
    private final jakarta.persistence.EntityManager entityManager;
    private final MesaRepository mesas;
    private final AreaRepository areas;
    private final VentaRepository ventas;

    @Transactional(readOnly = true)
    public List<Mesa> listar(Integer areaId) {
        return areaId == null ? mesas.findAllByOrderByNumeroAsc() : mesas.findAllByArea_IdOrderByNumeroAsc(areaId);
    }

    @Transactional
    public Mesa guardar(Integer id, MesaRequest request) {
        // Bloqueos de áreas en orden estable; evita competir con su desactivación.
        var ids = new TreeSet<Integer>();
        ids.add(request.areaId());
        if (id != null) ids.add(obtener(id).getArea().getId());
        for (Integer areaId : ids) areaActiva(areaId);
        Mesa mesa = id == null ? new Mesa() : bloquear(id);
        if (id != null && !ids.contains(mesa.getArea().getId())) throw conflict("La mesa cambió de área; actualiza el salón");
        if (mesa.getEstado() != EstadoMesa.LIBRE) throw conflict("Solo se puede editar una mesa libre");
        mesas.findByNumero(request.numero()).filter(m -> !m.getId().equals(id)).ifPresent(m -> {
            throw conflict("Ya existe la mesa " + request.numero());
        });
        mesa.setNumero(request.numero());
        mesa.setCapacidad(request.capacidad());
        mesa.setArea(areaActiva(request.areaId()));
        return mesas.save(mesa);
    }

    /** El área se bloquea antes que la mesa en todos los flujos de apertura. */
    @Transactional
    public Mesa bloquearParaAbrir(Integer id) {
        var areaId = obtener(id).getArea().getId();
        areaActiva(areaId);
        Mesa mesa = bloquear(id);
        if (!mesa.getArea().getId().equals(areaId)) throw conflict("La mesa cambió de área; actualiza el salón");
        return mesa;
    }

    @Transactional
    public Mesa abrir(Integer id) {
        Mesa mesa = bloquearParaAbrir(id);
        if (mesa.getEstado() != EstadoMesa.LIBRE) throw conflict("La mesa ya está ocupada");
        mesa.setEstado(EstadoMesa.OCUPADA);
        return mesa;
    }

    @Transactional
    public Mesa liberar(Integer id) {
        Mesa mesa = bloquear(id);
        if (ventas.existsByMesa_IdAndEstado(id, EstadoVenta.ABIERTA))
            throw conflict("Cobra la comanda antes de liberar la mesa");
        mesa.setEstado(EstadoMesa.LIBRE);
        return mesa;
    }

    @Transactional
    public void eliminar(Integer id) {
        Mesa mesa = bloquear(id);
        if (mesa.getEstado() != EstadoMesa.LIBRE) throw conflict("Solo se puede eliminar una mesa libre");
        mesas.delete(mesa);
    }

    private Mesa obtener(Integer id) {
        return mesas.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mesa no encontrada"));
    }
    private Mesa bloquear(Integer id) {
        var mesa = mesas.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mesa no encontrada"));
        entityManager.refresh(mesa, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return mesa;
    }
    private Area areaActiva(Integer id) {
        Area area = areas.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Área no encontrada"));
        entityManager.refresh(area, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (area.getEstado() != Area.EstadoArea.ACTIVA) throw conflict("El área está inactiva");
        return area;
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/service/ProductoService.java`

```java
package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.entity.Producto;
import MiTrampita.SistemaPOS.entity.Marca;
import MiTrampita.SistemaPOS.repositorio.ProductoRepository;
import MiTrampita.SistemaPOS.repositorio.MarcaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoService {
    private final ProductoRepository repository;
    private final MarcaRepository marcas;

    @Transactional(readOnly = true)
    public List<Producto> listar() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Producto obtener(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
    }

    @Transactional
    public Producto guardar(Producto producto) {
        if (producto.getMarca() == null || producto.getMarca().getId() == null || producto.getMarca().getId() <= 0) {
            producto.setMarca(marcas.findByNombreIgnoreCase("Sin marca").orElseGet(() -> {
                Marca marca = new Marca();
                marca.setNombre("Sin marca");
                return marcas.save(marca);
            }));
        }
        return repository.save(producto);
    }

    @Transactional
    public Producto actualizar(Integer id, Producto producto) {
        obtener(id);
        if (producto.getVersion() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recarga el producto para obtener su versión actual");
        producto.setId(id);
        return guardar(producto);
    }

    @Transactional
    public void eliminar(Integer id) {
        repository.delete(obtener(id));
    }
}
```

## `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/service/VentaService.java`

```java
package MiTrampita.SistemaPOS.service;

import MiTrampita.SistemaPOS.dto.VentaDtos.*;
import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.exception.StockInsuficienteException;
import MiTrampita.SistemaPOS.repositorio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.OffsetDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class VentaService {
    private static final BigDecimal IGV = new BigDecimal("0.18");
    private static final BigDecimal MAX_IMPORTE = new BigDecimal("99999999.99");
    private final jakarta.persistence.EntityManager entityManager;
    private final VentaRepository ventas;
    private final ProductoRepository productos;
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final ClienteRepository clientes;
    private final TipoComprobanteRepository comprobantes;
    private final MesaRepository mesas;
    private final MesaService mesaService;

    @Transactional(readOnly = true)
    public List<Venta> listar(boolean abiertas) {
        var result = abiertas ? ventas.findAllByEstadoOrderByFechaVentaDesc(EstadoVenta.ABIERTA) : ventas.findAll();
        result.forEach(v -> v.getDetalles().size());
        return result;
    }
    @Transactional(readOnly = true)
    public Venta obtener(Integer id) {
        Venta venta = ventas.findById(id).orElseThrow(() -> missing("Venta"));
        venta.getDetalles().size();
        return venta;
    }

    @Transactional
    public Venta registrar(VentaRequest request, Integer usuarioId) {
        if (request.mesaId() == null) throw bad("Faltan datos de la mesa");
        Mesa mesa = mesaService.bloquearParaAbrir(request.mesaId());
        if (mesa.getEstado() == EstadoMesa.ATENDIENDO || ventas.existsByMesa_IdAndEstado(mesa.getId(), EstadoVenta.ABIERTA))
            throw conflict("La mesa ya tiene una comanda abierta. Actualiza el salón");
        Venta venta = new Venta();
        venta.setMesa(mesa);
        venta.setEmpresa(empresas.findById(request.empresaId()).orElseThrow(() -> missing("Configuración fiscal")));
        var usuario = usuarios.findById(usuarioId).orElseThrow(() -> missing("Usuario"));
        if (usuario.getEstado() != EstadoUsuario.activo) throw bad("Usuario inactivo");
        venta.setUsuario(usuario);
        venta.setCliente(resolverCliente(request.clienteId(), request.cliente()));
        venta.setTipoComprobante(comprobantes.findById(request.tipoComprobanteId()).orElseThrow(() -> missing("Comprobante")));
        validarCliente(venta);
        venta.setNumeroComprobante(request.numeroComprobante().trim());
        venta.setFechaVenta(OffsetDateTime.now());
        agregarLineas(venta, request.items());
        mesa.setEstado(EstadoMesa.ATENDIENDO);
        return ventas.save(venta);
    }

    @Transactional
    public Venta agregarItems(Integer id, List<ItemRequest> items) {
        Venta venta = ventas.findByIdForUpdate(id).orElseThrow(() -> missing("Venta"));
        validarAbierta(venta);
        agregarLineas(venta, items);
        return venta;
    }

    /** No se reserva ni descuenta inventario al pedir. El cobro es la única salida de stock. */
    private void agregarLineas(Venta venta, List<ItemRequest> items) {
        var cantidades = consolidar(items);
        for (var entry : cantidades.entrySet()) {
            Producto producto = productos.findById(entry.getKey()).orElseThrow(() -> missing("Producto"));
            int yaPedido = venta.getDetalles().stream().filter(d -> d.getProducto().getId().equals(producto.getId()))
                    .map(DetalleVenta::getCantidad).reduce(0, this::sumar);
            int cantidadFinal = sumar(yaPedido, entry.getValue());
            if (producto.getStockActual() < cantidadFinal)
                throw new StockInsuficienteException(producto.getNombre(), producto.getStockActual(), cantidadFinal);
            BigDecimal precio = producto.getPrecioVenta();
            var existente = venta.getDetalles().stream().filter(d -> d.getProducto().getId().equals(producto.getId())
                    && d.getPrecioUnitario().compareTo(precio) == 0).findFirst().orElse(null);
            if (existente == null) {
                venta.getDetalles().add(new DetalleVenta(null, venta, producto, entry.getValue(), precio,
                        precio.multiply(BigDecimal.valueOf(entry.getValue()))));
            } else {
                existente.setCantidad(sumar(existente.getCantidad(), entry.getValue()));
                existente.setSubtotal(precio.multiply(BigDecimal.valueOf(existente.getCantidad())));
            }
        }
        BigDecimal subtotal = venta.getDetalles().stream().map(DetalleVenta::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal igv = subtotal.multiply(IGV).setScale(2, RoundingMode.HALF_UP);
        if (subtotal.add(igv).compareTo(MAX_IMPORTE) > 0) throw bad("El importe supera el límite de una venta");
        venta.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        venta.setIgv(igv);
        venta.setTotal(subtotal.add(igv).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Venta -> mesa -> productos por ID -> cliente. El orden estable de los bloqueos
     * evita que dos cajas descuenten el mismo stock o contabilicen dos veces la visita.
     * Cualquier excepción revierte stock, venta, mesa y fidelización juntos.
     */
    @Transactional
    public Venta cerrar(Integer id, MetodoPago metodoPago, BigDecimal montoRecibido) {
        Venta venta = ventas.findByIdForUpdate(id).orElseThrow(() -> missing("Venta"));
        validarAbierta(venta);
        if (metodoPago == null) throw bad("El método de pago es obligatorio");
        if (metodoPago == MetodoPago.efectivo && (montoRecibido == null || montoRecibido.compareTo(venta.getTotal()) < 0))
            throw bad("El monto recibido no puede ser menor que el total");
        Mesa mesa = venta.getMesa() == null ? null : mesas.findByIdForUpdate(venta.getMesa().getId()).orElseThrow(() -> missing("Mesa"));
        var cantidades = new TreeMap<Integer, Integer>();
        venta.getDetalles().forEach(d -> cantidades.merge(d.getProducto().getId(), d.getCantidad(), this::sumar));
        if (cantidades.isEmpty()) throw bad("La comanda no tiene productos");
        for (var entry : cantidades.entrySet()) {
            Producto producto = productos.findById(entry.getKey()).orElseThrow(() -> missing("Producto"));
            // El detalle ya pudo cargar este producto. refresh obtiene su versión y stock
            // después de adquirir el bloqueo, incluso si otra caja acaba de cobrarlo.
            entityManager.refresh(producto, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (producto.getStockActual() < entry.getValue())
                throw new StockInsuficienteException(producto.getNombre(), producto.getStockActual(), entry.getValue());
            producto.setStockActual(producto.getStockActual() - entry.getValue());
        }
        Cliente cliente = clientes.findByIdForUpdate(venta.getCliente().getId()).orElseThrow(() -> missing("Cliente"));
        entityManager.refresh(cliente, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        cliente.setFrecuenciaVisitas(Math.addExact(cliente.getFrecuenciaVisitas(), 1L));
        cliente.setTotalGastado(cliente.getTotalGastado().add(venta.getTotal()));
        venta.setCliente(cliente);
        venta.setMetodoPago(metodoPago);
        venta.setEstado(EstadoVenta.CERRADA);
        venta.setFechaCobro(OffsetDateTime.now());
        if (mesa != null) mesa.setEstado(EstadoMesa.LIBRE);
        return venta;
    }

    private Cliente resolverCliente(Integer id, ClienteRequest data) {
        if (id != null) return clientes.findById(id).orElseThrow(() -> missing("Cliente"));
        if (data == null) throw bad("Los datos del cliente son obligatorios");
        var existing = clientes.findByNumeroDocumento(data.numeroDocumento().trim());
        // No sobreescribir la ficha ni las métricas por un pedido simultáneo o por datos antiguos del POS.
        if (existing.isPresent()) return existing.get();
        var cliente = new Cliente();
        cliente.setNumeroDocumento(data.numeroDocumento().trim());
        cliente.setNombresRazonSocial(data.nombresRazonSocial().trim());
        cliente.setDireccion(data.direccion());
        cliente.setTelefono(data.telefono());
        cliente.setCorreo(data.correo());
        cliente.setFechaNacimiento(data.fechaNacimiento());
        return clientes.save(cliente);
    }
    private void validarCliente(Venta venta) {
        if (venta.getTipoComprobante().getNombre().toUpperCase(Locale.ROOT).contains("FACTURA")
                && (!venta.getCliente().getNumeroDocumento().matches("\\d{11}")
                || venta.getCliente().getDireccion() == null || venta.getCliente().getDireccion().isBlank()))
            throw bad("Para una factura se requiere RUC de 11 dígitos y dirección fiscal");
    }
    private Map<Integer, Integer> consolidar(List<ItemRequest> items) {
        if (items == null || items.isEmpty()) throw bad("La comanda requiere productos");
        var result = new TreeMap<Integer, Integer>();
        for (var item : items) {
            if (item == null || item.productoId() == null || item.cantidad() == null || item.cantidad() < 1)
                throw bad("Cada producto debe tener una cantidad mayor que cero");
            result.merge(item.productoId(), item.cantidad(), this::sumar);
        }
        return result;
    }
    private int sumar(int a, int b) {
        try { return Math.addExact(a, b); }
        catch (ArithmeticException e) { throw bad("La cantidad acumulada es demasiado grande"); }
    }
    private void validarAbierta(Venta venta) {
        if (venta.getEstado() != EstadoVenta.ABIERTA) throw conflict("La venta ya está cerrada");
    }
    private ResponseStatusException missing(String entity) { return new ResponseStatusException(HttpStatus.NOT_FOUND, entity + " no encontrado"); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
```

## `backend/SistemaPOS/src/main/resources/application.properties`

```properties
spring.application.name=SistemaPOS

# PostgreSQL is the default database. Override these values with environment variables in production.
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/pos_db}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:root}
spring.datasource.driver-class-name=${DB_DRIVER:org.postgresql.Driver}

# During development Hibernate checks that the Java model matches the SQL schema.
spring.jpa.hibernate.ddl-auto=${JPA_DDL_AUTO:validate}
spring.jpa.open-in-view=false
spring.jpa.show-sql=${JPA_SHOW_SQL:false}
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

server.port=${SERVER_PORT:9090}

server.servlet.session.timeout=8h
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=lax
server.servlet.session.cookie.secure=${SESSION_COOKIE_SECURE:false}
app.bootstrap.password=${BOOTSTRAP_ADMIN_PASSWORD:}
```

## `backend/SistemaPOS/src/test/java/MiTrampita/SistemaPOS/PosFlowTests.java`

```java
package MiTrampita.SistemaPOS;

import MiTrampita.SistemaPOS.dto.VentaDtos.*;
import MiTrampita.SistemaPOS.entity.*;
import MiTrampita.SistemaPOS.exception.StockInsuficienteException;
import MiTrampita.SistemaPOS.repositorio.*;
import MiTrampita.SistemaPOS.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class PosFlowTests {
    @Autowired MockMvc mvc;
    @Autowired VentaService service;
    @Autowired ProductoService productoService;
    @Autowired MarketingService marketing;
    @Autowired MesaService mesaService;
    @Autowired ProductoRepository productos;
    @Autowired MesaRepository mesas;
    @Autowired ClienteRepository clientes;
    @Autowired VentaRepository ventas;
    @Autowired EmpresaRepository empresas;
    @Autowired UsuarioRepository usuarios;
    @Autowired UsuarioRolRepository relaciones;
    @Autowired RolRepository roles;
    @Autowired CategoriaRepository categorias;
    @Autowired MarcaRepository marcas;
    @Autowired ProveedorRepository proveedores;
    @Autowired AreaRepository areas;
    @Autowired TipoComprobanteRepository comprobantes;
    private static final AtomicInteger sequence = new AtomicInteger(1000);

    record Fixture(Producto primero, Producto segundo, Cliente cliente, Mesa mesa, Empresa empresa, Usuario usuario) { }
    Fixture fixture(int stock) {
        int seq = sequence.incrementAndGet();
        var empresa = new Empresa(); empresa.setRuc("20" + String.format("%09d", seq)); empresa.setRazonSocial("Prueba"); empresa.setDireccion("Prueba");
        empresa = empresas.save(empresa);
        var proveedor = new Proveedor(); proveedor.setRucDni(String.valueOf(seq)); proveedor.setRazonSocial("Prueba"); proveedor = proveedores.save(proveedor);
        var cliente = new Cliente(); cliente.setNumeroDocumento(String.valueOf(10000000 + seq)); cliente.setNombresRazonSocial("Cliente " + seq);
        cliente.setFechaNacimiento(LocalDate.now().minusYears(25)); cliente = clientes.save(cliente);
        var user = user("MOZO");
        var mesa = mesa();
        var p1 = product("P" + seq, stock, proveedor);
        var p2 = product("Q" + seq, stock, proveedor);
        return new Fixture(p1, p2, cliente, mesa, empresa, user);
    }
    Producto product(String codigo, int stock, Proveedor proveedor) {
        var p = new Producto(); p.setCodigoBarras(codigo); p.setNombre(codigo);
        p.setCategoria(categorias.findAll().getFirst()); p.setMarca(marcas.findAll().getFirst()); p.setProveedor(proveedor);
        p.setPrecioVenta(new BigDecimal("10.00")); p.setStockActual(stock); return productos.save(p);
    }
    Mesa mesa() {
        var mesa = new Mesa(); mesa.setNumero(sequence.incrementAndGet()); mesa.setArea(areas.findAll().getFirst());
        return mesas.save(mesa);
    }
    Usuario user(String role) {
        var u = new Usuario(); u.setUsuario("test" + sequence.incrementAndGet()); u.setNombreCompleto("Prueba " + role);
        u.setContrasena("testpass123"); u = usuarios.save(u);
        var ur = new UsuarioRol(); ur.setUsuario(u); ur.setRol(roles.findByNombreIgnoreCase(role).orElseThrow()); relaciones.save(ur);
        return u;
    }
    Venta abrir(Fixture f, Mesa mesa, List<ItemRequest> items) {
        return service.registrar(new VentaRequest(f.empresa().getId(), f.cliente().getId(), null,
            comprobantes.findByNombreIgnoreCaseAndSerie("BOLETA", "B001").orElseThrow().getId(),
            "T-" + UUID.randomUUID(), mesa.getId(), items), f.usuario().getId());
    }
    MockHttpSession login(Usuario u) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"usuario\":\"" + u.getUsuario() + "\",\"contrasena\":\"testpass123\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    @Test void pedidoNoDescuentaYCobroActualizaTodasLasEntidadesUnaVez() {
        var f = fixture(10);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 2), new ItemRequest(f.primero().getId(), 1)));
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(10);
        assertThat(v.getDetalles()).hasSize(1);
        service.agregarItems(v.getId(), List.of(new ItemRequest(f.primero().getId(), 1)));
        service.cerrar(v.getId(), MetodoPago.efectivo, new BigDecimal("100"));
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(6);
        assertThat(mesas.findById(f.mesa().getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.LIBRE);
        var c = clientes.findById(f.cliente().getId()).orElseThrow();
        assertThat(c.getFrecuenciaVisitas()).isEqualTo(1);
        assertThat(c.getTotalGastado()).isEqualByComparingTo("47.20");
        assertThatThrownBy(() -> service.cerrar(v.getId(), MetodoPago.tarjeta, null)).hasMessageContaining("ya está cerrada");
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(6);
        assertThat(marketing.consumo(c.getId()).getFirst().getUnidades()).isEqualTo(4);
    }
    @Test void stockInsuficienteEnSegundaLineaRevierteElPrimerDescuento() {
        var f = fixture(2);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 2), new ItemRequest(f.segundo().getId(), 2)));
        var depleted = productos.findById(f.segundo().getId()).orElseThrow(); depleted.setStockActual(1); productos.save(depleted);
        assertThatThrownBy(() -> service.cerrar(v.getId(), MetodoPago.tarjeta, null)).isInstanceOf(StockInsuficienteException.class);
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(2);
        assertThat(ventas.findById(v.getId()).orElseThrow().getEstado()).isEqualTo(EstadoVenta.ABIERTA);
        assertThat(mesas.findById(f.mesa().getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.ATENDIENDO);
        assertThat(clientes.findById(f.cliente().getId()).orElseThrow().getFrecuenciaVisitas()).isZero();
        assertThat(marketing.consumo(f.cliente().getId())).isEmpty();
    }
    @Test void cobrosSimultaneosNoPermitenStockNegativoNiVisitasDuplicadas() throws Exception {
        var f = fixture(1);
        var first = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        var second = abrir(f, mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> tasks = new ArrayList<>();
            for (var sale : List.of(first, second)) tasks.add(executor.submit(() -> {
                start.await();
                try { service.cerrar(sale.getId(), MetodoPago.tarjeta, null); return true; }
                catch (StockInsuficienteException ex) { return false; }
            }));
            start.countDown();
            int successful = 0;
            for (var task : tasks) if (task.get(20, TimeUnit.SECONDS)) successful++;
            assertThat(successful).isEqualTo(1);
        }
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isZero();
        assertThat(clientes.findById(f.cliente().getId()).orElseThrow().getFrecuenciaVisitas()).isEqualTo(1);
    }
    @Test void efectivoInsuficienteNoModificaStock() {
        var f = fixture(5);
        var v = abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        assertThatThrownBy(() -> service.cerrar(v.getId(), MetodoPago.efectivo, BigDecimal.ONE)).hasMessageContaining("monto recibido");
        assertThat(productos.findById(f.primero().getId()).orElseThrow().getStockActual()).isEqualTo(5);
    }
    @Test void noPermiteDosComandasNiLiberarUnaMesaConPedido() {
        var f = fixture(10);
        abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        assertThatThrownBy(() -> abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)))).hasMessageContaining("comanda abierta");
        assertThatThrownBy(() -> mesaService.liberar(f.mesa().getId())).hasMessageContaining("Cobra la comanda");
    }
    @Test void rolesSeValidanEnServidorYLosCambiosSeAplicanASesionesExistentes() throws Exception {
        mvc.perform(get("/api/mesas")).andExpect(status().isUnauthorized());
        var mozo = user("MOZO"); var session = login(mozo);
        mvc.perform(get("/api/marketing").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/configuracion").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ventas").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ventas/abiertas").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/clientes").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pos/clientes").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].frecuenciaVisitas").isEmpty());
        mvc.perform(patch("/api/ventas/1/cerrar").session(session).with(csrf()).contentType("application/json")
            .content("{\"metodoPago\":\"tarjeta\"}")).andExpect(status().isForbidden());
        mozo = usuarios.findById(mozo.getId()).orElseThrow(); mozo.setEstado(EstadoUsuario.bloqueado); usuarios.save(mozo);
        mvc.perform(get("/api/mesas").session(session)).andExpect(status().isUnauthorized());
        var caja = login(user("CAJA"));
        mvc.perform(post("/api/ventas").session(caja).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/ventas/1/items").session(caja).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/marketing").session(caja)).andExpect(status().isForbidden());
        mvc.perform(get("/api/marketing").session(login(user("ADMIN")))).andExpect(status().isOk());
    }
    @Test void faltaMesaYElementosNulosDevuelven400SinCrearVenta() throws Exception {
        var f = fixture(5);
        var session = login(f.usuario());
        String body = "{\"empresaId\":" + f.empresa().getId() + ",\"clienteId\":" + f.cliente().getId()
            + ",\"tipoComprobanteId\":" + comprobantes.findAll().getFirst().getId() + ",\"numeroComprobante\":\"VALIDAR\",\"items\":[null]}";
        mvc.perform(post("/api/ventas").session(session).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ventas").session(session).contentType("application/json").content(body))
            .andExpect(status().isForbidden());
    }
    @Test void cumpleanerosIncluyeElMesActualYConsumoIgnoraPedidosAbiertos() {
        var f = fixture(5);
        abrir(f, f.mesa(), List.of(new ItemRequest(f.primero().getId(), 1)));
        assertThat(marketing.consumo(f.cliente().getId())).isEmpty();
        assertThat(marketing.cumpleaneros(LocalDate.now().getMonthValue())).extracting(MarketingService.ClienteResumen::id)
                .contains(f.cliente().getId());
    }
    @Test void usuarioEnJsonNoPuedeSuplantarAlMozoAutenticado() throws Exception {
        var f = fixture(5);
        String body = "{\"empresaId\":" + f.empresa().getId() + ",\"usuarioId\":999999,\"clienteId\":" + f.cliente().getId()
            + ",\"tipoComprobanteId\":" + comprobantes.findByNombreIgnoreCaseAndSerie("BOLETA", "B001").orElseThrow().getId()
            + ",\"numeroComprobante\":\"IDENTIDAD-" + sequence.incrementAndGet() + "\",\"mesaId\":" + f.mesa().getId()
            + ",\"items\":[{\"productoId\":" + f.primero().getId() + ",\"cantidad\":1}]}";
        mvc.perform(post("/api/ventas").session(login(f.usuario())).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.usuario.id").value(f.usuario().getId()));
    }

    @Test void editarUnProductoDesactualizadoNoPuedeReponerStockCobrado() {
        var f = fixture(5);
        Producto stale = productos.findById(f.primero().getId()).orElseThrow();
        var sale = abrir(f, f.mesa(), List.of(new ItemRequest(stale.getId(), 2)));
        service.cerrar(sale.getId(), MetodoPago.tarjeta, null);
        stale.setNombre("Nombre editado con datos antiguos");
        assertThatThrownBy(() -> productoService.actualizar(stale.getId(), stale))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(productos.findById(stale.getId()).orElseThrow().getStockActual()).isEqualTo(3);
    }
}
```

## `backend/SistemaPOS/src/test/resources/application-test.properties`

```properties
spring.datasource.url=jdbc:h2:mem:pos_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
app.bootstrap.password=admin123
logging.level.root=WARN
spring.main.banner-mode=off
```

## `database/scripts/04_zonificacion_marketing_roles_mysql.sql`

```sql
-- MySQL 8.0.16+. Ejecutar UNA VEZ, con el backend detenido y respaldo previo.
-- Seleccionar la base en el cliente (mysql ... nombre_base < este_archivo).
-- Base nueva: ejecutar primero 02_TablaMiTrampitaMySQL.sql.
-- MySQL confirma DDL implícitamente: si falla, restaurar el respaldo antes de reintentar.
-- La conversión de stock y métricas sí se confirma en una sola transacción.
DELIMITER $$
CREATE PROCEDURE migrar_pos_04()
BEGIN
    DECLARE legacy_sale INT;
    DECLARE new_table_id INT;
    DECLARE new_number INT;
    DECLARE old_check INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
    CREATE TABLE IF NOT EXISTS pos_migraciones (
        version VARCHAR(80) PRIMARY KEY, aplicada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB;
    IF EXISTS (SELECT 1 FROM pos_migraciones WHERE version = '04_zonificacion_marketing_roles') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La migración 04 ya fue aplicada';
    END IF;
    RENAME TABLE mesa TO mesas, venta TO ventas, cliente TO clientes;
    SELECT COUNT(*) INTO old_check FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE() AND table_name = 'mesas' AND constraint_name = 'ck_mesa_estado';
    IF old_check > 0 THEN ALTER TABLE mesas DROP CHECK ck_mesa_estado; END IF;
    SELECT COUNT(*) INTO old_check FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE() AND table_name = 'mesas' AND constraint_name = 'ck_mesa_numero';
    IF old_check > 0 THEN ALTER TABLE mesas DROP CHECK ck_mesa_numero; END IF;
    ALTER TABLE mesas RENAME COLUMN id_mesa TO id;
    ALTER TABLE mesas RENAME COLUMN numero_mesa TO numero;
    ALTER TABLE mesas RENAME COLUMN estado_mesa TO estado;
    ALTER TABLE ventas RENAME COLUMN id_mesa TO mesa_id;
    -- MySQL no permite CHECK sobre columnas con acciones referenciales CASCADE.
    -- Los IDs de mesa son inmutables; se conserva la FK con RESTRICT.
    ALTER TABLE ventas DROP FOREIGN KEY fk_venta_mesa;
    ALTER TABLE ventas ADD CONSTRAINT fk_venta_mesa FOREIGN KEY (mesa_id) REFERENCES mesas(id);
    CREATE TABLE areas (
        id INT AUTO_INCREMENT PRIMARY KEY, nombre VARCHAR(100) NOT NULL UNIQUE,
        estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
        CONSTRAINT ck_area_nombre CHECK (CHAR_LENGTH(TRIM(nombre)) > 0),
        CONSTRAINT ck_area_estado CHECK (estado IN ('ACTIVA','INACTIVA'))
    ) ENGINE=InnoDB;
    INSERT INTO areas(nombre) VALUES ('Salón Principal'),('Terraza'),('Zona Recreacional'),('Piscina');
    ALTER TABLE mesas ADD COLUMN area_id INT NULL;
    UPDATE mesas SET area_id = (SELECT id FROM areas WHERE nombre = 'Salón Principal');
    ALTER TABLE mesas MODIFY area_id INT NOT NULL,
        ADD CONSTRAINT fk_mesa_area FOREIGN KEY (area_id) REFERENCES areas(id);
    CREATE INDEX idx_mesas_area ON mesas(area_id);
    UPDATE mesas SET estado = 'OCUPADA' WHERE estado = 'RESERVADA';
    ALTER TABLE mesas ADD CONSTRAINT ck_mesa_numero CHECK (numero > 0);
    ALTER TABLE mesas ADD CONSTRAINT ck_mesa_estado CHECK (estado IN ('LIBRE','OCUPADA','ATENDIENDO'));

    SET new_number = 1;
    WHILE new_number <= 12 DO
        IF NOT EXISTS (SELECT 1 FROM mesas WHERE numero = new_number) THEN
            INSERT INTO mesas(numero,capacidad,estado,area_id)
            SELECT new_number,4,'LIBRE',id FROM areas WHERE nombre = CASE
                WHEN new_number <= 3 THEN 'Salón Principal' WHEN new_number <= 6 THEN 'Terraza'
                WHEN new_number <= 9 THEN 'Zona Recreacional' ELSE 'Piscina' END;
        END IF;
        SET new_number = new_number + 1;
    END WHILE;
    WHILE EXISTS (SELECT 1 FROM ventas WHERE estado_venta = 'ABIERTA' AND mesa_id IS NULL) DO
        SELECT MIN(id_venta) INTO legacy_sale FROM ventas WHERE estado_venta = 'ABIERTA' AND mesa_id IS NULL;
        SELECT COALESCE(MAX(numero),0) + 1 INTO new_number FROM mesas;
        INSERT INTO mesas(numero,capacidad,estado,area_id)
            SELECT new_number,4,'OCUPADA',id FROM areas WHERE nombre = 'Salón Principal';
        SET new_table_id = LAST_INSERT_ID();
        UPDATE ventas SET mesa_id = new_table_id WHERE id_venta = legacy_sale;
    END WHILE;
    UPDATE mesas m SET estado = 'ATENDIENDO'
        WHERE EXISTS (SELECT 1 FROM ventas v WHERE v.mesa_id = m.id AND v.estado_venta = 'ABIERTA');
    ALTER TABLE ventas ADD CONSTRAINT ck_venta_mesa_abierta CHECK (estado_venta <> 'ABIERTA' OR mesa_id IS NOT NULL);
    -- Una única comanda ABIERTA por mesa; los NULL de ventas cerradas no colisionan.
    ALTER TABLE ventas ADD COLUMN mesa_abierta_id INT GENERATED ALWAYS AS
        (CASE WHEN estado_venta = 'ABIERTA' THEN mesa_id ELSE NULL END) STORED,
        ADD UNIQUE KEY uk_venta_mesa_abierta (mesa_abierta_id);
    ALTER TABLE clientes ADD COLUMN fecha_nacimiento DATE NULL,
        ADD COLUMN frecuencia_visitas BIGINT NOT NULL DEFAULT 0,
        ADD COLUMN total_gastado DECIMAL(19,2) NOT NULL DEFAULT 0,
        ADD CONSTRAINT ck_cliente_metricas CHECK (frecuencia_visitas >= 0 AND total_gastado >= 0);
    CREATE INDEX idx_cliente_frecuencia ON clientes(frecuencia_visitas,total_gastado);
    CREATE INDEX idx_cliente_nacimiento ON clientes(fecha_nacimiento);
    ALTER TABLE ventas ADD COLUMN fecha_cobro TIMESTAMP NULL;
    ALTER TABLE usuario MODIFY estado VARCHAR(20) NOT NULL DEFAULT 'activo',
        ADD CONSTRAINT ck_usuario_estado CHECK (estado IN ('activo','inactivo','bloqueado'));
    ALTER TABLE ventas MODIFY metodo_pago VARCHAR(20) NOT NULL DEFAULT 'efectivo',
        ADD CONSTRAINT ck_venta_metodo_pago CHECK (metodo_pago IN ('efectivo','tarjeta','transferencia','yape_plin'));

    ALTER TABLE producto ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
    START TRANSACTION;
    UPDATE ventas SET fecha_cobro = fecha_venta WHERE estado_venta = 'CERRADA';
    UPDATE producto p JOIN (
        SELECT d.id_producto,SUM(d.cantidad) cantidad FROM detalle_venta d JOIN ventas v ON v.id_venta=d.id_venta
        WHERE v.estado_venta='ABIERTA' GROUP BY d.id_producto
    ) x ON x.id_producto=p.id_producto SET p.stock_actual=p.stock_actual+x.cantidad;
    UPDATE clientes c JOIN (
        SELECT id_cliente,COUNT(*) visitas,SUM(total) gastado FROM ventas WHERE estado_venta='CERRADA' GROUP BY id_cliente
    ) x ON x.id_cliente=c.id_cliente SET c.frecuencia_visitas=x.visitas,c.total_gastado=x.gastado;
    INSERT INTO rol(nombre_rol,descripcion) VALUES
        ('ADMIN','Administración completa'),('MOZO','Mesas, pedidos y comandas'),('CAJA','Cobros y comprobantes')
        ON DUPLICATE KEY UPDATE nombre_rol=VALUES(nombre_rol);
    INSERT INTO usuario_rol(id_usuario,id_rol)
    SELECT DISTINCT ur.id_usuario,canonical.id_rol FROM usuario_rol ur JOIN rol legacy ON legacy.id_rol=ur.id_rol
    JOIN rol canonical ON canonical.nombre_rol=CASE UPPER(TRIM(legacy.nombre_rol))
        WHEN 'ADMINISTRADOR' THEN 'ADMIN' WHEN 'VENTAS' THEN 'MOZO' WHEN 'VENDEDOR' THEN 'MOZO'
        WHEN 'MOZO/VENTAS' THEN 'MOZO' WHEN 'CAJERO' THEN 'CAJA' ELSE UPPER(TRIM(legacy.nombre_rol)) END
    WHERE canonical.nombre_rol IN ('ADMIN','MOZO','CAJA')
    ON DUPLICATE KEY UPDATE id_usuario=VALUES(id_usuario);
    INSERT INTO pos_migraciones(version) VALUES ('04_zonificacion_marketing_roles');
    COMMIT;
END$$
DELIMITER ;
CALL migrar_pos_04();
DROP PROCEDURE migrar_pos_04;
```

## `database/scripts/04_zonificacion_marketing_roles_postgresql.sql`

```sql
-- PostgreSQL 14+. Ejecutar UNA VEZ, con el backend detenido.
-- Base nueva: ejecutar primero 01_TablaMiTrampitaPostgreSQL.sql.
-- Base antigua sin mesa/estado_venta: ejecutar primero 03_migracion_estado_venta_postgresql.sql.
-- Conserva IDs, relaciones e históricos. Toda la migración es atómica.
BEGIN;
CREATE TABLE IF NOT EXISTS pos_migraciones (
    version VARCHAR(80) PRIMARY KEY,
    aplicada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pos_migraciones WHERE version = '04_zonificacion_marketing_roles') THEN
        RAISE EXCEPTION 'La migración 04 ya fue aplicada';
    END IF;
END $$;
LOCK TABLE mesa, venta, detalle_venta, producto, cliente IN ACCESS EXCLUSIVE MODE;

ALTER TABLE mesa RENAME TO mesas;
ALTER TABLE mesas RENAME COLUMN id_mesa TO id;
ALTER TABLE mesas RENAME COLUMN numero_mesa TO numero;
ALTER TABLE mesas RENAME COLUMN estado_mesa TO estado;
ALTER TABLE venta RENAME TO ventas;
ALTER TABLE ventas RENAME COLUMN id_mesa TO mesa_id;
ALTER TABLE cliente RENAME TO clientes;

CREATE TABLE areas (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    CONSTRAINT uk_area_nombre UNIQUE (nombre),
    CONSTRAINT ck_area_nombre CHECK (length(trim(nombre)) > 0),
    CONSTRAINT ck_area_estado CHECK (estado IN ('ACTIVA', 'INACTIVA'))
);
INSERT INTO areas(nombre) VALUES ('Salón Principal'), ('Terraza'), ('Zona Recreacional'), ('Piscina');
CREATE UNIQUE INDEX uk_area_nombre_ci ON areas(lower(nombre));
ALTER TABLE mesas ADD COLUMN area_id INT;
UPDATE mesas SET area_id = (SELECT id FROM areas WHERE nombre = 'Salón Principal');
ALTER TABLE mesas ALTER COLUMN area_id SET NOT NULL;
ALTER TABLE mesas ADD CONSTRAINT fk_mesa_area FOREIGN KEY (area_id) REFERENCES areas(id);
CREATE INDEX idx_mesas_area ON mesas(area_id);
ALTER TABLE mesas DROP CONSTRAINT IF EXISTS ck_mesa_estado;
UPDATE mesas SET estado = 'OCUPADA' WHERE estado = 'RESERVADA';
ALTER TABLE mesas ADD CONSTRAINT ck_mesa_estado CHECK (estado IN ('LIBRE', 'OCUPADA', 'ATENDIENDO'));

-- Semillas: conserva los números existentes y añade solo los ausentes.
INSERT INTO mesas(numero, capacidad, estado, area_id)
SELECT n, 4, 'LIBRE', a.id
FROM generate_series(1, 12) n
JOIN areas a ON a.nombre = CASE WHEN n <= 3 THEN 'Salón Principal'
    WHEN n <= 6 THEN 'Terraza' WHEN n <= 9 THEN 'Zona Recreacional' ELSE 'Piscina' END
ON CONFLICT (numero) DO NOTHING;

-- Las antiguas comandas de mostrador reciben una mesa propia para poder cobrarse.
DO $$
DECLARE antigua RECORD; nuevo_id INT; nuevo_numero INT;
BEGIN
    FOR antigua IN SELECT id_venta FROM ventas WHERE estado_venta = 'ABIERTA' AND mesa_id IS NULL ORDER BY id_venta LOOP
        SELECT COALESCE(MAX(numero), 0) + 1 INTO nuevo_numero FROM mesas;
        INSERT INTO mesas(numero, capacidad, estado, area_id)
        VALUES (nuevo_numero, 4, 'OCUPADA', (SELECT id FROM areas WHERE nombre = 'Salón Principal')) RETURNING id INTO nuevo_id;
        UPDATE ventas SET mesa_id = nuevo_id WHERE id_venta = antigua.id_venta;
    END LOOP;
END $$;
UPDATE mesas m SET estado = 'ATENDIENDO'
WHERE EXISTS (SELECT 1 FROM ventas v WHERE v.mesa_id = m.id AND v.estado_venta = 'ABIERTA');
ALTER TABLE ventas ADD CONSTRAINT ck_venta_mesa_abierta CHECK (estado_venta <> 'ABIERTA' OR mesa_id IS NOT NULL);
CREATE UNIQUE INDEX uk_venta_mesa_abierta ON ventas(mesa_id) WHERE estado_venta = 'ABIERTA';

ALTER TABLE clientes ADD COLUMN fecha_nacimiento DATE;
ALTER TABLE clientes ADD COLUMN frecuencia_visitas BIGINT NOT NULL DEFAULT 0;
ALTER TABLE clientes ADD COLUMN total_gastado NUMERIC(19,2) NOT NULL DEFAULT 0;
ALTER TABLE clientes ADD CONSTRAINT ck_cliente_metricas CHECK (frecuencia_visitas >= 0 AND total_gastado >= 0);
CREATE INDEX idx_cliente_frecuencia ON clientes(frecuencia_visitas DESC, total_gastado DESC);
CREATE INDEX idx_cliente_mes_nacimiento ON clientes(EXTRACT(MONTH FROM fecha_nacimiento));
ALTER TABLE ventas ADD COLUMN fecha_cobro TIMESTAMPTZ;
UPDATE ventas SET fecha_cobro = fecha_venta WHERE estado_venta = 'CERRADA';

-- Conversión a VARCHAR para que el mismo modelo JPA funcione en PostgreSQL y MySQL.
ALTER TABLE usuario ALTER COLUMN estado DROP DEFAULT;
ALTER TABLE usuario ALTER COLUMN estado TYPE VARCHAR(20) USING estado::text;
ALTER TABLE usuario ALTER COLUMN estado SET DEFAULT 'activo';
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_estado CHECK (estado IN ('activo','inactivo','bloqueado'));
ALTER TABLE ventas ALTER COLUMN metodo_pago DROP DEFAULT;
ALTER TABLE ventas ALTER COLUMN metodo_pago TYPE VARCHAR(20) USING metodo_pago::text;
ALTER TABLE ventas ALTER COLUMN metodo_pago SET DEFAULT 'efectivo';
ALTER TABLE ventas ADD CONSTRAINT ck_venta_metodo_pago CHECK (metodo_pago IN ('efectivo','tarjeta','transferencia','yape_plin'));

-- El backend anterior descontaba al pedir: revertir SOLO las comandas abiertas.
ALTER TABLE producto ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE producto p SET stock_actual = p.stock_actual + x.cantidad
FROM (
    SELECT d.id_producto, SUM(d.cantidad)::INT AS cantidad FROM detalle_venta d
    JOIN ventas v ON v.id_venta = d.id_venta WHERE v.estado_venta = 'ABIERTA' GROUP BY d.id_producto
) x WHERE p.id_producto = x.id_producto;

UPDATE clientes c SET frecuencia_visitas = x.visitas, total_gastado = x.gastado
FROM (SELECT id_cliente, COUNT(*) AS visitas, SUM(total) AS gastado FROM ventas
      WHERE estado_venta = 'CERRADA' GROUP BY id_cliente) x WHERE c.id_cliente = x.id_cliente;
INSERT INTO rol(nombre_rol, descripcion) VALUES
    ('ADMIN', 'Administración completa'), ('MOZO', 'Mesas, pedidos y comandas'), ('CAJA', 'Cobros, comprobantes y liberación de mesas')
ON CONFLICT (nombre_rol) DO NOTHING;
-- Preservar permisos de roles antiguos mediante asignaciones canónicas.
INSERT INTO usuario_rol(id_usuario,id_rol)
SELECT DISTINCT ur.id_usuario, canonical.id_rol
FROM usuario_rol ur JOIN rol legacy ON legacy.id_rol = ur.id_rol
JOIN rol canonical ON canonical.nombre_rol = CASE upper(trim(legacy.nombre_rol))
    WHEN 'ADMINISTRADOR' THEN 'ADMIN' WHEN 'VENTAS' THEN 'MOZO' WHEN 'VENDEDOR' THEN 'MOZO'
    WHEN 'MOZO/VENTAS' THEN 'MOZO' WHEN 'CAJERO' THEN 'CAJA' ELSE upper(trim(legacy.nombre_rol)) END
WHERE canonical.nombre_rol IN ('ADMIN','MOZO','CAJA')
ON CONFLICT (id_usuario,id_rol) DO NOTHING;
INSERT INTO pos_migraciones(version) VALUES ('04_zonificacion_marketing_roles');
COMMIT;
```

## `frontend/src/App.jsx`

```jsx
import { useEffect, useMemo, useState } from 'react'
import {
  Archive, ArrowRight, BadgeDollarSign, BarChart3, Boxes, BriefcaseBusiness, ChevronRight,
  CircleUserRound, ClipboardList, Command, LayoutDashboard, LogOut, Menu, Package,
  Plus, ReceiptText, Search, Settings2, ShieldCheck, Store, Tags, Truck, UserRound,
  UsersRound, X, Zap, Minus, ShoppingCart, CheckCircle2, Pencil, Trash2
} from 'lucide-react'
import { api } from './api'
import SalesPos from './SalesPos'
import MarketingDashboard from './MarketingDashboard'
import { permissions, canView } from './permissions'

const resources = {
  productos: { label: 'Productos', singular: 'producto', endpoint: '/api/productos', icon: Package, columns: [['nombre', 'Producto'], ['codigoBarras', 'Código'], ['precioVenta', 'Precio'], ['stockActual', 'Stock']] },
  clientes: { label: 'Clientes', singular: 'cliente', endpoint: '/api/clientes', icon: UsersRound, columns: [['numeroDocumento', 'Documento'], ['nombresRazonSocial', 'Nombre'], ['telefono', 'Teléfono'], ['correo', 'Correo']] },
  proveedores: { label: 'Proveedores', singular: 'proveedor', endpoint: '/api/proveedores', icon: Truck, columns: [['rucDni', 'RUC / DNI'], ['razonSocial', 'Razón social'], ['telefono', 'Teléfono'], ['correo', 'Correo']] },
  categorias: { label: 'Categorías', singular: 'categoría', endpoint: '/api/categorias', icon: Tags, columns: [['nombre', 'Nombre'], ['descripcion', 'Descripción']] },
  marcas: { label: 'Marcas', singular: 'marca', endpoint: '/api/marcas', icon: Archive, columns: [['nombre', 'Nombre']] },
  usuarios: { label: 'Usuarios', singular: 'usuario', endpoint: '/api/usuarios', icon: UserRound, columns: [['usuario', 'Usuario'], ['nombreCompleto', 'Nombre'], ['correoElectronico', 'Correo'], ['estado', 'Estado'], ['roles', 'Roles']] },
  roles: { readOnly: true, label: 'Roles', singular: 'rol', endpoint: '/api/roles', icon: ShieldCheck, columns: [['nombre', 'Rol'], ['descripcion', 'Descripción']] },
  configuracion: { label: 'Configuración', singular: 'configuración fiscal', endpoint: '/api/configuracion', icon: BriefcaseBusiness, columns: [['ruc', 'RUC'], ['razonSocial', 'Razón social'], ['telefono', 'Teléfono']] },
  'tipos-comprobante': { label: 'Comprobantes', singular: 'tipo de comprobante', endpoint: '/api/tipos-comprobante', icon: ReceiptText, columns: [['nombre', 'Tipo'], ['serie', 'Serie']] },
  areas: { label: 'Áreas', singular: 'área', endpoint: '/api/areas', icon: Store, columns: [['nombre', 'Nombre'], ['estado', 'Estado']] },
  mesas: { label: 'Mesas', singular: 'mesa', endpoint: '/api/mesas', icon: Store, columns: [['numero', 'Mesa'], ['area', 'Área'], ['capacidad', 'Capacidad'], ['estado', 'Estado']] }
}

const navGroups = [
  { title: 'Operación', items: [['ventas', 'Mesas y ventas', ReceiptText]] },
  { title: 'Gestión', reqAdmin: true, items: [['dashboard', 'Inicio', LayoutDashboard], ['marketing', 'Marketing', BarChart3], ['areas', 'Áreas', Store], ['mesas', 'Mesas', Store], ['productos', 'Productos', Package]] },
  { title: 'Directorio', reqAdmin: true, items: [['clientes', 'Clientes', UsersRound], ['proveedores', 'Proveedores', Truck], ['categorias', 'Categorías', Tags], ['marcas', 'Marcas', Archive]] },
  { title: 'Administración', reqAdmin: true, items: [['usuarios', 'Usuarios', UserRound], ['roles', 'Roles', ShieldCheck], ['configuracion', 'Configuración', Settings2], ['tipos-comprobante', 'Comprobantes', ReceiptText]] },
]

function App() {
  const [session, setSession] = useState(null)
  const [checking, setChecking] = useState(true)
  const [view, setView] = useState('ventas')
  const [mobileNav, setMobileNav] = useState(false)
  useEffect(() => {
    localStorage.removeItem('pos-session')
    api.list('/api/auth/me').then(setSession).catch(() => setSession(null)).finally(() => setChecking(false))
    const expired = () => setSession(null)
    window.addEventListener('pos-session-expired', expired)
    return () => window.removeEventListener('pos-session-expired', expired)
  }, [])
  if (checking) return <div className="loading">Verificando sesión…</div>
  if (!session) return <Login onLogin={(data) => { setSession(data); setView(permissions(data).isAdmin ? 'dashboard' : 'ventas') }} />
  const safeView = canView(session, view) ? view : 'ventas'
  return <Shell session={session} view={safeView} setView={(next) => { if (canView(session, next)) setView(next); setMobileNav(false) }} mobileNav={mobileNav} setMobileNav={setMobileNav}
    onLogout={async () => { try { await api.create('/api/auth/logout', {}); setSession(null) } catch (err) { window.alert(err.message) } }} />
}

function Login({ onLogin }) {
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  
  async function submit(event) {
    event.preventDefault()
    if (!name.trim() || !password.trim()) return setError('Ingresa tu usuario y contraseña.')
    setLoading(true)
    setError('')
    try {
      const res = await api.create('/api/auth/login', { usuario: name.trim(), contrasena: password })
      onLogin(res)
    } catch(e) {
      setError(e.message || 'Credenciales inválidas o error de conexión.')
    } finally {
      setLoading(false)
    }
  }
  
  return <main className="login-page">
    <section className="login-art"><div className="brand-mark"><Store size={20} /> MI TRAMPITA</div><div className="login-quote"><span>01 / POS</span><h1>MI TRAMPITA<br /><em>Trabajo con ritmo.</em></h1><p>Todo para que tu tienda avance.</p></div><div className="art-grid" /></section>
    <section className="login-panel"><div className="login-form"><div className="mobile-brand"><Store size={20} /> MI TRAMPITA</div><span className="eyebrow">Punto de venta</span><h2>Bienvenido de vuelta</h2><p className="muted">Accede a tu espacio de trabajo.</p><form onSubmit={submit}><label>Usuario<input autoFocus value={name} onChange={(event) => setName(event.target.value)} placeholder="Tu nombre de usuario" /></label><label style={{marginTop: '15px'}}>Contraseña<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Tu contraseña" /></label>{error && <div className="form-error">{error}</div>}<button className="primary-button full" type="submit" disabled={loading}>{loading ? 'Iniciando sesión...' : 'Entrar'} <ArrowRight size={18} /></button></form><p className="login-note"><Zap size={14} /> Autenticación en vivo contra la base de datos PostgreSQL.</p></div><span className="login-footer">Mi Trampita · 2026</span></section>
  </main>
}

function Shell({ session, view, setView, mobileNav, setMobileNav, onLogout }) {
  const { isAdmin } = permissions(session);

  return <div className="app-shell"><aside className={`sidebar ${mobileNav ? 'open' : ''}`}><div className="sidebar-top"><div className="brand-mark dark"><Store size={19} /> MI TRAMPITA</div><button className="icon-button close-nav" onClick={() => setMobileNav(false)} aria-label="Cerrar menú"><X size={20} /></button></div><div className="workspace"><span className="workspace-dot" /><span>Mi tienda</span><ChevronRight size={14} /></div><nav>{navGroups.map((group) => {
    if (group.reqAdmin && !isAdmin) return null;
    return <div className="nav-group" key={group.title}><span className="nav-label">{group.title}</span>{group.items.map(([key, label, Icon]) => <button className={`nav-item ${view === key ? 'active' : ''}`} key={key} onClick={() => setView(key)}><Icon size={17} /><span>{label}</span>{key === 'ventas' && <span className="nav-badge">POS</span>}</button>)}</div>
  })}</nav><div className="sidebar-bottom">
  {isAdmin && <button className="nav-item" onClick={() => setView('configuracion')}><Settings2 size={17} /><span>Configuración</span></button>}
  <div className="user-card"><div className="avatar">{session.nombreCompleto?.charAt(0).toUpperCase() || 'U'}</div><div><strong title={session.nombreCompleto}>{session.usuario}</strong><small>{isAdmin ? 'Administrador' : session.roles?.includes('CAJA') ? 'Caja' : 'Mozo / Ventas'}</small></div><button className="icon-button" onClick={onLogout} title="Cerrar sesión"><LogOut size={16} /></button></div></div></aside>{mobileNav && <button className="scrim" onClick={() => setMobileNav(false)} aria-label="Cerrar menú" />}<main className="main-content"><header className="topbar"><button className="icon-button menu-button" onClick={() => setMobileNav(true)} aria-label="Abrir menú"><Menu size={21} /></button><div className="crumb"><Command size={16} /><span>/</span><strong>{view === 'dashboard' ? 'Inicio' : view === 'ventas' ? 'Mesas y ventas' : view === 'marketing' ? 'Marketing' : resources[view]?.label}</strong></div><div className="top-actions"><span className="connection"><span /> API conectada</span><button className="icon-button" title="Perfil" onClick={() => { if(isAdmin) setView('usuarios') }}><CircleUserRound size={19} /></button></div></header><div className="content">{view === 'dashboard' ? <Dashboard setView={setView} /> : view === 'ventas' ? <Sales session={session} /> : view === 'marketing' && isAdmin ? <MarketingDashboard /> : isAdmin && resources[view] ? <ResourceView key={view} resource={resources[view]} /> : null}</div></main></div>
}

function Dashboard({ setView }) { return <><DashboardLegacy setView={setView} /><RecentSales /></> }

function RecentSales() {
  const [sales, setSales] = useState([])
  const [error, setError] = useState('')
  useEffect(() => { api.list('/api/ventas').then(setSales).catch((err) => setError(err.message)) }, [])
  const recent = sales.filter(sale => sale.estado === 'CERRADA').sort((a, b) => new Date(b.fechaVenta || 0) - new Date(a.fechaVenta || 0)).slice(0, 5)
  return <section className="panel recent-panel"><div className="panel-head"><div><span className="eyebrow">Actividad</span><h3>Últimas ventas</h3></div><ReceiptText size={20} className="accent-icon" /></div>{error ? <div className="api-error">No se pudo cargar la actividad.</div> : recent.length ? <div className="recent-sales">{recent.map((sale) => <div className="recent-sale" key={sale.id}><span className="recent-sale-icon"><ReceiptText size={15} /></span><div><strong>{sale.tipoComprobante?.nombre || 'Comprobante'} · {sale.numeroComprobante}</strong><small>{sale.cliente?.nombresRazonSocial || 'Cliente'} · {sale.fechaVenta ? new Date(sale.fechaVenta).toLocaleString('es-PE') : 'Fecha pendiente'}</small></div><b>S/ {Number(sale.total || 0).toFixed(2)}</b></div>)}</div> : <EmptyState text="Todavía no hay ventas registradas." />}</section>
}

function DashboardLegacy({ setView }) {
  const [products, setProducts] = useState([])
  const [sales, setSales] = useState([])
  useEffect(() => { api.list('/api/productos').then(setProducts).catch(() => setProducts([])); api.list('/api/ventas').then(setSales).catch(() => setSales([])) }, [])
  const lowStock = products.filter((item) => item.stockActual <= item.stockMinimo).length
  return <><div className="page-heading"><div><span className="eyebrow">Hoy</span><h1>Buen día, <em>equipo.</em></h1><p className="muted">Una mirada rápida a tu operación.</p></div><button className="primary-button" onClick={() => setView('ventas')}><Plus size={17} /> Nueva venta</button></div><div className="metric-grid"><Metric label="Ventas registradas" value={sales.filter(sale => sale.estado === 'CERRADA').length} detail="Desde el backend" icon={BadgeDollarSign} tone="green" /><Metric label="Productos" value={products.length} detail="Catálogo activo" icon={Package} tone="yellow" /><Metric label="Stock por revisar" value={lowStock} detail="Bajo mínimo" icon={Boxes} tone="orange" /><Metric label="Operación" value="Activa" detail="localhost:9090" icon={BarChart3} tone="blue" /></div><div className="dashboard-grid"><section className="panel spotlight"><div className="panel-head"><div><span className="eyebrow">Acciones rápidas</span><h3>¿Qué quieres hacer?</h3></div><Zap size={20} className="accent-icon" /></div><div className="quick-grid"><QuickAction icon={ReceiptText} title="Registrar venta" detail="Cobrar y actualizar stock" onClick={() => setView('ventas')} /><QuickAction icon={Package} title="Nuevo producto" detail="Añadir al catálogo" onClick={() => setView('productos')} /><QuickAction icon={UsersRound} title="Nuevo cliente" detail="Crear ficha de cliente" onClick={() => setView('clientes')} /><QuickAction icon={Truck} title="Ver proveedores" detail="Consultar directorio" onClick={() => setView('proveedores')} /></div></section><section className="panel stock-panel"><div className="panel-head"><div><span className="eyebrow">Inventario</span><h3>Stock bajo</h3></div><button className="text-button" onClick={() => setView('productos')}>Ver todo <ArrowRight size={14} /></button></div>{products.filter((item) => item.stockActual <= item.stockMinimo).slice(0, 4).map((item) => <div className="stock-row" key={item.id}><div className="product-symbol"><Package size={15} /></div><div><strong>{item.nombre}</strong><small>{item.codigoBarras}</small></div><span className="stock-value">{item.stockActual} <small>/ {item.stockMinimo}</small></span></div>)}{lowStock === 0 && <EmptyState text="Todo el inventario está en orden." />}</section></div></>
}

function Metric({ label, value, detail, icon: Icon, tone }) { return <div className="metric"><div className={`metric-icon ${tone}`}><Icon size={19} /></div><div><span>{label}</span><strong>{value}</strong><small>{detail}</small></div></div> }
function QuickAction({ icon: Icon, title, detail, onClick }) { return <button className="quick-action" onClick={onClick}><span className="quick-icon"><Icon size={18} /></span><span><strong>{title}</strong><small>{detail}</small></span><ArrowRight size={16} /></button> }

function ResourceView({ resource }) {
  const [items, setItems] = useState([])
  const [query, setQuery] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = () => { setLoading(true); api.list(resource.endpoint).then(setItems).catch((err) => setError(err.message)).finally(() => setLoading(false)) }
  useEffect(load, [resource.endpoint])
  const filtered = useMemo(() => items.filter((item) => JSON.stringify(item).toLowerCase().includes(query.toLowerCase())), [items, query])
  const openNew = () => { setEditing(null); setShowForm(true) }
  const openEdit = (item) => { setEditing(item); setShowForm(true) }
  return <><div className="page-heading compact"><div><span className="eyebrow">Directorio / {resource.label}</span><h1>{resource.label}</h1><p className="muted">Gestiona la información conectada a tu base de datos.</p></div>{!resource.readOnly && <button className="primary-button" onClick={openNew}><Plus size={17} /> Nuevo {resource.singular}</button>}</div><section className="panel table-panel"><div className="table-toolbar"><div className="search-box"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={`Buscar ${resource.label.toLowerCase()}...`} /></div><span className="result-count">{filtered.length} registros</span></div>{error && <div className="api-error">{error}</div>}{loading ? <div className="loading">Cargando datos...</div> : <DataTable resource={resource} items={filtered} onRefresh={load} onEdit={openEdit} onError={setError} />}</section>{showForm && <ResourceForm key={`${resource.endpoint}-${editing?.id || 'new'}`} resource={resource} item={editing} onClose={() => setShowForm(false)} onSaved={() => { setShowForm(false); setEditing(null); load() }} />}</>
}

function DataTable({ resource, items, onRefresh, onEdit, onError }) { return items.length ? <div className="table-wrap"><table><thead><tr>{resource.columns.map(([, label]) => <th key={label}>{label}</th>)}{!resource.readOnly && <th>Acciones</th>}</tr></thead><tbody>{items.map((item) => <tr key={item.id}><>{resource.columns.map(([key]) => <td key={key}>{key.includes('precio') ? 'S/ ' + Number(item[key] || 0).toFixed(2) : (Array.isArray(item[key]) ? item[key].join(', ') : typeof item[key] === 'object' && item[key] !== null ? item[key].nombre || item[key].razonSocial || item[key].nombreCategoria || item[key].nombreMarca || 'Objeto' : item[key] ?? '—')}</td>)}</>{!resource.readOnly && <td className="row-actions"><button className="row-action" onClick={() => onEdit(item)} title={`Modificar ${resource.singular}`}><Pencil size={15} /></button><button className="row-action danger" onClick={() => { if (confirm(`¿Eliminar este ${resource.singular}? Esta acción no se puede deshacer.`)) api.remove(`${resource.endpoint}/${item.id}`).then(onRefresh).catch((err) => onError(err.message || 'No se pudo eliminar el registro.')) }} title={`Eliminar ${resource.singular}`}><Trash2 size={15} /></button></td>}</tr>)}</tbody></table></div> : <EmptyState text="No hay registros para mostrar." /> }
function EmptyState({ text }) { return <div className="empty"><ClipboardList size={22} /><span>{text}</span></div> }

function ResourceForm({ resource, item, onClose, onSaved }) {
  const initialForm = useMemo(() => {
    let base
    switch (resource.label) {
      case 'Categorías': base = { nombre: '', descripcion: '' }; break
      case 'Marcas': base = { nombre: '' }; break
      case 'Clientes': base = { numeroDocumento: '', nombresRazonSocial: '', direccion: '', telefono: '', correo: '', fechaNacimiento: '' }; break
      case 'Proveedores': base = { rucDni: '', razonSocial: '', telefono: '', correo: '' }; break
      case 'Usuarios': base = { usuario: '', contrasena: '', nombreCompleto: '', correoElectronico: '', estado: 'activo', rolIds: [] }; break
      case 'Roles': base = { nombre: '', descripcion: '' }; break
      case 'Configuración': base = { ruc: '', razonSocial: '', nombreComercial: '', direccion: '', telefono: '', correo: '' }; break
      case 'Comprobantes': base = { nombre: '', serie: '', descripcion: '' }; break
      case 'Áreas': base = { nombre: '', estado: 'ACTIVA' }; break
      case 'Mesas': base = { numero: '', capacidad: 4, areaId: '' }; break
      case 'Productos': base = { categoriaId: '', marcaId: '', proveedorId: '', codigoBarras: '', nombre: '', descripcion: '', precioCompra: 0, precioVenta: 0, stockActual: 0, stockMinimo: 5 }; break
      default: base = {}
    }
    if (!item) return base
    const values = { ...base }
    Object.keys(base).forEach((field) => { if (field !== 'contrasena') values[field] = item[field] ?? '' })
    if (resource.label === 'Productos') {
      values.categoriaId = item.categoria?.id ?? ''
      values.marcaId = item.marca?.nombre?.toLowerCase() === 'sin marca' ? '' : (item.marca?.id ?? '')
      values.proveedorId = item.proveedor?.id ?? ''
    }
    if (resource.label === 'Mesas') values.areaId = item.area?.id || ''
    return values
  }, [resource.label, item]);

  const [form, setForm] = useState(initialForm)
  const [error, setError] = useState('')
  const [relations, setRelations] = useState({ categorias: [], marcas: [], proveedores: [], areas: [], roles: [] })

  useEffect(() => {
    if (resource.label === 'Productos') {
      Promise.all([
        api.list('/api/categorias'),
        api.list('/api/marcas'),
        api.list('/api/proveedores')
      ]).then(([c, m, p]) => setRelations(current => ({ ...current, categorias: c, marcas: m, proveedores: p }))).catch(err => setError(err.message))
    }
    if (resource.label === 'Mesas') api.list('/api/areas').then(areas => setRelations(current => ({ ...current, areas }))).catch(err => setError(err.message))
    if (resource.label === 'Usuarios') api.list('/api/roles').then(roles => setRelations(current => ({ ...current, roles: roles.filter(role => ['ADMIN', 'MOZO', 'CAJA'].includes(role.nombre)) }))).catch(err => setError(err.message))
  }, [resource.label])

  const update = (key, value) => setForm((current) => ({ ...current, [key]: value }))
  
  async function submit(event) {
    event.preventDefault()
    setError('')
    const requiredText = ['nombre', 'codigoBarras', 'nombresRazonSocial', 'numeroDocumento', 'rucDni', 'razonSocial', 'ruc', 'usuario', 'nombreCompleto', 'serie']
    if (resource.label === 'Configuración') requiredText.push('direccion')
    if (!item && 'contrasena' in form) requiredText.push('contrasena')
    const missing = requiredText.find((field) => field in form && !String(form[field]).trim())
    if (missing) return setError('Completa todos los campos obligatorios.')
    if ('contrasena' in form && form.contrasena && form.contrasena.length < 8) return setError('La contraseña debe tener al menos 8 caracteres.')
    if ('correo' in form && form.correo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.correo)) return setError('Ingresa un correo válido.')
    if ('correoElectronico' in form && form.correoElectronico && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.correoElectronico)) return setError('Ingresa un correo válido.')
    if (resource.label === 'Usuarios' && !form.rolIds.length) return setError('Selecciona al menos un rol.')
    if (resource.label === 'Mesas' && !form.areaId) return setError('Selecciona el área de la mesa.')
    const numericFields = ['precioCompra', 'precioVenta', 'stockActual', 'stockMinimo', 'numero', 'capacidad']
    if (numericFields.some((field) => field in form && (!Number.isFinite(Number(form[field])) || Number(form[field]) < 0))) return setError('Los precios y el stock deben ser números mayores o iguales a cero.')
    if (resource.label === 'Productos' && ['categoriaId', 'proveedorId'].some((field) => !Number.isInteger(Number(form[field])) || Number(form[field]) <= 0)) return setError('Selecciona categoría y proveedor.')
    try {
      const cleanForm = Object.fromEntries(Object.entries(form).map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value]))
      const payload = resource.label === 'Productos' ? {
        ...(item ? { version: item.version } : {}),
        categoria: { id: Number(form.categoriaId) }, 
        marca: form.marcaId ? { id: Number(form.marcaId) } : null, 
        proveedor: { id: Number(form.proveedorId) }, 
        codigoBarras: form.codigoBarras, 
        nombre: form.nombre, 
        descripcion: form.descripcion, 
        precioCompra: Number(form.precioCompra), 
        precioVenta: Number(form.precioVenta), 
        stockActual: Number(form.stockActual), 
        stockMinimo: Number(form.stockMinimo) 
      } : resource.label === 'Mesas' ? {
        numero: Number(form.numero),
        capacidad: Number(form.capacidad),
        areaId: Number(form.areaId)
      } : resource.label === 'Clientes' ? { ...cleanForm, fechaNacimiento: form.fechaNacimiento || null } : cleanForm;
      
      if (item) await api.update(`${resource.endpoint}/${item.id}`, payload)
      else await api.create(resource.endpoint, payload)
      onSaved();
    } catch (err) { 
      setError(err.message || 'No se pudo guardar el registro.')
    } 
  }
  
  const fields = Object.keys(form)
  return <div className="modal-backdrop"><section className="modal" style={{maxHeight: '90vh', overflowY: 'auto'}}><div className="modal-head"><div><span className="eyebrow">{item ? 'Modificar registro' : 'Nuevo registro'}</span><h2>{resource.singular}</h2></div><button className="icon-button" onClick={onClose} aria-label="Cerrar"><X size={19} /></button></div><form onSubmit={submit} className="form-grid">
    {fields.map((field) => {
      const isSelect = (resource.label === 'Productos' && ['categoriaId', 'marcaId', 'proveedorId'].includes(field)) || field === 'areaId';
      if (field === 'rolIds') return <fieldset key={field} className="roles-field"><legend>Permisos del usuario</legend>{relations.roles.map(role => <label key={role.id}><input type="checkbox" checked={form.rolIds.includes(role.id)} onChange={event => update('rolIds', event.target.checked ? [...form.rolIds, role.id] : form.rolIds.filter(id => id !== role.id))} />{role.nombre}</label>)}</fieldset>
      const labelText = field.replace(/[A-Z]/g, (letter) => ` ${letter}`).replace(/^./, (letter) => letter.toUpperCase());
      const isRequired = (field === 'direccion' && resource.label === 'Configuración') || ['areaId','nombre', 'codigoBarras', 'nombresRazonSocial', 'numeroDocumento', 'rucDni', 'razonSocial', 'ruc', 'categoriaId', 'proveedorId', 'usuario', 'nombreCompleto', 'serie', 'numero', 'capacidad'].includes(field) || (field === 'contrasena' && !item);
      const isNumber = field.toLowerCase().includes('precio') || field.toLowerCase().includes('stock') || ['numero', 'capacidad'].includes(field);

      if (isSelect) {
        const options = field === 'areaId' ? relations.areas.filter(area => area.estado === 'ACTIVA') : field === 'categoriaId' ? relations.categorias : field === 'marcaId' ? relations.marcas : relations.proveedores;
        const nameField = field === 'areaId' ? 'nombre' : field === 'categoriaId' ? 'nombre' : field === 'marcaId' ? 'nombre' : 'razonSocial';
        return <label key={field}>{labelText}{field === 'marcaId' && <small className="form-hint">Solo necesario para bebidas; las comidas usan “Sin marca”.</small>}
          <select required={isRequired} value={form[field]} onChange={(e) => update(field, e.target.value)} style={{border: '1px solid var(--line)', padding: '14px 15px', borderRadius: '4px', background: 'var(--paper)'}}>
            <option value="">Seleccione una opción</option>
            {options.map(opt => <option key={opt.id} value={opt.id}>{opt[nameField]}</option>)}
          </select>
        </label>
      }

      if (field === 'estado') {
        return <label key={field}>Estado
          <select value={form[field]} onChange={(e) => update(field, e.target.value)} style={{border: '1px solid var(--line)', padding: '14px 15px', borderRadius: '4px', background: 'var(--paper)'}}>
            {resource.label === 'Áreas' ? <><option value="ACTIVA">Activa</option><option value="INACTIVA">Inactiva</option></> : <><option value="activo">Activo</option><option value="inactivo">Inactivo</option><option value="bloqueado">Bloqueado</option></>}
          </select>
        </label>
      }

      return <label key={field}>{labelText}
        <input required={isRequired} min={['numero', 'capacidad'].includes(field) ? 1 : isNumber ? 0 : undefined} minLength={field === 'contrasena' ? 8 : undefined} maxLength={field === 'contrasena' ? 72 : undefined} type={field === 'fechaNacimiento' ? 'date' : field.toLowerCase().includes('contrase') ? 'password' : isNumber ? 'number' : field === 'correo' || field === 'correoElectronico' ? 'email' : 'text'} value={form[field]} onChange={(event) => update(field, event.target.value)} />
      </label>
    })}
    {error && <div className="form-error">{error}</div>}<div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>Cancelar</button><button className="primary-button" type="submit">Guardar <ArrowRight size={16} /></button></div></form></section></div>
}

function Sales({ session }) { return <SalesPos session={session} /> }

export default App
```

## `frontend/src/MarketingDashboard.jsx`

```jsx
import { useEffect, useState } from 'react'
import { BarChart3, Cake, RefreshCw, UsersRound } from 'lucide-react'
import { api } from './api'

const money = (value) => 'S/ ' + Number(value || 0).toFixed(2)
export default function MarketingDashboard() {
  const [data, setData] = useState(null)
  const [clients, setClients] = useState([])
  const [clientId, setClientId] = useState('')
  const [consumption, setConsumption] = useState([])
  const [segment, setSegment] = useState('todos')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadingConsumption, setLoadingConsumption] = useState(false)
  const load = async () => {
    setLoading(true); setError('')
    try {
      const [dashboard, customerList] = await Promise.all([api.list('/api/marketing'), api.list('/api/clientes')])
      setData(dashboard); setClients(customerList)
    } catch (err) { setError(err.message) } finally { setLoading(false) }
  }
  useEffect(() => { load() }, [])
  useEffect(() => {
    let active = true
    setConsumption([])
    if (!clientId) { setLoadingConsumption(false); return }
    setLoadingConsumption(true); setError('')
    api.list(`/api/marketing/clientes/${clientId}/consumo`)
      .then((items) => { if (active) setConsumption(items) })
      .catch((err) => { if (active) setError(err.message) })
      .finally(() => { if (active) setLoadingConsumption(false) })
    return () => { active = false }
  }, [clientId])
  const filtered = clients.filter((client) => segment === 'todos'
    || (segment === 'frecuentes' && client.frecuenciaVisitas >= 5)
    || (segment === 'alto-consumo' && Number(client.totalGastado) >= 500)
    || (segment === 'nuevos' && client.frecuenciaVisitas <= 1))
  const maxVisits = Math.max(1, ...(data?.frecuentes || []).map((client) => client.frecuenciaVisitas))
  return <>
    <div className="page-heading compact"><div><span className="eyebrow">Administración / Fidelización</span><h1>Marketing</h1>
      <p className="muted">Conoce a tus clientes y prepara promociones según sus visitas, cumpleaños y consumo.</p></div>
      <button className="secondary-button" disabled={loading} onClick={load}><RefreshCw size={16} /> Actualizar</button></div>
    {error && <div className="form-error" role="alert">{error}</div>}
    {loading ? <div className="loading">Cargando indicadores…</div> : data && <>
      <div className="metric-grid marketing-metrics">
        <div className="metric"><UsersRound /><div><span>Clientes registrados</span><strong>{data.totalClientes}</strong></div></div>
        <div className="metric"><Cake /><div><span>Cumpleaños del mes</span><strong>{data.cumpleaneros.length}</strong></div></div>
        <div className="metric"><BarChart3 /><div><span>Clientes con 5 o más visitas</span><strong>{clients.filter(c => c.frecuenciaVisitas >= 5).length}</strong></div></div>
      </div>
      <p className="muted">Una visita equivale a una venta cobrada. Los totales incluyen IGV; los pedidos abiertos no cuentan.</p>
      <div className="marketing-grid">
        <section className="panel"><div className="panel-head"><h3>Top 10 clientes frecuentes</h3></div>
          <div className="marketing-ranking">{data.frecuentes.map((client, index) => <button type="button" className="ranking-row" key={client.id} onClick={() => setClientId(String(client.id))}>
            <span>{index + 1}. {client.nombre}</span><b>{client.frecuenciaVisitas} visitas · {money(client.totalGastado)}</b>
            <span className="ranking-bar" style={{ width: `${100 * client.frecuenciaVisitas / maxVisits}%` }} /></button>)}
            {!data.frecuentes.length && <p className="empty">Aún no hay ventas cobradas.</p>}</div>
        </section>
        <section className="panel"><div className="panel-head"><h3>Cumpleaños · {new Intl.DateTimeFormat('es-PE', { month: 'long', timeZone: 'UTC' }).format(new Date(Date.UTC(2000, data.mes - 1, 1)))}</h3></div>
          <div className="table-wrap"><table><thead><tr><th>Cliente</th><th>Día</th><th>Contacto</th></tr></thead><tbody>
            {data.cumpleaneros.map(client => <tr key={client.id}><td>{client.nombre}</td><td>{client.fechaNacimiento?.slice(-2)}</td><td>{client.telefono || client.correo || 'Sin contacto'}</td></tr>)}
          </tbody></table>{!data.cumpleaneros.length && <p className="empty">No hay cumpleaños registrados este mes.</p>}</div>
        </section>
      </div>
      <section className="panel recent-panel"><div className="panel-head"><h3>Segmentos de clientes</h3>
        <select aria-label="Segmento" value={segment} onChange={event => setSegment(event.target.value)}>
          <option value="todos">Todos</option><option value="frecuentes">Frecuentes: 5+ visitas</option>
          <option value="alto-consumo">Consumo acumulado: S/ 500+</option><option value="nuevos">Nuevos: 0–1 visitas</option>
        </select></div>
        <div className="table-wrap"><table><thead><tr><th>Cliente</th><th>Visitas</th><th>Gasto total</th><th>Hábitos</th></tr></thead>
          <tbody>{filtered.map(client => <tr key={client.id}><td>{client.nombresRazonSocial}</td><td>{client.frecuenciaVisitas}</td><td>{money(client.totalGastado)}</td>
            <td><button className="text-button" onClick={() => setClientId(String(client.id))}>Ver consumo</button></td></tr>)}</tbody></table>
          {!filtered.length && <p className="empty">No hay clientes en este segmento.</p>}</div>
      </section>
      <section className="panel recent-panel"><div className="panel-head"><div><h3>Platos y bebidas más consumidos</h3><small>Top 10 por unidades · importes sin IGV</small></div>
        <select aria-label="Cliente para consultar consumo" value={clientId} onChange={event => setClientId(event.target.value)}>
          <option value="">Seleccionar cliente</option>{clients.map(client => <option key={client.id} value={client.id}>{client.nombresRazonSocial}</option>)}</select></div>
        {loadingConsumption ? <div className="loading">Consultando consumo…</div> : <div className="table-wrap"><table><thead><tr><th>Producto</th><th>Categoría</th><th>Unidades</th><th>Importe</th></tr></thead>
          <tbody>{consumption.map(item => <tr key={item.productoId}><td>{item.nombre}</td><td>{item.categoria}</td><td>{item.unidades}</td><td>{money(item.importe)}</td></tr>)}</tbody></table>
          {!consumption.length && <p className="empty">{clientId ? 'Este cliente todavía no tiene consumo cobrado.' : 'Selecciona un cliente para consultar sus hábitos.'}</p>}</div>}
      </section>
    </>}
  </>
}
```

## `frontend/src/Receipt.jsx`

```jsx
const money = value => Number(value || 0).toFixed(2)
export default function Receipt({ sale }) {
  if (!sale) return null
  const command = sale.printType === 'COMANDA'
  return <section className="print-only receipt-paper">
    <div className="receipt-header"><strong>{sale.empresa?.razonSocial || 'RECREO MI TRAMPITA'}</strong>
      {!command && <><span>RUC: {sale.empresa?.ruc}</span><span>{sale.empresa?.direccion}</span></>}</div>
    <div className="receipt-title"><strong>{command ? 'COMANDA · PEDIDO COMPLETO' : sale.tipoComprobante?.nombre}</strong>
      <span>{sale.numeroComprobante}</span><strong>Mesa {sale.mesa?.numero} · {sale.mesa?.area?.nombre}</strong></div>
    <div className="receipt-customer"><span>Cliente: {sale.cliente?.nombresRazonSocial}</span>
      {!command && <span>Documento: {sale.cliente?.numeroDocumento}</span>}
      <span>Fecha: {new Date((command ? sale.fechaVenta : sale.fechaCobro) || Date.now()).toLocaleString('es-PE')}</span></div>
    <table><thead><tr><th>Descripción</th><th>Cant.</th>{!command && <><th>P. unit.</th><th>Total</th></>}</tr></thead>
      <tbody>{(sale.detalles || []).map(detail => <tr key={detail.id}><td>{detail.producto?.nombre}</td><td>{detail.cantidad}</td>
        {!command && <><td>S/ {money(detail.precioUnitario)}</td><td>S/ {money(detail.subtotal)}</td></>}</tr>)}</tbody></table>
    {!command && <><div className="receipt-totals"><span>Subtotal: S/ {money(sale.subtotal)}</span><span>IGV: S/ {money(sale.igv)}</span>
      <strong>Total: S/ {money(sale.total)}</strong></div><p>Medio de pago: {sale.metodoPago}</p><p>Gracias por su visita.</p></>}
  </section>
}
```

## `frontend/src/SalesPos.jsx`

```jsx
import { useEffect, useMemo, useState } from 'react'
import {
  AlertCircle, CheckCircle2, FileText, Minus, Package, Plus, Printer, Search,
  ShoppingCart, UserRound, X
} from 'lucide-react'
import { api } from './api'
import { flushSync } from 'react-dom'
import { permissions } from './permissions'
import TablesView from './TablesView'
import Receipt from './Receipt'

const blankCustomer = {
  numeroDocumento: '',
  nombresRazonSocial: '',
  direccion: '',
  telefono: '',
  correo: '',
  fechaNacimiento: ''
}

function formatMoney(value) {
  return Number(value || 0).toFixed(2)
}

function receiptLabel(name) {
  const value = (name || '').toUpperCase()
  if (value.includes('FACTURA')) return 'Factura'
  if (value.includes('BOLETA')) return 'Boleta'
  return 'Otro'
}

export default function SalesPos({ session }) {
  const { canOrder, canCharge } = permissions(session)
  const [areas, setAreas] = useState([])
  const [lastOrder, setLastOrder] = useState(null)
  const [printDocument, setPrintDocument] = useState(null)
  const printTicket = (sale, printType) => { flushSync(() => setPrintDocument({ ...sale, printType })); window.print() }
  const [products, setProducts] = useState([])
  const [categories, setCategories] = useState([])
  const [brands, setBrands] = useState([])
  const [clients, setClients] = useState([])
  const [empresas, setEmpresas] = useState([])
  const [comprobantes, setComprobantes] = useState([])
  const [mesas, setMesas] = useState([])
  const [openSales, setOpenSales] = useState([])
  const [activeSaleId, setActiveSaleId] = useState('')
  const [selectedMesaId, setSelectedMesaId] = useState('')
  const [cart, setCart] = useState([])
  const [search, setSearch] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [brandFilter, setBrandFilter] = useState('')
  const [comprobanteId, setComprobanteId] = useState('')
  const [metodoPago, setMetodoPago] = useState('efectivo')
  const [customer, setCustomer] = useState(blankCustomer)
  const [amountReceived, setAmountReceived] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [lastSale, setLastSale] = useState(null)
  const [notice, setNotice] = useState('')

  useEffect(() => {
    setLoading(true)
    Promise.all([
      api.list('/api/productos'),
      api.list('/api/categorias'),
      api.list('/api/marcas'),
      api.list('/api/pos/clientes'),
      api.list('/api/pos/configuracion'),
      api.list('/api/tipos-comprobante'),
      api.list('/api/ventas/abiertas'),
      api.list('/api/mesas'),
      api.list('/api/areas')
    ]).then(([productData, categoryData, brandData, clientData, companyData, receiptData, salesData, mesaData, areaData]) => {
      setProducts(productData)
      setCategories(categoryData)
      setBrands(brandData)
      setClients(clientData)
      setEmpresas(companyData)
      setComprobantes(receiptData)
      setOpenSales(salesData.filter((sale) => sale.estado === 'ABIERTA'))
      setMesas(mesaData)
      setAreas(areaData)
      if (!comprobanteId && receiptData.length) {
        const preferred = [...receiptData].sort((a, b) => {
          const order = { BOLETA: 0, FACTURA: 1 }
          return (order[receiptLabel(a.nombre).toUpperCase()] ?? 2) - (order[receiptLabel(b.nombre).toUpperCase()] ?? 2)
        })
        setComprobanteId(String(preferred[0].id))
      }
    }).catch((err) => setError(err.message)).finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    let mounted = true
    const refresh = async () => {
      try {
        const [tables, sales] = await Promise.all([api.list('/api/mesas'), api.list('/api/ventas/abiertas')])
        if (mounted) { setMesas(tables); setOpenSales(sales) }
      } catch (err) { if (mounted) setError(err.message) }
    }
    const timer = setInterval(refresh, 15000)
    return () => { mounted = false; clearInterval(timer) }
  }, [])

  const selectedReceipt = useMemo(
    () => comprobantes.find((item) => String(item.id) === String(comprobanteId)),
    [comprobantes, comprobanteId]
  )
  const activeSale = useMemo(
    () => openSales.find((sale) => String(sale.id) === String(activeSaleId)),
    [openSales, activeSaleId]
  )
  const isInvoice = selectedReceipt?.nombre?.toUpperCase().includes('FACTURA')
  const filteredProducts = useMemo(() => {
    const term = search.trim().toLowerCase()
    return products.filter((product) => {
      const matchesSearch = !term || product.nombre?.toLowerCase().includes(term) || product.codigoBarras?.toLowerCase().includes(term)
      const matchesCategory = !categoryFilter || String(product.categoria?.id) === String(categoryFilter)
      const matchesBrand = !brandFilter || String(product.marca?.id) === String(brandFilter)
      return matchesSearch && matchesCategory && matchesBrand
    })
  }, [products, search, categoryFilter, brandFilter])
  const additionalSubtotal = cart.reduce((sum, item) => sum + Number(item.precioVenta || 0) * item.cantidad, 0)
  const subtotal = additionalSubtotal + Number(activeSale?.subtotal || 0)
  const igv = Math.round((subtotal * 0.18 + Number.EPSILON) * 100) / 100
  const total = !cart.length && activeSale ? Number(activeSale.total) : Math.round((subtotal + igv + Number.EPSILON) * 100) / 100
  const additionalTotal = additionalSubtotal * 1.18
  const change = Math.max(0, Number(amountReceived || 0) - total)

  useEffect(() => {
    const document = customer.numeroDocumento.trim()
    if (document.length < 6) return
    const existing = clients.find((client) => client.numeroDocumento === document)
    if (existing && existing.nombresRazonSocial !== customer.nombresRazonSocial) {
      setCustomer({
        numeroDocumento: existing.numeroDocumento || document,
        nombresRazonSocial: existing.nombresRazonSocial || '',
        direccion: existing.direccion || '',
        telefono: existing.telefono || '',
        correo: existing.correo || '',
        fechaNacimiento: existing.fechaNacimiento || ''
      })
    }
  }, [customer.numeroDocumento, clients])

  const updateCustomer = (field, value) => {
    setError('')
    setNotice('')
    setCustomer((current) => ({ ...current, [field]: value }))
  }

  const selectOpenSale = (value) => {
    if (saving) return
    if (cart.length) return setError('Envía o quita los productos pendientes antes de cambiar de mesa.')
    setActiveSaleId(value)
    setCart([])
    setLastSale(null)
    setLastOrder(null)
    setAmountReceived('')
    setError('')
    setNotice('')
    const sale = openSales.find((item) => String(item.id) === String(value))
    if (sale?.cliente) {
      setCustomer({
        numeroDocumento: sale.cliente.numeroDocumento || '',
        nombresRazonSocial: sale.cliente.nombresRazonSocial || '',
        direccion: sale.cliente.direccion || '',
        telefono: sale.cliente.telefono || '',
        correo: sale.cliente.correo || '',
        fechaNacimiento: sale.cliente.fechaNacimiento || ''
      })
    }
    if (sale) {
      setSelectedMesaId(String(sale.mesa?.id || ''))
      setMetodoPago(sale.metodoPago || 'efectivo')
    } else {
      setSelectedMesaId('')
    }
  }

  const selectMesa = (mesa) => {
    if (cart.length && String(selectedMesaId) !== String(mesa.id)) return setError('Envía o quita los productos pendientes antes de cambiar de mesa.')
    if (String(selectedMesaId) === String(mesa.id)) return
    const sale = openSales.find((item) => item.mesa?.id === mesa.id)
    if (sale) return selectOpenSale(String(sale.id))
    if (mesa.estado === 'ATENDIENDO') return setError('Actualiza el salón para cargar la comanda de esta mesa.')
    setActiveSaleId('')
    setSelectedMesaId(String(mesa.id))
    setCart([])
    setLastSale(null)
    setLastOrder(null)
    setAmountReceived('')
    setError('')
    setNotice('')
  }

  const addToCart = (product) => {
    if (!canOrder || saving) return
    if (!selectedMesaId) return setError('Selecciona una mesa antes de tomar el pedido.')
    if (Number(product.stockActual) <= 0) return setError('Este producto no tiene stock disponible.')
    setError('')
    setCart((current) => {
      const found = current.find((item) => item.id === product.id)
      if (!found) return [...current, { ...product, cantidad: 1 }]
      if (found.cantidad >= product.stockActual) {
        setError(`Stock insuficiente para ${product.nombre}.`)
        return current
      }
      return current.map((item) => item.id === product.id ? { ...item, cantidad: item.cantidad + 1 } : item)
    })
  }

  const updateQuantity = (id, delta) => {
    if (saving) return
    setError('')
    setCart((current) => current.flatMap((item) => {
      if (item.id !== id) return [item]
      const quantity = item.cantidad + delta
      if (quantity > item.stockActual) {
        setError(`No puedes superar el stock de ${item.nombre}.`)
        return [item]
      }
      return quantity > 0 ? [{ ...item, cantidad: quantity }] : []
    }))
  }

  const removeFromCart = (id) => !saving && setCart((current) => current.filter((item) => item.id !== id))

  const submitSale = async () => {
    if (!canOrder || saving) return
    if (!selectedMesaId) return setError('Faltan datos de la mesa. Selecciona una mesa del salón.')
    if (activeSaleId && !activeSale) return setError('La comanda ya no está abierta. Vuelve a seleccionar la mesa.')
    setError('')
    setNotice('')
    if (activeSale) {
      if (!cart.length) return setError('Agrega al menos un producto adicional.')
      setSaving(true)
      try {
        const updatedSale = await api.patch(`/api/ventas/${activeSale.id}/items`, {
          items: cart.map((item) => ({ productoId: item.id, cantidad: item.cantidad }))
        })
        setOpenSales((current) => current.map((sale) => sale.id === updatedSale.id ? updatedSale : sale))
        setCart([])
        setMetodoPago(updatedSale.metodoPago || metodoPago)
        setLastOrder({ ...updatedSale, detalles: updatedSale.detalles })
        setNotice(`Adicional registrado. Comanda ${updatedSale.numeroComprobante} actualizada.`)
      } catch (err) {
        setError(err.message || 'No se pudo actualizar la comanda.')
      } finally {
        setSaving(false)
      }
      return
    }
    const document = customer.numeroDocumento.trim()
    const name = customer.nombresRazonSocial.trim()
    if (!empresas.length) return setError('Solicita al administrador completar los datos fiscales en Configuración.')
    if (!selectedReceipt) return setError('Selecciona el tipo de comprobante.')
    if (!cart.length) return setError('Agrega al menos un producto al carrito.')
    if (!/^\d{6,20}$/.test(document)) return setError('El documento debe contener entre 6 y 20 dígitos.')
    if (!name) return setError('Ingresa el nombre o razón social del cliente.')
    if (isInvoice && !/^\d{11}$/.test(document)) return setError('Para una factura debes ingresar un RUC de 11 dígitos.')
    if (isInvoice && !customer.direccion.trim()) return setError('La dirección es obligatoria para una factura.')
    if (customer.correo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customer.correo)) return setError('El correo del cliente no es válido.')
    setSaving(true)
    try {
      const sale = await api.create('/api/ventas', {
        empresaId: empresas[0].id,
        cliente: {
          ...customer,
          numeroDocumento: document,
          nombresRazonSocial: name,
          direccion: customer.direccion.trim(),
          telefono: customer.telefono.trim(),
          correo: customer.correo.trim(),
          fechaNacimiento: customer.fechaNacimiento || null
        },
        tipoComprobanteId: Number(selectedReceipt.id),
        numeroComprobante: `${selectedReceipt.serie}-${crypto.randomUUID().slice(0, 18)}`,
        mesaId: selectedMesaId ? Number(selectedMesaId) : null,
        items: cart.map((item) => ({ productoId: item.id, cantidad: item.cantidad }))
      })
      setLastOrder(sale)
      setNotice('Comanda registrada. Puedes imprimir el pedido para cocina.')
      setCart([])
      setAmountReceived('')
      setActiveSaleId(String(sale.id))
      setSelectedMesaId(String(sale.mesa?.id || ''))
      setMetodoPago(sale.metodoPago || metodoPago)
      setOpenSales((current) => [...current, sale].filter((item) => item.estado === 'ABIERTA'))
      if (sale.mesa?.id) setMesas((current) => current.map((mesa) => mesa.id === sale.mesa.id ? { ...mesa, estado: 'ATENDIENDO' } : mesa))
    } catch (err) {
      setError(err.message || 'No se pudo registrar la venta.')
    } finally {
      setSaving(false)
    }
  }

  const closeSale = async () => {
    if (!canCharge || saving || !activeSale) return
    setError('')
    setNotice('')
    if (cart.length) return setError('Envía primero los productos adicionales a la comanda.')
    if (metodoPago === 'efectivo' && Number(amountReceived || 0) < total) {
      return setError('El monto recibido no puede ser menor que el total.')
    }
    setSaving(true)
    try {
      const closedSale = await api.patch(`/api/ventas/${activeSale.id}/cerrar`, {
        metodoPago,
        montoRecibido: metodoPago === 'efectivo' ? Number(amountReceived || 0) : null
      })
      setLastSale(closedSale)
      setLastOrder(null)
      setOpenSales((current) => current.filter((sale) => sale.id !== closedSale.id))
      setMesas(current => current.map(mesa => mesa.id === closedSale.mesa?.id ? { ...mesa, estado: 'LIBRE' } : mesa))
      api.list('/api/productos').then(setProducts).catch(err => setError('Cobro realizado. No se pudo actualizar el catálogo: ' + err.message))
      setActiveSaleId('')
      setSelectedMesaId('')
      setCart([])
      setAmountReceived('')
      setNotice(`Venta ${closedSale.numeroComprobante} cerrada. La mesa quedó libre.`)
    } catch (err) {
      setError(err.message || 'No se pudo cerrar la venta.')
    } finally {
      setSaving(false)
    }
  }

  const changeTableState = async (action) => {
    setSaving(true); setError('')
    try {
      const table = await api.patch(`/api/mesas/${selectedMesaId}/${action}`, {})
      setMesas(current => current.map(mesa => mesa.id === table.id ? table : mesa))
      setNotice(action === 'abrir' ? 'Mesa ocupada. Puedes tomar el pedido.' : 'Mesa liberada.')
    } catch (err) { setError(err.message) } finally { setSaving(false) }
  }

  if (loading) return <div className="loading">Cargando productos y configuración del POS...</div>

  return <>
    <div className="page-heading compact">
      <div><span className="eyebrow">Operación / POS</span><h1>Punto de Venta</h1><p className="muted">{canOrder ? 'Selecciona una mesa y registra su comanda.' : 'Selecciona una comanda para cobrar y liberar la mesa.'}</p></div>
      <div className="pos-company-badge"><span className="workspace-dot" /> {empresas[0]?.nombreComercial || empresas[0]?.razonSocial || 'Configuración fiscal pendiente'}</div>
    </div>
    <TablesView areas={areas} mesas={mesas} openSales={openSales} selectedMesaId={selectedMesaId} onSelect={selectMesa} disabled={saving} />
    {selectedMesaId && !activeSale && <div className="table-actions">
      {canOrder && mesas.find(mesa => String(mesa.id) === String(selectedMesaId))?.estado === 'LIBRE' && <button className="secondary-button" disabled={saving} onClick={() => changeTableState('abrir')}>Ocupar mesa</button>}
      {canCharge && mesas.find(mesa => String(mesa.id) === String(selectedMesaId))?.estado === 'OCUPADA' && <button className="secondary-button" disabled={saving} onClick={() => changeTableState('liberar')}>Liberar mesa sin pedido</button>}
    </div>}
    <div className={`pos-layout ${!canOrder ? 'cashier-layout' : ''}`}>
      {canOrder && <section className="panel pos-products">
        <div className="table-toolbar">
          <div className="search-box"><Search size={16} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar por nombre o código..." /></div>
          <span className="result-count">{filteredProducts.length} productos</span>
        </div>
        <div className="catalog-filters">
          <div className="catalog-filter-row"><strong>Comidas por categoría</strong><button type="button" className={!categoryFilter ? 'filter-chip active' : 'filter-chip'} onClick={() => setCategoryFilter('')}>Todas</button>{categories.map((category) => <button type="button" className={String(category.id) === String(categoryFilter) ? 'filter-chip active' : 'filter-chip'} key={category.id} onClick={() => setCategoryFilter(String(category.id))}>{category.nombre}</button>)}</div>
          <div className="catalog-filter-row"><strong>Bebidas por marca</strong><button type="button" className={!brandFilter ? 'filter-chip active' : 'filter-chip'} onClick={() => setBrandFilter('')}>Todas</button>{brands.filter((brand) => brand.nombre?.toLowerCase() !== 'sin marca').map((brand) => <button type="button" className={String(brand.id) === String(brandFilter) ? 'filter-chip active' : 'filter-chip'} key={brand.id} onClick={() => setBrandFilter(String(brand.id))}>{brand.nombre}</button>)}</div>
        </div>
        <div className="product-grid">
          {filteredProducts.map((product) => <button type="button" className="product-card" key={product.id} onClick={() => addToCart(product)} disabled={saving || product.stockActual <= 0}>
            <div className="product-card-top"><Package size={19} /><strong>S/ {formatMoney(product.precioVenta)}</strong></div>
            <strong>{product.nombre}</strong><small>{product.codigoBarras}</small>
            <span className={product.stockActual <= product.stockMinimo ? 'stock-warning' : 'stock-ok'}>Stock: {product.stockActual}</span>
          </button>)}
          {!filteredProducts.length && <EmptyPos text="No se encontraron productos." />}
        </div>
      </section>}

      <section className="panel pos-checkout">
        <div className="panel-head"><div><span className="eyebrow">{activeSale ? 'Comanda abierta' : canOrder ? 'Pedido' : 'Caja'}</span><h3>{activeSale ? `Mesa ${activeSale.mesa?.numero}` : canOrder ? `Carrito (${cart.length})` : 'Selecciona una comanda'}</h3></div><ShoppingCart size={20} className="accent-icon" /></div>
        <div className="cart-list">
          <label className="open-sale-selector"><span>Venta / mesa</span><select value={activeSaleId} onChange={(event) => selectOpenSale(event.target.value)}><option value="">Nueva venta {selectedMesaId ? `· Mesa ${mesas.find((mesa) => String(mesa.id) === String(selectedMesaId))?.numero || ''}` : '· Selecciona una mesa'}</option>{openSales.map((sale) => <option key={sale.id} value={sale.id}>Mesa {sale.mesa?.numero || 'sin mesa'} · {sale.numeroComprobante} · S/ {formatMoney(sale.total)}</option>)}</select></label>
          {activeSale && <div className="open-sale-summary"><strong>Pedido actual</strong>{(activeSale.detalles || []).map((detail) => <div key={detail.id || detail.producto?.id}><span>{detail.cantidad} × {detail.producto?.nombre || 'Producto'}</span><b>S/ {formatMoney(detail.subtotal)}</b></div>)}</div>}
          {!cart.length ? (!activeSale && <EmptyPos text={canOrder ? 'Agrega productos para comenzar.' : 'Selecciona una mesa con comanda abierta.'} />) : cart.map((item) => <div className="cart-item" key={item.id}>
            <div className="cart-item-info"><strong>{item.nombre}</strong><small>S/ {formatMoney(item.precioVenta)} c/u · stock {item.stockActual}</small></div>
            <div className="quantity-control"><button type="button" onClick={() => updateQuantity(item.id, -1)}><Minus size={13} /></button><b>{item.cantidad}</b><button type="button" onClick={() => updateQuantity(item.id, 1)}><Plus size={13} /></button></div>
            <button type="button" className="icon-button" onClick={() => removeFromCart(item.id)} title="Quitar producto"><X size={15} /></button>
          </div>)}
        </div>

        <div className="checkout-form">
          {!activeSale && canOrder && <>
          <div className="checkout-section-title"><UserRound size={15} /> Datos del cliente</div>
          <div className="inline-fields">
            <label><span>{isInvoice ? 'RUC' : 'DNI / documento'} *</span><input inputMode="numeric" maxLength={20} value={customer.numeroDocumento} onChange={(event) => updateCustomer('numeroDocumento', event.target.value.replace(/\D/g, ''))} placeholder={isInvoice ? '11 dígitos' : 'DNI del cliente'} /></label>
            <label><span>{isInvoice ? 'Razón social' : 'Nombre completo'} *</span><input maxLength={150} value={customer.nombresRazonSocial} onChange={(event) => updateCustomer('nombresRazonSocial', event.target.value)} placeholder="Nombre del cliente" /></label>
          </div>
          <label><span>Dirección {isInvoice ? '*' : '(opcional)'}</span><input maxLength={255} value={customer.direccion} onChange={(event) => updateCustomer('direccion', event.target.value)} placeholder="Dirección fiscal o domicilio" /></label>
          <div className="inline-fields">
            <label><span>Teléfono</span><input maxLength={20} value={customer.telefono} onChange={(event) => updateCustomer('telefono', event.target.value)} /></label>
            <label><span>Correo</span><input type="email" maxLength={100} value={customer.correo} onChange={(event) => updateCustomer('correo', event.target.value)} /></label>
          </div>

          <label><span>Fecha de nacimiento (opcional)</span><input type="date" value={customer.fechaNacimiento} onChange={event => updateCustomer('fechaNacimiento', event.target.value)} /></label>
          <div className="checkout-section-title"><FileText size={15} /> Comprobante solicitado</div>
          <div className="receipt-options" role="group" aria-label="Tipo de comprobante">
            {comprobantes.map((item) => <button type="button" key={item.id} className={`receipt-option ${String(item.id) === String(comprobanteId) ? 'selected' : ''}`} onClick={() => { setComprobanteId(String(item.id)); setError('') }}>
              <FileText size={17} /><span>{receiptLabel(item.nombre)}</span><small>{item.serie}</small>
            </button>)}
          </div>
          {!comprobantes.length && <div className="form-error">No hay comprobantes configurados.</div>}
          </>} {/* Datos de cliente y comprobante */}
          {canCharge && activeSale && <div className="inline-fields">
            <label><span>Método de pago *</span><select value={metodoPago} onChange={(event) => setMetodoPago(event.target.value)}><option value="efectivo">Efectivo</option><option value="tarjeta">Tarjeta</option><option value="transferencia">Transferencia</option><option value="yape_plin">Yape / Plin</option></select></label>
          </div>}
          {canCharge && activeSale && metodoPago === 'efectivo' && <label><span>Monto recibido</span><input type="number" min="0" step="0.01" value={amountReceived} onChange={(event) => setAmountReceived(event.target.value)} placeholder={`Mínimo S/ ${formatMoney(total)}`} /></label>}

          <div className="totals"><div><span>Subtotal</span><b>S/ {formatMoney(subtotal)}</b></div><div><span>IGV (18%)</span><b>S/ {formatMoney(igv)}</b></div><div className="total-line"><strong>Total</strong><strong>S/ {formatMoney(total)}</strong></div>{canCharge && activeSale && metodoPago === 'efectivo' && <div className="change-line"><span>Vuelto</span><b>S/ {formatMoney(change)}</b></div>}</div>
          {error && <div className="form-error" role="alert"><AlertCircle size={15} /> {error}</div>}
          {notice && <div className="sale-success" role="status"><CheckCircle2 size={16} /><span>{notice}</span></div>}
          {lastOrder && canOrder && <button type="button" className="secondary-button full" onClick={() => printTicket(lastOrder, 'COMANDA')}><Printer size={15} /> Imprimir comanda completa para cocina</button>}
          {lastSale && canCharge && <div className="sale-success"><CheckCircle2 size={16} /><span>Venta cobrada.</span><button type="button" className="print-sale-button" onClick={() => printTicket(lastSale, 'COMPROBANTE')}><Printer size={15} /> Imprimir comprobante</button></div>}
          {canOrder && <button type="button" className="primary-button full" onClick={submitSale} disabled={saving || !cart.length}>{saving ? 'Guardando pedido...' : activeSale ? `Enviar adicional S/ ${formatMoney(additionalTotal)}` : `Enviar comanda S/ ${formatMoney(total)}`}</button>}
          {canCharge && activeSale && <button type="button" className="secondary-button full" onClick={closeSale} disabled={saving}>{saving ? 'Procesando...' : `Cobrar y liberar mesa · S/ ${formatMoney(total)}`}</button>}
        </div>
      </section>
    </div>
    <Receipt sale={printDocument} />
  </>
}

function EmptyPos({ text }) {
  return <div className="empty"><Package size={22} /><span>{text}</span></div>
}
```

## `frontend/src/TablesView.jsx`

```jsx
import { useEffect, useState } from 'react'

const states = { LIBRE: 'Libre', OCUPADA: 'Ocupada', ATENDIENDO: 'Atendiendo' }

export default function TablesView({ areas, mesas, openSales, selectedMesaId, onSelect, disabled }) {
  const [areaId, setAreaId] = useState('')
  const visibleAreas = areas.filter((area) => area.estado === 'ACTIVA')
  useEffect(() => {
    if (!visibleAreas.some((area) => String(area.id) === areaId)) setAreaId(String(visibleAreas[0]?.id || ''))
  }, [areas, areaId])
  const tables = mesas.filter((mesa) => String(mesa.area?.id) === areaId)
  return <section className="mesa-board panel" aria-label="Áreas y mesas">
    <div className="panel-head"><div><span className="eyebrow">Salón</span><h3>Áreas y mesas</h3></div>
      <span className="result-count">{tables.filter((mesa) => mesa.estado === 'LIBRE').length} libres · {tables.length} mesas</span></div>
    <div className="area-tabs" role="tablist" aria-label="Áreas del recreo">
      {visibleAreas.map((area) => <button key={area.id} id={`area-tab-${area.id}`} type="button" role="tab"
        aria-selected={String(area.id) === areaId} aria-controls="area-tables" className={String(area.id) === areaId ? 'filter-chip active' : 'filter-chip'}
        onClick={() => setAreaId(String(area.id))}>{area.nombre}</button>)}
    </div>
    <div className="mesa-grid" id="area-tables" role="tabpanel" aria-labelledby={areaId ? `area-tab-${areaId}` : undefined}>
      {tables.map((mesa) => {
        const sale = openSales.find((item) => item.mesa?.id === mesa.id)
        return <button type="button" key={mesa.id} disabled={disabled} onClick={() => onSelect(mesa)}
          aria-pressed={String(selectedMesaId) === String(mesa.id)}
          className={`mesa-card mesa-${mesa.estado.toLowerCase()} ${String(selectedMesaId) === String(mesa.id) ? 'selected' : ''}`}>
          <strong>Mesa {mesa.numero}</strong><span>{states[mesa.estado] || mesa.estado}</span>
          <small>{mesa.capacidad} personas{sale ? ` · S/ ${Number(sale.total).toFixed(2)}` : ''}</small>
        </button>
      })}
      {!tables.length && <p className="empty">No hay mesas en esta área.</p>}
    </div>
    <div className="table-legend"><span>● Libre</span><span>● Ocupada: esperando pedido</span><span>● Atendiendo: comanda abierta</span></div>
  </section>
}
```

## `frontend/src/api.js`

```javascript
const API_URL = import.meta.env.VITE_API_URL || ''

export class ApiError extends Error {
  constructor(message, status) { super(message); this.status = status }
}
async function request(path, options = {}) {
  let response
  try {
    const headers = { 'Content-Type': 'application/json', ...options.headers }
    // Solicitar el token actual también cubre la rotación de sesión al iniciar/cerrar sesión.
    if (options.method && options.method !== 'GET') {
      const csrfResponse = await fetch(`${API_URL}/api/auth/csrf`, { credentials: 'include' })
      if (!csrfResponse.ok) throw new ApiError('No se pudo validar la sesión. Vuelve a ingresar.', csrfResponse.status)
      const csrf = await csrfResponse.json()
      headers[csrf.headerName] = csrf.token
    }
    response = await fetch(`${API_URL}${path}`, { ...options, credentials: 'include', headers })
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError('No se pudo conectar con el servidor. Verifica la conexión e inténtalo de nuevo.', 0)
  }
  if (!response.ok) {
    const body = await response.text()
    let message = `No se pudo completar la operación (HTTP ${response.status}).`
    try { const parsed = JSON.parse(body); message = parsed.message || parsed.error || message } catch { /* Respuesta sin JSON. */ }
    if (response.status === 401 && !path.startsWith('/api/auth/')) window.dispatchEvent(new Event('pos-session-expired'))
    throw new ApiError(message, response.status)
  }
  if (response.status === 204) return null
  return response.json()
}
export const api = {
  list: (path) => request(path),
  create: (path, data) => request(path, { method: 'POST', body: JSON.stringify(data) }),
  update: (path, data) => request(path, { method: 'PUT', body: JSON.stringify(data) }),
  patch: (path, data) => request(path, { method: 'PATCH', body: JSON.stringify(data) }),
  remove: (path) => request(path, { method: 'DELETE' }),
}
```

## `frontend/src/permissions.js`

```javascript
export function permissions(session) {
  const roles = new Set(session?.roles || [])
  const isAdmin = roles.has('ADMIN')
  return { isAdmin, canOrder: isAdmin || roles.has('MOZO'), canCharge: isAdmin || roles.has('CAJA') }
}
export function canView(session, view) {
  const rights = permissions(session)
  return rights.isAdmin || (view === 'ventas' && (rights.canOrder || rights.canCharge))
}
```

## `frontend/src/styles.css`

```css
@import url("https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=DM+Sans:wght@400;500;600;700;800&family=Sora:wght@400;500;600;700;800&display=swap");
:root {
  font-family: "DM Sans", sans-serif;
  color: #1d293b;
  background: #f5f7fb;
  font-synthesis: none;
  --ink: #1d293b;
  --muted: #718096;
  --line: #e4e9f1;
  --paper: #ffffff;
  --green: #e45b45;
  --lime: #ffc857;
  --orange: #ff9f68;
  --yellow: #f5cf70;
  --blue: #a9c8ff;
}
* {
  box-sizing: border-box;
}
body {
  margin: 0;
  min-width: 320px;
}
button,
input {
  font: inherit;
}
button {
  cursor: pointer;
  border: 0;
}
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.08fr 0.92fr;
  background: #f4f6f1;
}
.login-art {
  position: relative;
  overflow: hidden;
  background: #285d47;
  color: #f4f6f1;
  padding: 38px 7vw;
  display: flex;
  flex-direction: column;
}
.brand-mark {
  display: flex;
  align-items: center;
  gap: 9px;
  font: 700 13px "Space Grotesk";
  letter-spacing: 0.1em;
}
.brand-mark.dark {
  color: #d7f06c;
}
.login-quote {
  margin-top: auto;
  margin-bottom: 12vh;
  position: relative;
  z-index: 1;
}
.login-quote span,
.eyebrow,
.nav-label {
  font: 500 11px "DM Mono";
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: #94a69b;
}
.login-quote h1 {
  font: 700 clamp(60px, 7.5vw, 115px)/0.9 "Space Grotesk";
  letter-spacing: -0.06em;
  margin: 22px 0;
}
.login-quote em,
.page-heading em {
  color: #d7f06c;
  font-style: normal;
}
.login-quote p {
  font-size: 15px;
  color: #c4d5c9;
}
.art-grid {
  position: absolute;
  inset: 22% -10% 0;
  background:
    linear-gradient(
      135deg,
      transparent 49.8%,
      rgba(215, 240, 108, 0.18) 50%,
      transparent 50.4%
    ),
    linear-gradient(
      45deg,
      transparent 49.8%,
      rgba(215, 240, 108, 0.12) 50%,
      transparent 50.4%
    );
  background-size: 58px 58px;
  mask-image: linear-gradient(transparent, #000 35%);
}
.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.login-form {
  width: min(380px, 78%);
}
.login-form h2 {
  font: 700 38px/1.05 "Space Grotesk";
  letter-spacing: -0.04em;
  margin: 14px 0 9px;
}
.muted {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.6;
}
.login-form form {
  margin-top: 34px;
}
.login-form label,
.modal label {
  display: grid;
  gap: 8px;
  color: #435149;
  font-size: 12px;
  font-weight: 700;
}
.login-form input,
.modal input {
  border: 1px solid var(--line);
  background: var(--paper);
  padding: 14px 15px;
  border-radius: 4px;
  outline: 0;
  color: var(--ink);
  font-size: 14px;
  transition: border 0.2s;
}
.login-form input:focus,
.modal input:focus {
  border-color: var(--green);
}
.primary-button,
.secondary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 12px 17px;
  border-radius: 4px;
  font-weight: 800;
  font-size: 12px;
}
.primary-button {
  background: var(--green);
  color: #fff;
}
.primary-button:hover {
  background: #1d4735;
}
.full {
  width: 100%;
  margin-top: 20px;
  padding: 15px;
}
.login-note {
  display: flex;
  gap: 7px;
  align-items: center;
  margin-top: 22px;
  color: #9aa69f;
  font-size: 11px;
}
.login-footer {
  position: absolute;
  bottom: 32px;
  color: #9aa69f;
  font-size: 11px;
}
.mobile-brand {
  display: none;
}
.form-error,
.api-error {
  padding: 10px 12px;
  border-radius: 4px;
  background: #fff0ed;
  color: #a44932;
  font-size: 12px;
  margin-top: 12px;
}
.app-shell {
  min-height: 100vh;
  display: flex;
}
.sidebar {
  width: 248px;
  flex: 0 0 248px;
  background: #1c2a24;
  color: #bac9c0;
  padding: 27px 16px 17px;
  display: flex;
  flex-direction: column;
}
.sidebar-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 9px;
}
.workspace {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #eef5ef;
  background: #273a31;
  margin: 35px 0 23px;
  padding: 11px;
  border-radius: 4px;
  font-size: 12px;
}
.workspace > svg {
  margin-left: auto;
}
.workspace-dot,
.connection span {
  width: 7px;
  height: 7px;
  background: #91cf75;
  border-radius: 50%;
  display: inline-block;
}
.nav-group {
  margin-bottom: 26px;
}
.nav-label {
  display: block;
  padding: 0 11px 9px;
  color: #74867c;
  font-size: 10px;
}
.nav-item {
  background: transparent;
  color: #aabdaf;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 11px;
  text-align: left;
  padding: 10px 11px;
  border-radius: 4px;
  font-size: 12px;
  margin: 2px 0;
}
.nav-item:hover {
  color: #fff;
  background: #25382e;
}
.nav-item.active {
  background: #d7f06c;
  color: #1c2a24;
  font-weight: 800;
}
.nav-badge {
  margin-left: auto;
  color: #87988d;
  font: 10px "DM Mono";
}
.nav-item.active .nav-badge {
  color: #52702f;
}
.sidebar-bottom {
  margin-top: auto;
}
.user-card {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 14px 8px 0;
  border-top: 1px solid #314139;
  margin-top: 10px;
}
.avatar {
  background: #f4a261;
  color: #3e2718;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 12px;
}
.user-card strong,
.user-card small {
  display: block;
}
.user-card strong {
  color: #eef5ef;
  font-size: 11px;
}
.user-card small {
  color: #82958a;
  font-size: 10px;
  margin-top: 2px;
}
.user-card .icon-button {
  margin-left: auto;
  color: #8ca096;
}
.main-content {
  flex: 1;
  min-width: 0;
}
.topbar {
  height: 70px;
  background: #fff;
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: center;
  padding: 0 4.5%;
  gap: 13px;
}
.crumb {
  display: flex;
  gap: 10px;
  align-items: center;
  color: #a4b0aa;
  font-size: 12px;
}
.crumb strong {
  color: var(--ink);
}
.top-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 21px;
}
.connection {
  display: flex;
  gap: 7px;
  align-items: center;
  color: #718078;
  font: 10px "DM Mono";
}
.icon-button {
  background: transparent;
  color: var(--muted);
  display: grid;
  place-items: center;
  padding: 5px;
  border-radius: 4px;
}
.icon-button:hover {
  background: #eef2ed;
  color: var(--ink);
}
.menu-button,
.close-nav {
  display: none;
}
.content {
  padding: 47px 4.5%;
  max-width: 1450px;
}
.page-heading {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-end;
  margin-bottom: 35px;
}
.page-heading h1 {
  font: 700 42px/1 "Space Grotesk";
  letter-spacing: -0.055em;
  margin: 13px 0 8px;
}
.page-heading.compact {
  margin-bottom: 28px;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 13px;
  margin-bottom: 26px;
}
.metric {
  background: #fff;
  border: 1px solid var(--line);
  padding: 19px;
  display: flex;
  gap: 14px;
  border-radius: 5px;
}
.metric-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 4px;
}
.metric-icon.green {
  background: #d8ecd9;
  color: #285d47;
}
.metric-icon.yellow {
  background: #fbefbf;
  color: #8a6d13;
}
.metric-icon.orange {
  background: #fce1d0;
  color: #9d5127;
}
.metric-icon.blue {
  background: #dceff2;
  color: #357184;
}
.metric span,
.metric small {
  display: block;
  color: var(--muted);
  font-size: 10px;
}
.metric strong {
  display: block;
  font: 700 25px "Space Grotesk";
  margin: 5px 0;
}
.dashboard-grid {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 15px;
}
.panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 5px;
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 23px 24px 18px;
}
.panel-head h3 {
  font: 700 20px "Space Grotesk";
  margin: 9px 0 0;
}
.accent-icon {
  color: #a7bf36;
}
.quick-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-top: 1px solid var(--line);
}
.quick-action {
  display: flex;
  align-items: center;
  gap: 12px;
  text-align: left;
  background: #fff;
  padding: 18px 22px;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  color: var(--ink);
}
.quick-action:nth-child(even) {
  border-right: 0;
}
.quick-action:hover {
  background: #f8faf5;
}
.quick-action > svg {
  margin-left: auto;
  color: #b7c2bb;
}
.quick-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  background: #eef4e7;
  color: #557a43;
  border-radius: 4px;
}
.quick-action strong,
.quick-action small {
  display: block;
}
.quick-action strong {
  font-size: 12px;
}
.quick-action small {
  color: var(--muted);
  font-size: 10px;
  margin-top: 4px;
}
.text-button {
  display: flex;
  align-items: center;
  gap: 5px;
  background: transparent;
  color: var(--green);
  font-size: 11px;
  font-weight: 800;
}
.stock-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 22px;
  padding: 14px 0;
  border-top: 1px solid #edf0ed;
}
.product-symbol {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  background: #f1f4ed;
  color: #829979;
  border-radius: 4px;
}
.stock-row > div:nth-child(2) {
  flex: 1;
}
.stock-row strong,
.stock-row small {
  display: block;
}
.stock-row strong {
  font-size: 12px;
}
.stock-row small {
  font-size: 10px;
  color: var(--muted);
  margin-top: 3px;
}
.stock-value {
  font: 700 13px "Space Grotesk";
  color: #bb6340;
}
.stock-value small {
  display: inline;
}
.recent-panel {
  margin-top: 15px;
  overflow: hidden;
}
.recent-sales {
  border-top: 1px solid var(--line);
}
.recent-sale {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 13px 23px;
  border-bottom: 1px solid #edf0ed;
}
.recent-sale:last-child { border-bottom: 0; }
.recent-sale-icon {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  color: var(--green);
  background: #edf4e5;
  border-radius: 4px;
}
.recent-sale > div { flex: 1; min-width: 0; }
.recent-sale strong,
.recent-sale small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.recent-sale strong { font-size: 11px; }
.recent-sale small { margin-top: 4px; color: var(--muted); font-size: 10px; }
.recent-sale > b { color: var(--green); font: 700 13px "Space Grotesk"; }
.empty {
  min-height: 130px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 10px;
  color: #9aa89f;
  font-size: 12px;
}
.table-panel {
  overflow: hidden;
}
.table-toolbar {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px 19px;
  border-bottom: 1px solid var(--line);
}
.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--line);
  background: #fafcf9;
  padding: 9px 11px;
  width: 290px;
  color: #8c9990;
  border-radius: 4px;
}
.search-box input {
  border: 0;
  outline: 0;
  background: transparent;
  width: 100%;
  font-size: 11px;
}
.result-count {
  margin-left: auto;
  color: var(--muted);
  font: 10px "DM Mono";
}
.table-wrap {
  overflow-x: auto;
}
table {
  border-collapse: collapse;
  width: 100%;
  font-size: 12px;
}
th {
  text-align: left;
  color: #849189;
  font: 10px "DM Mono";
  text-transform: uppercase;
  letter-spacing: 0.06em;
  background: #fafcf9;
}
th,
td {
  padding: 15px 19px;
  border-bottom: 1px solid #edf0ed;
  white-space: nowrap;
}
td {
  color: #35443b;
}
tbody tr:hover {
  background: #fbfcfa;
}
.row-action {
  color: #b1bbb4;
  background: transparent;
}
.row-action:hover {
  color: #b04d35;
}
.loading {
  padding: 45px;
  text-align: center;
  color: var(--muted);
  font-size: 12px;
}
.secondary-button {
  background: #eef1ed;
  color: #405247;
}
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(22, 35, 28, 0.42);
  display: grid;
  place-items: center;
  padding: 20px;
  z-index: 5;
}
.modal {
  width: min(520px, 100%);
  background: #fff;
  border-radius: 6px;
  padding: 26px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.16);
}
.modal-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 23px;
}
.modal h2 {
  font: 700 27px "Space Grotesk";
  margin: 8px 0 0;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.form-grid label:nth-child(1),
.form-grid label:nth-child(2) {
}
.form-grid label:has(input[type="text"]:only-child) {
}
.form-hint {
  grid-column: 1/-1;
  background: #f4f7ee;
  padding: 10px 12px;
  color: #718078;
  font-size: 11px;
  line-height: 1.5;
}
.modal-actions {
  grid-column: 1/-1;
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  margin-top: 5px;
}
.sales-placeholder,
.unavailable {
  text-align: center;
  padding: 75px 25px;
}
.sales-icon {
  margin: auto;
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  background: #edf4e5;
  color: #5c803f;
  border-radius: 50%;
}
.sales-placeholder h2,
.unavailable h2 {
  font: 700 26px "Space Grotesk";
  margin: 19px 0 8px;
}
.sales-placeholder p,
.unavailable p {
  max-width: 490px;
  margin: 0 auto;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}
.sales-fields {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
  margin: 25px 0;
}
.sales-fields span,
.unavailable code {
  background: #f1f4ef;
  color: #56705d;
  padding: 8px 11px;
  border-radius: 3px;
  font: 10px "DM Mono";
}
.unavailable > svg {
  color: #9cbd41;
}
.unavailable code {
  display: inline-block;
  margin-top: 20px;
}
.pos-company-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--green);
  background: #edf4e5;
  padding: 10px 13px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 800;
}
.pos-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(390px, 0.9fr);
  gap: 18px;
  align-items: start;
}
.pos-products {
  min-height: 620px;
}
.product-grid {
  padding: 18px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 12px;
  max-height: 650px;
  overflow-y: auto;
}
.product-card {
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-height: 142px;
  padding: 15px;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: #fff;
  color: var(--ink);
  transition: transform .15s, border-color .15s, box-shadow .15s;
}
.product-card:hover:not(:disabled) {
  transform: translateY(-2px);
  border-color: #9fc16e;
  box-shadow: 0 7px 18px rgba(40, 93, 71, .09);
}
.product-card:disabled {
  cursor: not-allowed;
  opacity: .48;
}
.product-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--green);
  margin-bottom: 5px;
}
.product-card-top strong {
  color: var(--green);
  font: 700 15px "Space Grotesk";
}
.product-card > strong {
  font-size: 12px;
  line-height: 1.35;
}
.product-card small {
  color: var(--muted);
  font: 10px "DM Mono";
}
.stock-ok,
.stock-warning {
  margin-top: auto;
  font-size: 10px;
  font-weight: 800;
}
.stock-ok { color: #668255; }
.stock-warning { color: #b4613b; }
.pos-checkout {
  position: sticky;
  top: 18px;
  overflow: hidden;
}
.cart-list {
  max-height: 238px;
  overflow-y: auto;
  padding: 0 18px;
}
.open-sale-selector {
  display: block;
  padding: 14px 0 6px;
}
.open-sale-selector span {
  display: block;
  margin-bottom: 6px;
  color: var(--muted);
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
}
.open-sale-selector select {
  width: 100%;
  padding: 9px 10px;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: #fff;
  color: var(--ink);
}
.open-sale-summary {
  padding: 10px 0;
  border-bottom: 1px dashed var(--line);
  color: var(--muted);
  font-size: 11px;
}
.open-sale-summary > strong {
  display: block;
  margin-bottom: 6px;
  color: var(--green);
  font-size: 10px;
  text-transform: uppercase;
}
.open-sale-summary div {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 3px 0;
}
.mesa-board {
  margin-bottom: 18px;
  padding: 18px;
}
.mesa-board .panel-head { padding: 0 0 12px; border-bottom: 0; }
.mesa-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(118px, 1fr));
  gap: 10px;
}
.mesa-card {
  display: grid;
  gap: 4px;
  min-height: 84px;
  padding: 12px;
  text-align: left;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  background: #f7fbf5;
  color: var(--ink);
}
.mesa-card strong { font-size: 12px; }
.mesa-card span { color: var(--green); font-size: 11px; font-weight: 800; }
.mesa-card small { color: var(--muted); font-size: 10px; }
.mesa-card.mesa-ocupada { background: #fff5ef; border-color: #f2c6b5; }
.mesa-card.mesa-ocupada span { color: #c35d45; }
.mesa-card.mesa-reservada { background: #fff9e8; border-color: #eadca8; }
.mesa-card.mesa-reservada span { color: #a17b20; }
.mesa-card.selected { outline: 2px solid var(--green); outline-offset: 1px; }
.secondary-button.full { width: 100%; margin-top: 8px; }
.cart-item {
  display: grid;
  grid-template-columns: 1fr auto auto;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  border-top: 1px solid #edf0ed;
}
.cart-item-info {
  min-width: 0;
}
.cart-item-info strong,
.cart-item-info small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cart-item-info strong { font-size: 12px; }
.cart-item-info small { color: var(--muted); font-size: 10px; margin-top: 4px; }
.quantity-control {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--line);
  border-radius: 4px;
  padding: 3px;
}
.quantity-control button {
  display: grid;
  place-items: center;
  width: 23px;
  height: 23px;
  color: var(--green);
  background: #eef4e7;
  border-radius: 3px;
}
.quantity-control b { min-width: 15px; text-align: center; font-size: 11px; }
.checkout-form {
  padding: 18px;
  border-top: 1px solid var(--line);
  background: #fafcf9;
}
.checkout-section-title {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--green);
  font: 800 11px "Space Grotesk";
  margin: 4px 0 12px;
}
.checkout-section-title:not(:first-child) { margin-top: 21px; }
.receipt-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 14px;
}
.receipt-option {
  min-height: 66px;
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: 1fr auto;
  align-items: center;
  gap: 2px 7px;
  padding: 10px;
  text-align: left;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: #fff;
  color: #536159;
  transition: border-color .15s, background .15s, color .15s, transform .15s;
}
.receipt-option:hover {
  transform: translateY(-1px);
  border-color: #a7c96d;
}
.receipt-option.selected {
  border-color: var(--green);
  background: #edf4e5;
  color: var(--green);
  box-shadow: inset 0 0 0 1px var(--green);
}
.receipt-option svg { grid-row: 1 / span 2; }
.receipt-option span { font-size: 11px; font-weight: 800; }
.receipt-option small { color: var(--muted); font: 9px "DM Mono"; }
.receipt-option.selected small { color: #668255; }
.catalog-filters { display: grid; gap: 10px; padding: 0 18px 16px; border-bottom: 1px solid var(--line); }
.catalog-filter-row { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; }
.catalog-filter-row strong { margin-right: 4px; color: var(--muted); font-size: 10px; text-transform: uppercase; letter-spacing: .06em; }
.filter-chip { padding: 7px 10px; border: 1px solid var(--line); border-radius: 999px; background: var(--paper); color: var(--muted); font-size: 11px; }
.filter-chip:hover, .filter-chip.active { border-color: var(--green); background: #edf7ee; color: var(--green); }
.checkout-form label {
  display: grid;
  gap: 6px;
  margin-bottom: 10px;
  color: #526158;
  font-size: 10px;
  font-weight: 800;
}
.checkout-form input,
.checkout-form select {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 4px;
  padding: 10px 11px;
  background: #fff;
  color: var(--ink);
  font-size: 11px;
  outline: 0;
}
.checkout-form input:focus,
.checkout-form select:focus { border-color: var(--green); }
.inline-fields {
  display: grid;
  grid-template-columns: 1fr 1.4fr;
  gap: 10px;
}
.totals {
  border-top: 1px solid var(--line);
  margin-top: 14px;
  padding-top: 12px;
}
.totals > div {
  display: flex;
  justify-content: space-between;
  margin: 5px 0;
  color: var(--muted);
  font-size: 11px;
}
.totals .total-line {
  color: var(--green);
  font: 700 18px "Space Grotesk";
  margin-top: 12px;
}
.change-line b { color: #668255; }
.sale-success {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 10px;
  margin-top: 12px;
  border-radius: 4px;
  background: #d8ecd9;
  color: #285d47;
  font-size: 11px;
}
.sale-success span { flex: 1; }
.sale-success button {
  display: grid;
  place-items: center;
  color: #285d47;
  background: transparent;
}
.print-sale-button {
  display: inline-flex !important;
  align-items: center;
  gap: 6px;
  padding: 7px 9px;
  border: 1px solid rgba(40, 93, 71, .25);
  border-radius: 4px;
  font-size: 11px;
}
.print-sale-button:hover { background: rgba(255,255,255,.7); }
.row-actions { display: flex; align-items: center; gap: 5px; }
.row-action { display: grid; place-items: center; padding: 7px; border-radius: 4px; }
.row-action.danger:hover { color: #b04d35; background: #fff1ed; }
.print-only { display: none; }
.receipt-paper { width: 760px; max-width: 100%; margin: 0 auto; color: #111; font: 13px Arial, sans-serif; }
.receipt-header { display: flex; flex-direction: column; align-items: center; gap: 4px; text-align: center; }
.receipt-header strong { font-size: 19px; }
.receipt-title { display: flex; justify-content: space-between; border: 1px solid #111; padding: 12px; margin: 22px 0 12px; }
.receipt-customer { display: grid; gap: 4px; margin-bottom: 15px; }
.receipt-paper table { width: 100%; border-collapse: collapse; }
.receipt-paper th, .receipt-paper td { padding: 8px; border: 1px solid #bbb; white-space: normal; color: #111; }
.receipt-paper th:not(:first-child), .receipt-paper td:not(:first-child) { text-align: right; }
.receipt-totals { display: grid; justify-content: end; gap: 6px; margin-top: 15px; text-align: right; }
.receipt-paper > p:last-child { text-align: center; margin-top: 24px; }
@media print {
  @page { margin: 12mm; }
  body * { visibility: hidden !important; }
  .print-only, .print-only * { visibility: visible !important; }
  .print-only { display: block !important; position: absolute; inset: 0; width: 100%; }
}
.form-error { display: flex; align-items: center; gap: 7px; }
.scrim {
  display: none;
}
@media (max-width: 900px) {
  .sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 10;
    transform: translateX(-100%);
    transition: transform 0.25s;
  }
  .sidebar.open {
    transform: translateX(0);
  }
  .scrim {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(13, 25, 19, 0.4);
    z-index: 9;
  }
  .menu-button,
  .close-nav {
    display: grid;
  }
  .metric-grid {
    grid-template-columns: 1fr 1fr;
  }
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
  .pos-layout {
    grid-template-columns: 1fr;
  }
  .pos-checkout {
    position: static;
  }
  .content {
    padding: 35px 5%;
  }
}
@media (max-width: 580px) {
  .login-page {
    display: block;
  }
  .login-art {
    min-height: 245px;
    padding: 25px 8%;
  }
  .login-quote {
    margin: auto 0 0;
  }
  .login-quote h1 {
    font-size: 54px;
    margin: 15px 0 0;
  }
  .login-quote p {
    display: none;
  }
  .login-panel {
    min-height: calc(100vh - 245px);
    align-items: flex-start;
    padding-top: 48px;
  }
  .login-form {
    width: 84%;
  }
  .mobile-brand {
    display: flex;
    align-items: center;
    gap: 9px;
    font: 700 13px "Space Grotesk";
    letter-spacing: 0.1em;
    margin-bottom: 44px;
  }
  .login-form h2 {
    font-size: 31px;
  }
  .login-footer {
    display: none;
  }
  .topbar {
    height: 62px;
    padding: 0 5%;
  }
  .connection {
    display: none;
  }
  .content {
    padding: 30px 5%;
  }
  .page-heading {
    display: block;
    margin-bottom: 25px;
  }
  .page-heading .primary-button {
    margin-top: 20px;
  }
  .page-heading h1 {
    font-size: 36px;
  }
  .metric-grid {
    gap: 8px;
  }
  .metric {
    padding: 13px;
    gap: 9px;
  }
  .metric-icon {
    width: 31px;
    height: 31px;
  }
  .metric strong {
    font-size: 21px;
  }
  .metric span {
    font-size: 9px;
  }
  .metric small {
    font-size: 9px;
  }
  .quick-grid {
    grid-template-columns: 1fr;
  }
  .quick-action {
    border-right: 0 !important;
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
  .form-hint,
  .modal-actions {
    grid-column: 1;
  }
  .pos-company-badge {
    margin-top: 14px;
    width: fit-content;
  }
  .inline-fields {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .receipt-options {
    grid-template-columns: 1fr;
  }
  .search-box {
    width: 100%;
  }
  .table-toolbar {
    display: block;
  }
  .result-count {
    display: block;
    margin-top: 10px;
  }
  .table-panel {
    margin: 0 -1px;
  }
}

/* Nueva identidad visual: cálida, moderna y orientada a negocios de alimentos. */
:root {
  --shadow-soft: 0 12px 30px rgba(29, 41, 59, .07);
  --shadow-card: 0 18px 45px rgba(29, 41, 59, .10);
}
body {
  background: #f5f7fb;
  color: var(--ink);
  letter-spacing: .005em;
}
h1, h2, h3, strong, .brand-mark, .primary-button, .secondary-button, .nav-item, .metric strong, .quick-action strong,
.product-card > strong, .cart-item-info strong, .receipt-option span, .modal h2 {
  font-family: "Sora", sans-serif;
}
.login-page { background: #f5f7fb; }
.login-art {
  background: linear-gradient(145deg, #1d293b 0%, #263b56 58%, #345775 100%);
  color: #f8fbff;
  box-shadow: inset -30px 0 80px rgba(14, 27, 45, .16);
}
.brand-mark { letter-spacing: .14em; }
.brand-mark.dark { color: var(--lime); }
.login-quote span, .eyebrow, .nav-label { color: #a9b8ca; }
.login-quote h1 { font-family: "Sora", sans-serif; font-weight: 700; letter-spacing: -.075em; }
.login-quote em, .page-heading em { color: #ff8065; }
.login-quote p { color: #cad5e3; }
.art-grid {
  background:
    linear-gradient(135deg, transparent 49.8%, rgba(255, 200, 87, .18) 50%, transparent 50.4%),
    linear-gradient(45deg, transparent 49.8%, rgba(255, 128, 101, .14) 50%, transparent 50.4%);
}
.login-panel { background: #fffdf9; }
.login-form h2 { font-family: "Sora", sans-serif; color: #1d293b; }
.login-form label, .modal label { color: #46546a; }
.login-form input, .modal input {
  border-color: #dbe3ee;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 3px 10px rgba(29, 41, 59, .025);
}
.login-form input:focus, .modal input:focus { border-color: var(--green); box-shadow: 0 0 0 4px rgba(228, 91, 69, .12); }
.primary-button, .secondary-button {
  min-height: 42px;
  border-radius: 10px;
  transition: transform .18s ease, box-shadow .18s ease, background .18s ease;
}
.primary-button {
  background: linear-gradient(135deg, #e45b45, #c9443d);
  box-shadow: 0 8px 18px rgba(201, 68, 61, .22);
}
.primary-button:hover { background: linear-gradient(135deg, #ed6d55, #d44a42); transform: translateY(-2px); box-shadow: 0 12px 24px rgba(201, 68, 61, .28); }
.primary-button:active, .secondary-button:active, .quick-action:active { transform: translateY(0); }
.primary-button:disabled { opacity: .55; transform: none; box-shadow: none; }
.secondary-button { border: 1px solid #dce4ee; background: #f4f6fa; color: #46546a; }
.secondary-button:hover { background: #e9edf4; transform: translateY(-1px); }
.form-error, .api-error { border: 1px solid #ffd5cc; border-radius: 10px; background: #fff3f0; color: #b84b3c; }
.app-shell { background: #f5f7fb; }
.sidebar {
  background: linear-gradient(180deg, #1d293b 0%, #162235 100%);
  color: #b7c4d4;
  box-shadow: 8px 0 30px rgba(22, 34, 53, .08);
}
.workspace { margin: 25px 8px 0; border: 1px solid rgba(255,255,255,.08); border-radius: 10px; padding: 10px 12px; color: #e6edf5; background: rgba(255,255,255,.04); }
.workspace > svg { color: #8292a8; }
.workspace-dot, .connection span { background: #57c58a; box-shadow: 0 0 0 4px rgba(87,197,138,.12); }
.nav-label { color: #8191a7; }
.nav-item { border-radius: 10px; color: #b6c2d2; }
.nav-item:hover { background: rgba(255,255,255,.07); color: #fff; }
.nav-item.active { background: linear-gradient(90deg, rgba(255, 200, 87, .18), rgba(228, 91, 69, .11)); color: #fff; box-shadow: inset 3px 0 0 var(--lime); }
.nav-item.active svg { color: var(--lime); }
.nav-badge { border-radius: 99px; background: #ff8065; color: #fff; }
.nav-item.active .nav-badge { background: var(--lime); color: #1d293b; }
.user-card { border-top-color: rgba(255,255,255,.09); }
.avatar { background: linear-gradient(135deg, #ff8065, #e45b45); border-radius: 11px; box-shadow: 0 7px 16px rgba(228,91,69,.22); }
.user-card strong { color: #f4f7fb; }
.user-card small { color: #92a2b6; }
.main-content { background: #f5f7fb; }
.topbar { background: rgba(255,255,255,.86); border-bottom-color: #e7ecf3; backdrop-filter: blur(14px); }
.crumb { color: #8190a4; }
.crumb strong { color: #25344a; }
.connection { color: #6d7c91; }
.icon-button { border-radius: 9px; color: #8492a5; transition: background .18s, color .18s, transform .18s; }
.icon-button:hover { color: var(--green); background: #fff0eb; transform: translateY(-1px); }
.content { background: radial-gradient(circle at 100% 0%, rgba(255, 200, 87, .09), transparent 28%), #f5f7fb; }
.page-heading h1 { font-family: "Sora", sans-serif; color: #1d293b; letter-spacing: -.06em; }
.page-heading .muted, .muted { color: #718096; }
.metric, .panel { border: 1px solid #e6ebf2; border-radius: 16px; background: rgba(255,255,255,.94); box-shadow: var(--shadow-soft); }
.metric { transition: transform .2s, box-shadow .2s; }
.metric:hover { transform: translateY(-3px); box-shadow: var(--shadow-card); }
.metric-icon { border-radius: 12px; }
.metric-icon.green { background: #fff0eb; color: #e45b45; }
.metric-icon.yellow { background: #fff7dc; color: #d89a18; }
.metric-icon.orange { background: #fff0e7; color: #e27a42; }
.metric-icon.blue { background: #eaf1ff; color: #537fc9; }
.metric strong { color: #1d293b; }
.quick-action { border-color: #edf0f5; border-radius: 12px; background: #fff; transition: transform .2s, box-shadow .2s, border .2s; }
.quick-action:hover { background: #fffaf6; border-color: #ffd0c5; box-shadow: 0 10px 25px rgba(228,91,69,.11); transform: translateY(-3px); }
.quick-icon { border-radius: 10px; background: #fff0eb; color: var(--green); }
.quick-action > svg { color: #a0aabd; }
.text-button { border-radius: 8px; color: var(--green); }
.text-button:hover { background: #fff0eb; }
.stock-row, .recent-sale { border-bottom-color: #edf0f5; }
.product-symbol, .recent-sale-icon { border-radius: 10px; background: #fff4df; color: #d79a22; }
.stock-value, .recent-sale > b { color: var(--green); }
.empty { color: #94a1b2; }
.table-panel { overflow: hidden; }
.table-toolbar { background: #fff; border-bottom-color: #edf0f5; }
.search-box { border-radius: 10px; background: #f7f9fc; border-color: #e1e7ef; }
.search-box:focus-within { border-color: var(--green); box-shadow: 0 0 0 4px rgba(228,91,69,.09); }
.search-box input { color: #25344a; }
.result-count { color: #8a97a8; }
th { background: #fbfcfe; color: #8290a2; border-bottom-color: #edf0f5; }
td { color: #405069; border-bottom-color: #edf0f5; }
tbody tr:hover { background: #fffaf7; }
.row-action { border-radius: 8px; color: #9aa7b8; }
.row-action:hover { color: var(--green); background: #fff0eb; }
.row-action.danger:hover { color: #c9443d; background: #fff0ed; }
.modal-backdrop { background: rgba(23, 35, 53, .58); backdrop-filter: blur(5px); }
.modal { border: 1px solid rgba(255,255,255,.7); border-radius: 18px; box-shadow: 0 25px 80px rgba(18, 31, 48, .24); }
.modal h2 { color: #1d293b; letter-spacing: -.04em; }
.form-hint { border-radius: 10px; background: #fff7e2; color: #86682b; }
.modal select, .modal input { border-color: #dbe3ee !important; border-radius: 10px !important; }
.pos-company-badge { border: 1px solid #ffd4c8; border-radius: 999px; background: #fff4f0; color: #bd4d3d; }
.pos-layout { gap: 22px; }
.pos-products, .pos-checkout { border-radius: 18px; }
.product-card { border: 1px solid #e7ebf1; border-radius: 14px; background: #fff; box-shadow: 0 5px 16px rgba(29,41,59,.035); transition: transform .2s, border .2s, box-shadow .2s; }
.product-card:hover:not(:disabled) { border-color: #ffb8a8; box-shadow: 0 13px 28px rgba(228,91,69,.13); transform: translateY(-4px); }
.product-card-top strong { color: var(--green); }
.product-card small { color: #8a97a8; }
.stock-ok { color: #4b9b70; }
.stock-warning { color: #d1773f; }
.cart-list { background: #fbfcfe; border-bottom-color: #edf0f5; }
.cart-item { border-bottom-color: #e7ebf1; }
.quantity-control { border: 1px solid #e1e7ef; border-radius: 9px; background: #fff; }
.quantity-control button { border-radius: 6px; color: #68778d; }
.quantity-control button:hover { background: #fff0eb; color: var(--green); }
.checkout-form { background: #fff; }
.checkout-section-title { color: #263950; }
.checkout-section-title svg { color: var(--green); }
.checkout-form input, .checkout-form select { border-color: #dbe3ee; border-radius: 10px; }
.checkout-form input:focus, .checkout-form select:focus { border-color: var(--green); box-shadow: 0 0 0 4px rgba(228,91,69,.09); }
.receipt-option { border-color: #dfe6ef; border-radius: 12px; background: #fff; }
.receipt-option:hover { border-color: #ffb8a8; background: #fffaf7; }
.receipt-option.selected { border-color: var(--green); background: #fff0eb; box-shadow: inset 0 0 0 1px var(--green); }
.receipt-option.selected small { color: #bd4d3d; }
.catalog-filters { background: #fff; }
.filter-chip { border-color: #dfe6ef; border-radius: 999px; background: #fff; color: #718096; }
.filter-chip:hover, .filter-chip.active { border-color: var(--green); background: #fff0eb; color: var(--green); }
.totals { border-top-color: #e7ebf1; }
.totals .total-line { color: var(--green); }
.change-line b { color: #4b9b70; }
.sale-success { border: 1px solid #bfe6cd; border-radius: 10px; background: #effbf3; color: #32815a; }
.print-sale-button { border-color: #bfe6cd; color: #32815a !important; }
.print-sale-button:hover { background: #fff; }

@media (max-width: 900px) {
  .sidebar { box-shadow: 15px 0 35px rgba(18,31,48,.2); }
  .content { padding-left: 5%; padding-right: 5%; }
}
@media (max-width: 580px) {
  .login-panel { background: #fffdf9; }
  .content { padding-left: 5%; padding-right: 5%; }
  .modal { border-radius: 14px; padding: 20px; }
  .catalog-filters { padding-left: 12px; padding-right: 12px; }
}
@media print {
  .print-only, .print-only * { visibility: visible !important; }
  .print-only { display: block !important; position: absolute; inset: 0; width: 100%; }
}

.area-tabs { display: flex; flex-wrap: wrap; gap: 10px; padding: 0 22px 18px; }
.mesa-atendiendo { background: #eaf1ff; border-color: #a8c3ef; color: #254c86; }
.mesa-ocupada { background: #fff4dc; border-color: #e6c579; color: #725209; }
.mesa-libre { background: #effbf3; border-color: #bfe6cd; color: #276847; }
.mesa-card.selected { outline: 3px solid #334b6d; outline-offset: 2px; }
.table-legend { display: flex; flex-wrap: wrap; gap: 16px; padding: 10px 22px 20px; color: #61718a; font-size: 12px; }
.table-actions { display: flex; gap: 12px; margin: 0 0 18px; }
.cashier-layout { grid-template-columns: minmax(0, 900px); justify-content: center; }
.marketing-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; }
.marketing-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.marketing-ranking { padding: 0 22px 22px; }
.ranking-row { position: relative; display: grid; text-align: left; gap: 8px; padding: 12px 0; width: 100%; border-bottom: 1px solid var(--line); background: transparent; }
.ranking-row b { font-size: 12px; color: #52677c; }
.ranking-bar { display: block; height: 5px; background: #537fc9; border-radius: 5px; }
.roles-field { grid-column: 1 / -1; border: 1px solid var(--line); padding: 16px; border-radius: 10px; }
.roles-field label { display: inline-flex; align-items: center; margin: 10px 20px 0 0; gap: 8px; }
.roles-field input { width: 18px; height: 18px; }
.panel-head select { max-width: 100%; border: 1px solid var(--line); border-radius: 8px; padding: 10px; }
@media (max-width: 900px) { .marketing-grid, .marketing-metrics { grid-template-columns: 1fr; } .panel-head { flex-wrap: wrap; gap: 12px; } }
```

