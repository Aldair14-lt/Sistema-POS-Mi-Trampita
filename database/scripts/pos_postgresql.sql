-- ============================================================================
-- POS MI TRAMPITA — INSTALACIÓN COMPLETA PARA POSTGRESQL 14+
--
-- Base nueva: crear pos_db, conectarse a ella y ejecutar este archivo completo.
-- Base existente: respaldo previo y ejecutar solo las migraciones pendientes
-- (03, 04 y/o 05), nunca la sección de instalación.
-- Si ya tiene 04, seleccionar únicamente la sección 05 hasta el fin del archivo.
-- Si ya tiene 05, no repetir: se protege el inventario contra dobles descuentos.
-- Backend detenido. Las migraciones 04 y 05 son transaccionales.
-- ============================================================================

-- 01. ESQUEMA BASE
CREATE TYPE estado_usuario AS ENUM ('activo', 'inactivo', 'bloqueado');
CREATE TYPE tipo_metodo_pago AS ENUM ('efectivo', 'tarjeta', 'transferencia', 'yape_plin');

CREATE TABLE empresa (
  id_empresa SERIAL PRIMARY KEY, ruc VARCHAR(20) NOT NULL UNIQUE,
  razon_social VARCHAR(150) NOT NULL, nombre_comercial VARCHAR(150),
  direccion TEXT NOT NULL, telefono VARCHAR(20), correo VARCHAR(100),
  fecha_registro TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE rol (
  id_rol SERIAL PRIMARY KEY, nombre_rol VARCHAR(50) NOT NULL UNIQUE,
  descripcion VARCHAR(255)
);
CREATE TABLE usuario (
  id_usuario SERIAL PRIMARY KEY, usuario VARCHAR(50) NOT NULL UNIQUE,
  contraseña VARCHAR(255) NOT NULL, nombre_completo VARCHAR(150) NOT NULL,
  correo_electronico VARCHAR(100), pin_caja VARCHAR(255),
  estado estado_usuario NOT NULL DEFAULT 'activo',
  fecha_creacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE usuario_rol (
  id_usuario_rol SERIAL PRIMARY KEY, id_usuario INT NOT NULL, id_rol INT NOT NULL,
  CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_usuario_rol_rol FOREIGN KEY (id_rol) REFERENCES rol(id_rol) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT uk_usuario_rol UNIQUE (id_usuario, id_rol)
);
CREATE TABLE categoria (
  id_categoria SERIAL PRIMARY KEY, nombre_categoria VARCHAR(100) NOT NULL,
  descripcion VARCHAR(255)
);
CREATE TABLE marca (id_marca SERIAL PRIMARY KEY, nombre_marca VARCHAR(100) NOT NULL);
CREATE TABLE proveedor (
  id_proveedor SERIAL PRIMARY KEY, ruc_dni VARCHAR(20) NOT NULL,
  razon_social VARCHAR(150) NOT NULL, telefono VARCHAR(20), correo VARCHAR(100)
);
CREATE TABLE producto (
  id_producto SERIAL PRIMARY KEY, id_categoria INT NOT NULL, id_marca INT NOT NULL,
  id_proveedor INT NOT NULL, codigo_barras VARCHAR(50) NOT NULL UNIQUE,
  nombre_producto VARCHAR(150) NOT NULL, descripcion TEXT,
  precio_compra NUMERIC(10,2) NOT NULL DEFAULT 0,
  precio_venta NUMERIC(10,2) NOT NULL DEFAULT 0,
  stock_actual INT NOT NULL DEFAULT 0, stock_minimo INT NOT NULL DEFAULT 5,
  fecha_registro TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_producto_categoria FOREIGN KEY (id_categoria) REFERENCES categoria(id_categoria) ON UPDATE CASCADE,
  CONSTRAINT fk_producto_marca FOREIGN KEY (id_marca) REFERENCES marca(id_marca) ON UPDATE CASCADE,
  CONSTRAINT fk_producto_proveedor FOREIGN KEY (id_proveedor) REFERENCES proveedor(id_proveedor) ON UPDATE CASCADE,
  CONSTRAINT ck_producto_precios CHECK (precio_compra >= 0 AND precio_venta >= 0),
  CONSTRAINT ck_producto_stock CHECK (stock_actual >= 0 AND stock_minimo >= 0)
);
CREATE INDEX idx_producto_categoria ON producto(id_categoria);
CREATE INDEX idx_producto_marca ON producto(id_marca);
CREATE INDEX idx_producto_proveedor ON producto(id_proveedor);
CREATE TABLE cliente (
  id_cliente SERIAL PRIMARY KEY, numero_documento VARCHAR(20) NOT NULL,
  nombres_razon_social VARCHAR(150) NOT NULL, direccion VARCHAR(255),
  telefono VARCHAR(20), correo VARCHAR(100)
);
CREATE TABLE tipo_comprobante (
  id_tipo_comprobante SERIAL PRIMARY KEY, nombre_tipo VARCHAR(50) NOT NULL,
  serie VARCHAR(10) NOT NULL, descripcion VARCHAR(255)
);
CREATE TABLE mesa (
  id_mesa SERIAL PRIMARY KEY, numero_mesa INT NOT NULL UNIQUE,
  capacidad INT NOT NULL DEFAULT 4, estado_mesa VARCHAR(20) NOT NULL DEFAULT 'LIBRE',
  CONSTRAINT ck_mesa_numero CHECK (numero_mesa > 0),
  CONSTRAINT ck_mesa_capacidad CHECK (capacidad > 0),
  CONSTRAINT ck_mesa_estado CHECK (estado_mesa IN ('LIBRE','OCUPADA','RESERVADA'))
);
CREATE TABLE venta (
  id_venta SERIAL PRIMARY KEY, id_empresa INT NOT NULL, id_usuario INT NOT NULL,
  id_cliente INT NOT NULL, id_tipo_comprobante INT NOT NULL, id_mesa INT,
  numero_comprobante VARCHAR(50) NOT NULL, subtotal NUMERIC(10,2) NOT NULL DEFAULT 0,
  igv_impuesto NUMERIC(10,2) NOT NULL DEFAULT 0, total NUMERIC(10,2) NOT NULL DEFAULT 0,
  metodo_pago tipo_metodo_pago NOT NULL DEFAULT 'efectivo',
  estado_venta VARCHAR(20) NOT NULL DEFAULT 'ABIERTA',
  fecha_venta TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_venta_empresa FOREIGN KEY (id_empresa) REFERENCES empresa(id_empresa) ON UPDATE CASCADE,
  CONSTRAINT fk_venta_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario) ON UPDATE CASCADE,
  CONSTRAINT fk_venta_cliente FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente) ON UPDATE CASCADE,
  CONSTRAINT fk_venta_tipo_comprobante FOREIGN KEY (id_tipo_comprobante) REFERENCES tipo_comprobante(id_tipo_comprobante) ON UPDATE CASCADE,
  CONSTRAINT fk_venta_mesa FOREIGN KEY (id_mesa) REFERENCES mesa(id_mesa) ON UPDATE CASCADE,
  CONSTRAINT ck_venta_importes CHECK (subtotal >= 0 AND igv_impuesto >= 0 AND total >= 0),
  CONSTRAINT ck_venta_estado CHECK (estado_venta IN ('ABIERTA','CERRADA','ANULADA'))
);
CREATE TABLE detalle_venta (
  id_detalle_venta SERIAL PRIMARY KEY, id_venta INT NOT NULL, id_producto INT NOT NULL,
  cantidad INT NOT NULL DEFAULT 1, precio_unitario NUMERIC(10,2) NOT NULL DEFAULT 0,
  subtotal NUMERIC(10,2) NOT NULL DEFAULT 0,
  CONSTRAINT fk_detalle_venta_venta FOREIGN KEY (id_venta) REFERENCES venta(id_venta) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_detalle_venta_producto FOREIGN KEY (id_producto) REFERENCES producto(id_producto) ON UPDATE CASCADE,
  CONSTRAINT ck_detalle_cantidad CHECK (cantidad > 0),
  CONSTRAINT ck_detalle_importes CHECK (precio_unitario >= 0 AND subtotal >= 0),
  CONSTRAINT ck_detalle_subtotal CHECK (subtotal = cantidad * precio_unitario)
);
CREATE UNIQUE INDEX uk_cliente_numero_documento ON cliente(numero_documento);
CREATE UNIQUE INDEX uk_tipo_comprobante_serie ON tipo_comprobante(nombre_tipo, serie);
CREATE UNIQUE INDEX uk_venta_comprobante ON venta(id_tipo_comprobante, numero_comprobante);
CREATE INDEX idx_venta_fecha ON venta(fecha_venta);
CREATE INDEX idx_venta_mesa_estado ON venta(id_mesa, estado_venta);
CREATE INDEX idx_detalle_venta_producto ON detalle_venta(id_producto);

-- 03. MIGRACIÓN DE BASE EXISTENTE (idempotente para instalaciones nuevas).
CREATE TABLE IF NOT EXISTS mesa (
  id_mesa SERIAL PRIMARY KEY, numero_mesa INT NOT NULL UNIQUE,
  capacidad INT NOT NULL DEFAULT 4, estado_mesa VARCHAR(20) NOT NULL DEFAULT 'LIBRE'
);
ALTER TABLE venta ADD COLUMN IF NOT EXISTS estado_venta VARCHAR(20) NOT NULL DEFAULT 'CERRADA';
ALTER TABLE venta ADD COLUMN IF NOT EXISTS id_mesa INT;
CREATE INDEX IF NOT EXISTS idx_venta_mesa_estado ON venta(id_mesa, estado_venta);
UPDATE venta SET estado_venta = 'CERRADA' WHERE estado_venta IS NULL;
ALTER TABLE venta ALTER COLUMN estado_venta SET DEFAULT 'ABIERTA';
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_venta_estado' AND conrelid='venta'::regclass) THEN
    ALTER TABLE venta ADD CONSTRAINT ck_venta_estado CHECK (estado_venta IN ('ABIERTA','CERRADA','ANULADA'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_venta_mesa' AND conrelid='venta'::regclass) THEN
    ALTER TABLE venta ADD CONSTRAINT fk_venta_mesa FOREIGN KEY (id_mesa) REFERENCES mesa(id_mesa) ON UPDATE CASCADE;
  END IF;
END $$;

-- 04. MIGRACIÓN DE BASE EXISTENTE / FINALIZACIÓN DEL ESQUEMA.
BEGIN;
CREATE TABLE IF NOT EXISTS pos_migraciones(version VARCHAR(80) PRIMARY KEY, aplicada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pos_migraciones WHERE version='04_zonificacion_marketing_roles') THEN
    RAISE EXCEPTION 'La migración 04 ya fue aplicada';
  END IF;
END $$;
LOCK TABLE mesa, venta, detalle_venta, producto, cliente IN ACCESS EXCLUSIVE MODE;
ALTER TABLE mesa RENAME TO mesas;
ALTER TABLE mesas RENAME COLUMN id_mesa TO id;
ALTER TABLE mesas RENAME COLUMN numero_mesa TO numero;
ALTER TABLE mesas RENAME COLUMN estado_mesa TO estado;
ALTER TABLE venta RENAME TO ventas;
ALTER TABLE ventas RENAME COLUMN id_mesa TO mesa_id;
ALTER TABLE cliente RENAME TO clientes;
CREATE TABLE areas (
  id SERIAL PRIMARY KEY, nombre VARCHAR(100) NOT NULL, estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
  CONSTRAINT uk_area_nombre UNIQUE(nombre), CONSTRAINT ck_area_nombre CHECK(length(trim(nombre))>0),
  CONSTRAINT ck_area_estado CHECK(estado IN ('ACTIVA','INACTIVA'))
);
INSERT INTO areas(nombre) VALUES ('Salón Principal'),('Terraza'),('Zona Recreacional'),('Piscina');
CREATE UNIQUE INDEX uk_area_nombre_ci ON areas(lower(nombre));
ALTER TABLE mesas ADD COLUMN area_id INT;
UPDATE mesas SET area_id=(SELECT id FROM areas WHERE nombre='Salón Principal');
ALTER TABLE mesas ALTER COLUMN area_id SET NOT NULL;
ALTER TABLE mesas ADD CONSTRAINT fk_mesa_area FOREIGN KEY(area_id) REFERENCES areas(id);
CREATE INDEX idx_mesas_area ON mesas(area_id);
ALTER TABLE mesas DROP CONSTRAINT IF EXISTS ck_mesa_estado;
UPDATE mesas SET estado='OCUPADA' WHERE estado='RESERVADA';
ALTER TABLE mesas ADD CONSTRAINT ck_mesa_estado CHECK(estado IN ('LIBRE','OCUPADA','ATENDIENDO'));
INSERT INTO mesas(numero,capacidad,estado,area_id)
SELECT n,4,'LIBRE',a.id FROM generate_series(1,12) n
JOIN areas a ON a.nombre=CASE WHEN n<=3 THEN 'Salón Principal' WHEN n<=6 THEN 'Terraza' WHEN n<=9 THEN 'Zona Recreacional' ELSE 'Piscina' END
ON CONFLICT(numero) DO NOTHING;
DO $$ DECLARE old_sale RECORD; new_id INT; new_number INT; BEGIN
  FOR old_sale IN SELECT id_venta FROM ventas WHERE estado_venta='ABIERTA' AND mesa_id IS NULL ORDER BY id_venta LOOP
    SELECT COALESCE(MAX(numero),0)+1 INTO new_number FROM mesas;
    INSERT INTO mesas(numero,capacidad,estado,area_id) VALUES(new_number,4,'OCUPADA',(SELECT id FROM areas WHERE nombre='Salón Principal')) RETURNING id INTO new_id;
    UPDATE ventas SET mesa_id=new_id WHERE id_venta=old_sale.id_venta;
  END LOOP;
END $$;
UPDATE mesas m SET estado='ATENDIENDO' WHERE EXISTS(SELECT 1 FROM ventas v WHERE v.mesa_id=m.id AND v.estado_venta='ABIERTA');
ALTER TABLE ventas ADD CONSTRAINT ck_venta_mesa_abierta CHECK(estado_venta<>'ABIERTA' OR mesa_id IS NOT NULL);
CREATE UNIQUE INDEX uk_venta_mesa_abierta ON ventas(mesa_id) WHERE estado_venta='ABIERTA';
ALTER TABLE clientes ADD COLUMN fecha_nacimiento DATE;
ALTER TABLE clientes ADD COLUMN frecuencia_visitas BIGINT NOT NULL DEFAULT 0;
ALTER TABLE clientes ADD COLUMN total_gastado NUMERIC(19,2) NOT NULL DEFAULT 0;
ALTER TABLE clientes ADD CONSTRAINT ck_cliente_metricas CHECK(frecuencia_visitas>=0 AND total_gastado>=0);
CREATE INDEX idx_cliente_frecuencia ON clientes(frecuencia_visitas DESC,total_gastado DESC);
CREATE INDEX idx_cliente_mes_nacimiento ON clientes(EXTRACT(MONTH FROM fecha_nacimiento));
ALTER TABLE ventas ADD COLUMN fecha_cobro TIMESTAMPTZ;
UPDATE ventas SET fecha_cobro=fecha_venta WHERE estado_venta='CERRADA';
ALTER TABLE usuario ALTER COLUMN estado DROP DEFAULT;
ALTER TABLE usuario ALTER COLUMN estado TYPE VARCHAR(20) USING estado::text;
ALTER TABLE usuario ALTER COLUMN estado SET DEFAULT 'activo';
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_estado CHECK(estado IN ('activo','inactivo','bloqueado'));
ALTER TABLE ventas ALTER COLUMN metodo_pago DROP DEFAULT;
ALTER TABLE ventas ALTER COLUMN metodo_pago TYPE VARCHAR(20) USING metodo_pago::text;
ALTER TABLE ventas ALTER COLUMN metodo_pago SET DEFAULT 'efectivo';
ALTER TABLE ventas ADD CONSTRAINT ck_venta_metodo_pago CHECK(metodo_pago IN ('efectivo','tarjeta','transferencia','yape_plin'));
ALTER TABLE producto ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE producto p SET stock_actual=p.stock_actual+x.cantidad FROM (
  SELECT d.id_producto,SUM(d.cantidad)::INT cantidad FROM detalle_venta d JOIN ventas v ON v.id_venta=d.id_venta
  WHERE v.estado_venta='ABIERTA' GROUP BY d.id_producto
) x WHERE p.id_producto=x.id_producto;
UPDATE clientes c SET frecuencia_visitas=x.visitas,total_gastado=x.gastado FROM (
  SELECT id_cliente,COUNT(*) visitas,SUM(total) gastado FROM ventas WHERE estado_venta='CERRADA' GROUP BY id_cliente
) x WHERE c.id_cliente=x.id_cliente;
INSERT INTO rol(nombre_rol,descripcion) VALUES ('ADMIN','Administración completa'),('MOZO','Mesas, pedidos y comandas'),('CAJA','Cobros, comprobantes y liberación de mesas') ON CONFLICT(nombre_rol) DO NOTHING;
INSERT INTO usuario_rol(id_usuario,id_rol)
SELECT DISTINCT ur.id_usuario,c.id_rol FROM usuario_rol ur JOIN rol old ON old.id_rol=ur.id_rol
JOIN rol c ON c.nombre_rol=CASE upper(trim(old.nombre_rol)) WHEN 'ADMINISTRADOR' THEN 'ADMIN' WHEN 'VENTAS' THEN 'MOZO' WHEN 'VENDEDOR' THEN 'MOZO' WHEN 'MOZO/VENTAS' THEN 'MOZO' WHEN 'CAJERO' THEN 'CAJA' ELSE upper(trim(old.nombre_rol)) END
WHERE c.nombre_rol IN ('ADMIN','MOZO','CAJA') ON CONFLICT(id_usuario,id_rol) DO NOTHING;
-- Cuentas iniciales de desarrollo: cambiar estas contraseñas antes de producción.
INSERT INTO usuario(usuario, "contraseña", nombre_completo, estado) VALUES
  ('admin', '$2a$10$BnkUIi3Cmcwy4NhxxogScuGuu245TdsdbbDp3Ok2TK9Gp9ygwa3ja', 'Administrador', 'activo'),
  ('mozo', '$2a$10$A6XNEdkEgy0vxF3S42mFA.Z48U.fRssEv32RfmOj2KYntHXyaTbbm', 'Usuario Mozo', 'activo'),
  ('caja', '$2a$10$aH2Nd4u6yvlb9TYWgpaiYeqPDz0gPf0I/DSG6pXiKgyESCohW2AjO', 'Usuario Caja', 'activo')
ON CONFLICT (usuario) DO NOTHING;
INSERT INTO usuario_rol(id_usuario,id_rol)
SELECT u.id_usuario,r.id_rol
FROM (VALUES ('admin','ADMIN'),('mozo','MOZO'),('caja','CAJA')) AS semilla(nombre_usuario,nombre_rol)
JOIN usuario u ON u.usuario=semilla.nombre_usuario
JOIN rol r ON r.nombre_rol=semilla.nombre_rol
ON CONFLICT (id_usuario,id_rol) DO NOTHING;
INSERT INTO pos_migraciones(version) VALUES('04_zonificacion_marketing_roles');
COMMIT;

-- 05. MIGRACIÓN DE BASE EXISTENTE / CUENTAS, ONLINE Y COCINA.
-- Ejecutar después de la migración 04, con el backend detenido y respaldo previo.
-- PostgreSQL: conversión completa atómica. No modifica ventas históricas ni métricas.
BEGIN;
CREATE TABLE IF NOT EXISTS pos_migraciones (
  version VARCHAR(80) PRIMARY KEY, aplicada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
LOCK TABLE ventas, detalle_venta, producto IN ACCESS EXCLUSIVE MODE;
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pos_migraciones WHERE version='05_cuentas_online_cocina') THEN
    RAISE EXCEPTION 'La migración 05 ya fue aplicada';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pos_migraciones WHERE version='04_zonificacion_marketing_roles') THEN
    RAISE EXCEPTION 'Primero aplica la migración 04';
  END IF;
  -- Las comandas abiertas de la versión 04 aún no descontaron inventario.
  -- Detener sin cambios si no hay suficiente stock; nunca producir stock negativo.
  IF EXISTS (
    SELECT 1 FROM producto p JOIN (
      SELECT d.id_producto, SUM(d.cantidad) unidades FROM detalle_venta d
      JOIN ventas v ON v.id_venta=d.id_venta WHERE v.estado_venta='ABIERTA' GROUP BY d.id_producto
    ) x ON x.id_producto=p.id_producto WHERE p.stock_actual<x.unidades
  ) THEN RAISE EXCEPTION 'Stock insuficiente para comandas abiertas; regulariza el inventario antes de migrar'; END IF;
END $$;

ALTER TABLE ventas
  ADD COLUMN estado_cuenta VARCHAR(25) NOT NULL DEFAULT 'ABIERTA',
  ADD COLUMN origen_pedido VARCHAR(10) NOT NULL DEFAULT 'LOCAL',
  ADD COLUMN tipo_entrega VARCHAR(10) NOT NULL DEFAULT 'MESA',
  ADD COLUMN direccion_envio VARCHAR(255),
  ADD CONSTRAINT ck_cuenta_estado CHECK(estado_cuenta IN('ABIERTA','PAGADA_PARCIALMENTE','CERRADA')),
  ADD CONSTRAINT ck_pedido_origen CHECK(origen_pedido IN('LOCAL','ONLINE'));
ALTER TABLE ventas DROP CONSTRAINT IF EXISTS ck_venta_mesa_abierta;
ALTER TABLE ventas ADD CONSTRAINT ck_pedido_entrega CHECK(
  (origen_pedido='LOCAL' AND tipo_entrega='MESA' AND direccion_envio IS NULL
    AND (estado_venta<>'ABIERTA' OR mesa_id IS NOT NULL)) OR
  (origen_pedido='ONLINE' AND mesa_id IS NULL AND
    (tipo_entrega='RECOJO' OR (tipo_entrega='DELIVERY' AND direccion_envio IS NOT NULL AND length(trim(direccion_envio))>0)))
);
ALTER TABLE detalle_venta
  ADD COLUMN estado_preparacion VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  ADD COLUMN fecha_pedido TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN fecha_estado TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD CONSTRAINT ck_detalle_preparacion CHECK(estado_preparacion IN('PENDIENTE','EN_PREPARACION','LISTO','SERVIDO'));

CREATE TABLE pagos_venta (
  id_pago SERIAL PRIMARY KEY,
  id_venta INT NOT NULL REFERENCES ventas(id_venta),
  id_usuario INT NOT NULL REFERENCES usuario(id_usuario),
  monto NUMERIC(10,2) NOT NULL, metodo_pago VARCHAR(20) NOT NULL,
  monto_recibido NUMERIC(10,2) NOT NULL, referencia VARCHAR(100) NOT NULL DEFAULT '',
  clave_operacion VARCHAR(36) NOT NULL, fecha_pago TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_pago_operacion UNIQUE(id_venta,clave_operacion),
  CONSTRAINT ck_pago_monto CHECK(monto>0 AND monto_recibido>=monto),
  CONSTRAINT ck_pago_metodo CHECK(metodo_pago IN('efectivo','tarjeta','transferencia','yape_plin'))
);
CREATE INDEX idx_cocina_estado_fecha ON detalle_venta(estado_preparacion,fecha_pedido,id_detalle_venta);
CREATE INDEX idx_online_estado_fecha ON ventas(origen_pedido,estado_venta,fecha_venta);

UPDATE ventas SET estado_cuenta='CERRADA' WHERE estado_venta IN('CERRADA','ANULADA');
INSERT INTO pagos_venta(id_venta,id_usuario,monto,metodo_pago,monto_recibido,referencia,clave_operacion,fecha_pago)
SELECT id_venta,id_usuario,total,metodo_pago,total,'Cobro histórico anterior a migración 05',
       'historico-'||id_venta,COALESCE(fecha_cobro,fecha_venta,CURRENT_TIMESTAMP)
FROM ventas WHERE estado_venta='CERRADA' AND total>0;
UPDATE detalle_venta d SET
  estado_preparacion=CASE WHEN v.estado_venta='ABIERTA' THEN 'PENDIENTE' ELSE 'SERVIDO' END,
  fecha_pedido=COALESCE(v.fecha_venta,CURRENT_TIMESTAMP),
  fecha_estado=COALESCE(v.fecha_cobro,v.fecha_venta,CURRENT_TIMESTAMP)
FROM ventas v WHERE v.id_venta=d.id_venta;
UPDATE producto p SET stock_actual=p.stock_actual-x.unidades,version=p.version+1
FROM (
  SELECT d.id_producto,SUM(d.cantidad)::INT unidades FROM detalle_venta d
  JOIN ventas v ON v.id_venta=d.id_venta WHERE v.estado_venta='ABIERTA' GROUP BY d.id_producto
) x WHERE x.id_producto=p.id_producto;
INSERT INTO rol(nombre_rol,descripcion) VALUES
 ('ADMIN','Administración completa'),('MOZO','Pedidos y entrega de platos'),
 ('CAJA','Abonos, recepción online y cierre'),('COCINERO','Acceso exclusivo a preparación de cocina')
ON CONFLICT(nombre_rol) DO NOTHING;
INSERT INTO pos_migraciones(version) VALUES('05_cuentas_online_cocina');
COMMIT;
-- Crear/asignar el usuario cocinero desde Usuarios; no se incluye una contraseña compartida.
