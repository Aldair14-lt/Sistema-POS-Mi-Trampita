# Evidencias de validación comercial 08

Prueba ejecutada el 8 de octubre de 2026 con Microsoft Edge/Playwright, Vite QA y backend conectado a una base PostgreSQL aislada. Los datos son fixtures de prueba. Los verificadores de esquema y HTTP también aprobaron por separado en PostgreSQL y MySQL.

[`resultado.json`](resultado.json) registra la aprobación de los flujos y la ausencia de errores JavaScript. La pantalla de cobro se verificó a 390 píxeles; los tableros se revisaron a 1440 píxeles.

| Evidencia | Qué verifica |
| --- | --- |
| [Caja](01-caja.png) | Apertura, turno y medios de pago. |
| [Cobro móvil](02-pago-movil.png) | Cuenta abierta, abono y efectivo con vuelto. |
| [Factura A4](03-factura-a4.pdf) | Cliente fiscal, detalle, IGV, pagos y firmas. |
| [Ticket 80 mm](04-ticket-80mm.pdf) | Ancho térmico, detalle y vuelto. |
| [Cierre A4](05-cierre-a4.pdf) | Montos por método, efectivo esperado, contado y diferencia. |
| [Online](06-online.png) | WhatsApp con adelanto y Kanban de preparación. |
| [Mesas](07-mesas.png) | Zonas y los cuatro colores de estado. |
| [Marketing](08-marketing.png) | Segmentos y descarga CSV. |
| [Cocina](09-cocina.png), [Bar](09-bar.png) | Acceso separado por rol y estación. |
| [Comanda Cocina](10-comanda-cocina.png), [Comanda Bar](10-comanda-bar.png) | Mozo, observaciones y ausencia de precios. |

[`paginas-impresion.json`](paginas-impresion.json) contiene la comprobación independiente de cantidad y dimensiones de páginas de los tres PDF. También se revisaron visualmente las capturas de las plantillas. No se probó hardware de impresión ni una integración SUNAT/OSE.

Para reproducir, consultar [la guía comercial](../../ECOSISTEMA_COMERCIAL_08.md) y `frontend/scripts/verify-commercial.cjs`. Las verificaciones crean sus propios datos; ejecutarlas únicamente en bases QA.
