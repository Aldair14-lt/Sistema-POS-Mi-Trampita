# Recreo Mi Trampita: áreas, marketing, roles y cobro transaccional

Esta guía describe la versión 04. La versión actual requiere también la sección 05, integrada al final de los mismos dos archivos SQL: [CUENTAS_ONLINE_KDS.md](CUENTAS_ONLINE_KDS.md). Desde 05 el stock se descuenta al enviar pedidos, los abonos se registran por separado y el cierre exige pago completo y entrega. Las instrucciones de abajo sobre stock al cobrar corresponden a 04.

Los cambios ya están aplicados en el workspace. El archivo [CODIGO_IMPLEMENTADO.md](CODIGO_IMPLEMENTADO.md) contiene el código completo en bloques separados, encabezados con la ruta exacta de cada archivo. No es necesario volver a pegarlo.

## 1. Migrar la base de datos

El proyecto existente usa PostgreSQL, Java 21 y tablas singulares. La migración 04 conserva los IDs y las relaciones, y renombra `mesa → mesas`, `venta → ventas`, `cliente → clientes`. Crea `areas(id, nombre, estado)`, adapta `mesas(id, numero, estado, capacidad, area_id)` y añade `ventas.mesa_id`.

**Base PostgreSQL actual:** detener el backend, crear un respaldo y ejecutar únicamente la sección `04. MIGRACIÓN DE BASE EXISTENTE / FINALIZACIÓN DEL ESQUEMA` de `database/scripts/pos_postgresql.sql`. Después aplica la sección 05 del mismo archivo. Si ya tiene 04, ejecuta solo 05; si ya tiene 05, no repitas. No ejecutar el instalador completo sobre una base existente.

Crear el respaldo desde PowerShell:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita'
& 'C:\Program Files\PostgreSQL\17\bin\pg_dump.exe' -h localhost -U postgres -W -d pos_db --format=custom --file 'respaldo-pos-antes-04.dump'
if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el respaldo' }
```

Introduce la contraseña cuando el cliente la solicite. Después, en pgAdmin/Query Tool, abre `database/scripts/pos_postgresql.sql`, selecciona únicamente la sección 04 (o las secciones 03 y 04 si la base no tiene `mesa` o `estado_venta`) y ejecuta la selección; después ejecuta la sección 05 del mismo archivo. No uses `psql -f` con el instalador completo para una base existente.

**Instalación nueva PostgreSQL:** crear una base vacía y ejecutar completo `database/scripts/pos_postgresql.sql`. Si una base histórica no tiene `mesa` o `estado_venta`, ejecutar únicamente las secciones 03 y 04 del archivo unificado. Si ya tiene el esquema previo con esas tablas/columnas, ejecutar las secciones 04 y 05. Si ya tiene 04, ejecutar únicamente 05.

**MySQL:** para una base nueva, ejecutar completo `database/scripts/pos_mysql.sql` con MySQL Workbench o el cliente. Para una base existente, ejecutar únicamente las secciones pendientes 03, 04 y 05 del archivo unificado (04 y 05 si ya tiene `mesa` y `estado_venta`; solo 05 si ya tiene 04). El DDL requiere MySQL 8.0.16 o superior. MySQL confirma DDL implícitamente: detener el backend, respaldar antes de aplicar y restaurar el respaldo si una ejecución queda a medias. La conversión de inventario y métricas sí se realiza dentro de una transacción.

Las dos migraciones incluyen las semillas de Salón Principal, Terraza, Zona Recreacional y Piscina; mesas numeradas del 1 al 12, sin duplicar números existentes; y roles ADMIN, MOZO y CAJA. Las mesas existentes quedan inicialmente en Salón Principal para que el administrador las reasigne desde Mesas.

En la instalación SQL se crean las cuentas `admin` / `AdminPOS#2026`, `mozo` / `MozoPOS#2026` y `caja` / `CajaPOS#2026`, cada una con su rol. Sus contraseñas están almacenadas con BCrypt; cámbialas desde **Usuarios** antes de operar en producción.

La migración devuelve al stock las unidades de comandas ABIERTAS que el backend anterior ya había descontado. A partir de entonces se descuentan al cobrar. Las ventas CERRADAS conservan su stock y aportan el historial inicial de fidelización. Las comandas antiguas sin mesa reciben una mesa propia. Un índice impide dos comandas abiertas en la misma mesa. La migración rechaza una segunda ejecución.

Se mantiene `ddl-auto=validate`; migrar antes de iniciar el backend actualizado. **La base de trabajo original no fue migrada durante esta implementación:** las verificaciones se hicieron en bases independientes.

## 2. Iniciar el sistema

Terminal 1 — backend:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\backend\SistemaPOS'
.\mvnw.cmd spring-boot:run
```

Terminal 2 — frontend:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\frontend'
npm.cmd run dev
```

Frontend: http://localhost:5173. Backend: http://localhost:9090. El proxy de Vite permite usar las cookies de sesión en el mismo origen; en despliegue se debe conservar un proxy del frontend hacia `/api`.

Para MySQL, establecer en la terminal del backend `DB_URL=jdbc:mysql://localhost:3306/pos_db`, `DB_DRIVER=com.mysql.cj.jdbc.Driver`, `DB_USERNAME` y `DB_PASSWORD` con la configuración de esa instalación.

Si una instalación no contiene usuarios, establecer `BOOTSTRAP_ADMIN_PASSWORD` antes del primer arranque para crear el usuario `admin`. Los usuarios existentes no son reemplazados. Las contraseñas antiguas se convierten a BCrypt en su primer inicio válido; los usuarios nuevos se guardan con BCrypt. La contraseña configurada para bootstrap debe ser privada; eliminar esa variable después de inicializar. En HTTPS, configurar `SESSION_COOKIE_SECURE=true`.

## 3. Operación y permisos

| Función | ADMIN | MOZO / VENTAS | CAJA |
|---|---|---|---|
| Ver áreas, mesas y comandas abiertas | Sí | Sí | Sí |
| Ocupar mesa, crear pedido, añadir productos e imprimir comanda | Sí | Sí | No |
| Cobrar e imprimir comprobante | Sí | No | Sí |
| Liberar mesa sin pedido | Sí | No | Sí |
| Configuración, usuarios, inventario, reportes y marketing | Sí | No | No |

ADMIN asigna los roles desde **Usuarios**. Los roles son fijos; la pantalla Roles sirve de consulta. Se reconocen los nombres antiguos VENTAS/VENDEDOR como MOZO, CAJERO como CAJA y ADMINISTRADOR como ADMIN. Las asignaciones se normalizan durante la migración.

Los permisos se comprueban en el servidor y en la interfaz. La identidad del mozo se obtiene de la sesión: un `usuarioId` enviado por el navegador no permite suplantarlo. Los cambios de rol y bloqueos se comprueban en cada petición. Las fichas mínimas del POS no exponen los indicadores de marketing.

Estados: **LIBRE → OCUPADA** al ocupar una mesa; **ATENDIENDO** al registrar su comanda; **LIBRE** después del cobro. También se puede registrar directamente una comanda en una mesa libre. Una mesa con pedido no puede liberarse ni editarse manualmente.

Los pedidos se guardan en el servidor y pueden imprimirse como comanda completa para cocina. El comprobante se imprime después del cobro. Esta entrega conserva la impresión local; no incorpora envío automático a impresoras de cocina ni emisión electrónica ante SUNAT.

## 4. Reglas de inventario y marketing

- Los precios se fijan en cada línea al enviar el pedido. Los adicionales con otro precio conservan su propia línea.
- Pedir no reserva ni descuenta stock. Su disponibilidad definitiva se comprueba al cobrar; otra mesa puede consumirlo antes.
- El cobro bloquea la venta, la mesa, los productos ordenados por ID y el cliente. Un fallo revierte toda la transacción.
- Una venta cerrada no se puede cobrar otra vez. La versión de Producto impide que una edición antigua sobreescriba el stock actualizado.
- `frecuencia_visitas` cuenta ventas cobradas; `total_gastado` suma sus totales con IGV. No son campos editables por la API.
- Marketing muestra top 10 por visitas y gasto, cumpleaños del mes en America/Lima, consumo por producto y segmentos configurados en la interfaz.
- El consumo por producto muestra unidades e importes **sin IGV**. El gasto acumulado del cliente **incluye IGV**.
- Los segmentos de la pantalla son: frecuentes con 5+ visitas, consumo acumulado de S/ 500+ y nuevos con 0–1 ventas cobradas.

## 5. Archivos principales y contratos

| Componente | Ruta |
|---|---|
| Seguridad y sesiones | `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/config/SecurityConfig.java`, `security/`, `controller/AuthController.java` |
| Records y respuestas del POS | `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/dto/` |
| Áreas y mesas | `entity/Area.java`, `entity/Mesa.java`, `controller/AreaController.java`, `controller/MesaController.java`, `service/AreaService.java`, `service/MesaService.java` dentro del mismo paquete |
| Cobro ACID | `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/service/VentaService.java` |
| Marketing | `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/controller/MarketingController.java`, `service/MarketingService.java` |
| Interfaz | `frontend/src/App.jsx`, `SalesPos.jsx`, `TablesView.jsx`, `MarketingDashboard.jsx`, `Receipt.jsx`, `permissions.js`, `api.js` |

Endpoints operativos: `GET /api/areas`, `GET /api/mesas?areaId=...`, `PATCH /api/mesas/{id}/abrir`, `PATCH /api/mesas/{id}/liberar`, `GET /api/ventas/abiertas`, `POST /api/ventas`, `PATCH /api/ventas/{id}/items` y `PATCH /api/ventas/{id}/cerrar`.

Marketing: `GET /api/marketing`, `/api/marketing/frecuentes`, `/api/marketing/cumpleaneros?mes=10` y `/api/marketing/clientes/{id}/consumo`.

Configuración fiscal usa `/api/configuracion`. Se conserva `/api/empresas` como alias administrativo para compatibilidad; las pantallas usan Configuración. `/api/pos/configuracion` y `/api/pos/clientes` proporcionan los datos necesarios para atender y cobrar.

Las escrituras requieren sesión y el token obtenido en `GET /api/auth/csrf`. El cliente `api.js` ya lo gestiona. Los errores devuelven `{ status, message }`: 400 para validaciones, 401 para sesión, 403 para permisos y 409 para stock/conflictos.

Ejemplo de comanda:

```json
{
  "empresaId": 1,
  "clienteId": 1,
  "tipoComprobanteId": 1,
  "numeroComprobante": "B001-PEDIDO-001",
  "mesaId": 1,
  "items": [{ "productoId": 1, "cantidad": 2 }]
}
```

Usar IDs existentes y un número único. También se puede enviar `cliente` con documento, nombre, contacto y fecha de nacimiento en lugar de `clienteId`; no se aceptan ambos a la vez.

Ejemplo de cobro en efectivo:

```json
{ "metodoPago": "efectivo", "montoRecibido": 100.00 }
```

Al editar productos mediante API, incluir la `version` obtenida al leerlos. La interfaz ya la envía.

## 6. Validación reproducible

Backend:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\backend\SistemaPOS'
.\mvnw.cmd clean test -q
```

Frontend:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\frontend'
npm.cmd run build
```

En este equipo Windows bloqueó el borrado de una carpeta previa en `dist`. Se verificaron compilaciones con un directorio de salida nuevo y también con `npm.cmd run build -- --emptyOutDir=false`.

La suite contiene 11 pruebas: carga del contexto y 10 escenarios funcionales, incluyendo permisos, validación de mesa, comanda, consolidación de cantidades, efectivo insuficiente, rollback de varias líneas, cobros concurrentes, doble cobro, cumpleaños, protección de identidad y edición de inventario desactualizada.

La prueba `backend/SistemaPOS/scripts/verify_http_flow.py` usa HTTP real, cookies y CSRF. Ejecutarla **solo contra una base de pruebas**, porque crea usuarios, productos, clientes y ventas. Toma la contraseña de `POS_TEST_PASSWORD`:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\backend\SistemaPOS'
python scripts/verify_http_flow.py --url http://localhost:9091 --user admin
```

Se verificaron arranque con `ddl-auto=validate` y flujo HTTP completo en PostgreSQL y MySQL. También se probaron las migraciones con ventas cerradas, comandas abiertas, una comanda antigua sin mesa y rechazo de una segunda ejecución, sin doble devolución de inventario.

No se pudo realizar revisión visual en navegador: la herramienta no expuso ningún navegador disponible.

Referencias de diseño: [persistencia de autenticación en Spring Security](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/persistence.html) y [protección CSRF para clientes JavaScript](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html).
