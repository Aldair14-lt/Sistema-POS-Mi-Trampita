# Comprobantes y cliente opcional

El ticket simple permite tomar una comanda local, registrar abonos, agregar productos, cerrar la mesa e imprimir sin solicitar identificación. Se guarda `ventas.id_cliente = NULL` y el comprobante muestra `CONSUMIDOR FINAL`, sin inventar DNI, RUC ni una ficha de cliente.

| Documento | Datos solicitados |
|---|---|
| Ticket simple (`NOTA_VENTA`) | Ningún dato del cliente para consumo local. Documento interno. |
| Boleta hasta S/ 700 inclusive | Puede emitirse sin identificación. La opción «Emitir Boleta a nombre del cliente» exige DNI de 8 dígitos y nombre. |
| Boleta mayor a S/ 700 | DNI de 8 dígitos y nombre obligatorios. |
| Factura | RUC de 11 dígitos, razón social y dirección fiscal obligatorios. |

El importe se verifica con el **total de la cuenta calculado por el servidor**, incluso si el último pago es parcial o la cuenta aumentó con adicionales. Cambiar a Ticket simple oculta los datos fiscales y envía `cliente: null`; cambiar de comprobante al cobrar tampoco envía los campos ocultos del tipo anterior.

La regla de identificación de Boleta sigue la [orientación de SUNAT](https://orientacion.sunat.gob.pe/03-boleta-de-venta). El contrato actual admite DNI para Boleta; otros documentos requieren ampliar el contrato. La impresión sigue siendo un registro interno: la emisión electrónica SUNAT/OSE requiere integración adicional.

Los pedidos externos conservan sus validaciones de cliente, teléfono y dirección para delivery. Las cuentas sin cliente se permiten únicamente en consumo local. Las ventas anónimas no incrementan las métricas de otro cliente ni crean registros ficticios para marketing. Los datos fiscales recogidos al cerrar una cuenta se guardan en el comprobante inmutable.

## Archivos

- `frontend/src/SalesPos.jsx`: tipo de comprobante antes del formulario, Ticket simple predeterminado y cliente condicional.
- `frontend/src/utils/receipt.js`: reglas comunes de identificación para pedido y cobro.
- `frontend/src/components/cashier/PaymentModal.jsx`: requisitos fiscales y exclusión de campos ocultos.
- `frontend/src/pages/CashierDashboard.jsx`: cuentas y comprobantes sin ficha de cliente.
- `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/dto/VentaDtos.java`: cliente opcional solo para pedidos locales, sin aceptar dos referencias simultáneas.
- `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/service/VentaService.java`: registro, stock y cierre transaccionales; marketing solo para clientes existentes.
- `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/service/ComprobanteService.java`: consumidor final, validación fiscal y fotografía histórica.
- `database/scripts/pos_postgresql.sql` y `database/scripts/pos_mysql.sql`: sección 11, sin nuevas tablas ni scripts separados.

## Bases existentes

Respalda la base y detén el backend antes de aplicar la **sección 11 únicamente** cuando `pos_migraciones` ya contiene `10_comandas_idempotentes`. Después inicia el backend conservando `ddl-auto=validate`. Una instalación nueva ejecuta el archivo completo del motor elegido.

PostgreSQL permite el cliente opcional con una restricción para consumo local. MySQL conserva la clave foránea y su `ON UPDATE CASCADE`; usa triggers para validar consumidor local y la transición de una cuenta sin identificar a un cliente existente. Los verificadores comprueban instalación completa, upgrade, preservación de filas, referencias y rechazo de reejecución.

La base local `pos_db` fue respaldada y actualizada solo con la sección 11. Los fingerprints de usuarios, productos, clientes, ventas, detalles, pagos, comprobantes y sesiones de caja coinciden antes y después. Respaldo: `.local/backups/pos_db-before-11-20261008-222545.dump`. El backend se reinició en el puerto 9090 y respondió HTTP 200 en `/api/auth/csrf` con validación del esquema.

## Validación

- 67 pruebas Java aprobadas; 9 pruebas de frontend aprobadas y build Vite correcto.
- Instalación completa y migraciones 05–11 verificadas en PostgreSQL 17 y MySQL 9.6, en bases QA nuevas.
- Flujos HTTP privados y públicos aprobados en ambos motores; incluyen ticket sin cliente, rollback, stock, abonos, reintentos, Caja, KDS, delivery y recojo.
- Navegador Edge: pedido y cobro sin cliente, Factura inválida bloqueada, cambio de documento sin filtrar datos ocultos, impresión térmica y vista móvil de 390 px.
- Regresiones de navegador aprobadas: Caja, Cocina/Bar, mesas, Kanban, CSV, impresión A4/80 mm y recuperación de pagos/adicionales sin duplicación.
- Evidencia: `docs/validation/receipts11/resultado.json`, capturas y PDF térmico. La prueba simula `window.print`; no verifica una impresora física.

Desde `frontend`, ejecuta `npm.cmd test` y `npm.cmd run build`. Desde `backend/SistemaPOS`, ejecuta `.\mvnw.cmd test package`.

Los verificadores `backend/SistemaPOS/scripts/verify_schema_05.py`, `verify_http_flow.py`, `verify_public_orders.py` y `frontend/scripts/verify-receipts.cjs` se ejecutan **solo con bases QA** y nunca con `pos_db`.
