# Sistema POS - Frontend

Aplicación React + Vite para el sistema POS Mi Trampita.

## Ejecutar

Requisitos: Node.js 20 o superior.

```bash
npm install
npm run dev
```

Abrir `http://localhost:5173`.

Durante el desarrollo, Vite redirige automáticamente las rutas `/api` hacia `http://localhost:9090`. El backend Spring Boot debe estar iniciado y PostgreSQL configurado.

Para usar otra URL del backend, crear `.env` a partir de `.env.example` y definir `VITE_API_URL`.

## Conectividad real

La interfaz usa estos endpoints existentes:

- `POST /api/auth/login`
- `GET/POST/PUT/DELETE /api/productos`
- `GET/POST /api/categorias`
- `GET/POST /api/marcas`
- `GET/POST /api/proveedores`
- `GET/POST /api/clientes`
- `GET/POST /api/empresas`
- `GET/POST /api/tipos-comprobante`
- `GET /api/ventas`
- `POST /api/ventas`

El POS permite seleccionar boleta, factura o nota de venta, registrar los datos del cliente durante el cobro, calcular IGV y vuelto, validar stock y guardar la venta en PostgreSQL.
