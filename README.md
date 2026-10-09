# Sistema-POS-Mi-Trampita
Un proyecto de sistema POS

## Inicio rápido

1. Para una instalación nueva, crea la base `pos_db` en PostgreSQL y ejecuta `database/scripts/pos_postgresql.sql` completo. Para MySQL, ejecuta `database/scripts/pos_mysql.sql` completo. Cada archivo incluye el esquema y todas las migraciones hasta la sección 09 de menú público y pedidos web.
   No ejecutes el instalador completo sobre una base existente: respáldala y sigue las instrucciones de migración en `docs/IMPLEMENTACION_ESCALAMIENTO.md`.
   Solo existen **dos scripts SQL**, uno por motor. Aplica las versiones pendientes desde las secciones del mismo archivo, en orden 05 → 06 → 07 → 08 → 09. Si ya tiene 06, ejecuta 07, 08 y 09. Si ya tiene 07, ejecuta 08 y 09. Si ya tiene 08, ejecuta solamente 09. Si ya tiene 09, no repitas ninguna sección. La guía del menú público es [MENU_PUBLICO_09.md](docs/MENU_PUBLICO_09.md). Los módulos internos se documentan en [ECOSISTEMA_COMERCIAL_08.md](docs/ECOSISTEMA_COMERCIAL_08.md).
2. Inicia el backend desde `backend/SistemaPOS`:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

3. Inicia el frontend desde `frontend`:

   ```powershell
   npm.cmd install
   npm.cmd run dev
   ```

Abre `http://localhost:5173`. Los scripts SQL precargan estas cuentas iniciales: `admin` / `AdminPOS#2026` (ADMIN), `mozo` / `MozoPOS#2026` (MOZO) y `caja` / `CajaPOS#2026` (CAJA). Las contraseñas se guardan como BCrypt; cámbialas desde **Usuarios** antes de usar el sistema en producción. Los comprobantes base se inicializan al arrancar.
