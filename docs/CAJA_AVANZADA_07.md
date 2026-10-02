# Caja avanzada, comprobantes y cancelaciones

Implementación del 2 de octubre de 2026. Esta guía sustituye los contratos de cobro y permisos descritos en las guías 05/06. Se conserva Java 21, Records, Jakarta Validation, sesiones con CSRF y `ddl-auto=validate`.

## Flujo operativo

1. **Mozo:** abre una mesa, envía tandas y ve los avisos de platos listos. Cada tanda descuenta stock con bloqueo de productos y transacción. Puede cancelar un ítem únicamente mientras siga `PENDIENTE`, indicando el motivo.
2. **Cocina:** tablero oscuro con `PENDIENTE → PREPARANDO → LISTO`. Al marcar Listo, el evento SSE actualiza las pantallas de Mozo y Caja. El ítem permanece en la tercera columna hasta su entrega. El sondeo cada cinco segundos respalda la conexión SSE.
3. **Entrega:** Mozo confirma los platos de mesas y Caja confirma pedidos externos. Los ítems entregados quedan `SERVIDO`.
4. **Caja:** puede registrar abonos de cualquier importe válido, incluso cobrar todo por anticipado sin liberar la mesa. Para emitir y cerrar una mesa, el saldo debe quedar en cero y todos sus ítems activos deben estar entregados.
5. **WhatsApp:** recepción autenticada registra cliente, teléfono, recojo/delivery, dirección y productos. Admite contra entrega, adelanto Yape/Plin y pago total. La creación, el inventario y el adelanto se confirman juntos. Se trata de ingreso manual de pedidos recibidos, sin conexión automática a WhatsApp Business ni a una pasarela de pagos.
6. **Pedido externo pagado:** puede emitirse el comprobante antes de la preparación. El pedido sigue en Cocina y recepción hasta su entrega.

El mapa conserva áreas y colores: verde libre, naranja comiendo, dorado por cobrar y azul esperando comida. Muestra el **saldo pendiente**, descontando los abonos, y una alerta adicional cuando hay platos listos.

## Archivos exactos

Rutas desde la raíz del repositorio:

| Archivo | Responsabilidad |
| --- | --- |
| `frontend/src/components/cashier/PaymentModal.jsx` | Cobro en dos pasos, métodos con iconos, vuelto y datos fiscales condicionales |
| `frontend/src/components/UniversalPaymentModal.jsx` | Alias compatible usado por mesas y recepción |
| `frontend/src/pages/CashierDashboard.jsx` | Cuentas, abonos, despacho, cancelaciones y reimpresión |
| `frontend/src/components/WhatsAppOrderForm.jsx` | Ingreso rápido y adelantos; etiquetas accesibles |
| `frontend/src/components/TablesGrid.jsx` | Estados visuales y saldo acumulado de mesas |
| `frontend/src/components/KitchenBoard.jsx` | Kanban oscuro con tercera columna Listos |
| `frontend/src/components/CancelItemButton.jsx` | Cancelación con motivo, errores y actualización de la cuenta |
| `frontend/src/SalesPos.jsx` | Entrega y cancelación desde el salón |
| `frontend/src/Receipt.jsx` | Recibo inmutable, historial, vuelto y salida de impresión fuera de la aplicación |
| `frontend/src/styles/cashier.css`, `kitchen.css`, `receipt.css` | Adaptación móvil, KDS y ticket de 72 mm para rollo de 80 mm |
| `frontend/src/permissions.js` | Navegación exclusiva por rol |
| `frontend/scripts/verify-cashier.cjs` | Flujo real en navegador y capturas |

Rutas desde `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/`:

| Archivo | Responsabilidad |
| --- | --- |
| `dto/FacturacionRequest.java` | Record fiscal; factura anidada con `@NotBlank`, RUC de 11 dígitos y dirección fiscal |
| `dto/CobrarVentaRequest.java` | Pago opcional y selección fiscal al cerrar |
| `dto/CancelarItemRequest.java` | Motivo obligatorio de hasta 255 caracteres |
| `dto/ComprobanteResponse.java`, `VentaResponse.java` | Datos fiscales inmutables y motivo de cancelación |
| `entity/Comprobante.java`, `TipoDocumento.java` | `NOTA_VENTA`, `BOLETA`, `FACTURA`, RUC, razón social y DNI |
| `entity/MetodoPago.java` | `EFECTIVO`, `YAPE`, `PLIN`, `TARJETA`; compatibilidad con transferencias y métodos históricos |
| `entity/DetalleVenta.java`, `EstadoPreparacion.java` | Estado `CANCELADO`, motivo, usuario y fecha de cambio |
| `service/ComprobanteService.java` | Validación fiscal, correlativos bajo bloqueo y fotografía del comprobante |
| `service/VentaService.java` | Pago/cierre y cancelación con reposición de stock transaccionales |
| `service/KitchenService.java` | Máquina de estados y cola con platos listos |
| `repositorio/DetalleVentaRepository.java`, `VentaRepository.java` | Exclusión de cancelados del consumo y entregas pendientes |
| `controller/VentaController.java`, `config/SecurityConfig.java` | Contratos, identidad de sesión y autorización de servidor |

## Cobro y renderizado condicional

`POST /api/ventas/{id}/cobrar`, solo ADMIN/CAJA, con sesión y CSRF:

```json
{
  "pago": {
    "monto": 36.05,
    "metodoPago": "EFECTIVO",
    "montoRecibido": 50.00,
    "referencia": "Cobro final",
    "claveOperacion": "da14347c-5a25-40b2-a83d-c4a9a6ba875f"
  },
  "facturacion": {
    "tipoComprobante": "FACTURA",
    "factura": {
      "ruc": "20123456789",
      "razonSocial": "Servicios Amazónicos SAC",
      "direccionFiscal": "Av. Los Jardines 120"
    },
    "dni": null,
    "nombreCliente": null
  }
}
```

Para una cuenta ya abonada completamente, enviar `pago: null`. Para un abono que conserva la cuenta abierta, usar `POST /api/ventas/{id}/pagos` con el objeto `pago`, sin envoltorio y sin facturación.

En el modal, `receipt === 'FACTURA'` monta los campos RUC, razón social y dirección fiscal; `receipt === 'BOLETA'` monta DNI y nombre. Ticket no monta campos fiscales. El objeto enviado se construye con la misma condición: cambiar de Factura a Boleta elimina los datos de factura del contrato, aunque permanezcan en el estado local para facilitar volver atrás.

En efectivo se calcula `max(0, centimos(recibido) - centimos(importe)) / 100`; por ejemplo, S/ 50 recibidos para un pago de S/ 36.05 producen S/ 13.95 de vuelto. Se usan céntimos en la UI y `BigDecimal` en el servidor. Un monto recibido insuficiente no se puede confirmar. Yape, Plin y Tarjeta aplican solo el importe del pago.

Para boleta, DNI de ocho dígitos es opcional hasta S/ 700 inclusive; si se consigna DNI, se exige nombre. Por encima de S/ 700, ambos son obligatorios. El umbral se evalúa contra el **total de la venta**, aunque el último abono sea pequeño. Fuente de la regla de identificación: [SUNAT, Boleta de Venta Electrónica](https://cpe.sunat.gob.pe/tipos_de_comprobantes/boleta). Este contrato cubre DNI; otros documentos y excepciones fiscales requieren una extensión específica.

Los endpoints anteriores sin `facturacion` conservan compatibilidad: toman el tipo y los datos del cliente existente, pero los revalidan antes de emitir. Elegir otra razón social al cobrar no modifica la ficha del cliente de la comanda.

Un UUID identifica cada abono. Repetir la misma solicitud no duplica pago ni correlativo. Reutilizarlo con otros importes/métodos devuelve 409; intentar cambiar los datos de un comprobante ya emitido también. Si falla el pago, la factura o la entrega, se revierten pago, numeración, fidelización y liberación de mesa.

## Cancelaciones y permisos

`PATCH /api/ventas/items/{id}/cancelar`:

```json
{ "motivo": "El cliente cambió su pedido antes de la preparación" }
```

Solo ADMIN, MOZO y CAJA; MOZO únicamente sobre pedidos locales. Se toma el mismo bloqueo de venta utilizado por Cocina y cobros. Solo `PENDIENTE` puede pasar a `CANCELADO`. Se conserva la línea original con usuario, motivo y fecha; se devuelve su cantidad al producto, se recalculan subtotal/IGV/total y se excluye del comprobante y marketing. Repetir la cancelación devuelve el resultado existente sin volver a reponer stock.

Si se cancela la última línea sin abonos, la cuenta queda `ANULADA`, sin comprobante ni fidelización, y la mesa queda libre. Si la cancelación haría que los abonos superen el nuevo total, se rechaza con 409 y se revierte todo. La devolución de dinero requiere un flujo de reembolsos adicional; esta implementación no elimina ni compensa pagos confirmados automáticamente.

| Rol | Acceso operativo |
| --- | --- |
| ADMIN | Operación completa y configuración |
| CAJA | Cobros, comprobantes, cierre, recepción y entrega externa |
| MOZO | Mesas, pedidos locales, cancelaciones pendientes, entrega y solicitud de cuenta |
| COCINERO | Exclusivamente cola/estados/eventos de Cocina y sesión |

## SQL exacto y aplicación

La base de datos se entrega en **dos únicos scripts SQL**, con la instalación y todas las migraciones integradas:

- [`database/scripts/pos_postgresql.sql`](../database/scripts/pos_postgresql.sql)
- [`database/scripts/pos_mysql.sql`](../database/scripts/pos_mysql.sql)

La sección **07** de cada archivo requiere la versión 06 y rechaza una segunda ejecución. Añade `tipo_comprobante`, `ruc`, `razon_social`, `dni`, motivo y autor de cancelación, claves foráneas y `CHECK`. Normaliza `ventas.metodo_pago` y `pagos_venta.metodo_pago` a mayúsculas, preservando `TRANSFERENCIA` y `YAPE_PLIN` históricos sin atribuirles un método distinto. No altera stock ni importes históricos.

Los archivos consolidados `database/scripts/pos_postgresql.sql` y `pos_mysql.sql` contienen la instalación completa hasta 07. Allí están también los `CREATE TABLE comprobantes`, `detalle_comprobante`, `pagos_venta`, y los campos de entrega/origen agregados por 05/06. Sobre una base existente, ejecutar **solo las secciones pendientes**, nunca repetir la instalación inicial.

Consultar antes `SELECT version FROM pos_migraciones ORDER BY version;`. Si la última versión es 04, aplicar primero 05 y 06 del consolidado. La 05 verifica stock suficiente antes de reservar los pedidos abiertos; si rechaza el preflight, resolver el inventario antes de continuar. Detener el backend y respaldar antes de migrar. PostgreSQL 07 es transaccional; en MySQL el DDL confirma implícitamente y un fallo parcial requiere restaurar el respaldo.

| Estado de la base | Qué ejecutar del único archivo del motor |
| --- | --- |
| Base nueva vacía | Archivo completo |
| Versión 04 | Secciones 05, 06 y 07 |
| Versión 05 | Secciones 06 y 07 |
| Versión 06 | Sección 07 hasta el final |
| Versión 07 | Nada; ya está actualizada |

En pgAdmin o Workbench, abrir el archivo del motor y ejecutar únicamente la selección correspondiente. Cada sección termina antes del siguiente encabezado numerado. La sección 07 comienza en `-- 07. FACTURACIÓN EN CAJA Y CANCELACIONES.` y termina al final del archivo. No hay archivos de migración separados.

Ejemplo PostgreSQL por PowerShell, únicamente si ya está instalada la 06. Extrae la sección 07 en memoria, sin crear otro script:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita'
$OutputEncoding = [System.Text.UTF8Encoding]::new()
$sqlPOS = Get-Content -LiteralPath 'database/scripts/pos_postgresql.sql' -Raw -Encoding UTF8
$inicio07 = $sqlPOS.IndexOf('-- 07. FACTURACIÓN EN CAJA Y CANCELACIONES.')
if ($inicio07 -lt 0) { throw 'No se encontró la sección 07' }
$sqlPOS.Substring($inicio07) | & 'C:\Program Files\PostgreSQL\17\bin\psql.exe' -h localhost -U postgres -W -d pos_db -X -v ON_ERROR_STOP=1
```

Ejemplo MySQL por PowerShell, únicamente después de 06:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita'
$OutputEncoding = [System.Text.UTF8Encoding]::new()
$sqlPOS = Get-Content -LiteralPath 'database/scripts/pos_mysql.sql' -Raw -Encoding UTF8
$inicio07 = $sqlPOS.IndexOf('-- 07. FACTURACIÓN EN CAJA Y CANCELACIONES.')
if ($inicio07 -lt 0) { throw 'No se encontró la sección 07' }
$sqlPOS.Substring($inicio07) | & 'C:\Program Files\MySQL\MySQL Server 9.6\bin\mysql.exe' --host=127.0.0.1 --user=root --password --default-character-set=utf8mb4 --database=pos_db
```

Se mantiene `spring.jpa.hibernate.ddl-auto=validate`; no cambiarlo a `update` para omitir migraciones.

## Arranque y verificación reproducible

Terminal backend:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\backend\SistemaPOS'
.\mvnw.cmd spring-boot:run
```

Terminal frontend:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\frontend'
npm.cmd run dev
```

Frontend: `http://localhost:5173`; backend: `http://localhost:9090`. Para una instancia QA independiente, `SERVER_PORT` configura el backend y `VITE_API_PROXY` configura su destino en Vite.

```powershell
# Desde backend/SistemaPOS
.\mvnw.cmd test
# Exclusivamente contra una instancia conectada a una BD de pruebas:
$env:POS_TEST_PASSWORD = '<contraseña del usuario ADMIN de pruebas>'
python scripts/verify_http_flow.py --url http://localhost:19090 --user '<ADMIN de pruebas>'
# Cada ejecución de esquema crea bases nuevas; proporcionar DB_PASSWORD y, si corresponde, DB_USERNAME/DB_PORT.
python scripts/verify_schema_05.py --engine postgresql --database pos_kds_qa_<nombre_nuevo>
python scripts/verify_schema_05.py --engine mysql --database pos_kds_qa_<otro_nombre_nuevo>

# Desde frontend
npm.cmd run build -- --outDir dist-validation
# Navegador Microsoft Edge y Playwright instalado en una carpeta de herramientas:
$env:PLAYWRIGHT_MODULE = '<ruta absoluta a node_modules\playwright>'
$env:POS_UI_URL = 'http://127.0.0.1:15173'
$env:POS_TEST_USER = '<ADMIN de pruebas>'
$env:POS_TEST_PASSWORD = '<contraseña de pruebas>'
node scripts/verify-cashier.cjs
```

La prueba de navegador crea datos de prueba y verifica efectivo/vuelto, abono, cambio de comprobante, cierre, impresión, adaptación móvil, separación de roles, eventos de Cocina y pedido WhatsApp con adelanto. Guarda capturas en `docs/validation/caja07/`.

Verificación realizada el 2 de octubre de 2026: 30 pruebas funcionales y una de contexto aprobadas; JAR y build Vite generados; instalación completa y migraciones incrementales aprobadas en PostgreSQL 17 y MySQL 9.6; flujo HTTP completo aprobado contra ambos motores con `ddl-auto=validate`; prueba de UI aprobada en Microsoft Edge a 1440/1600 px y modal a 390 px, sin errores JavaScript. Se revisaron visualmente las capturas. [Resultado del navegador](validation/caja07/resultado.json).

Las pruebas usaron bases `pos_kds_qa_*` independientes; MySQL se ejecutó en una instancia temporal separada. **La base de trabajo `pos_db` no fue migrada.** Antes de arrancar el código nuevo sobre ella, aplicar las versiones pendientes según el apartado SQL.

## Alcance fiscal

Se generan **registros y recibos internos** con datos fiscales y correlativos. No se ha implementado firma XML, envío a SUNAT/OSE/PSE, CDR ni autorización electrónica. El ticket impreso lo indica para evitar presentarlo como un CPE aceptado. La impresión se validó en el navegador; queda la prueba física con la impresora térmica del establecimiento, configurada para rollo de 80 mm.
