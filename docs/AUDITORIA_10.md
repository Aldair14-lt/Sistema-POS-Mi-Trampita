# Auditoría funcional y de seguridad — 8 de octubre de 2026

Se revisaron frontend, backend, permisos, contratos HTTP, transacciones, esquema y dependencias. Se corrigieron los problemas descritos abajo y se verificaron los seis módulos, incluyendo menú público con carrito, recojo y delivery. Los resultados corresponden a las pruebas ejecutadas; no constituyen una garantía de ausencia absoluta de errores o vulnerabilidades.

## Correcciones y archivos

| Problema encontrado | Corrección | Archivos principales |
| --- | --- | --- |
| Un MOZO podía añadir productos a pedidos externos usando su ID | Validación del origen en el servidor; responde 403 | `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/controller/VentaController.java` |
| POST de catálogos aceptaba un ID existente y podía sobrescribirlo | Creaciones siempre generan su propio ID | `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/controller/CatalogoController.java` |
| Una contraseña cambiada no revocaba sesiones anteriores | Huella privada de credencial en sesión, comparación constante y revocación | `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/security/SessionCredentials.java`, `SessionUserFilter.java` |
| Era posible eliminar, bloquear o quitar el rol al último administrador | Protección transaccional, incluyendo desactivaciones concurrentes | `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/controller/UsuarioController.java`, `repositorio/RolRepository.java`, `UsuarioRolRepository.java` |
| Acceso sin límite de fallos y diferencias de tiempo para usuarios inexistentes | Límite temporal por usuario/IP, BCrypt de comparación y rechazo seguro de contraseñas mayores a 72 bytes UTF-8 | `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/security/LoginAttemptLimiter.java`, `controller/AuthController.java` |
| Cuerpos JSON privados sin límite antes de deserializar | Máximo 4 KiB en login, 32 KiB en pedido público y 256 KiB en otras escrituras; también sin Content-Length | `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/security/ApiRequestBodyFilter.java` |
| Consultas públicas y conexiones SSE sin límites suficientes | Consultas públicas 120/min/IP; envíos 10/min/IP; SSE máximo 6 por usuario y 500 globales, con reautorización al reconectar | `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/security/PublicOrderRateLimiter.java`, `service/OperationEvents.java` |
| Producto recibía entidades anidadas incompletas | DTO Record con `@Valid`, `@NotNull`, `@DecimalMin`, `@Digits` y resolución de referencias existentes | `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/dto/ProductoRequest.java`, `service/ProductoService.java` |
| Tipos de comprobante arbitrarios permitían crear cuentas imposibles de emitir | Rechazo al configurar o utilizar un tipo no soportado, antes del descuento de stock | `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/controller/CatalogoController.java`, `service/VentaService.java` |
| Una respuesta perdida al añadir productos podía duplicar la tanda y el stock descontado | UUID por operación, huella del contenido, bloqueo de venta, registro único y reintento sin repetir cambios | `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/entity/OperacionComanda.java`, `service/VentaService.java`, `frontend/src/SalesPos.jsx` |
| Imprimir un adicional repetía los productos anteriores | La impresión filtra únicamente los detalles de la nueva tanda | `frontend/src/SalesPos.jsx`, `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/dto/VentaResponse.java` |
| Recargar tras un pago incierto perdía su UUID | Conservación del cuerpo y UUID en sessionStorage por cuenta y recuperación al abrir el cobro | `frontend/src/utils/pendingOperation.js`, `frontend/src/components/cashier/PaymentModal.jsx` |
| JSON corrupto, respuesta vacía o corte al leer podían confundirse con operaciones confirmadas | Errores recuperables 502/0, timeout de 30 segundos también durante lectura y reintentos con la misma operación | `frontend/src/api.js`, `frontend/src/public/publicApi.js` |
| Navegadores con almacenamiento restringido podían dejar la página vacía | Acceso protegido al getter de localStorage/sessionStorage; la interfaz continúa usando memoria | `frontend/src/utils/storage.js`, `frontend/src/public/PublicMenu.jsx`, `frontend/src/App.jsx` |
| Formularios rechazaban precios decimales o permitían dobles envíos | `step="0.01"`, guardas inmediatas y botones deshabilitados; contraseñas sin recorte de espacios | `frontend/src/App.jsx`, `frontend/src/SalesPos.jsx` |
| El código de seguimiento podía pasar como referrer a otros sitios | Política `no-referrer` | `frontend/index.html` |
| Dependencias con avisos de vulnerabilidad | Actualización de source-map-js a 1.2.2, Jackson a 3.1.7 y Tomcat a 11.0.25 | `frontend/package-lock.json`, `backend/SistemaPOS/pom.xml` |
| Dependencias instaladas y compilados estaban dentro de Git | Exclusión del índice, conservando los archivos locales; instalaciones reproducibles con lockfile | `.gitignore`, `frontend/package.json` |

Las ventas, pagos, anulaciones, comandas y cambios de stock conservan `@Transactional`. Los importes del navegador no son autoridad: el servidor recalcula precios, IGV, saldo y vuelto. Las restricciones únicas y bloqueos de la base protegen los cobros y las comandas repetidas.

Los límites de solicitudes se mantienen en memoria por instancia. Para varias instancias o exposición pública, el proxy debe aplicar límites adicionales por cliente y distribuir las conexiones correctamente. No se confía en un `X-Forwarded-For` enviado libremente por el visitante.

## Verificaciones ejecutadas

| Verificación | Resultado |
| --- | --- |
| Java: contratos, permisos, stock, rollback, pagos, fiscalidad interna, KDS, web y concurrencia | 63 pruebas, 0 errores, 0 fallos, 0 omitidas |
| Frontend: transporte, respuestas inciertas, CSRF, almacenamiento y recuperación de UUID | 7 pruebas aprobadas |
| Compilación React/Vite y empaquetado Spring Boot | Aprobados |
| Instalación completa y actualización 05–10 en PostgreSQL 17 y MySQL 9.6 aislados | Aprobadas; históricos y contraseñas preservados; reejecución rechazada |
| Flujos HTTP con sesiones y CSRF sobre ambos motores reales | Aprobados |
| Edge: caja, arqueo, Yape parcial, efectivo/vuelto, factura A4, ticket 80 mm, reporte A4, WhatsApp, mesas, marketing y roles | Aprobados, sin errores JavaScript |
| Edge: menú público, categorías, agotados, carrito persistente, móvil de 320/390 px, recojo/delivery y seguimiento | Aprobados, sin errores JavaScript ni llamadas privadas desde el cliente público |
| Edge: respuesta de comanda perdida/vacía y JSON de pago corrupto, recarga y reintento | Misma operación, una tanda, un descuento y un abono; impresión sólo de los adicionales |
| Dependencias Java consultadas en OSV | 88 dependencias de ejecución; 0 paquetes con avisos conocidos al verificar |
| npm audit | 0 vulnerabilidades conocidas al verificar |
| PDF generados en navegador | Factura y cierre: 209.89 × 297.01 mm; ticket: 80.09 × 297.01 mm; una página en cada fixture |

Evidencias y resultados JSON en [`docs/validation/audit10/`](validation/audit10/). Los datos de esas capturas pertenecen a bases QA. El esquema probado incluye los `CREATE TABLE` y `ALTER TABLE` de los dos archivos consolidados.

El build inicial encontró un bloqueo de `frontend/dist` en Windows. La verificación final utilizó un directorio de salida nuevo. El empaquetado también requirió cerrar los backends temporales que tenían abierto el JAR; tras liberarlo se generó correctamente.

Referencias de dependencias: [aviso source-map-js](https://github.com/advisories/GHSA-68fv-2mgg-jv7q), [seguridad de Tomcat 11](https://tomcat.apache.org/security-11.html), [avisos oficiales de Jackson](https://github.com/FasterXML/jackson-databind/security/advisories), [API de OSV](https://google.github.io/osv.dev/api/).

## Base local y migración 10

La base PostgreSQL local `pos_db` estaba en la versión 07. Se comprobó que no tenía conexiones activas, se obtuvo un respaldo completo y se aplicaron únicamente las secciones 08, 09 y 10. Se mantuvo `ddl-auto=validate`. Antes y después se compararon huellas de stock, hashes de contraseña, importes/estados de ventas y cantidad de pagos; también se compararon después de arrancar el backend. Todos permanecieron intactos.

Respaldo local: `.local/backups/pos_db-before-08-10-20261008-211336.dump`. Está excluido de Git y separado de `target` para conservarlo al ejecutar Maven clean. No se enviaron datos de tu base local a GitHub. El backend actualizado arrancó y `/api/auth/csrf` respondió HTTP 200. Los procesos temporales de verificación se cierran al terminar.

Para otros equipos, respalda y detén el backend antes de migrar. Consulta `pos_migraciones` y aplica sólo las versiones pendientes desde:

- `database/scripts/pos_postgresql.sql`
- `database/scripts/pos_mysql.sql`

La sección 10 crea `operaciones_comanda`, su restricción única `(id_venta, clave_operacion)`, y agrega `detalle_venta.clave_comanda` e índice. No modifica el stock histórico ni asigna claves ficticias a detalles anteriores. Si ya está registrada `10_comandas_idempotentes`, no vuelvas a ejecutarla.

El contrato `PATCH /api/ventas/{id}/items` exige ahora `claveOperacion` UUID. Los clientes externos que usen esa ruta deben generar una clave por tanda y conservarla junto al cuerpo hasta obtener confirmación. Reutilizarla con otros productos devuelve 409.

## Reproducir las verificaciones

Terminal en `backend/SistemaPOS`:

```powershell
.\mvnw.cmd test package
.\mvnw.cmd dependency:list '-DincludeScope=runtime' '-DoutputFile=target/dependencies-audit.txt'
python scripts/verify_dependencies.py
```

Terminal en `frontend`:

```powershell
npm.cmd ci
npm.cmd test
npm.cmd run build
npm.cmd audit
```

Si Windows mantiene `dist` bloqueado, utiliza `npm.cmd run build -- --outDir dist-verificacion-nueva` con una carpeta nueva. Cierra el backend antes de reemplazar su JAR.

Las pruebas de base de datos deben usar nombres nuevos `pos_kds_qa_*`. Define `DB_USERNAME`, `DB_PASSWORD` y, para puertos distintos del predeterminado, `DB_PORT`; ejecuta `scripts/verify_schema_05.py --engine postgresql|mysql --database pos_kds_qa_nombre_nuevo` por separado para cada motor. La herramienta crea las bases QA y las conserva; nunca usa `pos_db`.

Arranca un backend conectado a esas bases antes de ejecutar `scripts/verify_http_flow.py --url http://127.0.0.1:PUERTO` y `scripts/verify_public_orders.py --url http://127.0.0.1:PUERTO --qa`, con `POS_TEST_PASSWORD` de las cuentas QA.

Para navegador, instala Playwright en un directorio de pruebas, define `PLAYWRIGHT_MODULE`, `POS_UI_URL` de Vite conectado a ese backend QA, `POS_TEST_PASSWORD` y opcionalmente `POS_VALIDATION_DIR`. Desde la raíz ejecuta `node frontend/scripts/verify-commercial.cjs`, `node frontend/scripts/verify-public-menu.cjs` y `node frontend/scripts/verify-audit.cjs`. Estas pruebas crean datos y deben ejecutarse exclusivamente sobre QA.

`.github/workflows/quality.yml` configura pruebas Java/React, build, OSV/npm, instalación/migraciones y flujos HTTP con PostgreSQL y MySQL. Usa acciones fijadas por SHA y permisos de lectura. Las pruebas de Edge se ejecutaron localmente; ese workflow no las incluye. La ejecución remota del workflow es independiente de los resultados locales anteriores.

## Configuración de producción y alcance

`backend/SistemaPOS/src/main/resources/application-prod.properties` exige `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`, conserva validación de esquema, requiere cookie segura, reduce la sesión a dos horas y acota recursos del servidor. Actívalo con `SPRING_PROFILES_ACTIVE=prod`, credenciales propias y HTTPS. Sirve frontend y `/api` desde el mismo origen; sólo configura cabeceras de proxy mediante un proxy de confianza.

Las cuentas demo del instalador mantienen sus hashes originales. Cambia sus contraseñas desde Usuarios antes de exponer el sistema públicamente. El menú web necesita activar la tienda y publicar los productos desde Caja/ADMIN, según `docs/MENU_PUBLICO_09.md`.

Los comprobantes actuales son registros internos imprimibles; la integración electrónica SUNAT/OSE requiere proveedor, certificados y credenciales. Yape y tarjeta se registran como métodos de pago; la verificación automática con sus proveedores requiere contratar y configurar sus APIs. El botón de impresión usa el navegador: se verificaron plantillas/PDF, y resta probar el equipo térmico físico y su controlador. Dominio, HTTPS y despliegue público tampoco quedan configurados por una revisión del repositorio. La cobertura ejecutada no incluye una prueba de carga de producción o un pentest externo.
