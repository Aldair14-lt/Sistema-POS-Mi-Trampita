# Sistema-POS-Mi-Trampita
Un proyecto de sistema POS

## Inicio rápido

1. Para una instalación nueva, crea la base `pos_db` en PostgreSQL y ejecuta `database/scripts/pos_postgresql.sql` completo. Para MySQL, ejecuta `database/scripts/pos_mysql.sql` completo. Cada archivo incluye el esquema y todas las migraciones, incluida la sección 05 de cuentas, pedidos online y cocina.
   No ejecutes el instalador completo sobre una base existente: respáldala y sigue las instrucciones de migración en `docs/IMPLEMENTACION_ESCALAMIENTO.md`.
   Si ya tiene la migración 04, selecciona y ejecuta únicamente la sección `05. MIGRACIÓN DE BASE EXISTENTE / CUENTAS, ONLINE Y COCINA` hasta el final del archivo de tu motor. Si ya tiene la 05, no repitas la migración. La guía vigente de cuentas, pedidos online, cocina, rutas y permisos es [CUENTAS_ONLINE_KDS.md](docs/CUENTAS_ONLINE_KDS.md).
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
