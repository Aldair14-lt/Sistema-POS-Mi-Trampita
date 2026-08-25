# Sistema POS - Frontend

Aplicación React + Vite para el sistema POS Mi Trampita.

## Ejecutar

Requisitos: Node.js 20 o superior.

```bash
npm install
npm run dev
```

Abrir `http://localhost:5173`.

Durante el desarrollo, Vite redirige automáticamente las rutas `/api` hacia `http://localhost:8080`. El backend Spring Boot debe estar iniciado y PostgreSQL configurado.

Para usar otra URL del backend, crear `.env` a partir de `.env.example` y definir `VITE_API_URL`.

## Conectividad real

La interfaz usa estos endpoints existentes:

- `GET/POST/PUT/DELETE /api/productos`
- `GET/POST /api/categorias`
- `GET/POST /api/marcas`
- `GET/POST /api/proveedores`
- `GET/POST /api/clientes`
- `GET/POST /api/empresas`
- `GET/POST /api/tipos-comprobante`
- `GET /api/ventas`
- `POST /api/ventas`

El backend actualmente no tiene controladores REST para usuarios, roles ni autenticación. Por eso el login del frontend mantiene una sesión local de interfaz y esas dos vistas informan que su endpoint está pendiente, sin hacer llamadas inventadas.
