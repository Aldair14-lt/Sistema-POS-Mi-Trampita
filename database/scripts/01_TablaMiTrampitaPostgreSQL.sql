-- -----------------------------------------------------
-- BASE DE DATOS POS PARA POSTGRESQL
-- -----------------------------------------------------

-- Crear tipos ENUM nativos
CREATE TYPE estado_usuario AS ENUM ('activo', 'inactivo', 'bloqueado');
CREATE TYPE tipo_metodo_pago AS ENUM ('efectivo', 'tarjeta', 'transferencia', 'yape_plin');

-- -----------------------------------------------------
-- 1. MÓDULO DE ORGANIZACIÓN (TABLA MADRE)
-- -----------------------------------------------------
CREATE TABLE empresa (
  id_empresa SERIAL PRIMARY KEY,
  ruc VARCHAR(20) NOT NULL UNIQUE,
  razon_social VARCHAR(150) NOT NULL,
  nombre_comercial VARCHAR(150) NULL,
  direccion TEXT NOT NULL,
  telefono VARCHAR(20) NULL,
  correo VARCHAR(100) NULL,
  fecha_registro TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------
-- 2. MÓDULO DE SEGURIDAD, ROLES Y ACCESOS
-- -----------------------------------------------------
CREATE TABLE rol (
  id_rol SERIAL PRIMARY KEY,
  nombre_rol VARCHAR(50) NOT NULL UNIQUE,
  descripcion VARCHAR(255) NULL
);

CREATE TABLE usuario (
  id_usuario SERIAL PRIMARY KEY,
  usuario VARCHAR(50) NOT NULL UNIQUE,
  contraseña VARCHAR(255) NOT NULL,
  nombre_completo VARCHAR(150) NOT NULL,
  correo_electronico VARCHAR(100) NULL,
  pin_caja VARCHAR(255) NULL,
  estado estado_usuario NOT NULL DEFAULT 'activo',
  fecha_creacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE usuario_rol (
  id_usuario_rol SERIAL PRIMARY KEY,
  id_usuario INT NOT NULL,
  id_rol INT NOT NULL,
  CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_usuario_rol_rol FOREIGN KEY (id_rol) REFERENCES rol (id_rol) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE UNIQUE INDEX uk_usuario_rol ON usuario_rol (id_usuario, id_rol);

-- -----------------------------------------------------
-- 3. MÓDULO DE PRODUCTOS E INVENTARIO
-- -----------------------------------------------------
CREATE TABLE categoria (
  id_categoria SERIAL PRIMARY KEY,
  nombre_categoria VARCHAR(100) NOT NULL,
  descripcion VARCHAR(255) NULL
);

CREATE TABLE marca (
  id_marca SERIAL PRIMARY KEY,
  nombre_marca VARCHAR(100) NOT NULL
);

CREATE TABLE proveedor (
  id_proveedor SERIAL PRIMARY KEY,
  ruc_dni VARCHAR(20) NOT NULL,
  razon_social VARCHAR(150) NOT NULL,
  telefono VARCHAR(20) NULL,
  correo VARCHAR(100) NULL
);

CREATE TABLE producto (
  id_producto SERIAL PRIMARY KEY,
  id_categoria INT NOT NULL,
  id_marca INT NOT NULL,
  id_proveedor INT NOT NULL,
  codigo_barras VARCHAR(50) NOT NULL UNIQUE,
  nombre_producto VARCHAR(150) NOT NULL,
  descripcion TEXT NULL,
  precio_compra NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  precio_venta NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  stock_actual INT NOT NULL DEFAULT 0,
  stock_minimo INT NOT NULL DEFAULT 5,
  fecha_registro TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_producto_categoria FOREIGN KEY (id_categoria) REFERENCES categoria (id_categoria) ON UPDATE CASCADE,
  CONSTRAINT fk_producto_marca FOREIGN KEY (id_marca) REFERENCES marca (id_marca) ON UPDATE CASCADE,
  CONSTRAINT fk_producto_proveedor FOREIGN KEY (id_proveedor) REFERENCES proveedor (id_proveedor) ON UPDATE CASCADE
);
ALTER TABLE producto ADD CONSTRAINT ck_producto_precios CHECK (precio_compra >= 0 AND precio_venta >= 0);
ALTER TABLE producto ADD CONSTRAINT ck_producto_stock CHECK (stock_actual >= 0 AND stock_minimo >= 0);
CREATE INDEX idx_producto_categoria ON producto (id_categoria);
CREATE INDEX idx_producto_marca ON producto (id_marca);
CREATE INDEX idx_producto_proveedor ON producto (id_proveedor);

-- -----------------------------------------------------
-- 4. MÓDULO DE CLIENTES, VENTAS Y COMPROBANTES
-- -----------------------------------------------------
CREATE TABLE cliente (
  id_cliente SERIAL PRIMARY KEY,
  numero_documento VARCHAR(20) NOT NULL,
  nombres_razon_social VARCHAR(150) NOT NULL,
  direccion VARCHAR(255) NULL,
  telefono VARCHAR(20) NULL,
  correo VARCHAR(100) NULL
);

CREATE TABLE tipo_comprobante (
  id_tipo_comprobante SERIAL PRIMARY KEY,
  nombre_tipo VARCHAR(50) NOT NULL,
  serie VARCHAR(10) NOT NULL,
  descripcion VARCHAR(255) NULL
);

CREATE TABLE venta (
  id_venta SERIAL PRIMARY KEY,
  id_empresa INT NOT NULL,
  id_usuario INT NOT NULL,
  id_cliente INT NOT NULL,
  id_tipo_comprobante INT NOT NULL,
  numero_comprobante VARCHAR(50) NOT NULL,
  subtotal NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  igv_impuesto NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  total NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  metodo_pago tipo_metodo_pago NOT NULL DEFAULT 'efectivo',
  fecha_venta TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_venta_empresa FOREIGN KEY (id_empresa) REFERENCES empresa (id_empresa) ON UPDATE CASCADE,
  CONSTRAINT fk_venta_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario) ON UPDATE CASCADE,
  CONSTRAINT fk_venta_cliente FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente) ON UPDATE CASCADE,
  CONSTRAINT fk_venta_tipo_comprobante FOREIGN KEY (id_tipo_comprobante) REFERENCES tipo_comprobante (id_tipo_comprobante) ON UPDATE CASCADE
);
ALTER TABLE venta ADD CONSTRAINT ck_venta_importes CHECK (subtotal >= 0 AND igv_impuesto >= 0 AND total >= 0);

CREATE TABLE detalle_venta (
  id_detalle_venta SERIAL PRIMARY KEY,
  id_venta INT NOT NULL,
  id_producto INT NOT NULL,
  cantidad INT NOT NULL DEFAULT 1,
  precio_unitario NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  subtotal NUMERIC(10,2) NOT NULL DEFAULT 0.00,
  CONSTRAINT fk_detalle_venta_venta FOREIGN KEY (id_venta) REFERENCES venta (id_venta) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_detalle_venta_producto FOREIGN KEY (id_producto) REFERENCES producto (id_producto) ON UPDATE CASCADE
);
CREATE UNIQUE INDEX uk_cliente_numero_documento ON cliente (numero_documento);
CREATE UNIQUE INDEX uk_tipo_comprobante_serie ON tipo_comprobante (nombre_tipo, serie);
CREATE UNIQUE INDEX uk_venta_comprobante ON venta (id_tipo_comprobante, numero_comprobante);
CREATE INDEX idx_venta_fecha ON venta (fecha_venta);
ALTER TABLE detalle_venta ADD CONSTRAINT ck_detalle_cantidad CHECK (cantidad > 0);
ALTER TABLE detalle_venta ADD CONSTRAINT ck_detalle_importes CHECK (precio_unitario >= 0 AND subtotal >= 0);
ALTER TABLE detalle_venta ADD CONSTRAINT ck_detalle_subtotal CHECK (subtotal = cantidad * precio_unitario);
CREATE INDEX idx_detalle_venta_producto ON detalle_venta (id_producto);