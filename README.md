# Sistema-POS-Mi-Trampita
Un proyecto de sistema POS

## Inicio rápido

1. Para una instalación nueva, crea la base `pos_db` en PostgreSQL y ejecuta `database/scripts/pos_postgresql.sql` completo. Para MySQL, ejecuta `database/scripts/pos_mysql.sql` completo. Cada archivo incluye el esquema y todas las migraciones, incluida la sección 06 de Caja, WhatsApp y comprobantes.
   No ejecutes el instalador completo sobre una base existente: respáldala y sigue las instrucciones de migración en `docs/IMPLEMENTACION_ESCALAMIENTO.md`.
   Si ya tiene 04, aplica 05 y después 06; si ya tiene 05, aplica solo 06. No repitas las secciones instaladas. La guía vigente con rutas, permisos, archivos y pruebas es [CAJA_WHATSAPP_KDS.md](docs/CAJA_WHATSAPP_KDS.md).
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
