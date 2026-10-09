# Menú público y pedidos de clientes — versión 09

El cliente entra a `/pedir/{slug}` sin iniciar sesión, consulta la carta, arma su carrito y solicita recojo o delivery. Caja recibe el pedido en una bandeja de confirmación. Al aceptarlo se crea una cuenta WEB, se descuenta stock y se enrutan los ítems a Cocina/Bar en una sola transacción. El cobro continúa por el flujo existente de Caja.

## Activación

1. Respalda la base, detén el backend y consulta `SELECT version FROM pos_migraciones ORDER BY version;`. Aplica únicamente las secciones pendientes de `database/scripts/pos_postgresql.sql` o `database/scripts/pos_mysql.sql`. Sobre 08 ejecuta 09; sobre 07 ejecuta 08 y 09; sobre 06 ejecuta 07, 08 y 09. No repitas instalación ni versiones aplicadas. PostgreSQL 09 es transaccional; el DDL de MySQL confirma implícitamente.
2. Arranca el backend desde `backend/SistemaPOS` con `mvnw.cmd spring-boot:run` y el frontend desde `frontend` con `npm.cmd run dev`. Conserva `ddl-auto=validate`.
3. Ingresa como ADMIN. En **Productos**, edita los platos y bebidas y marca **Mostrar en el menú público**. Los productos se mantienen privados por defecto; los agotados publicados se muestran sin permitir añadirlos.
4. Abre **Pedidos online → Configurar y compartir menú público** —también disponible en Caja—. Selecciona empresa, usa un identificador como `mi-trampita`, habilita recepción, recojo y delivery y guarda. Completa el mensaje con horario y cobertura. El enlace se conserva una vez creado para mantener el seguimiento de pedidos anteriores.
5. Abre o copia el enlace. Con Vite estándar será `http://localhost:5173/pedir/mi-trampita`. El nombre, teléfono y dirección se toman de la empresa configurada.

**No se migró `pos_db` ni se activó públicamente una empresa real.** Las pruebas usan bases aisladas `pos_kds_qa_public09_*`.

## Operación

El cliente busca o filtra categorías, elige cantidades e indica observaciones por producto. El carrito se conserva en su navegador. Antes de enviar completa nombre, DNI de 8 dígitos para boleta, teléfono y modalidad; delivery exige dirección. El formulario permite un comentario general que Caja conserva en la venta.

Los precios públicos incluyen el IGV del modelo existente: el precio de venta interno es base y el sistema agrega 18 %. El total se redondea sobre la base acumulada y lo vuelve a calcular el servidor. Por ejemplo, una base de S/ 10 aparece como S/ 11.80. El cliente envía `totalEsperado` únicamente para detectar cambios: si no coincide con el cálculo del servidor, se rechaza el envío y se solicita actualizar la carta. No se usan precios ni pagos del cliente para crear importes contables.

El pedido aparece en **Pedidos de la página** en Caja y Pedidos online. ADMIN/CAJA verifican disponibilidad, dirección/cobertura y contacto. **Confirmar y enviar** descuenta stock y crea la cuenta; **Rechazar** requiere un motivo que el cliente verá. Un cambio de precio o producto publicado exige contactar al cliente y solicitar un nuevo pedido, sin alterar silenciosamente lo que solicitó.

El enlace de seguimiento contiene un código aleatorio y muestra recibido, confirmado, preparando, listo, entregado, rechazado, cancelado o expirado. No devuelve nombre, documento, teléfono, dirección ni IDs de venta. Los estados de pago y preparación son independientes. El cliente conserva ese enlace y no debe compartirlo con terceros.

El cliente paga al recoger o recibir. Caja puede registrar abonos confirmados y emitir nota, boleta o factura según los contratos existentes. Para factura completa los datos fiscales en el modal de cobro. No se añadió pasarela de pago, comprobación automática de Yape, recargo de delivery, WhatsApp automático ni integración SUNAT.

## Rutas exactas

Rutas relativas a `D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita`.

| Archivo | Función |
| --- | --- |
| `frontend/src/main.jsx` | Entrada pública por URL, independiente del login y menú del POS. |
| `frontend/src/public/PublicMenu.jsx` | Carta, categorías, búsqueda, carrito persistente, envío y seguimiento. |
| `frontend/src/public/CartPanel.jsx` | Cantidades, observaciones y total. |
| `frontend/src/public/CheckoutDialog.jsx` | Modal accesible con contacto, recojo/delivery y reintento. |
| `frontend/src/public/publicApi.js` | HTTP sin cookies de sesión, UUID seguro y cálculos en centavos para la vista. |
| `frontend/src/public/public-menu.css` | Diseño público adaptable a escritorio y móvil. |
| `frontend/src/components/web/WebOrdersInbox.jsx` | Recepción, confirmación y rechazo por Caja/ADMIN. |
| `frontend/src/components/web/WebStoreSettings.jsx` | Publicación y enlace por empresa, exclusivo ADMIN. |
| `frontend/src/styles/web-orders.css` | Estilos de recepción y configuración. |
| `frontend/src/App.jsx` | Opción de producto público y permisos del panel. |

Los siguientes archivos Java están en `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/`:

| Archivo | Función |
| --- | --- |
| `controller/PedidoWebController.java` | Endpoints públicos e internos, respuestas públicas sin caché. |
| `dto/PedidoWebDtos.java` | Records y validación Jakarta de contacto, entrega, UUID e ítems. |
| `service/PedidoWebService.java` | Precios de servidor, deduplicación, límites, confirmación transaccional y seguimiento. |
| `entity/TiendaWeb.java` | Configuración explícita de publicación por empresa. |
| `entity/SolicitudWeb.java`, `entity/SolicitudWebItem.java` | Solicitud y precios del catálogo al recibirla; importes con `@NotNull`, `@DecimalMin` y `@Digits`. |
| `repositorio/TiendaWebRepository.java`, `repositorio/SolicitudWebRepository.java` | Persistencia y bloqueo de solicitudes. |
| `security/PublicOrderRateLimiter.java`, `security/PublicOrderRequestFilter.java` | Límites previos al JSON, incluidos cuerpos sin Content-Length. |
| `config/SecurityConfig.java` | Acceso anónimo limitado a carta/envío/seguimiento, permisos internos y CSRF. |
| `entity/Producto.java`, `entity/Venta.java`, `dto/VentaResponse.java` | Publicación explícita y conservación de observaciones generales. |

## Contratos HTTP

| Acceso | Método y ruta | Comportamiento |
| --- | --- | --- |
| Público | `GET /api/public/tiendas/{slug}/menu` | Datos públicos del local y productos publicados; no costos, proveedores ni stock interno. |
| Público | `POST /api/public/tiendas/{slug}/pedidos` | UUID, contacto, entrega e ítems; devuelve referencia y código de seguimiento. |
| Público | `GET /api/public/tiendas/{slug}/pedidos/{codigo}` | Solo estado, importe, referencia y motivo de rechazo. |
| ADMIN | `GET /api/web/configuracion` | Configuraciones y empresas disponibles. |
| ADMIN | `PUT /api/web/configuracion/{empresaId}` | Publicar/pausar y configurar modalidades/mensaje. |
| ADMIN/CAJA | `GET /api/web/solicitudes` | Pendientes de confirmar, recibidos hace menos de 24 horas. |
| ADMIN/CAJA | `PATCH /api/web/solicitudes/{id}/aceptar` | Tipo de comprobante inicial; devuelve la venta WEB confirmada. |
| ADMIN/CAJA | `PATCH /api/web/solicitudes/{id}/rechazar` | Motivo obligatorio; no modifica stock. |

Los endpoints internos conservan sesión, roles y CSRF. Solo el POST anónimo de solicitudes queda exento de CSRF: no usa cookies ni credenciales del cliente y no cobra, modifica fichas ni descuenta stock. No se habilita el API interno del POS como API pública.

Una clave repetida con los mismos datos recupera la misma solicitud; con otros datos devuelve 409. La confirmación bloquea solicitud y productos para descontar una sola vez. Si falta stock, se revierte venta, cliente nuevo y cualquier descuento. Los pedidos pendientes no reservan inventario y expiran operativamente a las 24 horas, conservando el registro.

Límites: 30 líneas, 20 unidades acumuladas por producto, 60 por pedido; 3 pendientes por teléfono/local y 100 por local en 24 horas. El cuerpo JSON tiene un máximo de 32 KiB y se admiten 10 intentos por minuto por dirección de conexión, incluso malformados. El límite en memoria es por instancia; los pendientes y la deduplicación están en BD. El catálogo sigue usando el inventario compartido del POS; `visible_web` es global, la recepción y configuración corresponden a cada empresa.

## Compartir fuera de la computadora

`localhost` solo funciona en la computadora donde ejecutas Vite. Para comprobar desde otro dispositivo de la misma red, inicia Vite con `npm.cmd run dev -- --host 0.0.0.0` y usa la IP LAN de la computadora, por ejemplo `http://192.168.1.20:5173/pedir/mi-trampita`, cuando la red y firewall permitan el acceso. Esto sirve para pruebas locales.

Para clientes de Internet, publica la compilación React y Spring Boot bajo un dominio HTTPS. El servidor web debe servir `index.html` para `/pedir/*` y reenviar `/api/*` al backend; los demás archivos se sirven normalmente. Se recomienda el mismo origen para frontend y API. El enlace de configuración toma el origen de la página desde la que accede ADMIN. Si usas proxy, aplica límites también en el borde y configura únicamente proxies de confianza; la aplicación no toma IP desde un X-Forwarded-For arbitrario. En producción usa cookies seguras y no expongas PostgreSQL/MySQL.

No se desplegó el proyecto ni se abrió el firewall. Un dominio real, alojamiento y configuración de publicación son necesarios para compartirlo fuera de tu red.

## Validación

- 49 pruebas Java aprobadas: transacciones, concurrencia, idempotencia, rollback, permisos, CSRF, límites, cambios de precio, expiración y privacidad del API público.
- Instalación completa y actualización 09 aprobadas en PostgreSQL y MySQL QA, sin cambiar hashes ni stock histórico.
- Flujo HTTP público aprobado en ambos motores con `ddl-auto=validate`.
- Build React aprobado y prueba Edge/Playwright de carta, carrito, recepción y seguimiento; anchos de 1440, 390 y 320 píxeles sin desborde horizontal.
- Pérdida de respuesta simulada después de guardar: recarga y reintento recuperaron la misma solicitud, sin duplicar venta ni stock.

Ver [evidencias de navegador](validation/public09/README.md). Los verificadores son `backend/SistemaPOS/scripts/verify_schema_05.py`, `verify_public_orders.py` en la misma carpeta y `frontend/scripts/verify-public-menu.cjs`. Requieren bases QA; no ejecutarlos sobre producción. Las contraseñas se proporcionan por entorno con `DB_PASSWORD` o `POS_TEST_PASSWORD`.
