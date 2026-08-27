# Sistema-POS-Mi-Trampita
Un proyecto de sistema POS

## Inicio rápido

1. Ejecuta PostgreSQL y crea la base `pos_db` usando `database/scripts/01_TablaMiTrampitaPostgreSQL.sql`.
2. Inicia el backend desde `backend/SistemaPOS`:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

3. Inicia el frontend desde `frontend`:

   ```powershell
   npm install
   npm run dev
   ```

Abre `http://localhost:5173`. En desarrollo se crea automáticamente el usuario `admin` con contraseña `admin123` y comprobantes base de boleta, factura y nota de venta.
