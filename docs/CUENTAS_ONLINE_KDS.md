# Cuentas abiertas, recepción online y cocina

La implementación está en el código del workspace. Requiere el esquema de la migración 04 y luego la migración 05. Se conserva Java 21, React/Vite y la seguridad de sesión con CSRF existente.

## 1. Migración de datos

Los únicos dos archivos SQL están consolidados por motor; cada uno incluye el esquema base y las migraciones 03, 04 y 05:

- PostgreSQL: `database/scripts/pos_postgresql.sql`, sección 05.
- MySQL: `database/scripts/pos_mysql.sql`, sección 05.

Las tablas reales del proyecto son `ventas`, `detalle_venta`, `producto`, `mesas`, `rol` y `usuario_rol`. La migración agrega `ventas.estado_cuenta`, `origen_pedido`, `tipo_entrega`, `direccion_envio`; crea `pagos_venta`; agrega estado y fechas por ítem; inserta los cuatro roles y cambia la restricción que exigía mesa para todos los pedidos abiertos.

Para una base que ya tiene la migración 04, selecciona solo la sección `05. MIGRACIÓN DE BASE EXISTENTE / CUENTAS, ONLINE Y COCINA` hasta el final del archivo correspondiente. No ejecutes el archivo completo sobre una base existente. Detén el backend y respalda la base antes de aplicar la sección. PostgreSQL ejecuta la conversión en una única transacción. MySQL confirma el DDL implícitamente: ante un fallo después del preflight, restaura el respaldo antes de repetir.

En ambos motores se comprueba primero el stock necesario para las comandas abiertas de la versión 04. Si falta inventario, la migración se detiene antes de cambiar el esquema. Las comandas abiertas se descuentan una vez; las ventas cerradas reciben un abono histórico y sus ítems se marcan SERVIDO. Se conservan las métricas de fidelización existentes. Una marca de versión impide descontar de nuevo al reejecutar.

Para PostgreSQL, desde una terminal PowerShell:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita'
& 'C:\Program Files\PostgreSQL\17\bin\pg_dump.exe' -h localhost -U postgres -W -d pos_db --format=custom --file 'respaldo-pos-antes-05.dump'
if ($LASTEXITCODE -ne 0) { throw 'Falló el respaldo' }
# Después del respaldo, aplica solo la sección 05 desde pgAdmin, como se indica abajo.
```

Para PostgreSQL, abre `database/scripts/pos_postgresql.sql` en pgAdmin/Query Tool conectado a la base del POS y ejecuta únicamente la sección 05 hasta el final. Para MySQL, selecciona la base del POS en Workbench, abre `database/scripts/pos_mysql.sql` y ejecuta únicamente esa misma sección 05. Si la base aún usa `venta` y `mesa`, primero aplica las secciones pendientes 03/04 de `IMPLEMENTACION_ESCALAMIENTO.md`. Para instalaciones nuevas, ejecuta completo el único archivo de tu motor; ya incluye la sección 05.

Se mantiene `spring.jpa.hibernate.ddl-auto=validate`. No iniciar el backend nuevo sobre una base que no tenga 05.

## 2. Decisiones de negocio y transacciones

`estado_venta` representa el cierre operativo; `estado_cuenta` representa el pago. Una cuenta puede estar CERRADA financieramente y seguir ABIERTA operativamente mientras cocina prepara el pedido. Esto evita perder los pedidos online pagados al 100% del KDS.

Los precios y el IGV del 18% se calculan en el servidor con `BigDecimal`. El libro de abonos determina `totalPagado` y `saldoPendiente`; el navegador no decide el estado de cuenta. Los abonos no pueden superar el saldo. En efectivo se registra el importe aplicado y el monto recibido; el excedente es vuelto.

Cada tanda descuenta inventario en la transacción de creación/adición. Los productos se bloquean por ID en orden estable, se relee su stock y se conserva su versión optimista. Si falla cualquier línea, también se revierten los descuentos previos. Cobrar y cerrar no vuelven a descontar inventario.

Se crea una línea por producto y tanda, incluso si ese producto ya existe en la comanda. Un adicional nunca aumenta una línea que cocina ya preparó. Cada línea conserva `fecha_pedido` y `fecha_estado`.

Los cambios de cocina, pagos, adicionales y cierre se serializan mediante el bloqueo de la venta. La cocina solo admite `PENDIENTE → EN_PREPARACION → LISTO`; el mozo o recepción confirma `LISTO → SERVIDO`. La petición incluye el estado observado para detectar tableros desactualizados. Repetir el mismo cambio ya aplicado es seguro.

El cierre exige suma de pagos igual al total y todos los ítems SERVIDO. Libera la mesa y actualiza fidelización una sola vez. Se pueden agregar ítems a cuentas abiertas o parcialmente pagadas; una cuenta completamente pagada ya no acepta adicionales.

Cada pago exige `claveOperacion` UUID, única por venta. Reintentar la misma solicitud devuelve la cuenta sin crear otro abono. Reutilizar una clave con datos diferentes devuelve 409. El modal conserva la solicitud en fallos de conexión y comprueba el historial antes de habilitar otro cobro.

## 3. Archivos del backend

Rutas relativas a `backend/SistemaPOS/src/main/java/MiTrampita/SistemaPOS/`:

| Archivo | Responsabilidad |
| --- | --- |
| `entity/Venta.java`, `DetalleVenta.java`, `PagoVenta.java` | Cuenta, tandas y libro de pagos |
| `entity/EstadoCuenta.java`, `OrigenPedido.java`, `TipoEntrega.java`, `EstadoPreparacion.java` | Estados tipados |
| `dto/RegistrarPagoRequest.java` | Importe positivo, dos decimales, método y UUID; efectivo suficiente |
| `dto/PedidoOnlineRequest.java` | Cliente exclusivo, ítems validados, tipo de entrega y dirección requerida para delivery |
| `dto/UpdateItemStatusRequest.java` | Estado nuevo y estado esperado obligatorios |
| `dto/VentaResponse.java` | Cuenta, saldo, historial e ítems con preparación |
| `dto/KitchenItemResponse.java` | Producto, cantidad, destino y fechas; sin importes ni contactos |
| `service/VentaService.java` | Stock, pagos, recálculo y cierre transaccional |
| `service/KitchenService.java` | Cola y máquina de estados |
| `controller/VentaController.java`, `OnlineOrderController.java`, `KitchenController.java` | API validada |
| `repositorio/PagoVentaRepository.java`, `VentaRepository.java`, `DetalleVentaRepository.java` | Persistencia y consultas ordenadas |
| `config/SecurityConfig.java`, `DataInitializer.java`, `security/PosPrincipal.java`, `controller/UsuarioController.java` | Permisos y rol COCINERO |

## 4. Contratos HTTP

Las rutas requieren sesión y CSRF en escrituras. Se reutiliza `/api/auth/csrf` y `/api/auth/login`.

| Método / ruta | Permisos | Uso |
| --- | --- | --- |
| `POST /api/ventas` | ADMIN, MOZO | Abrir comanda local y descontar stock |
| `PATCH /api/ventas/{id}/items` | ADMIN, MOZO | Agregar tanda a cuenta no pagada |
| `GET /api/ventas/abiertas` | ADMIN, MOZO, CAJA | Comandas abiertas, incluidos estados de platos |
| `GET /api/ventas/{id}` | ADMIN, CAJA; MOZO para ventas abiertas | Detalle de cuenta |
| `POST /api/ventas/{id}/pagos` | ADMIN, CAJA | Registrar un abono |
| `GET /api/ventas/{id}/pagos` | ADMIN, CAJA | Historial |
| `PATCH /api/ventas/{id}/cerrar` | ADMIN, CAJA | Cerrar sin generar un nuevo pago |
| `GET /api/pedidos-online` | ADMIN, MOZO, CAJA | Recepción de pedidos activos |
| `POST /api/pedidos-online` | ADMIN, CAJA | Pedido online con pago inicial opcional |
| `GET /api/cocina/items` | ADMIN, COCINERO | Ítems pendientes/preparando ordenados por tiempo |
| `PATCH /api/cocina/items/{id}/estado` | ADMIN, COCINERO | Preparando o listo |
| `PATCH /api/ventas/items/{id}/servir` | ADMIN, MOZO, CAJA | Confirmar entrega |

El usuario COCINERO solo ve Cocina y las rutas de sesión. No puede consultar ventas, mesas, productos, marketing ni registrar pagos. Su DTO tampoco contiene datos financieros o personales. En Usuarios, COCINERO se asigna como rol exclusivo; ADMIN puede operar también cocina. La base inserta el rol; crea el usuario y su contraseña desde Usuarios.

Ejemplo de abono (POST `/api/ventas/123/pagos`):

```json
{
  "monto": 20.00,
  "metodoPago": "efectivo",
  "montoRecibido": 25.00,
  "referencia": "Abono del cliente",
  "claveOperacion": "9531c650-b146-4a63-82a3-9ccad260ff52"
}
```

Devuelve `totalPagado`, `saldoPendiente`, `estadoCuenta` e historial. Si el total es S/ 70, el saldo será S/ 50 y el vuelto S/ 5. Genera otro UUID para otro abono; conserva el UUID al reintentar el mismo.

Ejemplo de pedido online (sustituir IDs por los del catálogo):

```json
{
  "empresaId": 1,
  "clienteId": 1,
  "tipoComprobanteId": 1,
  "numeroComprobante": "B001-ONLINE-00001",
  "tipoEntrega": "DELIVERY",
  "direccionEnvio": "Dirección confirmada de entrega",
  "items": [{ "productoId": 1, "cantidad": 2 }],
  "pagoTotal": false,
  "pagoInicial": {
    "monto": 20.00,
    "metodoPago": "yape_plin",
    "referencia": "Operación confirmada por recepción",
    "claveOperacion": "fa934d2b-c553-4283-a3dc-c06c1fba5596"
  }
}
```

Contra entrega: `pagoInicial: null`, `pagoTotal: false`. Pago 100%: `pagoTotal: true` y abono igual al total calculado en servidor. Si cambió el precio, se rechaza toda la operación para actualizar el catálogo. Recojo: `tipoEntrega: "RECOJO"`, sin dirección de envío. Se puede enviar `cliente` validado en lugar de `clienteId`, nunca ambos.

Cambio de cocina:

```json
{ "estado": "EN_PREPARACION", "estadoActual": "PENDIENTE" }
```

El cierre usa PATCH sin campos financieros (`{}`). Si queda saldo o faltan entregas, devuelve 409. Las validaciones de datos devuelven 400; los permisos, 403; stock insuficiente y conflictos de estado, 409.

La recepción online es una API interna autenticada y una pantalla para registrar pedidos recibidos. Los pagos iniciales son pagos confirmados por recepción. No se integra una pasarela de tarjetas ni se expone una tienda pública; una futura pasarela debe confirmar por webhook autenticado antes de registrar un abono.

## 5. Archivos y operación del frontend

- `frontend/src/components/KitchenBoard.jsx`: tickets por comanda, destino local/online, cantidades, hora por tanda y botones Preparando/Listo.
- `frontend/src/components/OnlineOrdersBoard.jsx`: recepción, aviso de pedidos nuevos, dirección, estados Pagado/Adelanto/Contra entrega y entrega de ítems.
- `frontend/src/components/OnlineOrderForm.jsx`: recojo/delivery, productos y tres modalidades de pago inicial.
- `frontend/src/components/UniversalPaymentModal.jsx`: abonos, efectivo/vuelto, historial y cierre. Se invoca por `saleId` desde mesas o recepción.
- `frontend/src/components/OrderStatus.jsx`: etiquetas compartidas de preparación y pago.
- `frontend/src/hooks/usePolling.js`: actualización cada cinco segundos, sin solapar consultas periódicas ni aceptar respuestas obsoletas.
- `frontend/src/App.jsx`, `permissions.js`: navegación por rol y acceso inicial de cocinero a Cocina.
- `frontend/src/SalesPos.jsx`, `TablesView.jsx`: avisos de platos LISTO por mesa y confirmación Entregado.
- `frontend/src/Receipt.jsx`: comprobante con todos los métodos/abonos y saldo.
- `frontend/src/styles.css`: tableros y modal adaptados a pantallas pequeñas.

Flujo local: Mozo envía comanda → Cocina prepara y marca listo → Mozo ve aviso y confirma entregado → Caja registra los abonos y cierra con saldo cero. Caja puede cobrar antes de que termine cocina; la mesa se libera al terminar entrega y cierre.

Flujo online: Caja/ADMIN recibe pedido con pago total, adelanto o contra entrega → Cocina ve destino Online → recepción confirma entrega y cobra el saldo → finaliza el pedido. MOZO puede consultar recepción y confirmar la entrega, pero no crear pedidos con pagos ni cobrar.

## 6. Arranque y verificación

Terminal backend, después de migrar:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\backend\SistemaPOS'
.\mvnw.cmd spring-boot:run
```

Terminal frontend:

```powershell
Set-Location 'D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita\frontend'
npm.cmd run dev
```

Frontend `http://localhost:5173`; backend `http://localhost:9090`. El proxy Vite conserva sesión y CSRF.

Verificación reproducible:

```powershell
# Desde backend/SistemaPOS
.\mvnw.cmd test
# SOLO contra un backend conectado a una base de pruebas
$env:POS_TEST_PASSWORD = '<contraseña de ADMIN de pruebas>'
python scripts/verify_http_flow.py --url http://localhost:9090
# Desde frontend
npm.cmd run build
```

`scripts/verify_schema_05.py --engine postgresql|mysql --database pos_kds_qa_<nombre_nuevo>` crea bases nuevas y prueba la instalación ejecutando el archivo único completo, la sección 05 por separado, el preflight de stock insuficiente, la conversión de históricos y el rechazo de segunda ejecución. Usa `DB_USERNAME`, `DB_PASSWORD` y opcionalmente `DB_PORT`. Nunca migra `pos_db` ni elimina bases existentes.

La suite `PosFlowTests` verifica stock al pedir, rollback de varias líneas, pedidos/abonos simultáneos, protección de versiones de inventario, reintentos sin duplicados, respuestas HTTP de abonos, estados de cocina, permisos de cocinero/mozo/caja, los tres pagos online, validaciones y fidelización. Las pruebas HTTP se ejecutan además contra PostgreSQL y MySQL reales con `ddl-auto=validate`.

Verificación realizada el 1 de octubre de 2026: Maven package con 17 pruebas aprobadas (16 de flujos y 1 de arranque), build Vite aprobado, scripts de migración aprobados en PostgreSQL 17 y MySQL 9.6, y flujo HTTP completo aprobado en ambos motores. Se usaron bases de pruebas independientes; `pos_db` conserva la versión 04 y no fue migrada. La revisión visual y de interacción en navegador quedó pendiente porque la herramienta no dispuso de un navegador conectado.
