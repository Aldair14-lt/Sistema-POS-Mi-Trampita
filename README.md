# Sistema-POS-Mi-Trampita
Un proyecto de sistema POS

## Inicio rápido

1. Para una instalación nueva, crea la base `pos_db` en PostgreSQL y ejecuta `database/scripts/pos_postgresql.sql` completo. Para MySQL, ejecuta `database/scripts/pos_mysql.sql` completo. Cada archivo incluye el esquema y todas las migraciones hasta la sección 07 de facturación y cancelaciones.
   No ejecutes el instalador completo sobre una base existente: respáldala y sigue las instrucciones de migración en `docs/IMPLEMENTACION_ESCALAMIENTO.md`.
   Solo existen **dos scripts SQL**, uno por motor. Aplica las versiones pendientes desde las secciones del mismo archivo, en orden 05 → 06 → 07. Si ya tiene 06, ejecuta únicamente la sección 07 hasta el final de `pos_postgresql.sql` o `pos_mysql.sql`. Si ya tiene 07, no repitas ninguna sección. La guía vigente con rutas, contratos, permisos y pruebas es [CAJA_AVANZADA_07.md](docs/CAJA_AVANZADA_07.md).
2. Inicia el backend desde `backend/SistemaPOS`:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

3. Inicia el frontend desde `frontend`:

   ```powershell
   npm install
   npm run dev
   ```

Abre `http://localhost:5173`. Los scripts SQL precargan estas cuentas iniciales: `admin` / `AdminPOS#2026` (ADMIN), `mozo` / `MozoPOS#2026` (MOZO) y `caja` / `CajaPOS#2026` (CAJA). Las contraseñas se guardan como BCrypt; cámbialas desde **Usuarios** antes de usar el sistema en producción. Los comprobantes base se inicializan al arrancar.
