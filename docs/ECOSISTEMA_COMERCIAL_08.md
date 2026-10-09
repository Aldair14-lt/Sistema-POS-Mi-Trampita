# Recreo Mi Trampita: ecosistema comercial, versión 08

El menú público y la recepción desde clientes se amplían en [MENU_PUBLICO_09.md](MENU_PUBLICO_09.md), con migración 09. Esta guía describe la versión 08.

Implementación del 8 de octubre de 2026 sobre el proyecto existente. Conserva Java 21, React/Vite, Records, Jakarta Validation, sesiones con CSRF, BCrypt, pagos idempotentes y `ddl-auto=validate`. Esta guía sustituye las reglas operativas de 07 en turnos, estaciones, cancelaciones e impresión.

## Módulos y rutas exactas

Todas las rutas parten de la raíz del repositorio.

| Módulo | Archivos principales | Comportamiento |
| --- | --- | --- |
| 2. Caja | `frontend/src/components/CashRegister.jsx`, `frontend/src/components/cashier/PaymentModal.jsx`, `frontend/src/pages/CashierDashboard.jsx` | Sencillo inicial, turno vigente, abonos, efectivo con vuelto, datos fiscales y selección 80 mm/A4. |
| 3. KDS | `frontend/src/components/PreparationBoard.jsx`, `KitchenBoard.jsx`, `BarBoard.jsx` en la misma carpeta | Kanban oscuro por estación, observaciones y mozo, estados monotónicos y avisos SSE posteriores al commit. |
| 4. Mesas | `frontend/src/components/TablesGrid.jsx`, `frontend/src/SalesPos.jsx`, `frontend/src/styles/tables.css` | Zonas, saldo, verde libre, rojo ocupada, amarillo por cobrar y azul esperando comida. Notas por producto y comandas por estación. |
| 5. Online | `frontend/src/components/WhatsAppOrderForm.jsx`, `OnlineOrderKanban.jsx`, `OnlineOrdersBoard.jsx` en la misma carpeta | Recepción manual de WhatsApp/Web, empresa/cliente, recojo/delivery, contra entrega, adelanto y pago total. |
| 6. Marketing | `frontend/src/MarketingDashboard.jsx` | Próximos cumpleaños, inactivos de más de 60 días, mejores clientes por consumo y descarga CSV por segmento. |
| 1. Impresión | `frontend/src/components/PrintManager.jsx`, `frontend/src/components/print/`, `frontend/src/styles/print.css` | Un gestor común con plantillas de comanda, comprobante y arqueo. Portal fuera de la interfaz y tamaño de página según el formato. |

Rutas Java desde `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/`:

| Archivo | Responsabilidad |
| --- | --- |
| `entity/SesionCaja.java`, `dto/SesionCajaDtos.java`, `repositorio/SesionCajaRepository.java`, `service/CajaService.java`, `controller/CajaController.java` | Turnos, responsables, totales por método, arqueo y cierre. |
| `entity/PagoVenta.java`, `repositorio/PagoVentaRepository.java`, `service/VentaService.java` | Vinculación de nuevos pagos a su turno; saldo derivado del libro y stock transaccional. |
| `entity/AreaDestino.java`, `entity/Producto.java`, `entity/DetalleVenta.java`, `dto/VentaDtos.java`, `dto/KitchenItemResponse.java` | Destino y notas; la línea conserva el destino aunque cambie el catálogo. |
| `service/KitchenService.java`, `controller/BarController.java`, `repositorio/DetalleVentaRepository.java` | Cola filtrada y rechazo de cambios sobre ítems de otra estación. |
| `config/SecurityConfig.java`, `security/PosPrincipal.java`, `controller/UsuarioController.java` | Permisos de servidor y rol BARTENDER. |
| `service/MarketingService.java`, `controller/MarketingController.java`, `repositorio/ClienteRepository.java` | Segmentación y exportación sin exponer DNI/RUC ni datos de ventas en el CSV. |

## Caja: apertura, cobros y cierre

ADMIN y CAJA comparten el turno de una empresa. Solo puede existir **un turno abierto por empresa**. La apertura bloquea la empresa y mantiene una restricción única; cada apertura usa `claveOperacion` UUID y acepta reintentos del mismo importe. Un cierre repetido devuelve el arqueo existente; cambiar el contado o las observaciones después del cierre devuelve 409.

| Método | Endpoint | Contrato |
| --- | --- | --- |
| POST | `/api/caja/sesiones/abrir` | `empresaId`, `montoInicial` no negativo, `claveOperacion` UUID. |
| GET | `/api/caja/sesiones/actual?empresaId=1` | Turno actual; respuesta vacía si no hay uno abierto. |
| GET | `/api/caja/sesiones?empresaId=1` | Últimos 20 turnos. |
| GET | `/api/caja/sesiones/{id}` | Resumen del turno abierto o arqueo confirmado. |
| POST | `/api/caja/sesiones/{id}/cerrar` | `efectivoDeclarado` no negativo y `observaciones` de hasta 500 caracteres. |
| POST | `/api/ventas/{id}/pagos` | Abono con UUID, importe, método, efectivo recibido y referencia. |
| POST | `/api/ventas/{id}/cobrar` | Pago opcional y `facturacion` para emitir y finalizar. |

`SesionCajaDtos` usa `@NotNull`, `@DecimalMin`, `@Digits` y validación de UUID. Los cobros nuevos exigen un turno abierto de **la empresa de la venta**. Un reintento de un pago ya confirmado conserva su turno original, incluso después de cerrarlo. Los pagos anteriores a 08 permanecen sin turno y no se incluyen artificialmente en un arqueo nuevo.

Efectivo esperado = sencillo inicial + suma de importes aplicados en EFECTIVO. No se suma el billete recibido completo: un abono de S/ 5 recibido con S/ 20 suma S/ 5 a caja y registra S/ 15 de vuelto. Yape, Plin, tarjeta y transferencias se muestran por separado. Diferencia = contado − esperado.

Un cierre fija los montos por método y bloquea el turno hasta confirmar la transacción. Si compite con un cobro, el cobro queda incluido en el cierre o se rechaza; no queda fuera del arqueo. Las cuentas abiertas sobreviven al cierre y pueden recibir nuevos abonos en el siguiente turno. No se implementaron egresos/reembolsos automáticos; los desajustes se conservan como diferencia y observación.

Factura exige RUC de 11 dígitos, razón social y dirección fiscal. Se mantienen la validación de boleta y los comprobantes inmutables existentes. El modal permite conservar la cuenta mediante abonos y elegir el formato de impresión al emitir.

## KDS, mesas y cancelaciones

`producto.area_destino` acepta COCINA o BAR. El formulario de productos permite configurar la estación. Cada nueva tanda descuenta stock bajo `@Transactional` y bloqueo de productos ordenados por ID. Las cantidades de un producto se consolidan para el inventario; observaciones distintas quedan en líneas distintas.

COCINERO consulta y modifica solo `/api/cocina/items`; BARTENDER solo `/api/bar/items`. Ambos tienen su endpoint `/eventos`. La validación de estación se realiza también al cambiar un ítem por ID. ADMIN puede operar ambas estaciones. COCINERO y BARTENDER son roles exclusivos al asignarlos desde Usuarios.

Las transiciones son PENDIENTE → PREPARANDO → LISTO → SERVIDO. La última corresponde a Mozo/Caja. Los avisos a Mozo y Caja se publican después del commit, con polling de respaldo. Preparación y pago siguen siendo estados independientes: pagar un pedido externo no lo elimina del KDS.

Solo ADMIN/CAJA pueden anular ítems. Se conserva la restricción a PENDIENTE y el motivo obligatorio; la devolución de stock y el recálculo se confirman juntos, sin devolver stock dos veces ante reintentos. Se rechaza una cancelación que dejaría abonos por encima del nuevo total.

## Online y marketing

El Kanban de Caja/Online agrupa pedidos en recibidos, en preparación, listos y entregados por cobrar. Una cuenta con productos de ambas estaciones avanza según todos sus ítems activos. La recepción permite elegir empresa y añadir notas a cada línea. Pedidos, stock y adelanto forman una transacción: si falla el pago, se revierte todo.

| Endpoint ADMIN | Resultado |
| --- | --- |
| `/api/marketing/proximos-cumpleaneros?dias=30` | Cumpleaños desde hoy hasta la ventana indicada; soporta cambio de año y 29 de febrero. |
| `/api/marketing/inactivos?dias=60` | Clientes cuya última venta cobrada ocurrió hace más del plazo; no confunde cuentas abiertas ni clientes sin compras con compras recientes. |
| `/api/marketing/mejores` | Top 10 por consumo pagado, luego visitas. |
| `/api/marketing/exportar.csv?segmento=todos` | CSV con `email,phone,country`; segmentos `todos`, `cumpleaneros`, `inactivos`, `mejores`. |

La exportación descarga un archivo autenticado, sin subir datos a Meta. Normaliza correo, teléfonos móviles peruanos con prefijo 51 y números internacionales explícitos; omite contactos inválidos y filas duplicadas. Se usan identificadores principales sin inferir nombres personales desde razones sociales. Referencia: [formato de listas de Meta](https://www.facebook.com/business/help/2082575038703844).

## Impresión

`PrintManager` concentra la salida en una sola plantilla activa. Las vistas de caja, salón, online y KDS usan `usePrint`. Al terminar la impresión se retira el documento del DOM. React escapa el texto de clientes y observaciones.

- Comanda: papel 80 mm, ancho útil 72 mm, letras grandes, mesa/pedido, fecha, mozo, cantidades y observaciones. **No contiene importes**, ni siquiera en el tablero o su plantilla.
- Ticket/boleta: marca de la aplicación, datos de empresa y cliente/DNI, ítems con precios, IGV, total y pagos/vuelto. Ancho de papel 80 mm.
- Factura: A4, encabezado, RUC/razón social/dirección fiscal, detalle, IGV, pagos y espacios de firma/sello. También puede seleccionarse salida térmica.
- Cierre: A4, rango de turno y responsables, inicial, cobros por método, esperado, contado, diferencia y firmas. Reimprimible desde el historial de Caja.

CSS repite las cabeceras de tablas y evita cortar una fila entre páginas. La configuración de página térmica es 80 × 297 mm; el diálogo/driver de la impresora debe usar rollo de 80 mm y el largo apropiado. El navegador muestra la selección de impresora; la aplicación no elige dispositivos ni imprime silenciosamente. Los registros fiscales siguen siendo documentos internos: no se implementó integración SUNAT/OSE, XML firmado ni CDR.

## SQL y conservación de datos

Se mantienen únicamente [`database/scripts/pos_postgresql.sql`](../database/scripts/pos_postgresql.sql) y [`database/scripts/pos_mysql.sql`](../database/scripts/pos_mysql.sql). La sección **08. TURNOS DE CAJA Y KDS ENRUTADO** contiene `CREATE TABLE sesiones_caja`, `ALTER TABLE pagos_venta`, campos de destino/notas, FK, índices y restricciones.

La migración clasifica los productos de la categoría Bebidas como BAR. Las líneas anteriores quedan en COCINA para conservar la cola operativa en curso. A partir de la actualización, toda nueva tanda toma el destino configurado en el producto. No cambia stock, importes, clientes, comprobantes ni contraseñas.

Antes de actualizar una base existente, respaldar, detener el backend y consultar `SELECT version FROM pos_migraciones ORDER BY version;`. Ejecutar solamente las secciones pendientes, en orden. Sobre versión 07 ejecutar 08; sobre 06 ejecutar 07 y 08; sobre 08 no ejecutar nada. La sección 07 termina antes del encabezado 08. PostgreSQL 08 es transaccional; el DDL de MySQL confirma implícitamente y requiere restauración del respaldo ante un fallo parcial.

**Las verificaciones de esta entrega usan bases `pos_kds_qa_mod08_*`. No se aplicó la migración a `pos_db`.** Mantener `ddl-auto=validate` y aplicar las secciones pendientes antes de arrancar el backend nuevo sobre la base operativa.

## Comandos reproducibles

Raíz exacta: `D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita`.

```powershell
# Directorio: D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\backend\SistemaPOS
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\backend\SistemaPOS'
.\mvnw.cmd test package
.\mvnw.cmd spring-boot:run
```

En otra terminal:

```powershell
# Directorio: D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\frontend
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\frontend'
npm.cmd run build -- --outDir dist-validation
npm.cmd run dev
```

Verificadores: `backend/SistemaPOS/scripts/verify_schema_05.py` valida instalación y secciones 05–08 en una base nueva; `backend/SistemaPOS/scripts/verify_http_flow.py` verifica sesión/CSRF, stock, abonos, factura, vuelto, KDS Cocina/Bar, SSE y arqueo contra un backend QA. Usar `DB_USERNAME`, `DB_PASSWORD`, `DB_PORT` y `POS_TEST_PASSWORD` por entorno, sin escribir contraseñas en argumentos.

`frontend/scripts/verify-commercial.cjs` requiere Playwright, Microsoft Edge, `PLAYWRIGHT_MODULE`, `POS_TEST_PASSWORD` y `POS_UI_URL` apuntando al Vite QA. Crea sus fixtures y guarda capturas, PDF de impresión y resultado en `docs/validation/commercial08/`. Nunca ejecutar los verificadores HTTP/UI sobre datos de producción.

## Validación realizada

- Maven: **39 pruebas aprobadas**, cero fallos, errores u omisiones; empaquetado del backend correcto. Incluye concurrencia de apertura/cierre y pagos, idempotencia, reversión de stock, permisos, CSRF y validaciones.
- SQL: instalación completa y actualización hasta 08 aprobadas en bases QA nuevas de **PostgreSQL y MySQL**. Se verificaron datos históricos, hashes de contraseña, restricciones y protección contra repetir la migración.
- HTTP: flujo completo aprobado contra ambos motores con `ddl-auto=validate`: turnos, pedidos, stock, pagos parciales, factura, vuelto, KDS por estación y cierre.
- React: compilación de producción aprobada. Microsoft Edge verificó los flujos de caja, online, mesas, marketing y roles Cocina/Bar en anchos de 1440 y 390 píxeles, sin errores JavaScript.
- Impresión: revisión visual de plantillas y PDF generado por Chromium. Factura y cierre: una página A4 cada uno, aproximadamente 210 × 297 mm. Ticket: una página de aproximadamente 80 × 297 mm. No se probó una impresora física.

Las [evidencias de navegador e impresión](validation/commercial08/README.md) incluyen capturas, PDF, dimensiones y resultados automatizados. Son fixtures de QA y no documentación fiscal emitida a clientes reales.
