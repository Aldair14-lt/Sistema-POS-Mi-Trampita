# Revisión funcional del POS — 9 de octubre de 2026

Los dos instaladores SQL incluyen ahora la sección 12 con 24 productos, 6 categorías, 4 marcas, 3 proveedores, 6 clientes y una empresa de demostración. Los productos tienen precios, stock y destino Cocina/Bar. Las áreas, mesas y tipos de comprobante se agregan solo cuando faltan. La carga es repetible, conserva datos existentes y no genera ventas, pagos ni comprobantes emitidos.

Se verificó el sistema con PostgreSQL 17 y MySQL 9.6, mediante pruebas Java, pruebas frontend, peticiones HTTP y navegador Edge. Las comprobaciones descritas abajo pasaron. No fue necesario modificar la lógica de negocio del sistema para resolver fallos funcionales.

## Resultados

| Comprobación | PostgreSQL | MySQL |
| --- | --- | --- |
| Instalación completa de un único SQL, con datos de ejemplo | Aprobada | Aprobada |
| Migraciones 05–11, históricos, FK y controles de reejecución | Aprobadas | Aprobadas |
| Repetir datos sin duplicarlos ni reiniciar precios, stock o clientes editados | Aprobada | Aprobada |
| Conservar hashes de contraseñas | Aprobada | Aprobada |
| Arranque Spring Boot con `ddl-auto=validate` | Aprobado | Aprobado |
| Flujos HTTP de ventas, pagos, Caja, KDS y pedidos externos | Aprobados | Aprobados |
| Menú público, solicitudes y seguimiento por HTTP | Aprobados | Aprobados |
| Catálogos, usuarios, permisos y validaciones: 230 solicitudes por motor | Aprobados | Aprobados |
| Login y formularios de usuarios en navegador | Aprobados | Aprobados |
| Caja, ventas, mesas, Cocina/Bar, WhatsApp y marketing en navegador | Aprobados | Aprobados |
| Menú público, carrito, recojo, delivery y seguimiento en navegador | Aprobados | Aprobados |
| Ticket sin identificación, Boleta, Factura e impresión | Aprobados | Aprobados |
| Recuperación tras pérdida de respuestas, reintentos y almacenamiento bloqueado | Aprobada | Aprobada |
| Crear, buscar, editar y eliminar nueve recursos en navegador | Aprobados | Aprobados |

También pasaron **67 pruebas Java**, **9 pruebas frontend**, el empaquetado Maven y la compilación de producción Vite. Las pruebas de navegador se ejecutaron en escritorio de 1440 px y móvil de 390 px; el menú público también comprobó 320 px. Los reportes de navegador registran cero errores JavaScript. Se inspeccionaron capturas de Caja, formulario de pago móvil, menú móvil y ticket simple.

## Cobertura por módulo

- **Acceso y usuarios:** login válido e inválido, CSRF, cierre de sesión, información pública del usuario sin hashes, creación/edición/eliminación, roles exclusivos, usuarios inactivos y bloqueados, conservación de contraseña al editar y cambios aplicados a sesiones abiertas. Las pruebas Java cubren protección del último administrador y concurrencia.
- **Catálogos y configuración:** crear, consultar, buscar, editar y eliminar categorías, marcas, proveedores, clientes, empresa y tipos de comprobante; campos obligatorios, correo, fechas futuras, duplicados, registros inexistentes y borrados bloqueados por relaciones. No se permite modificar una serie que ya emitió comprobantes.
- **Productos e inventario:** referencias a categoría/marca/proveedor, precios y stock válidos, código duplicado, versión de edición, conflicto de datos desactualizados, stock suficiente, rollback y pedidos concurrentes sin stock negativo.
- **Áreas y mesas:** crear/editar/eliminar, filtro por área, abrir/liberar, número duplicado, capacidades, áreas inactivas, protección de mesas ocupadas y áreas con mesas.
- **Ventas y comandas:** pedido local identificado o consumidor final, tandas adicionales, notas separadas, solicitud de cuenta, preparación y entrega, cierre, cancelación con motivo y reposición de stock una sola vez.
- **Caja y pagos:** apertura única por empresa, abonos, saldo calculado en servidor, pago total, efectivo insuficiente, vuelto, Yape/Plin/transferencia, reintentos idempotentes, pagos simultáneos y cierre/arqueo sin perder cobros.
- **Documentos:** Ticket simple sin inventar DNI/RUC, Boleta según identificación y monto, Factura con RUC/razón social/dirección, emisión única, correlativos concurrentes e impresión de ticket 80 mm, factura A4 y cierre A4. La impresión se verificó mediante navegador y archivos, sin impresora física.
- **Cocina y Bar:** colas separadas, permisos exclusivos, transiciones de estado, rechazo de IDs de otra estación, eventos SSE y comandas por tanda sin precios.
- **Pedidos externos:** WhatsApp/Web, recojo y delivery, adelanto/pago total/contraentrega, recepción y despacho. Menú público sin sesión, filtrado de productos, precios del servidor, carrito persistente, respuesta perdida, reintento, aceptación única, rechazo y seguimiento.
- **Marketing e Inicio:** indicadores, cumpleaños, próximos cumpleaños, frecuentes, inactivos, mejores clientes, consumo, CSV para Meta y filtros inválidos. Los importes y visitas no se pueden cambiar mediante la edición del cliente.

## Entorno y evidencias

Las pruebas con escrituras se ejecutaron exclusivamente en:

- PostgreSQL: `pos_kds_qa_integral_pg_20261009a` y `pos_kds_qa_integral_pg_20261009a_full`.
- MySQL: `pos_kds_qa_integral_mysql_20261009a` y `pos_kds_qa_integral_mysql_20261009a_full`.

Los backends temporales usaron puertos 19090/19091; Vite usó 15198/15199. Se conservaron las bases QA y las evidencias para inspección. Las capturas, PDFs, CSV y resultados JSON están en [validation/integral20261009](validation/integral20261009/). El resumen está en [resultado-global.json](validation/integral20261009/resultado-global.json). Los logs de ejecución local están en `.local/revision-*.log`, excluidos de Git.

## Repetir las pruebas

Desde `frontend`:

```powershell
npm.cmd test
npm.cmd run build -- --emptyOutDir=false
```

Desde `backend/SistemaPOS`:

```powershell
.\mvnw.cmd -B test
```

Para SQL, definir `DB_USERNAME` y `DB_PASSWORD` en el entorno y usar nombres QA **nuevos**:

```powershell
python scripts\verify_schema_05.py --engine postgresql --database pos_kds_qa_revision_pg_nueva
python scripts\verify_schema_05.py --engine mysql --database pos_kds_qa_revision_mysql_nueva
```

Cambiar las credenciales del entorno para cada motor. Los verificadores crean y conservan las bases; no borrar ni reutilizar una QA existente. Arrancar el backend conectado a la base `_full`, manteniendo `ddl-auto=validate`, y definir `POS_TEST_PASSWORD` para su usuario administrador:

```powershell
python scripts\verify_http_flow.py --url http://127.0.0.1:19090
python scripts\verify_public_orders.py --url http://127.0.0.1:19090 --qa
python scripts\verify_catalog_flow.py --url http://127.0.0.1:19090 --qa --report reporte-catalogos.json
```

Para navegador, arrancar Vite con `VITE_API_PROXY` apuntando al backend QA. Definir `POS_UI_URL`, `POS_TEST_PASSWORD`, `POS_VALIDATION_DIR` y `PLAYWRIGHT_MODULE` para una instalación de Playwright. Ejecutar desde `frontend`, por separado: `node scripts/verify-passwords.cjs`, `node scripts/verify-commercial.cjs`, `node scripts/verify-public-menu.cjs`, `node scripts/verify-receipts.cjs`, `node scripts/verify-audit.cjs` y `node scripts/verify-catalogs.cjs`. Cambiar el directorio de evidencias para cada verificador. Ninguno de los verificadores con escrituras debe apuntar al backend de una base real.
