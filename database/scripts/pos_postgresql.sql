-- ============================================================================
-- POS MI TRAMPITA — SCRIPT ÚNICO PARA POSTGRESQL 14+ (VERSIONES 01 A 09)
--
-- Base nueva: crear pos_db, conectarse a ella y ejecutar este archivo completo.
-- Base existente: respaldo previo y ejecutar solo las migraciones pendientes
-- (03 a 09), nunca la sección de instalación.
-- Si ya tiene 04: ejecutar las secciones 05, 06, 07, 08 y 09 de este mismo archivo.
-- Si ya tiene 05: ejecutar las secciones 06, 07, 08 y 09 de este mismo archivo.
-- Si ya tiene 06: ejecutar las secciones 07, 08 y 09.
-- Si ya tiene 07: ejecutar las secciones 08 y 09.
-- Si ya tiene 08: ejecutar solamente la sección 09.
-- Si ya tiene 09: no ejecutar ninguna sección; la base ya está actualizada.
-- Cada sección termina antes del siguiente encabezado numerado.
-- No repetir migraciones: se protege el inventario contra dobles descuentos.
-- Backend detenido. Las migraciones 04, 05, 06 y 07 son transaccionales.
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

-- 06. MIGRACIÓN DE BASE EXISTENTE / CAJA, WHATSAPP Y COMPROBANTES.
-- Aplicar solo esta sección si 05 ya está instalada; backend detenido y respaldo.
-- No modifica stock, pagos históricos ni números de comprobantes existentes.
BEGIN;
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM pos_migraciones WHERE version='06_caja_whatsapp_comprobantes') THEN
    RAISE EXCEPTION 'La migración 06 ya fue aplicada';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pos_migraciones WHERE version='05_cuentas_online_cocina') THEN
    RAISE EXCEPTION 'Primero aplica la migración 05';
  END IF;
END $$;
LOCK TABLE ventas, detalle_venta, tipo_comprobante, pagos_venta IN ACCESS EXCLUSIVE MODE;
ALTER TABLE ventas DROP CONSTRAINT ck_pedido_origen;
ALTER TABLE ventas DROP CONSTRAINT ck_pedido_entrega;
ALTER TABLE ventas DROP CONSTRAINT ck_venta_metodo_pago;
ALTER TABLE pagos_venta DROP CONSTRAINT ck_pago_metodo;
ALTER TABLE detalle_venta DROP CONSTRAINT ck_detalle_preparacion;
ALTER TABLE ventas
  ADD COLUMN telefono_entrega VARCHAR(20),
  ADD COLUMN cuenta_solicitada BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN fecha_solicitud_cuenta TIMESTAMPTZ,
  ADD CONSTRAINT ck_pedido_origen CHECK(origen_pedido IN('LOCAL','ONLINE','WHATSAPP','WEB')),
  ADD CONSTRAINT ck_venta_metodo_pago CHECK(metodo_pago IN('efectivo','tarjeta','transferencia','yape_plin','yape','plin')),
  ADD CONSTRAINT ck_pedido_entrega CHECK(
    (origen_pedido='LOCAL' AND tipo_entrega='MESA' AND direccion_envio IS NULL AND (estado_venta<>'ABIERTA' OR mesa_id IS NOT NULL)) OR
    (origen_pedido<>'LOCAL' AND mesa_id IS NULL AND (tipo_entrega='RECOJO' OR
      (tipo_entrega='DELIVERY' AND direccion_envio IS NOT NULL AND length(trim(direccion_envio))>0)))),
  ADD CONSTRAINT ck_pedido_telefono CHECK(origen_pedido NOT IN('WHATSAPP','WEB') OR
    (telefono_entrega IS NOT NULL AND telefono_entrega ~ '^[+0-9 ()-]{6,20}$')),
  ADD CONSTRAINT ck_cuenta_solicitada CHECK(NOT cuenta_solicitada OR (mesa_id IS NOT NULL AND fecha_solicitud_cuenta IS NOT NULL));
ALTER TABLE pagos_venta ADD CONSTRAINT ck_pago_metodo CHECK(metodo_pago IN('efectivo','tarjeta','transferencia','yape_plin','yape','plin'));
UPDATE detalle_venta SET estado_preparacion='PREPARANDO' WHERE estado_preparacion='EN_PREPARACION';
ALTER TABLE detalle_venta ADD CONSTRAINT ck_detalle_preparacion CHECK(estado_preparacion IN('PENDIENTE','PREPARANDO','LISTO','SERVIDO'));
UPDATE ventas v SET telefono_entrega=c.telefono FROM clientes c WHERE c.id_cliente=v.id_cliente AND v.origen_pedido='ONLINE';
ALTER TABLE tipo_comprobante ADD COLUMN ultimo_correlativo BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tipo_comprobante ADD CONSTRAINT ck_comprobante_correlativo CHECK(ultimo_correlativo>=0);
-- Reservar los correlativos históricos convencionales SERIE-NÚMERO sin renumerarlos.
UPDATE tipo_comprobante t SET ultimo_correlativo=COALESCE((
 SELECT MAX(substring(v.numero_comprobante FROM length(t.serie)+2)::BIGINT)
 FROM ventas v WHERE v.id_tipo_comprobante=t.id_tipo_comprobante
 AND left(v.numero_comprobante,length(t.serie)+1)=t.serie||'-'
 AND substring(v.numero_comprobante FROM length(t.serie)+2) ~ '^[0-9]{1,18}$'
),0);
CREATE TABLE comprobantes(
 id_comprobante SERIAL PRIMARY KEY, id_venta INT NOT NULL REFERENCES ventas(id_venta),
 id_tipo_comprobante INT NOT NULL REFERENCES tipo_comprobante(id_tipo_comprobante),
 tipo VARCHAR(50) NOT NULL, serie VARCHAR(10) NOT NULL, correlativo BIGINT NOT NULL,
 empresa_ruc VARCHAR(20) NOT NULL, empresa_nombre VARCHAR(150) NOT NULL, empresa_direccion TEXT NOT NULL,
 cliente_documento VARCHAR(20) NOT NULL, cliente_nombre VARCHAR(150) NOT NULL, cliente_direccion VARCHAR(255),
 subtotal NUMERIC(10,2) NOT NULL, igv NUMERIC(10,2) NOT NULL, total NUMERIC(10,2) NOT NULL,
 fecha_emision TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_comprobante_venta UNIQUE(id_venta),
 CONSTRAINT uk_comprobante_numero UNIQUE(id_tipo_comprobante,correlativo),
 CONSTRAINT ck_comprobante_importe CHECK(correlativo>0 AND subtotal>=0 AND igv>=0 AND total=subtotal+igv)
);
CREATE TABLE detalle_comprobante(
 id_detalle_comprobante SERIAL PRIMARY KEY, id_comprobante INT NOT NULL REFERENCES comprobantes(id_comprobante),
 producto VARCHAR(150) NOT NULL, cantidad INT NOT NULL, precio_unitario NUMERIC(10,2) NOT NULL,
 subtotal NUMERIC(10,2) NOT NULL,
 CONSTRAINT ck_comprobante_detalle CHECK(cantidad>0 AND precio_unitario>=0 AND subtotal=precio_unitario*cantidad)
);
CREATE INDEX idx_detalle_comprobante ON detalle_comprobante(id_comprobante);
CREATE INDEX idx_caja_cuentas ON ventas(cuenta_solicitada,estado_venta,fecha_solicitud_cuenta);
INSERT INTO rol(nombre_rol,descripcion) VALUES('COCINERO','Acceso exclusivo a cocina')
ON CONFLICT(nombre_rol) DO NOTHING;
INSERT INTO pos_migraciones(version) VALUES('06_caja_whatsapp_comprobantes');
COMMIT;

-- 07. FACTURACIÓN EN CAJA Y CANCELACIONES. Requiere 06; backend detenido.
BEGIN;
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM pos_migraciones WHERE version='07_caja_fiscal_cancelaciones') THEN
    RAISE EXCEPTION 'La migración 07 ya fue aplicada';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pos_migraciones WHERE version='06_caja_whatsapp_comprobantes') THEN
    RAISE EXCEPTION 'Primero aplica la migración 06';
  END IF;
  IF EXISTS(SELECT 1 FROM comprobantes WHERE upper(replace(tipo,' ','_')) NOT IN ('NOTA_DE_VENTA','NOTA_VENTA','BOLETA','FACTURA')) THEN
    RAISE EXCEPTION 'Revisar tipos históricos de comprobantes antes de migrar';
  END IF;
END $$;
LOCK TABLE comprobantes, ventas, pagos_venta, detalle_venta IN ACCESS EXCLUSIVE MODE;
ALTER TABLE comprobantes
  ADD COLUMN tipo_comprobante VARCHAR(20),
  ADD COLUMN ruc VARCHAR(11),
  ADD COLUMN razon_social VARCHAR(150),
  ADD COLUMN dni VARCHAR(8);
UPDATE comprobantes SET
  tipo_comprobante=CASE WHEN upper(replace(tipo,' ','_')) IN ('NOTA_DE_VENTA','NOTA_VENTA') THEN 'NOTA_VENTA' ELSE upper(tipo) END,
  ruc=CASE WHEN upper(tipo)='FACTURA' THEN cliente_documento END,
  razon_social=CASE WHEN upper(tipo)='FACTURA' THEN cliente_nombre END,
  dni=CASE WHEN upper(tipo)='BOLETA' AND cliente_documento ~ '^[0-9]{8}$' THEN cliente_documento END;
ALTER TABLE comprobantes
  ALTER COLUMN tipo_comprobante SET NOT NULL,
  ADD CONSTRAINT ck_comprobante_tipo CHECK(tipo_comprobante IN ('NOTA_VENTA','BOLETA','FACTURA')),
  ADD CONSTRAINT ck_comprobante_fiscal CHECK(
    (tipo_comprobante='FACTURA' AND ruc IS NOT NULL AND ruc ~ '^[0-9]{11}$'
      AND razon_social IS NOT NULL AND length(trim(razon_social))>0 AND dni IS NULL)
    OR (tipo_comprobante<>'FACTURA' AND ruc IS NULL AND razon_social IS NULL)),
  ADD CONSTRAINT ck_comprobante_dni CHECK(dni IS NULL OR (tipo_comprobante='BOLETA' AND dni ~ '^[0-9]{8}$'));

ALTER TABLE ventas DROP CONSTRAINT ck_venta_metodo_pago;
ALTER TABLE pagos_venta DROP CONSTRAINT ck_pago_metodo;
UPDATE ventas SET metodo_pago=upper(metodo_pago);
UPDATE pagos_venta SET metodo_pago=upper(metodo_pago);
ALTER TABLE ventas ALTER COLUMN metodo_pago SET DEFAULT 'EFECTIVO';
ALTER TABLE ventas ADD CONSTRAINT ck_venta_metodo_pago CHECK(metodo_pago IN ('EFECTIVO','YAPE','PLIN','TARJETA','TRANSFERENCIA','YAPE_PLIN'));
ALTER TABLE pagos_venta ADD CONSTRAINT ck_pago_metodo CHECK(metodo_pago IN ('EFECTIVO','YAPE','PLIN','TARJETA','TRANSFERENCIA','YAPE_PLIN'));

ALTER TABLE detalle_venta DROP CONSTRAINT ck_detalle_preparacion;
ALTER TABLE detalle_venta
  ADD COLUMN motivo_cancelacion VARCHAR(255),
  ADD COLUMN cancelado_por INT REFERENCES usuario(id_usuario),
  ADD CONSTRAINT ck_detalle_preparacion CHECK(estado_preparacion IN ('PENDIENTE','PREPARANDO','LISTO','SERVIDO','CANCELADO')),
  ADD CONSTRAINT ck_detalle_cancelacion CHECK(estado_preparacion<>'CANCELADO' OR
    (motivo_cancelacion IS NOT NULL AND length(trim(motivo_cancelacion))>0 AND cancelado_por IS NOT NULL));
-- No se modifica stock ni importes: los platos existentes conservan su estado.
INSERT INTO pos_migraciones(version) VALUES('07_caja_fiscal_cancelaciones');
COMMIT;

-- 08. TURNOS DE CAJA Y KDS ENRUTADO. Requiere 07; backend detenido.
BEGIN;
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM pos_migraciones WHERE version='08_turnos_caja_kds_enrutado') THEN
    RAISE EXCEPTION 'La migración 08 ya fue aplicada';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pos_migraciones WHERE version='07_caja_fiscal_cancelaciones') THEN
    RAISE EXCEPTION 'Primero aplica la migración 07';
  END IF;
END $$;
LOCK TABLE producto, detalle_venta, pagos_venta IN ACCESS EXCLUSIVE MODE;
CREATE TABLE sesiones_caja (
  id_sesion SERIAL PRIMARY KEY, id_empresa INT NOT NULL REFERENCES empresa(id_empresa),
  empresa_abierta INT, abierto_por INT NOT NULL REFERENCES usuario(id_usuario),
  cerrado_por INT REFERENCES usuario(id_usuario), fecha_apertura TIMESTAMPTZ NOT NULL,
  fecha_cierre TIMESTAMPTZ, clave_operacion VARCHAR(36) NOT NULL,
  monto_inicial NUMERIC(14,2) NOT NULL,
  total_efectivo NUMERIC(14,2) NOT NULL DEFAULT 0, total_yape NUMERIC(14,2) NOT NULL DEFAULT 0,
  total_plin NUMERIC(14,2) NOT NULL DEFAULT 0, total_tarjeta NUMERIC(14,2) NOT NULL DEFAULT 0,
  total_transferencia NUMERIC(14,2) NOT NULL DEFAULT 0, total_yape_plin NUMERIC(14,2) NOT NULL DEFAULT 0,
  efectivo_declarado NUMERIC(14,2), observaciones VARCHAR(500) NOT NULL DEFAULT '',
  CONSTRAINT uk_caja_empresa_abierta UNIQUE(empresa_abierta),
  CONSTRAINT uk_caja_apertura UNIQUE(id_empresa,clave_operacion),
  CONSTRAINT ck_caja_importes CHECK(monto_inicial>=0 AND total_efectivo>=0 AND total_yape>=0 AND
    total_plin>=0 AND total_tarjeta>=0 AND total_transferencia>=0 AND total_yape_plin>=0 AND
    (efectivo_declarado IS NULL OR efectivo_declarado>=0)),
  CONSTRAINT ck_caja_estado CHECK(
    (fecha_cierre IS NULL AND empresa_abierta IS NOT NULL AND empresa_abierta=id_empresa AND cerrado_por IS NULL AND efectivo_declarado IS NULL)
    OR (fecha_cierre IS NOT NULL AND fecha_cierre>=fecha_apertura AND empresa_abierta IS NULL AND cerrado_por IS NOT NULL AND efectivo_declarado IS NOT NULL))
);
CREATE INDEX idx_caja_empresa_fecha ON sesiones_caja(id_empresa,fecha_apertura);
ALTER TABLE pagos_venta ADD COLUMN id_sesion_caja INT REFERENCES sesiones_caja(id_sesion);
CREATE INDEX idx_pago_sesion_metodo ON pagos_venta(id_sesion_caja,metodo_pago);
-- Los abonos históricos se conservan sin asignarlos a turnos ficticios.
ALTER TABLE producto ADD COLUMN area_destino VARCHAR(10) NOT NULL DEFAULT 'COCINA',
  ADD CONSTRAINT ck_producto_destino CHECK(area_destino IN('COCINA','BAR'));
UPDATE producto p SET area_destino='BAR' FROM categoria c
  WHERE p.id_categoria=c.id_categoria AND lower(trim(c.nombre_categoria))='bebidas';
ALTER TABLE detalle_venta ADD COLUMN area_destino VARCHAR(10) NOT NULL DEFAULT 'COCINA',
  ADD COLUMN observaciones VARCHAR(255) NOT NULL DEFAULT '',
  ADD CONSTRAINT ck_detalle_destino CHECK(area_destino IN('COCINA','BAR'));
-- Las comandas anteriores permanecen en su cola original; solo las nuevas usan el destino del producto.
CREATE INDEX idx_detalle_estacion ON detalle_venta(area_destino,estado_preparacion,fecha_pedido);
CREATE INDEX idx_venta_cliente_cobro ON ventas(id_cliente,estado_venta,fecha_cobro);
INSERT INTO rol(nombre_rol,descripcion) VALUES('BARTENDER','Acceso exclusivo a bar') ON CONFLICT(nombre_rol) DO NOTHING;
INSERT INTO pos_migraciones(version) VALUES('08_turnos_caja_kds_enrutado');
COMMIT;

-- 09. MENU PUBLICO Y RECEPCION WEB. Requiere 08; backend detenido.
BEGIN;
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM pos_migraciones WHERE version='09_menu_publico_pedidos_web') THEN
    RAISE EXCEPTION 'La migración 09 ya fue aplicada';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pos_migraciones WHERE version='08_turnos_caja_kds_enrutado') THEN
    RAISE EXCEPTION 'Primero aplica la migración 08';
  END IF;
END $$;
ALTER TABLE producto ADD COLUMN visible_web BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ventas ADD COLUMN observaciones_pedido VARCHAR(255) NOT NULL DEFAULT '';
CREATE TABLE tiendas_web (
  id_empresa INT PRIMARY KEY REFERENCES empresa(id_empresa),
  slug VARCHAR(60) NOT NULL UNIQUE, activa BOOLEAN NOT NULL DEFAULT FALSE,
  recojo BOOLEAN NOT NULL DEFAULT TRUE, delivery BOOLEAN NOT NULL DEFAULT TRUE,
  mensaje VARCHAR(300) NOT NULL DEFAULT '',
  CONSTRAINT ck_tienda_entrega CHECK(recojo OR delivery)
);
CREATE TABLE solicitudes_web (
  id_solicitud SERIAL PRIMARY KEY, id_empresa INT NOT NULL REFERENCES empresa(id_empresa),
  clave_operacion VARCHAR(36) NOT NULL, codigo_seguimiento VARCHAR(36) NOT NULL UNIQUE,
  huella VARCHAR(64) NOT NULL, nombre VARCHAR(150) NOT NULL, dni VARCHAR(8) NOT NULL,
  telefono VARCHAR(20) NOT NULL, tipo_entrega VARCHAR(10) NOT NULL,
  direccion VARCHAR(255) NOT NULL DEFAULT '', observaciones VARCHAR(255) NOT NULL DEFAULT '',
  estado VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE', fecha_creacion TIMESTAMPTZ NOT NULL,
  subtotal NUMERIC(10,2) NOT NULL, igv NUMERIC(10,2) NOT NULL,
  total NUMERIC(10,2) NOT NULL, motivo VARCHAR(255) NOT NULL DEFAULT '',
  id_venta INT UNIQUE REFERENCES ventas(id_venta),
  CONSTRAINT uk_web_operacion UNIQUE(id_empresa,clave_operacion),
  CONSTRAINT ck_web_importes CHECK(subtotal>=0 AND igv>=0 AND total=subtotal+igv),
  CONSTRAINT ck_web_dni CHECK(dni ~ '^[0-9]{8}$'),
  CONSTRAINT ck_web_entrega CHECK(tipo_entrega='RECOJO' OR (tipo_entrega='DELIVERY' AND LENGTH(TRIM(direccion))>0)),
  CONSTRAINT ck_web_estado CHECK(
    (estado='PENDIENTE' AND id_venta IS NULL) OR (estado='ACEPTADO' AND id_venta IS NOT NULL)
    OR (estado='RECHAZADO' AND id_venta IS NULL AND LENGTH(TRIM(motivo))>0))
);
CREATE TABLE solicitud_web_items (
  id_item SERIAL PRIMARY KEY,
  id_solicitud INT NOT NULL REFERENCES solicitudes_web(id_solicitud) ON DELETE CASCADE,
  id_producto INT NOT NULL REFERENCES producto(id_producto), nombre VARCHAR(150) NOT NULL,
  cantidad INT NOT NULL, precio_base NUMERIC(10,2) NOT NULL,
  observaciones VARCHAR(255) NOT NULL DEFAULT '',
  CONSTRAINT ck_web_item CHECK(cantidad BETWEEN 1 AND 20 AND precio_base>=0)
);
CREATE INDEX idx_web_pendientes ON solicitudes_web(estado,fecha_creacion);
CREATE INDEX idx_web_telefono ON solicitudes_web(id_empresa,telefono,estado,fecha_creacion);
CREATE INDEX idx_web_items_solicitud ON solicitud_web_items(id_solicitud);
-- No se publican productos ni se activan tiendas sin configuración del ADMIN.
INSERT INTO pos_migraciones(version) VALUES('09_menu_publico_pedidos_web');
COMMIT;
