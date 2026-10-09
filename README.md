# Sistema-POS-Mi-Trampita
Un proyecto de sistema POS

El login y los formularios de usuarios incluyen mostrar/ocultar contraseña. Consulta [las mejoras de accesibilidad y formularios compartidos](docs/LOGIN_Y_FORMULARIOS.md).

El Ticket simple permite tomar y cobrar un pedido local sin datos del cliente. Consulta [los requisitos de Boleta, Factura y la migración 11](docs/COMPROBANTES_11.md).

## Inicio rápido

1. Para una instalación nueva, crea la base `pos_db` en PostgreSQL y ejecuta `database/scripts/pos_postgresql.sql` completo. Para MySQL, ejecuta `database/scripts/pos_mysql.sql` completo. Cada archivo incluye el esquema y todas las migraciones hasta la sección 11 de ventas sin identificación.
   No ejecutes el instalador completo sobre una base existente: respáldala y sigue las instrucciones de migración en `docs/IMPLEMENTACION_ESCALAMIENTO.md`.
   Solo existen **dos scripts SQL**, uno por motor. Aplica las versiones pendientes desde las secciones del mismo archivo, en orden 05 → 06 → 07 → 08 → 09 → 10 → 11. Si ya tiene 10, ejecuta solamente 11. Si ya tiene 11, no repitas ninguna sección. Consulta [la actualización de comprobantes](docs/COMPROBANTES_11.md), [la auditoría y configuración de producción](docs/AUDITORIA_10.md), [el menú público](docs/MENU_PUBLICO_09.md) y [los módulos internos](docs/ECOSISTEMA_COMERCIAL_08.md).
2. Inicia el backend desde `backend/SistemaPOS`:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

3. Inicia el frontend desde `frontend`:

   ```powershell
   npm.cmd ci
   npm.cmd run dev
   ```

Abre `http://localhost:5173`. Los scripts SQL precargan estas cuentas iniciales: `admin` / `AdminPOS#2026` (ADMIN), `mozo` / `MozoPOS#2026` (MOZO) y `caja` / `CajaPOS#2026` (CAJA). Las contraseñas se guardan como BCrypt; cámbialas desde **Usuarios** antes de usar el sistema en producción. Los comprobantes base se inicializan al arrancar.
