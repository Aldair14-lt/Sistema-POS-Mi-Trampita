# Caja, WhatsApp, mesas y Cocina

El código incorpora Caja y recepción, ingreso manual de pedidos de WhatsApp/Web, pagos parciales, comprobantes y avisos en vivo. Requiere Java 21 y las migraciones hasta la versión **06**. Se mantiene `ddl-auto=validate` y la sesión con CSRF.

## Instalación y actualización

Los scripts de base de datos siguen siendo únicamente estos dos archivos:

- [PostgreSQL](../database/scripts/pos_postgresql.sql).
- [MySQL](../database/scripts/pos_mysql.sql).

Para una instalación nueva, ejecuta completo el archivo de tu motor. Para una base existente, consulta `SELECT version FROM pos_migraciones ORDER BY version;` y aplica exclusivamente las secciones pendientes, con el backend detenido y un respaldo previo:

| Última versión instalada | Secciones que debes ejecutar |
| --- | --- |
| 04 | 05 y después 06 |
| 05 | Solo 06 |
| 06 | Ninguna |

La sección nueva comienza en `06. MIGRACIÓN DE BASE EXISTENTE / CAJA, WHATSAPP Y COMPROBANTES`. No ejecutes el instalador completo sobre la base existente. La sección 05 termina justo antes del encabezado 06. Para versiones anteriores consulta [la guía de escalamiento](IMPLEMENTACION_ESCALAMIENTO.md).

La migración 06 crea `comprobantes` y `detalle_comprobante`, añade teléfono de entrega, solicitud de cuenta y contador por serie, y admite `WHATSAPP`, `WEB`, `yape` y `plin`. Convierte `EN_PREPARACION` a `PREPARANDO`. Conserva ventas, pagos e inventario; reserva los números históricos del formato `SERIE-NÚMERO` sin renumerarlos ni emitir boletas retrospectivas. `ONLINE` y `yape_plin` se mantienen para los registros anteriores.

PostgreSQL aplica la sección 06 en una transacción. MySQL 8.0.16+ confirma DDL implícitamente: si una aplicación falla parcialmente, restaura el respaldo antes de repetir. Ambas secciones rechazan una segunda aplicación.

## Arranque

Terminal de backend:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\backend\SistemaPOS'
.\mvnw.cmd spring-boot:run
```

Terminal de frontend:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\frontend'
npm.cmd run dev
```

La configuración predeterminada utiliza PostgreSQL y el backend en `9090`. Para MySQL configura `DB_URL`, `DB_DRIVER=com.mysql.cj.jdbc.Driver`, `DB_USERNAME` y `DB_PASSWORD` según tu instalación. Vite utiliza el proxy `/api` existente.

## Flujo operativo

1. **Mozo:** pulsa una mesa libre, registra el cliente y envía platos a Cocina. El servidor bloquea productos en orden de ID y descuenta stock dentro de la transacción de la comanda. Un fallo en cualquier línea revierte todos los descuentos.
2. **Cocinero:** ve un Kanban oscuro con pendientes y platos en preparación, ordenados por la hora de cada tanda. Marca `PENDIENTE → PREPARANDO → LISTO`. La API comprueba el estado esperado y rechaza saltos o retrocesos. Conservamos `SERVIDO` como confirmación de entrega del Mozo/Caja.
3. **Mozo/Caja:** reciben el aviso visual de platos listos y los marcan entregados. El sonido se activa con un botón por las restricciones de reproducción del navegador.
4. **Mozo:** cuando todos los platos están entregados, pulsa **Solicitar cuenta a Caja**. La mesa pasa a amarillo y aparece en **Mesas por Cobrar**. Un adicional vuelve a quitar esa solicitud y envía una tanda nueva a Cocina.
5. **Caja:** registra abonos o el saldo completo, selecciona Efectivo/Yape/Plin/Tarjeta/Transferencia y emite el comprobante. El modal muestra pagos anteriores, saldo y vuelto. Una respuesta de red perdida conserva el UUID y la solicitud exacta para reintentar sin duplicar el pago.
6. **WhatsApp/Web:** Caja selecciona un cliente existente o registra uno nuevo, teléfono, recojo/delivery y platos. Puede ingresar un adelanto, pago completo o contra entrega. Pedido, inventario y adelanto se confirman juntos. Un pedido online pagado puede seguir preparándose y permanece visible hasta despacharlo.

El grid deriva sus colores de la operación: **verde agua** libre, **rojo/naranja** consumiendo, **dorado** cuenta solicitada y **azul** con platos aún sin entregar. Muestra tiempo, total, capacidad y platos listos; el texto acompaña al color para facilitar su identificación.

## Decisiones arquitectónicas

- El libro `pagos_venta` es la fuente del saldo. Los importes usan `BigDecimal`; el cliente no decide el estado financiero ni el total definitivo.
- `estado_venta` y `estado_cuenta` son independientes. Una cuenta completamente abonada puede estar operativamente abierta hasta finalizar su mesa; un delivery cobrado puede continuar en Cocina.
- Cobro final, cierre, liberación de mesa, fidelización y comprobante usan una sola transacción. La venta se bloquea primero; la serie se bloquea para asignar correlativos únicos. Una mesa requiere todos los platos entregados antes del cierre.
- El comprobante guarda datos de empresa, cliente, productos, cantidades y precios al emitir. Las ediciones posteriores no cambian la impresión. La ficha fiscal se revalida al cobrar una factura. El catálogo no permite modificar una serie ya utilizada ni reiniciar su contador.
- SSE envía únicamente invalidaciones **después del commit**, sin datos de clientes o importes para Cocina. Cada pantalla comparte su conexión y mantiene polling cada cinco segundos como respaldo. En esta implementación las pantallas deben usar la misma instancia de backend para recibir los eventos; varias instancias requieren un canal compartido.
- Los permisos se validan en el servidor: `COCINERO` accede a Cocina y a su canal de eventos; `CAJA` cobra y registra pedidos externos; `MOZO` registra consumo, sirve y solicita cuentas. Administración conserva sus accesos. Cocina no obtiene datos de pagos o marketing.

Los comprobantes almacenados e impresos son internos. Esta implementación no incluye envío electrónico, firma, XML ni respuesta de SUNAT. El ingreso de WhatsApp es manual; no envía mensajes a terceros.

## Rutas y contratos

| Método y ruta | Permiso | Comportamiento |
| --- | --- | --- |
| `GET /api/caja/resumen` | ADMIN, CAJA | Mesas por cobrar, otras mesas, online activos y 20 comprobantes recientes |
| `POST /api/ventas` | ADMIN, MOZO, CAJA | LOCAL con mesa o WHATSAPP/WEB con entrega; Mozo solo LOCAL sin pago inicial |
| `POST /api/ventas/whatsapp` | ADMIN, CAJA | `RegistrarPedidoWhatsAppRequest`; pedido y adelanto atómicos |
| `PATCH /api/ventas/{id}/items` | ADMIN, MOZO | Adicional, nueva tanda y descuento de stock |
| `PATCH /api/ventas/{id}/solicitar-cuenta` | ADMIN, MOZO | Cuenta local con todos los platos servidos |
| `POST /api/ventas/{id}/pagos` | ADMIN, CAJA | Abono compatible con el contrato anterior, sin cierre automático |
| `POST /api/ventas/{id}/cobrar` | ADMIN, CAJA | Abono; si completa el saldo, cierra y emite en la misma transacción |
| `PATCH /api/ventas/{id}/cerrar` | ADMIN, CAJA | Cierre compatible, exige pago íntegro y entrega; ahora emite comprobante |
| `GET /api/cocina/items` | ADMIN, COCINERO | Solo pendientes/preparando, productos, cantidades, mesa/canal y fechas |
| `PATCH /api/cocina/items/{id}/estado` | ADMIN, COCINERO | Estado nuevo y estado esperado |
| `PATCH /api/ventas/items/{id}/servir` | ADMIN, MOZO, CAJA | LISTO → SERVIDO |
| `GET /api/cocina/eventos` | ADMIN, COCINERO | Canal SSE de Cocina |
| `GET /api/operacion/eventos` | ADMIN, MOZO, CAJA | Canal SSE de mesas y recepción |

Se conserva `/api/pedidos-online` para los contratos anteriores. `EN_PREPARACION` se acepta como alias de entrada JSON; las respuestas nuevas utilizan `PREPARANDO`.

Ejemplo de WhatsApp con cliente existente y adelanto por Yape (sustituye IDs y genera una clave UUID para cada operación nueva):

```json
{
  "empresaId": 1,
  "clienteId": 1,
  "tipoComprobanteId": 1,
  "numeroComprobante": "WA-referencia-unica-del-pedido",
  "tipoEntrega": "DELIVERY",
  "direccion": "Av. Principal 123",
  "telefono": "999888777",
  "items": [{ "productoId": 1, "cantidad": 2 }],
  "pagoTotal": false,
  "pagoInicial": {
    "monto": 20.00,
    "metodoPago": "yape",
    "referencia": "Operación confirmada",
    "claveOperacion": "dbeb3bfd-9246-4e87-8bdf-a654b34421ab"
  }
}
```

`numeroComprobante` mantiene su significado anterior como referencia única de la comanda. El número emitido se devuelve en `comprobante.numero`. Para crear un cliente, omite `clienteId` y envía `cliente` con sus datos; se exige exactamente una de las dos alternativas. La factura requiere RUC de 11 dígitos y dirección fiscal.

Para cobrar, envía `{ "pago": { ... } }` a `/api/ventas/{id}/cobrar`. `PagoParcialRequest` exige monto positivo, máximo dos decimales, método y UUID; efectivo requiere `montoRecibido >= monto`. Si la cuenta ya está íntegramente abonada, `{}` emite sin otro pago. El mismo UUID permite recuperar un cobro confirmado y devuelve el mismo comprobante. Un UUID reutilizado con datos distintos produce `409`.

## Archivos principales

Frontend:

- `frontend/src/pages/CashierDashboard.jsx`
- `frontend/src/components/WhatsAppOrderForm.jsx`
- `frontend/src/components/UniversalPaymentModal.jsx`
- `frontend/src/components/TablesGrid.jsx`
- `frontend/src/components/KitchenBoard.jsx`
- `frontend/src/components/OnlineOrdersBoard.jsx`
- `frontend/src/hooks/useOperationEvents.js` y `useOrderAlerts.js`
- `frontend/src/styles/tables.css`, `kitchen.css` y `cashier.css`
- Integración en `App.jsx`, `SalesPos.jsx`, `Receipt.jsx` y `permissions.js`

Backend, bajo `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/`:

- `entity/Comprobante.java`, `DetalleComprobante.java`, `Venta.java` y `TipoComprobante.java`
- `dto/RegistrarPedidoWhatsAppRequest.java`, `PagoParcialRequest.java`, `CobrarVentaRequest.java`, `ComprobanteResponse.java` y `CajaResponse.java`
- `service/VentaService.java`, `KitchenService.java` y `OperationEvents.java`
- `controller/VentaController.java`, `CajaController.java` y `OperationEventsController.java`
- `repositorio/ComprobanteRepository.java`, `TipoComprobanteRepository.java` y `VentaRepository.java`
- `config/SecurityConfig.java`

## Verificación reproducible

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\backend\SistemaPOS'
.\mvnw.cmd package

# Solo contra un backend conectado a una base de pruebas:
$env:POS_TEST_PASSWORD = 'contraseña-del-admin-de-pruebas'
python scripts/verify_http_flow.py --url http://localhost:19090
```

`scripts/verify_schema_05.py` ahora prueba las versiones 05 y 06 y el instalador completo. Crea exclusivamente bases nuevas con prefijo `pos_kds_qa_`, utiliza credenciales del entorno y las conserva para inspección:

```powershell
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = 'contraseña-de-pruebas'
python scripts/verify_schema_05.py --engine postgresql --database pos_kds_qa_nombre_nuevo
```

Para MySQL usa `--engine mysql`, las credenciales apropiadas y, si procede, `DB_PORT` de tu servidor de pruebas.

El build del frontend se ejecuta desde `frontend` con `npm.cmd run build`. La revisión visual, táctil, del sonido y de la impresión requiere un navegador conectado.

Verificación realizada el 1 de octubre de 2026: **24 pruebas Java aprobadas** (23 de flujos y una de arranque), empaquetado Maven y build Vite correctos. Instalación completa, migraciones y flujo HTTP aprobados en **PostgreSQL 17 y MySQL 9.6**, con `ddl-auto=validate`, sobre bases de pruebas independientes. También se verificaron por HTTP los alias de estados anteriores. La revisión visual y de interacción quedó pendiente porque no hubo un navegador conectado.

### Corrección del arranque: tabla `comprobantes` ausente

El 1 de octubre de 2026 a las 23:14 se aplicó únicamente la sección 06 sobre la base real PostgreSQL `pos_db`, que ya tenía 05. Se creó y validó el respaldo `D:\SistemaPOSMiTrampita\Respaldos\pos_db_antes_06_20261001_231408_345330.dump` antes de migrar. La comparación anterior/posterior confirmó que se conservaron productos, ventas, pagos, clientes, mesas y cantidades de detalle.

El backend arrancó con `ddl-auto=validate` en `9090` y autenticación/CSRF; `/api/mesas`, `/api/ventas/abiertas`, `/api/pedidos-online`, `/api/cocina/items` y `/api/caja/resumen` respondieron `200 OK`. No es necesario repetir 06 en esta base. El mensaje `No JTA platform available` es informativo; el fallo de arranque correspondía al esquema incompleto.

La instancia de comprobación con PID `27776` se detuvo a las 23:22 para liberar el puerto `9090` y permitir el arranque desde la terminal de desarrollo. Su log está en `%TEMP%\pos-migracion06-20261001-arranque.log`. Utiliza el comando habitual:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\backend\SistemaPOS'
.\mvnw.cmd spring-boot:run
```
