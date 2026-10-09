# Validación del menú público 09

Prueba del 8 de octubre de 2026 con Microsoft Edge/Playwright y bases QA. Datos de clientes, productos y empresas son fixtures. Se verificó el mismo flujo HTTP en PostgreSQL y MySQL por separado.

| Evidencia | Verificación |
| --- | --- |
| [Carta de escritorio](01-menu-escritorio.png) | Categorías, productos publicados, precios, agotados y carrito. |
| [Carta móvil](02-menu-movil.png) | Menú y acceso al carrito en 390 px. |
| [Carrito móvil](03-carrito-movil.png) | Observaciones, contacto, DNI y dirección de delivery. |
| [Pedido recibido](04-seguimiento-recibido.png) | Recuperación del mismo pedido después de perder la respuesta. |
| [Recepción en Caja](05-recepcion-caja.png) | Publicación, enlace, confirmación y notas del cliente. |
| [Pedido listo](06-seguimiento-listo.png) | Avance sincronizado con Cocina/Bar. |
| [Resultado automatizado](resultado.json) | Identificadores QA, comprobaciones y ausencia de errores JavaScript. |

La página pública se comprobó en anchos de 1440, 390 y 320 píxeles, sin desborde horizontal, sin cookies de sesión ni peticiones al API interno. Se revisaron visualmente las capturas. La pérdida de respuesta se simuló después de que el servidor guardara el pedido, con reintento tras recargar el navegador.

Consultar [activación y rutas exactas](../../MENU_PUBLICO_09.md). No se publicó un dominio ni se migró la base operativa.
