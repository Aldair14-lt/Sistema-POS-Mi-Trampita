# Evidencia de validación

2 de octubre de 2026. Datos ficticios en bases independientes; las capturas muestran usuarios, mesas y productos de QA.

- `01-pago-efectivo.png`: abono de S/ 20, recibido S/ 50, vuelto S/ 30.
- `02-factura.png`: datos fiscales y confirmación del saldo.
- `03-factura-movil.png`: modal en ancho de 390 px, con desplazamiento vertical.
- `04-ticket.png`: recibo térmico, datos fiscales y desglose de pagos.
- `05-mesas.png`: mesas libres, esperando y comiendo, con saldo pendiente.
- `06-cocina.png`: KDS oscuro con pendientes, preparación y listos.
- `resultado.json`: aserciones aprobadas del flujo real en navegador.

Backend: 30 pruebas de flujos y una de arranque, sin fallos. Migraciones 05/06/07 e instalación completa probadas en PostgreSQL 17 y MySQL 9.6. Ambos backends arrancaron con validación estricta de esquema y pasaron `verify_http_flow.py`, incluyendo cobro fiscal, cancelación, idempotencia, permisos y SSE.

La impresión se verificó en el navegador; no se probó una impresora física. La emisión es interna, sin conexión a SUNAT. Comandos y contratos: [guía de implementación](../../CAJA_AVANZADA_07.md).
