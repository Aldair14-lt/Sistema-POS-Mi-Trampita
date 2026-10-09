-- ============================================================================
-- POS MI TRAMPITA — SCRIPT ÚNICO PARA MYSQL 8.0.16+ (VERSIONES 01 A 11 + DATOS DE EJEMPLO)
--
-- Base nueva: ejecutar el archivo completo; crea y selecciona pos_db.
-- Base existente: respaldo previo, backend detenido y solo migraciones pendientes
-- (03 a 11), nunca la sección de instalación.
-- Si ya tiene 04: ejecutar las secciones 05, 06, 07, 08, 09 y 10 de este mismo archivo.
-- Si ya tiene 05: ejecutar las secciones 06, 07, 08, 09 y 10 de este mismo archivo.
-- Si ya tiene 06: ejecutar las secciones 07, 08, 09 y 10.
-- Si ya tiene 07: ejecutar las secciones 08, 09 y 10.
-- Si ya tiene 08: ejecutar las secciones 09 y 10.
-- Si ya tiene 09: ejecutar solamente la sección 10.
-- Si ya tiene 10: ejecutar solamente 11. Si ya tiene 11: no repetir migraciones.
-- Los datos de ejemplo están en 12; se pueden cargar por separado sin duplicarlos.
-- Cada sección termina antes del siguiente encabezado numerado.
-- No repetir migraciones: se protege el inventario contra dobles descuentos.
-- MySQL confirma DDL implícitamente; restaurar el respaldo si falla a medias.
-- ============================================================================
CREATE DATABASE IF NOT EXISTS `pos_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `pos_db`;

-- 02. ESQUEMA BASE
CREATE TABLE empresa (
  id_empresa INT AUTO_INCREMENT PRIMARY KEY, ruc VARCHAR(20) NOT NULL UNIQUE,
  razon_social VARCHAR(150) NOT NULL, nombre_comercial VARCHAR(150), direccion TEXT NOT NULL,
  telefono VARCHAR(20), correo VARCHAR(100), fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE rol (
  id_rol INT AUTO_INCREMENT PRIMARY KEY, nombre_rol VARCHAR(50) NOT NULL UNIQUE,
  descripcion VARCHAR(255)
) ENGINE=InnoDB;
CREATE TABLE usuario (
  id_usuario INT AUTO_INCREMENT PRIMARY KEY, usuario VARCHAR(50) NOT NULL UNIQUE,
  contraseña VARCHAR(255) NOT NULL, nombre_completo VARCHAR(150) NOT NULL,
  correo_electronico VARCHAR(100), pin_caja VARCHAR(255),
  estado ENUM('activo','inactivo','bloqueado') NOT NULL DEFAULT 'activo',
  fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE usuario_rol (
  id_usuario_rol INT AUTO_INCREMENT PRIMARY KEY, id_usuario INT NOT NULL, id_rol INT NOT NULL,
  CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY(id_usuario) REFERENCES usuario(id_usuario) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_usuario_rol_rol FOREIGN KEY(id_rol) REFERENCES rol(id_rol) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT uk_usuario_rol UNIQUE(id_usuario,id_rol)
) ENGINE=InnoDB;
CREATE TABLE categoria (id_categoria INT AUTO_INCREMENT PRIMARY KEY,nombre_categoria VARCHAR(100) NOT NULL,descripcion VARCHAR(255)) ENGINE=InnoDB;
CREATE TABLE marca (id_marca INT AUTO_INCREMENT PRIMARY KEY,nombre_marca VARCHAR(100) NOT NULL) ENGINE=InnoDB;
CREATE TABLE proveedor (
  id_proveedor INT AUTO_INCREMENT PRIMARY KEY,ruc_dni VARCHAR(20) NOT NULL,razon_social VARCHAR(150) NOT NULL,
  telefono VARCHAR(20),correo VARCHAR(100)
) ENGINE=InnoDB;
CREATE TABLE producto (
  id_producto INT AUTO_INCREMENT PRIMARY KEY,id_categoria INT NOT NULL,id_marca INT NOT NULL,id_proveedor INT NOT NULL,
  codigo_barras VARCHAR(50) NOT NULL UNIQUE,nombre_producto VARCHAR(150) NOT NULL,descripcion TEXT,
  precio_compra DECIMAL(10,2) NOT NULL DEFAULT 0,precio_venta DECIMAL(10,2) NOT NULL DEFAULT 0,
  stock_actual INT NOT NULL DEFAULT 0,stock_minimo INT NOT NULL DEFAULT 5,fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_producto_categoria FOREIGN KEY(id_categoria) REFERENCES categoria(id_categoria) ON UPDATE CASCADE,
  CONSTRAINT fk_producto_marca FOREIGN KEY(id_marca) REFERENCES marca(id_marca) ON UPDATE CASCADE,
  CONSTRAINT fk_producto_proveedor FOREIGN KEY(id_proveedor) REFERENCES proveedor(id_proveedor) ON UPDATE CASCADE,
  CONSTRAINT ck_producto_precios CHECK(precio_compra>=0 AND precio_venta>=0),
  CONSTRAINT ck_producto_stock CHECK(stock_actual>=0 AND stock_minimo>=0),
  INDEX idx_producto_categoria(id_categoria),INDEX idx_producto_marca(id_marca),INDEX idx_producto_proveedor(id_proveedor)
) ENGINE=InnoDB;
CREATE TABLE cliente (
  id_cliente INT AUTO_INCREMENT PRIMARY KEY,numero_documento VARCHAR(20) NOT NULL,
  nombres_razon_social VARCHAR(150) NOT NULL,direccion VARCHAR(255),telefono VARCHAR(20),correo VARCHAR(100),
  UNIQUE KEY uk_cliente_numero_documento(numero_documento)
) ENGINE=InnoDB;
CREATE TABLE tipo_comprobante (
  id_tipo_comprobante INT AUTO_INCREMENT PRIMARY KEY,nombre_tipo VARCHAR(50) NOT NULL,serie VARCHAR(10) NOT NULL,
  descripcion VARCHAR(255),UNIQUE KEY uk_tipo_comprobante_serie(nombre_tipo,serie)
) ENGINE=InnoDB;
CREATE TABLE mesa (
  id_mesa INT AUTO_INCREMENT PRIMARY KEY,numero_mesa INT NOT NULL UNIQUE,capacidad INT NOT NULL DEFAULT 4,
  estado_mesa VARCHAR(20) NOT NULL DEFAULT 'LIBRE',
  CONSTRAINT ck_mesa_numero CHECK(numero_mesa>0),CONSTRAINT ck_mesa_capacidad CHECK(capacidad>0),
  CONSTRAINT ck_mesa_estado CHECK(estado_mesa IN ('LIBRE','OCUPADA','RESERVADA'))
) ENGINE=InnoDB;
CREATE TABLE venta (
  id_venta INT AUTO_INCREMENT PRIMARY KEY,id_empresa INT NOT NULL,id_usuario INT NOT NULL,id_cliente INT NOT NULL,
  id_tipo_comprobante INT NOT NULL,id_mesa INT,numero_comprobante VARCHAR(50) NOT NULL,
  subtotal DECIMAL(10,2) NOT NULL DEFAULT 0,igv_impuesto DECIMAL(10,2) NOT NULL DEFAULT 0,total DECIMAL(10,2) NOT NULL DEFAULT 0,
  metodo_pago ENUM('efectivo','tarjeta','transferencia','yape_plin') NOT NULL DEFAULT 'efectivo',
  estado_venta VARCHAR(20) NOT NULL DEFAULT 'ABIERTA',fecha_venta TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_venta_empresa FOREIGN KEY(id_empresa) REFERENCES empresa(id_empresa) ON UPDATE CASCADE,
  CONSTRAINT fk_venta_usuario FOREIGN KEY(id_usuario) REFERENCES usuario(id_usuario) ON UPDATE CASCADE,
  CONSTRAINT fk_venta_cliente FOREIGN KEY(id_cliente) REFERENCES cliente(id_cliente) ON UPDATE CASCADE,
  CONSTRAINT fk_venta_tipo_comprobante FOREIGN KEY(id_tipo_comprobante) REFERENCES tipo_comprobante(id_tipo_comprobante) ON UPDATE CASCADE,
  CONSTRAINT fk_venta_mesa FOREIGN KEY(id_mesa) REFERENCES mesa(id_mesa) ON UPDATE CASCADE,
  CONSTRAINT ck_venta_importes CHECK(subtotal>=0 AND igv_impuesto>=0 AND total>=0),
  CONSTRAINT ck_venta_estado CHECK(estado_venta IN ('ABIERTA','CERRADA','ANULADA')),
  UNIQUE KEY uk_venta_comprobante(id_tipo_comprobante,numero_comprobante),
  INDEX idx_venta_fecha(fecha_venta),INDEX idx_venta_mesa_estado(id_mesa,estado_venta)
) ENGINE=InnoDB;
CREATE TABLE detalle_venta (
  id_detalle_venta INT AUTO_INCREMENT PRIMARY KEY,id_venta INT NOT NULL,id_producto INT NOT NULL,
  cantidad INT NOT NULL DEFAULT 1,precio_unitario DECIMAL(10,2) NOT NULL DEFAULT 0,subtotal DECIMAL(10,2) NOT NULL DEFAULT 0,
  CONSTRAINT fk_detalle_venta_venta FOREIGN KEY(id_venta) REFERENCES venta(id_venta) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_detalle_venta_producto FOREIGN KEY(id_producto) REFERENCES producto(id_producto) ON UPDATE CASCADE,
  CONSTRAINT ck_detalle_cantidad CHECK(cantidad>0),
  CONSTRAINT ck_detalle_importes CHECK(precio_unitario>=0 AND subtotal>=0),
  CONSTRAINT ck_detalle_subtotal CHECK(subtotal=cantidad*precio_unitario),
  INDEX idx_detalle_venta_producto(id_producto)
) ENGINE=InnoDB;

-- 03. MIGRACIÓN DE BASE EXISTENTE (idempotente para instalaciones nuevas).
CREATE TABLE IF NOT EXISTS mesa (
  id_mesa INT AUTO_INCREMENT PRIMARY KEY,numero_mesa INT NOT NULL UNIQUE,
  capacidad INT NOT NULL DEFAULT 4,estado_mesa VARCHAR(20) NOT NULL DEFAULT 'LIBRE'
) ENGINE=InnoDB;
SET @schema_name = DATABASE();
SET @has_estado = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='venta' AND column_name='estado_venta');
SET @ddl = IF(@has_estado=0,'ALTER TABLE venta ADD COLUMN estado_venta VARCHAR(20) NOT NULL DEFAULT ''CERRADA''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_mesa_id = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='venta' AND column_name='id_mesa');
SET @ddl = IF(@has_mesa_id=0,'ALTER TABLE venta ADD COLUMN id_mesa INT NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_mesa_fk = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=@schema_name AND table_name='venta' AND constraint_name='fk_venta_mesa');
SET @ddl = IF(@has_mesa_fk=0,'ALTER TABLE venta ADD CONSTRAINT fk_venta_mesa FOREIGN KEY(id_mesa) REFERENCES mesa(id_mesa) ON UPDATE CASCADE','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_mesa_index = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@schema_name AND table_name='venta' AND index_name='idx_venta_mesa_estado');
SET @ddl = IF(@has_mesa_index=0,'CREATE INDEX idx_venta_mesa_estado ON venta(id_mesa,estado_venta)','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE venta SET estado_venta='CERRADA' WHERE estado_venta IS NULL;
ALTER TABLE venta ALTER COLUMN estado_venta SET DEFAULT 'ABIERTA';
SET @has_estado_check = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=@schema_name AND table_name='venta' AND constraint_name='ck_venta_estado');
SET @ddl = IF(@has_estado_check=0,'ALTER TABLE venta ADD CONSTRAINT ck_venta_estado CHECK(estado_venta IN (''ABIERTA'',''CERRADA'',''ANULADA''))','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 04. MIGRACIÓN DE BASE EXISTENTE / FINALIZACIÓN DEL ESQUEMA.
DELIMITER $$
CREATE PROCEDURE migrar_pos_04()
BEGIN
  DECLARE old_check INT DEFAULT 0;
  DECLARE new_number INT DEFAULT 0;
  DECLARE new_table_id INT DEFAULT 0;
  DECLARE legacy_sale INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  CREATE TABLE IF NOT EXISTS pos_migraciones(version VARCHAR(80) PRIMARY KEY,aplicada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB;
  IF EXISTS(SELECT 1 FROM pos_migraciones WHERE version='04_zonificacion_marketing_roles') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='La migración 04 ya fue aplicada';
  END IF;
  RENAME TABLE mesa TO mesas,venta TO ventas,cliente TO clientes;
  SELECT COUNT(*) INTO old_check FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='mesas' AND constraint_name='ck_mesa_estado';
  IF old_check>0 THEN ALTER TABLE mesas DROP CHECK ck_mesa_estado; END IF;
  SELECT COUNT(*) INTO old_check FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='mesas' AND constraint_name='ck_mesa_numero';
  IF old_check>0 THEN ALTER TABLE mesas DROP CHECK ck_mesa_numero; END IF;
  ALTER TABLE mesas RENAME COLUMN id_mesa TO id;
  ALTER TABLE mesas RENAME COLUMN numero_mesa TO numero;
  ALTER TABLE mesas RENAME COLUMN estado_mesa TO estado;
  ALTER TABLE ventas RENAME COLUMN id_mesa TO mesa_id;
  ALTER TABLE ventas DROP FOREIGN KEY fk_venta_mesa;
  ALTER TABLE ventas ADD CONSTRAINT fk_venta_mesa FOREIGN KEY(mesa_id) REFERENCES mesas(id);
  CREATE TABLE areas(
    id INT AUTO_INCREMENT PRIMARY KEY,nombre VARCHAR(100) NOT NULL UNIQUE,estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    CONSTRAINT ck_area_nombre CHECK(CHAR_LENGTH(TRIM(nombre))>0),CONSTRAINT ck_area_estado CHECK(estado IN('ACTIVA','INACTIVA'))
  ) ENGINE=InnoDB;
  INSERT INTO areas(nombre) VALUES('Salón Principal'),('Terraza'),('Zona Recreacional'),('Piscina');
  ALTER TABLE mesas ADD COLUMN area_id INT NULL;
  UPDATE mesas SET area_id=(SELECT id FROM areas WHERE nombre='Salón Principal');
  ALTER TABLE mesas MODIFY area_id INT NOT NULL,ADD CONSTRAINT fk_mesa_area FOREIGN KEY(area_id) REFERENCES areas(id);
  CREATE INDEX idx_mesas_area ON mesas(area_id);
  UPDATE mesas SET estado='OCUPADA' WHERE estado='RESERVADA';
  ALTER TABLE mesas ADD CONSTRAINT ck_mesa_numero CHECK(numero>0);
  ALTER TABLE mesas ADD CONSTRAINT ck_mesa_estado CHECK(estado IN('LIBRE','OCUPADA','ATENDIENDO'));
  SET new_number=1;
  WHILE new_number<=12 DO
    IF NOT EXISTS(SELECT 1 FROM mesas WHERE numero=new_number) THEN
      INSERT INTO mesas(numero,capacidad,estado,area_id) SELECT new_number,4,'LIBRE',id FROM areas WHERE nombre=CASE WHEN new_number<=3 THEN 'Salón Principal' WHEN new_number<=6 THEN 'Terraza' WHEN new_number<=9 THEN 'Zona Recreacional' ELSE 'Piscina' END;
    END IF;
    SET new_number=new_number+1;
  END WHILE;
  WHILE EXISTS(SELECT 1 FROM ventas WHERE estado_venta='ABIERTA' AND mesa_id IS NULL) DO
    SELECT MIN(id_venta) INTO legacy_sale FROM ventas WHERE estado_venta='ABIERTA' AND mesa_id IS NULL;
    SELECT COALESCE(MAX(numero),0)+1 INTO new_number FROM mesas;
    INSERT INTO mesas(numero,capacidad,estado,area_id) SELECT new_number,4,'OCUPADA',id FROM areas WHERE nombre='Salón Principal';
    SET new_table_id=LAST_INSERT_ID();
    UPDATE ventas SET mesa_id=new_table_id WHERE id_venta=legacy_sale;
  END WHILE;
  UPDATE mesas m SET estado='ATENDIENDO' WHERE EXISTS(SELECT 1 FROM ventas v WHERE v.mesa_id=m.id AND v.estado_venta='ABIERTA');
  ALTER TABLE ventas ADD CONSTRAINT ck_venta_mesa_abierta CHECK(estado_venta<>'ABIERTA' OR mesa_id IS NOT NULL);
  ALTER TABLE ventas ADD COLUMN mesa_abierta_id INT GENERATED ALWAYS AS(CASE WHEN estado_venta='ABIERTA' THEN mesa_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_venta_mesa_abierta(mesa_abierta_id);
  ALTER TABLE clientes ADD COLUMN fecha_nacimiento DATE NULL,ADD COLUMN frecuencia_visitas BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN total_gastado DECIMAL(19,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_cliente_metricas CHECK(frecuencia_visitas>=0 AND total_gastado>=0);
  CREATE INDEX idx_cliente_frecuencia ON clientes(frecuencia_visitas,total_gastado);
  CREATE INDEX idx_cliente_nacimiento ON clientes(fecha_nacimiento);
  ALTER TABLE ventas ADD COLUMN fecha_cobro TIMESTAMP NULL;
  ALTER TABLE usuario MODIFY estado VARCHAR(20) NOT NULL DEFAULT 'activo',ADD CONSTRAINT ck_usuario_estado CHECK(estado IN('activo','inactivo','bloqueado'));
  ALTER TABLE ventas MODIFY metodo_pago VARCHAR(20) NOT NULL DEFAULT 'efectivo',ADD CONSTRAINT ck_venta_metodo_pago CHECK(metodo_pago IN('efectivo','tarjeta','transferencia','yape_plin'));
  ALTER TABLE producto ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
  START TRANSACTION;
  UPDATE ventas SET fecha_cobro=fecha_venta WHERE estado_venta='CERRADA';
  UPDATE producto p JOIN(SELECT d.id_producto,SUM(d.cantidad) cantidad FROM detalle_venta d JOIN ventas v ON v.id_venta=d.id_venta WHERE v.estado_venta='ABIERTA' GROUP BY d.id_producto)x ON x.id_producto=p.id_producto SET p.stock_actual=p.stock_actual+x.cantidad;
  UPDATE clientes c JOIN(SELECT id_cliente,COUNT(*) visitas,SUM(total) gastado FROM ventas WHERE estado_venta='CERRADA' GROUP BY id_cliente)x ON x.id_cliente=c.id_cliente SET c.frecuencia_visitas=x.visitas,c.total_gastado=x.gastado;
  INSERT INTO rol(nombre_rol,descripcion) VALUES('ADMIN','Administración completa'),('MOZO','Mesas, pedidos y comandas'),('CAJA','Cobros, comprobantes y liberación de mesas') ON DUPLICATE KEY UPDATE nombre_rol=VALUES(nombre_rol);
  INSERT INTO usuario_rol(id_usuario,id_rol)
  SELECT DISTINCT ur.id_usuario,c.id_rol FROM usuario_rol ur JOIN rol old ON old.id_rol=ur.id_rol JOIN rol c
  ON c.nombre_rol=CASE UPPER(TRIM(old.nombre_rol)) WHEN 'ADMINISTRADOR' THEN 'ADMIN' WHEN 'VENTAS' THEN 'MOZO' WHEN 'VENDEDOR' THEN 'MOZO' WHEN 'MOZO/VENTAS' THEN 'MOZO' WHEN 'CAJERO' THEN 'CAJA' ELSE UPPER(TRIM(old.nombre_rol)) END
  WHERE c.nombre_rol IN('ADMIN','MOZO','CAJA') ON DUPLICATE KEY UPDATE id_usuario=VALUES(id_usuario);
  -- Cuentas iniciales de desarrollo: cambiar estas contraseñas antes de producción.
  INSERT INTO usuario(usuario,`contraseña`,nombre_completo,estado) VALUES
    ('admin','$2a$10$BnkUIi3Cmcwy4NhxxogScuGuu245TdsdbbDp3Ok2TK9Gp9ygwa3ja','Administrador','activo'),
    ('mozo','$2a$10$A6XNEdkEgy0vxF3S42mFA.Z48U.fRssEv32RfmOj2KYntHXyaTbbm','Usuario Mozo','activo'),
    ('caja','$2a$10$aH2Nd4u6yvlb9TYWgpaiYeqPDz0gPf0I/DSG6pXiKgyESCohW2AjO','Usuario Caja','activo')
  ON DUPLICATE KEY UPDATE usuario=VALUES(usuario);
  INSERT INTO usuario_rol(id_usuario,id_rol)
  SELECT u.id_usuario,r.id_rol FROM (
    SELECT 'admin' AS nombre_usuario,'ADMIN' AS nombre_rol
    UNION ALL SELECT 'mozo','MOZO'
    UNION ALL SELECT 'caja','CAJA'
  ) semilla JOIN usuario u ON u.usuario=semilla.nombre_usuario
    JOIN rol r ON r.nombre_rol=semilla.nombre_rol
  ON DUPLICATE KEY UPDATE id_usuario=VALUES(id_usuario);
  INSERT INTO pos_migraciones(version) VALUES('04_zonificacion_marketing_roles');
  COMMIT;
END$$
DELIMITER ;
CALL migrar_pos_04();
DROP PROCEDURE migrar_pos_04;

-- 05. MIGRACIÓN DE BASE EXISTENTE / CUENTAS, ONLINE Y COCINA.
-- MySQL 8.0.16+: ejecutar después de 04, backend detenido y respaldo previo.
-- El DDL de MySQL confirma implícitamente. Ante un fallo después del preflight,
-- restaurar el respaldo; no reejecutar una migración parcialmente aplicada.
DROP PROCEDURE IF EXISTS migrar_pos_05;
DELIMITER $$
CREATE PROCEDURE migrar_pos_05()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF EXISTS(SELECT 1 FROM pos_migraciones WHERE version='05_cuentas_online_cocina') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='La migración 05 ya fue aplicada';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pos_migraciones WHERE version='04_zonificacion_marketing_roles') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Primero aplica la migración 04';
  END IF;
  IF EXISTS(
    SELECT 1 FROM producto p JOIN(
      SELECT d.id_producto,SUM(d.cantidad) unidades FROM detalle_venta d
      JOIN ventas v ON v.id_venta=d.id_venta WHERE v.estado_venta='ABIERTA' GROUP BY d.id_producto
    ) x ON x.id_producto=p.id_producto WHERE p.stock_actual<x.unidades
  ) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Stock insuficiente para comandas abiertas; regulariza antes de migrar'; END IF;

  ALTER TABLE ventas
    ADD COLUMN estado_cuenta VARCHAR(25) NOT NULL DEFAULT 'ABIERTA',
    ADD COLUMN origen_pedido VARCHAR(10) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN tipo_entrega VARCHAR(10) NOT NULL DEFAULT 'MESA',
    ADD COLUMN direccion_envio VARCHAR(255),
    ADD CONSTRAINT ck_cuenta_estado CHECK(estado_cuenta IN('ABIERTA','PAGADA_PARCIALMENTE','CERRADA')),
    ADD CONSTRAINT ck_pedido_origen CHECK(origen_pedido IN('LOCAL','ONLINE'));
  ALTER TABLE ventas DROP CHECK ck_venta_mesa_abierta;
  ALTER TABLE ventas ADD CONSTRAINT ck_pedido_entrega CHECK(
    (origen_pedido='LOCAL' AND tipo_entrega='MESA' AND direccion_envio IS NULL
      AND (estado_venta<>'ABIERTA' OR mesa_id IS NOT NULL)) OR
    (origen_pedido='ONLINE' AND mesa_id IS NULL AND
      (tipo_entrega='RECOJO' OR (tipo_entrega='DELIVERY' AND direccion_envio IS NOT NULL AND CHAR_LENGTH(TRIM(direccion_envio))>0)))
  );
  ALTER TABLE detalle_venta
    ADD COLUMN estado_preparacion VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    ADD COLUMN fecha_pedido TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN fecha_estado TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT ck_detalle_preparacion CHECK(estado_preparacion IN('PENDIENTE','EN_PREPARACION','LISTO','SERVIDO'));
  CREATE TABLE pagos_venta(
    id_pago INT AUTO_INCREMENT PRIMARY KEY,id_venta INT NOT NULL,id_usuario INT NOT NULL,
    monto DECIMAL(10,2) NOT NULL,metodo_pago VARCHAR(20) NOT NULL,
    monto_recibido DECIMAL(10,2) NOT NULL,referencia VARCHAR(100) NOT NULL DEFAULT '',
    clave_operacion VARCHAR(36) NOT NULL,fecha_pago TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pago_venta FOREIGN KEY(id_venta) REFERENCES ventas(id_venta),
    CONSTRAINT fk_pago_usuario FOREIGN KEY(id_usuario) REFERENCES usuario(id_usuario),
    CONSTRAINT uk_pago_operacion UNIQUE(id_venta,clave_operacion),
    CONSTRAINT ck_pago_monto CHECK(monto>0 AND monto_recibido>=monto),
    CONSTRAINT ck_pago_metodo CHECK(metodo_pago IN('efectivo','tarjeta','transferencia','yape_plin'))
  ) ENGINE=InnoDB;
  CREATE INDEX idx_cocina_estado_fecha ON detalle_venta(estado_preparacion,fecha_pedido,id_detalle_venta);
  CREATE INDEX idx_online_estado_fecha ON ventas(origen_pedido,estado_venta,fecha_venta);

  START TRANSACTION;
  UPDATE ventas SET estado_cuenta='CERRADA' WHERE estado_venta IN('CERRADA','ANULADA');
  INSERT INTO pagos_venta(id_venta,id_usuario,monto,metodo_pago,monto_recibido,referencia,clave_operacion,fecha_pago)
  SELECT id_venta,id_usuario,total,metodo_pago,total,'Cobro histórico anterior a migración 05',
         CONCAT('historico-',id_venta),COALESCE(fecha_cobro,fecha_venta,CURRENT_TIMESTAMP)
  FROM ventas WHERE estado_venta='CERRADA' AND total>0;
  UPDATE detalle_venta d JOIN ventas v ON v.id_venta=d.id_venta SET
    d.estado_preparacion=CASE WHEN v.estado_venta='ABIERTA' THEN 'PENDIENTE' ELSE 'SERVIDO' END,
    d.fecha_pedido=COALESCE(v.fecha_venta,CURRENT_TIMESTAMP),
    d.fecha_estado=COALESCE(v.fecha_cobro,v.fecha_venta,CURRENT_TIMESTAMP);
  UPDATE producto p JOIN(
    SELECT d.id_producto,SUM(d.cantidad) unidades FROM detalle_venta d
    JOIN ventas v ON v.id_venta=d.id_venta WHERE v.estado_venta='ABIERTA' GROUP BY d.id_producto
  ) x ON x.id_producto=p.id_producto SET p.stock_actual=p.stock_actual-x.unidades,p.version=p.version+1;
  INSERT INTO rol(nombre_rol,descripcion) VALUES
    ('ADMIN','Administración completa'),('MOZO','Pedidos y entrega de platos'),
    ('CAJA','Abonos, recepción online y cierre'),('COCINERO','Acceso exclusivo a preparación de cocina')
  ON DUPLICATE KEY UPDATE nombre_rol=VALUES(nombre_rol);
  INSERT INTO pos_migraciones(version) VALUES('05_cuentas_online_cocina');
  COMMIT;
END$$
DELIMITER ;
CALL migrar_pos_05();
DROP PROCEDURE migrar_pos_05;
-- Crear/asignar el usuario cocinero desde Usuarios; no se incluye una contraseña compartida.

-- 06. MIGRACIÓN DE BASE EXISTENTE / CAJA, WHATSAPP Y COMPROBANTES.
-- Aplicar solo esta sección si 05 ya está instalada, backend detenido y respaldo.
-- MySQL confirma DDL implícitamente: restaurar respaldo ante fallo parcial.
-- Esta migración no modifica stock ni pagos históricos.
DROP PROCEDURE IF EXISTS migrar_pos_06;
DELIMITER $$
CREATE PROCEDURE migrar_pos_06()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF EXISTS(SELECT 1 FROM pos_migraciones WHERE version='06_caja_whatsapp_comprobantes') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='La migración 06 ya fue aplicada';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pos_migraciones WHERE version='05_cuentas_online_cocina') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Primero aplica la migración 05';
  END IF;
ALTER TABLE ventas DROP CHECK ck_pedido_origen;
ALTER TABLE ventas DROP CHECK ck_pedido_entrega;
ALTER TABLE ventas DROP CHECK ck_venta_metodo_pago;
ALTER TABLE pagos_venta DROP CHECK ck_pago_metodo;
ALTER TABLE detalle_venta DROP CHECK ck_detalle_preparacion;
ALTER TABLE ventas
  ADD COLUMN telefono_entrega VARCHAR(20),
  ADD COLUMN cuenta_solicitada BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN fecha_solicitud_cuenta TIMESTAMP NULL,
  ADD CONSTRAINT ck_pedido_origen CHECK(origen_pedido IN('LOCAL','ONLINE','WHATSAPP','WEB')),
  ADD CONSTRAINT ck_venta_metodo_pago CHECK(metodo_pago IN('efectivo','tarjeta','transferencia','yape_plin','yape','plin')),
  ADD CONSTRAINT ck_pedido_entrega CHECK(
    (origen_pedido='LOCAL' AND tipo_entrega='MESA' AND direccion_envio IS NULL AND (estado_venta<>'ABIERTA' OR mesa_id IS NOT NULL)) OR
    (origen_pedido<>'LOCAL' AND mesa_id IS NULL AND (tipo_entrega='RECOJO' OR
      (tipo_entrega='DELIVERY' AND direccion_envio IS NOT NULL AND length(trim(direccion_envio))>0)))),
  ADD CONSTRAINT ck_pedido_telefono CHECK(origen_pedido NOT IN('WHATSAPP','WEB') OR
    (telefono_entrega IS NOT NULL AND REGEXP_LIKE(telefono_entrega,'^[+0-9 ()-]{6,20}$'))),
  ADD CONSTRAINT ck_cuenta_solicitada CHECK(NOT cuenta_solicitada OR (mesa_id IS NOT NULL AND fecha_solicitud_cuenta IS NOT NULL));
ALTER TABLE pagos_venta ADD CONSTRAINT ck_pago_metodo CHECK(metodo_pago IN('efectivo','tarjeta','transferencia','yape_plin','yape','plin'));
UPDATE detalle_venta SET estado_preparacion='PREPARANDO' WHERE estado_preparacion='EN_PREPARACION';
ALTER TABLE detalle_venta ADD CONSTRAINT ck_detalle_preparacion CHECK(estado_preparacion IN('PENDIENTE','PREPARANDO','LISTO','SERVIDO'));
UPDATE ventas v JOIN clientes c ON c.id_cliente=v.id_cliente SET v.telefono_entrega=c.telefono WHERE v.origen_pedido='ONLINE';
ALTER TABLE tipo_comprobante ADD COLUMN ultimo_correlativo BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tipo_comprobante ADD CONSTRAINT ck_comprobante_correlativo CHECK(ultimo_correlativo>=0);
-- Reservar los correlativos históricos convencionales SERIE-NÚMERO sin renumerarlos.
UPDATE tipo_comprobante t SET ultimo_correlativo=COALESCE((
 SELECT MAX(CAST(SUBSTRING(v.numero_comprobante,CHAR_LENGTH(t.serie)+2) AS UNSIGNED))
 FROM ventas v WHERE v.id_tipo_comprobante=t.id_tipo_comprobante
 AND LEFT(v.numero_comprobante,CHAR_LENGTH(t.serie)+1)=CONCAT(t.serie,'-')
 AND REGEXP_LIKE(SUBSTRING(v.numero_comprobante,CHAR_LENGTH(t.serie)+2),'^[0-9]{1,18}$')
),0);
CREATE TABLE comprobantes(
 id_comprobante INT AUTO_INCREMENT PRIMARY KEY, id_venta INT NOT NULL,
 id_tipo_comprobante INT NOT NULL,
 tipo VARCHAR(50) NOT NULL, serie VARCHAR(10) NOT NULL, correlativo BIGINT NOT NULL,
 empresa_ruc VARCHAR(20) NOT NULL, empresa_nombre VARCHAR(150) NOT NULL, empresa_direccion TEXT NOT NULL,
 cliente_documento VARCHAR(20) NOT NULL, cliente_nombre VARCHAR(150) NOT NULL, cliente_direccion VARCHAR(255),
 subtotal DECIMAL(10,2) NOT NULL, igv DECIMAL(10,2) NOT NULL, total DECIMAL(10,2) NOT NULL,
 fecha_emision TIMESTAMP NOT NULL,
 CONSTRAINT fk_comprobante_venta FOREIGN KEY(id_venta) REFERENCES ventas(id_venta),
 CONSTRAINT fk_comprobante_tipo FOREIGN KEY(id_tipo_comprobante) REFERENCES tipo_comprobante(id_tipo_comprobante),
 CONSTRAINT uk_comprobante_venta UNIQUE(id_venta),
 CONSTRAINT uk_comprobante_numero UNIQUE(id_tipo_comprobante,correlativo),
 CONSTRAINT ck_comprobante_importe CHECK(correlativo>0 AND subtotal>=0 AND igv>=0 AND total=subtotal+igv)
);
CREATE TABLE detalle_comprobante(
 id_detalle_comprobante INT AUTO_INCREMENT PRIMARY KEY, id_comprobante INT NOT NULL,
 producto VARCHAR(150) NOT NULL, cantidad INT NOT NULL, precio_unitario DECIMAL(10,2) NOT NULL,
 subtotal DECIMAL(10,2) NOT NULL,
 CONSTRAINT fk_detalle_comprobante FOREIGN KEY(id_comprobante) REFERENCES comprobantes(id_comprobante),
 CONSTRAINT ck_comprobante_detalle CHECK(cantidad>0 AND precio_unitario>=0 AND subtotal=precio_unitario*cantidad)
);
CREATE INDEX idx_detalle_comprobante ON detalle_comprobante(id_comprobante);
CREATE INDEX idx_caja_cuentas ON ventas(cuenta_solicitada,estado_venta,fecha_solicitud_cuenta);
  START TRANSACTION;
  INSERT INTO rol(nombre_rol,descripcion) VALUES('COCINERO','Acceso exclusivo a cocina')
    ON DUPLICATE KEY UPDATE nombre_rol=VALUES(nombre_rol);
  INSERT INTO pos_migraciones(version) VALUES('06_caja_whatsapp_comprobantes');
  COMMIT;
END$$
DELIMITER ;
CALL migrar_pos_06();
DROP PROCEDURE migrar_pos_06;

-- 07. FACTURACIÓN EN CAJA Y CANCELACIONES. Requiere 06; backend detenido.
-- MySQL confirma DDL implícitamente. Respaldar antes; restaurar si una fase falla.
DROP PROCEDURE IF EXISTS migrar_pos_07;
DELIMITER $$
CREATE PROCEDURE migrar_pos_07()
BEGIN
  IF EXISTS(SELECT 1 FROM pos_migraciones WHERE version='07_caja_fiscal_cancelaciones') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='La migración 07 ya fue aplicada';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pos_migraciones WHERE version='06_caja_whatsapp_comprobantes') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Primero aplica la migración 06';
  END IF;
  IF EXISTS(SELECT 1 FROM comprobantes WHERE UPPER(REPLACE(tipo,' ','_')) NOT IN ('NOTA_DE_VENTA','NOTA_VENTA','BOLETA','FACTURA')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Revisar tipos históricos de comprobantes antes de migrar';
  END IF;
  IF EXISTS(SELECT 1 FROM comprobantes WHERE UPPER(tipo)='FACTURA' AND
      (NOT REGEXP_LIKE(cliente_documento,'^[0-9]{11}$') OR LENGTH(TRIM(cliente_nombre))=0)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Revisar datos fiscales de facturas históricas';
  END IF;
  ALTER TABLE comprobantes
    ADD COLUMN tipo_comprobante VARCHAR(20),
    ADD COLUMN ruc VARCHAR(11),
    ADD COLUMN razon_social VARCHAR(150),
    ADD COLUMN dni VARCHAR(8);
  UPDATE comprobantes SET
    tipo_comprobante=CASE WHEN UPPER(REPLACE(tipo,' ','_')) IN ('NOTA_DE_VENTA','NOTA_VENTA') THEN 'NOTA_VENTA' ELSE UPPER(tipo) END,
    ruc=CASE WHEN UPPER(tipo)='FACTURA' THEN cliente_documento END,
    razon_social=CASE WHEN UPPER(tipo)='FACTURA' THEN cliente_nombre END,
    dni=CASE WHEN UPPER(tipo)='BOLETA' AND REGEXP_LIKE(cliente_documento,'^[0-9]{8}$') THEN cliente_documento END;
  ALTER TABLE comprobantes
    MODIFY tipo_comprobante VARCHAR(20) NOT NULL,
    ADD CONSTRAINT ck_comprobante_tipo CHECK(tipo_comprobante IN ('NOTA_VENTA','BOLETA','FACTURA')),
    ADD CONSTRAINT ck_comprobante_fiscal CHECK(
      (tipo_comprobante='FACTURA' AND ruc IS NOT NULL AND REGEXP_LIKE(ruc,'^[0-9]{11}$')
        AND razon_social IS NOT NULL AND LENGTH(TRIM(razon_social))>0 AND dni IS NULL)
      OR (tipo_comprobante<>'FACTURA' AND ruc IS NULL AND razon_social IS NULL)),
    ADD CONSTRAINT ck_comprobante_dni CHECK(dni IS NULL OR (tipo_comprobante='BOLETA' AND REGEXP_LIKE(dni,'^[0-9]{8}$')));
  ALTER TABLE ventas DROP CHECK ck_venta_metodo_pago;
  ALTER TABLE pagos_venta DROP CHECK ck_pago_metodo;
  UPDATE ventas SET metodo_pago=UPPER(metodo_pago);
  UPDATE pagos_venta SET metodo_pago=UPPER(metodo_pago);
  ALTER TABLE ventas ALTER COLUMN metodo_pago SET DEFAULT 'EFECTIVO';
  ALTER TABLE ventas ADD CONSTRAINT ck_venta_metodo_pago CHECK(metodo_pago IN ('EFECTIVO','YAPE','PLIN','TARJETA','TRANSFERENCIA','YAPE_PLIN'));
  ALTER TABLE pagos_venta ADD CONSTRAINT ck_pago_metodo CHECK(metodo_pago IN ('EFECTIVO','YAPE','PLIN','TARJETA','TRANSFERENCIA','YAPE_PLIN'));
  ALTER TABLE detalle_venta DROP CHECK ck_detalle_preparacion;
  ALTER TABLE detalle_venta
    ADD COLUMN motivo_cancelacion VARCHAR(255),
    ADD COLUMN cancelado_por INT,
    ADD CONSTRAINT fk_detalle_cancelado_por FOREIGN KEY(cancelado_por) REFERENCES usuario(id_usuario),
    ADD CONSTRAINT ck_detalle_preparacion CHECK(estado_preparacion IN ('PENDIENTE','PREPARANDO','LISTO','SERVIDO','CANCELADO')),
    ADD CONSTRAINT ck_detalle_cancelacion CHECK(estado_preparacion<>'CANCELADO' OR
      (motivo_cancelacion IS NOT NULL AND LENGTH(TRIM(motivo_cancelacion))>0 AND cancelado_por IS NOT NULL));
  INSERT INTO pos_migraciones(version) VALUES('07_caja_fiscal_cancelaciones');
END$$
DELIMITER ;
CALL migrar_pos_07();
DROP PROCEDURE migrar_pos_07;

-- 08. TURNOS DE CAJA Y KDS ENRUTADO. Requiere 07; backend detenido.
-- MySQL confirma DDL implícitamente. Respaldar antes; restaurar ante un fallo parcial.
DROP PROCEDURE IF EXISTS migrar_pos_08;
DELIMITER $$
CREATE PROCEDURE migrar_pos_08()
BEGIN
  IF EXISTS(SELECT 1 FROM pos_migraciones WHERE version='08_turnos_caja_kds_enrutado') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='La migración 08 ya fue aplicada';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pos_migraciones WHERE version='07_caja_fiscal_cancelaciones') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Primero aplica la migración 07';
  END IF;
  CREATE TABLE sesiones_caja (
    id_sesion INT AUTO_INCREMENT PRIMARY KEY, id_empresa INT NOT NULL,
    empresa_abierta INT, abierto_por INT NOT NULL, cerrado_por INT,
    fecha_apertura TIMESTAMP(6) NOT NULL, fecha_cierre TIMESTAMP(6), clave_operacion VARCHAR(36) NOT NULL,
    monto_inicial DECIMAL(14,2) NOT NULL,
    total_efectivo DECIMAL(14,2) NOT NULL DEFAULT 0, total_yape DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_plin DECIMAL(14,2) NOT NULL DEFAULT 0, total_tarjeta DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_transferencia DECIMAL(14,2) NOT NULL DEFAULT 0, total_yape_plin DECIMAL(14,2) NOT NULL DEFAULT 0,
    efectivo_declarado DECIMAL(14,2), observaciones VARCHAR(500) NOT NULL DEFAULT '',
    CONSTRAINT fk_caja_empresa FOREIGN KEY(id_empresa) REFERENCES empresa(id_empresa),
    CONSTRAINT fk_caja_abierto FOREIGN KEY(abierto_por) REFERENCES usuario(id_usuario),
    CONSTRAINT fk_caja_cerrado FOREIGN KEY(cerrado_por) REFERENCES usuario(id_usuario),
    CONSTRAINT uk_caja_empresa_abierta UNIQUE(empresa_abierta),
    CONSTRAINT uk_caja_apertura UNIQUE(id_empresa,clave_operacion),
    CONSTRAINT ck_caja_importes CHECK(monto_inicial>=0 AND total_efectivo>=0 AND total_yape>=0 AND
      total_plin>=0 AND total_tarjeta>=0 AND total_transferencia>=0 AND total_yape_plin>=0 AND
      (efectivo_declarado IS NULL OR efectivo_declarado>=0)),
    CONSTRAINT ck_caja_estado CHECK(
      (fecha_cierre IS NULL AND empresa_abierta IS NOT NULL AND empresa_abierta=id_empresa AND cerrado_por IS NULL AND efectivo_declarado IS NULL)
      OR (fecha_cierre IS NOT NULL AND fecha_cierre>=fecha_apertura AND empresa_abierta IS NULL AND cerrado_por IS NOT NULL AND efectivo_declarado IS NOT NULL))
  ) ENGINE=InnoDB;
  CREATE INDEX idx_caja_empresa_fecha ON sesiones_caja(id_empresa,fecha_apertura);
  ALTER TABLE pagos_venta ADD COLUMN id_sesion_caja INT,
    ADD CONSTRAINT fk_pago_sesion FOREIGN KEY(id_sesion_caja) REFERENCES sesiones_caja(id_sesion);
  CREATE INDEX idx_pago_sesion_metodo ON pagos_venta(id_sesion_caja,metodo_pago);
  ALTER TABLE producto ADD COLUMN area_destino VARCHAR(10) NOT NULL DEFAULT 'COCINA',
    ADD CONSTRAINT ck_producto_destino CHECK(area_destino IN('COCINA','BAR'));
  UPDATE producto p JOIN categoria c ON p.id_categoria=c.id_categoria
    SET p.area_destino='BAR' WHERE LOWER(TRIM(c.nombre_categoria))='bebidas';
  ALTER TABLE detalle_venta ADD COLUMN area_destino VARCHAR(10) NOT NULL DEFAULT 'COCINA',
    ADD COLUMN observaciones VARCHAR(255) NOT NULL DEFAULT '',
    ADD CONSTRAINT ck_detalle_destino CHECK(area_destino IN('COCINA','BAR'));
  CREATE INDEX idx_detalle_estacion ON detalle_venta(area_destino,estado_preparacion,fecha_pedido);
  CREATE INDEX idx_venta_cliente_cobro ON ventas(id_cliente,estado_venta,fecha_cobro);
  INSERT INTO rol(nombre_rol,descripcion) VALUES('BARTENDER','Acceso exclusivo a bar')
    ON DUPLICATE KEY UPDATE nombre_rol=VALUES(nombre_rol);
  INSERT INTO pos_migraciones(version) VALUES('08_turnos_caja_kds_enrutado');
END$$
DELIMITER ;
CALL migrar_pos_08();
DROP PROCEDURE migrar_pos_08;

-- 09. MENU PUBLICO Y RECEPCION WEB. Requiere 08; backend detenido.
-- DDL MySQL confirma implícitamente: respaldar antes de actualizar.
DROP PROCEDURE IF EXISTS migrar_pos_09;
DELIMITER $$
CREATE PROCEDURE migrar_pos_09()
BEGIN
  IF EXISTS(SELECT 1 FROM pos_migraciones WHERE version='09_menu_publico_pedidos_web') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='La migración 09 ya fue aplicada';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pos_migraciones WHERE version='08_turnos_caja_kds_enrutado') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Primero aplica la migración 08';
  END IF;
ALTER TABLE producto ADD COLUMN visible_web BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ventas ADD COLUMN observaciones_pedido VARCHAR(255) NOT NULL DEFAULT '';
CREATE TABLE tiendas_web (
  id_empresa INT PRIMARY KEY, FOREIGN KEY(id_empresa) REFERENCES empresa(id_empresa),
  slug VARCHAR(60) NOT NULL UNIQUE, activa BOOLEAN NOT NULL DEFAULT FALSE,
  recojo BOOLEAN NOT NULL DEFAULT TRUE, delivery BOOLEAN NOT NULL DEFAULT TRUE,
  mensaje VARCHAR(300) NOT NULL DEFAULT '',
  CONSTRAINT ck_tienda_entrega CHECK(recojo OR delivery)
) ENGINE=InnoDB;
CREATE TABLE solicitudes_web (
  id_solicitud INT AUTO_INCREMENT PRIMARY KEY, id_empresa INT NOT NULL, FOREIGN KEY(id_empresa) REFERENCES empresa(id_empresa),
  clave_operacion VARCHAR(36) NOT NULL, codigo_seguimiento VARCHAR(36) NOT NULL UNIQUE,
  huella VARCHAR(64) NOT NULL, nombre VARCHAR(150) NOT NULL, dni VARCHAR(8) NOT NULL,
  telefono VARCHAR(20) NOT NULL, tipo_entrega VARCHAR(10) NOT NULL,
  direccion VARCHAR(255) NOT NULL DEFAULT '', observaciones VARCHAR(255) NOT NULL DEFAULT '',
  estado VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE', fecha_creacion TIMESTAMP(6) NOT NULL,
  subtotal DECIMAL(10,2) NOT NULL, igv DECIMAL(10,2) NOT NULL,
  total DECIMAL(10,2) NOT NULL, motivo VARCHAR(255) NOT NULL DEFAULT '',
  id_venta INT UNIQUE, FOREIGN KEY(id_venta) REFERENCES ventas(id_venta),
  CONSTRAINT uk_web_operacion UNIQUE(id_empresa,clave_operacion),
  CONSTRAINT ck_web_importes CHECK(subtotal>=0 AND igv>=0 AND total=subtotal+igv),
  CONSTRAINT ck_web_dni CHECK(REGEXP_LIKE(dni,'^[0-9]{8}$')),
  CONSTRAINT ck_web_entrega CHECK(tipo_entrega='RECOJO' OR (tipo_entrega='DELIVERY' AND LENGTH(TRIM(direccion))>0)),
  CONSTRAINT ck_web_estado CHECK(
    (estado='PENDIENTE' AND id_venta IS NULL) OR (estado='ACEPTADO' AND id_venta IS NOT NULL)
    OR (estado='RECHAZADO' AND id_venta IS NULL AND LENGTH(TRIM(motivo))>0))
) ENGINE=InnoDB;
CREATE TABLE solicitud_web_items (
  id_item INT AUTO_INCREMENT PRIMARY KEY,
  id_solicitud INT NOT NULL, FOREIGN KEY(id_solicitud) REFERENCES solicitudes_web(id_solicitud) ON DELETE CASCADE,
  id_producto INT NOT NULL, FOREIGN KEY(id_producto) REFERENCES producto(id_producto), nombre VARCHAR(150) NOT NULL,
  cantidad INT NOT NULL, precio_base DECIMAL(10,2) NOT NULL,
  observaciones VARCHAR(255) NOT NULL DEFAULT '',
  CONSTRAINT ck_web_item CHECK(cantidad BETWEEN 1 AND 20 AND precio_base>=0)
) ENGINE=InnoDB;
CREATE INDEX idx_web_pendientes ON solicitudes_web(estado,fecha_creacion);
CREATE INDEX idx_web_telefono ON solicitudes_web(id_empresa,telefono,estado,fecha_creacion);
CREATE INDEX idx_web_items_solicitud ON solicitud_web_items(id_solicitud);
  INSERT INTO pos_migraciones(version) VALUES('09_menu_publico_pedidos_web');
END$$
DELIMITER ;
CALL migrar_pos_09();
DROP PROCEDURE migrar_pos_09;

-- 10. COMANDAS ADICIONALES IDEMPOTENTES. Requiere 09; backend detenido.
-- El DDL MySQL confirma implícitamente; conservar el respaldo antes de migrar.
DROP PROCEDURE IF EXISTS migrar_pos_10;
DELIMITER $$
CREATE PROCEDURE migrar_pos_10()
BEGIN
  IF EXISTS(SELECT 1 FROM pos_migraciones WHERE version='10_comandas_idempotentes') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='La migración 10 ya fue aplicada';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pos_migraciones WHERE version='09_menu_publico_pedidos_web') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Primero aplica la migración 09';
  END IF;
  CREATE TABLE operaciones_comanda (
    id_operacion INT AUTO_INCREMENT PRIMARY KEY, id_venta INT NOT NULL,
    clave_operacion VARCHAR(36) NOT NULL, huella VARCHAR(64) NOT NULL,
    fecha_creacion TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_comanda_venta FOREIGN KEY(id_venta) REFERENCES ventas(id_venta),
    CONSTRAINT uk_comanda_operacion UNIQUE(id_venta,clave_operacion)
  ) ENGINE=InnoDB;
  ALTER TABLE detalle_venta ADD COLUMN clave_comanda VARCHAR(36);
  CREATE INDEX idx_detalle_comanda ON detalle_venta(id_venta,clave_comanda);
  INSERT INTO pos_migraciones(version) VALUES('10_comandas_idempotentes');
END$$
DELIMITER ;
CALL migrar_pos_10();
DROP PROCEDURE migrar_pos_10;

-- 11. VENTAS LOCALES SIN IDENTIFICACION. Requiere 10; respaldo y backend detenido.
-- El DDL MySQL confirma implícitamente; conservar el respaldo antes de migrar.
DROP PROCEDURE IF EXISTS migrar_pos_11;
DELIMITER $$
CREATE PROCEDURE migrar_pos_11()
BEGIN
  IF EXISTS(SELECT 1 FROM pos_migraciones WHERE version='11_ventas_sin_identificacion') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='La migración 11 ya fue aplicada';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pos_migraciones WHERE version='10_comandas_idempotentes') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Primero aplica la migración 10';
  END IF;
  ALTER TABLE ventas MODIFY COLUMN id_cliente INT NULL;
END$$
DELIMITER ;
CALL migrar_pos_11();
-- MySQL no admite CHECK sobre esta FK con ON UPDATE CASCADE. Se conserva la FK.
DELIMITER $$
CREATE TRIGGER validar_cliente_venta_insert BEFORE INSERT ON ventas FOR EACH ROW
BEGIN
  IF NEW.id_cliente IS NULL AND NEW.origen_pedido <> 'LOCAL' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Pedidos externos requieren cliente';
  END IF;
END$$
CREATE TRIGGER validar_cliente_venta_update BEFORE UPDATE ON ventas FOR EACH ROW
BEGIN
  DECLARE cliente_existente INT DEFAULT NULL;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET cliente_existente = NULL;
  IF NEW.id_cliente IS NULL AND NEW.origen_pedido <> 'LOCAL' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Pedidos externos requieren cliente';
  END IF;
  -- Valida y bloquea la referencia también al identificar una cuenta antes anónima.
  IF OLD.id_cliente IS NULL AND NEW.id_cliente IS NOT NULL THEN
    SELECT id_cliente INTO cliente_existente FROM clientes WHERE id_cliente=NEW.id_cliente FOR SHARE;
    IF cliente_existente IS NULL THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cliente inexistente';
    END IF;
  END IF;
END$$
DELIMITER ;
INSERT INTO pos_migraciones(version) VALUES('11_ventas_sin_identificacion');
DROP PROCEDURE migrar_pos_11;

-- 12. DATOS DE EJEMPLO. Requiere 11; carga opcional y repetible sobre una base existente.
-- Instalación nueva: este bloque se ejecuta al final del archivo completo.
-- Base existente: respaldo previo, backend detenido y ejecutar solamente este bloque.
-- Solo agrega lo faltante; no cambia usuarios, contraseñas, stock ni registros existentes.
-- Empresa, clientes, documentos, contactos y precios son ficticios de demostración.
-- Configurar los datos reales de la empresa antes de emitir comprobantes reales.
-- No genera ventas, pagos, comprobantes emitidos ni tiendas públicas.
DROP PROCEDURE IF EXISTS cargar_pos_datos_demo;
DELIMITER $$
CREATE PROCEDURE cargar_pos_datos_demo()
BEGIN
  DECLARE bloqueo INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    IF bloqueo=1 THEN DO RELEASE_LOCK(CONCAT(DATABASE(),':pos_datos_demo')); END IF;
    RESIGNAL;
  END;
  IF NOT EXISTS(SELECT 1 FROM pos_migraciones WHERE version='11_ventas_sin_identificacion') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Primero aplica la migración 11';
  END IF;
  SELECT GET_LOCK(CONCAT(DATABASE(),':pos_datos_demo'),10) INTO bloqueo;
  IF bloqueo IS NULL OR bloqueo<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='No se pudo bloquear la carga de datos demo';
  END IF;
  START TRANSACTION;

INSERT INTO categoria(nombre_categoria,descripcion)
SELECT 'Comidas','Platos y alimentos preparados'
WHERE NOT EXISTS(SELECT 1 FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Comidas'));

INSERT INTO categoria(nombre_categoria,descripcion)
SELECT 'Bebidas','Bebidas frías y calientes'
WHERE NOT EXISTS(SELECT 1 FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Bebidas'));

INSERT INTO categoria(nombre_categoria,descripcion)
SELECT 'Entradas','Entradas y porciones para compartir'
WHERE NOT EXISTS(SELECT 1 FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Entradas'));

INSERT INTO categoria(nombre_categoria,descripcion)
SELECT 'Parrillas','Carnes y platos a la parrilla'
WHERE NOT EXISTS(SELECT 1 FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Parrillas'));

INSERT INTO categoria(nombre_categoria,descripcion)
SELECT 'Postres','Postres individuales'
WHERE NOT EXISTS(SELECT 1 FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Postres'));

INSERT INTO categoria(nombre_categoria,descripcion)
SELECT 'Combos','Combos de comida; bebidas adicionales por separado'
WHERE NOT EXISTS(SELECT 1 FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Combos'));

INSERT INTO marca(nombre_marca)
SELECT 'Sin marca'
WHERE NOT EXISTS(SELECT 1 FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Sin marca'));

INSERT INTO marca(nombre_marca)
SELECT 'Mi Trampita'
WHERE NOT EXISTS(SELECT 1 FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita'));

INSERT INTO marca(nombre_marca)
SELECT 'Bebidas Demo'
WHERE NOT EXISTS(SELECT 1 FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Bebidas Demo'));

INSERT INTO marca(nombre_marca)
SELECT 'Postres Demo'
WHERE NOT EXISTS(SELECT 1 FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Postres Demo'));

INSERT INTO proveedor(ruc_dni,razon_social,telefono,correo)
SELECT '20999999001','DEMO - Abastos y carnes','900000001','abastos@example.com'
WHERE NOT EXISTS(SELECT 1 FROM proveedor WHERE ruc_dni='20999999001');

INSERT INTO proveedor(ruc_dni,razon_social,telefono,correo)
SELECT '20999999002','DEMO - Distribuidora de bebidas','900000002','bebidas@example.com'
WHERE NOT EXISTS(SELECT 1 FROM proveedor WHERE ruc_dni='20999999002');

INSERT INTO proveedor(ruc_dni,razon_social,telefono,correo)
SELECT '20999999003','DEMO - Panadería y postres','900000003','postres@example.com'
WHERE NOT EXISTS(SELECT 1 FROM proveedor WHERE ruc_dni='20999999003');

INSERT INTO clientes(numero_documento,nombres_razon_social,direccion,telefono,correo,fecha_nacimiento)
SELECT '90000001','DEMO - Ana Torres','Dirección ficticia 101','900000101','ana@example.com','1995-03-15'
WHERE NOT EXISTS(SELECT 1 FROM clientes WHERE numero_documento='90000001');

INSERT INTO clientes(numero_documento,nombres_razon_social,direccion,telefono,correo,fecha_nacimiento)
SELECT '90000002','DEMO - Luis Ramos','Dirección ficticia 102','900000102','luis@example.com','1990-07-22'
WHERE NOT EXISTS(SELECT 1 FROM clientes WHERE numero_documento='90000002');

INSERT INTO clientes(numero_documento,nombres_razon_social,direccion,telefono,correo,fecha_nacimiento)
SELECT '90000003','DEMO - María Flores','Dirección ficticia 103','900000103','maria@example.com','1998-10-09'
WHERE NOT EXISTS(SELECT 1 FROM clientes WHERE numero_documento='90000003');

INSERT INTO clientes(numero_documento,nombres_razon_social,direccion,telefono,correo,fecha_nacimiento)
SELECT '90000004','DEMO - Carlos Mendoza','Dirección ficticia 104','900000104','carlos@example.com','1987-12-10'
WHERE NOT EXISTS(SELECT 1 FROM clientes WHERE numero_documento='90000004');

INSERT INTO clientes(numero_documento,nombres_razon_social,direccion,telefono,correo,fecha_nacimiento)
SELECT '90000005','DEMO - Rosa Quispe','Dirección ficticia 105','900000105','rosa@example.com','1993-05-28'
WHERE NOT EXISTS(SELECT 1 FROM clientes WHERE numero_documento='90000005');

INSERT INTO clientes(numero_documento,nombres_razon_social,direccion,telefono,correo,fecha_nacimiento)
SELECT '90000006','DEMO - Pedro García','Dirección ficticia 106','900000106','pedro@example.com','2000-01-18'
WHERE NOT EXISTS(SELECT 1 FROM clientes WHERE numero_documento='90000006');

INSERT INTO empresa(ruc,razon_social,nombre_comercial,direccion,telefono,correo)
SELECT '20999999991','DEMO - Restaurante Mi Trampita','Mi Trampita - Demostración','Dirección ficticia - configurar los datos reales antes de emitir comprobantes','900000000','mitrampita@example.com'
WHERE NOT EXISTS(SELECT 1 FROM empresa WHERE 1=1);

INSERT INTO tipo_comprobante(nombre_tipo,serie,descripcion)
SELECT 'BOLETA','B001','Boleta de venta'
WHERE NOT EXISTS(SELECT 1 FROM tipo_comprobante WHERE LOWER(TRIM(nombre_tipo))=LOWER('BOLETA') AND serie='B001');

INSERT INTO tipo_comprobante(nombre_tipo,serie,descripcion)
SELECT 'FACTURA','F001','Factura de venta'
WHERE NOT EXISTS(SELECT 1 FROM tipo_comprobante WHERE LOWER(TRIM(nombre_tipo))=LOWER('FACTURA') AND serie='F001');

INSERT INTO tipo_comprobante(nombre_tipo,serie,descripcion)
SELECT 'NOTA DE VENTA','NV01','Comprobante interno'
WHERE NOT EXISTS(SELECT 1 FROM tipo_comprobante WHERE LOWER(TRIM(nombre_tipo))=LOWER('NOTA DE VENTA') AND serie='NV01');

INSERT INTO areas(nombre)
SELECT 'Salón Principal'
WHERE NOT EXISTS(SELECT 1 FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Salón Principal'));

INSERT INTO areas(nombre)
SELECT 'Terraza'
WHERE NOT EXISTS(SELECT 1 FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Terraza'));

INSERT INTO areas(nombre)
SELECT 'Zona Recreacional'
WHERE NOT EXISTS(SELECT 1 FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Zona Recreacional'));

INSERT INTO areas(nombre)
SELECT 'Piscina'
WHERE NOT EXISTS(SELECT 1 FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Piscina'));

INSERT INTO mesas(numero,capacidad,estado,area_id)
SELECT 1,4,'LIBRE',(SELECT MIN(id) FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Salón Principal'))
WHERE NOT EXISTS(SELECT 1 FROM mesas WHERE numero=1);

INSERT INTO mesas(numero,capacidad,estado,area_id)
SELECT 2,4,'LIBRE',(SELECT MIN(id) FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Salón Principal'))
WHERE NOT EXISTS(SELECT 1 FROM mesas WHERE numero=2);

INSERT INTO mesas(numero,capacidad,estado,area_id)
SELECT 3,4,'LIBRE',(SELECT MIN(id) FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Salón Principal'))
WHERE NOT EXISTS(SELECT 1 FROM mesas WHERE numero=3);

INSERT INTO mesas(numero,capacidad,estado,area_id)
SELECT 4,4,'LIBRE',(SELECT MIN(id) FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Terraza'))
WHERE NOT EXISTS(SELECT 1 FROM mesas WHERE numero=4);

INSERT INTO mesas(numero,capacidad,estado,area_id)
SELECT 5,4,'LIBRE',(SELECT MIN(id) FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Terraza'))
WHERE NOT EXISTS(SELECT 1 FROM mesas WHERE numero=5);

INSERT INTO mesas(numero,capacidad,estado,area_id)
SELECT 6,4,'LIBRE',(SELECT MIN(id) FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Terraza'))
WHERE NOT EXISTS(SELECT 1 FROM mesas WHERE numero=6);

INSERT INTO mesas(numero,capacidad,estado,area_id)
SELECT 7,4,'LIBRE',(SELECT MIN(id) FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Zona Recreacional'))
WHERE NOT EXISTS(SELECT 1 FROM mesas WHERE numero=7);

INSERT INTO mesas(numero,capacidad,estado,area_id)
SELECT 8,4,'LIBRE',(SELECT MIN(id) FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Zona Recreacional'))
WHERE NOT EXISTS(SELECT 1 FROM mesas WHERE numero=8);

INSERT INTO mesas(numero,capacidad,estado,area_id)
SELECT 9,4,'LIBRE',(SELECT MIN(id) FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Zona Recreacional'))
WHERE NOT EXISTS(SELECT 1 FROM mesas WHERE numero=9);

INSERT INTO mesas(numero,capacidad,estado,area_id)
SELECT 10,4,'LIBRE',(SELECT MIN(id) FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Piscina'))
WHERE NOT EXISTS(SELECT 1 FROM mesas WHERE numero=10);

INSERT INTO mesas(numero,capacidad,estado,area_id)
SELECT 11,4,'LIBRE',(SELECT MIN(id) FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Piscina'))
WHERE NOT EXISTS(SELECT 1 FROM mesas WHERE numero=11);

INSERT INTO mesas(numero,capacidad,estado,area_id)
SELECT 12,4,'LIBRE',(SELECT MIN(id) FROM areas WHERE LOWER(TRIM(nombre))=LOWER('Piscina'))
WHERE NOT EXISTS(SELECT 1 FROM mesas WHERE numero=12);

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Comidas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999001'),
  'DEMO-COM-001','Arroz con pollo','DEMO: Porción de arroz con pollo y ensalada',8.00,16.00,40,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-COM-001');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Comidas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999001'),
  'DEMO-COM-002','Lomo saltado','DEMO: Carne salteada con papas y arroz',12.00,25.00,35,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-COM-002');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Comidas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999001'),
  'DEMO-COM-003','Ají de gallina','DEMO: Ají de gallina con arroz y papa',8.50,18.00,30,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-COM-003');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Comidas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999001'),
  'DEMO-COM-004','Tallarin saltado de pollo','DEMO: Tallarines salteados con pollo y verduras',9.00,19.00,30,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-COM-004');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Comidas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999001'),
  'DEMO-COM-005','Milanesa de pollo','DEMO: Pollo empanizado con papas y ensalada',9.50,20.00,30,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-COM-005');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Entradas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999001'),
  'DEMO-ENT-001','Papa a la huancaína','DEMO: Papa con salsa huancaína',4.00,9.00,35,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-ENT-001');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Entradas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999001'),
  'DEMO-ENT-002','Tequeños de queso','DEMO: Porción de seis tequeños',5.00,12.00,30,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-ENT-002');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Entradas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999001'),
  'DEMO-ENT-003','Porción de papas fritas','DEMO: Porción individual de papas fritas',3.00,7.00,50,10,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-ENT-003');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Parrillas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999001'),
  'DEMO-PAR-001','Pollo a la parrilla','DEMO: Pollo a la parrilla con guarnición',12.00,24.00,25,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-PAR-001');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Parrillas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999001'),
  'DEMO-PAR-002','Chuleta a la parrilla','DEMO: Chuleta con papas y ensalada',14.00,28.00,20,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-PAR-002');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Parrillas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999001'),
  'DEMO-PAR-003','Anticuchos','DEMO: Dos palitos de anticucho con papa',8.00,18.00,25,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-PAR-003');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Combos')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999001'),
  'DEMO-CBO-001','Combo hamburguesa y papas','DEMO: Hamburguesa de carne con papas; bebida por separado',8.00,17.00,35,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-CBO-001');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Combos')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999001'),
  'DEMO-CBO-002','Combo salchipapa especial','DEMO: Papas, salchicha y pollo; bebida por separado',7.00,15.00,35,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-CBO-002');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Bebidas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Bebidas Demo')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999002'),
  'DEMO-BEB-001','Agua sin gas 625 ml','DEMO: Botella individual de agua',1.20,3.00,100,15,0,'BAR',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-BEB-001');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Bebidas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Bebidas Demo')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999002'),
  'DEMO-BEB-002','Gaseosa personal 500 ml','DEMO: Gaseosa en botella personal',2.00,5.00,80,15,0,'BAR',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-BEB-002');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Bebidas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Bebidas Demo')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999002'),
  'DEMO-BEB-003','Gaseosa familiar 1.5 L','DEMO: Botella para compartir',5.50,10.00,40,8,0,'BAR',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-BEB-003');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Bebidas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999002'),
  'DEMO-BEB-004','Chicha morada vaso','DEMO: Vaso de chicha morada de la casa',1.50,4.00,70,10,0,'BAR',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-BEB-004');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Bebidas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999002'),
  'DEMO-BEB-005','Chicha morada jarra','DEMO: Jarra de un litro',4.00,12.00,35,5,0,'BAR',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-BEB-005');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Bebidas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999002'),
  'DEMO-BEB-006','Limonada vaso','DEMO: Vaso de limonada preparada',1.50,4.00,60,10,0,'BAR',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-BEB-006');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Bebidas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Mi Trampita')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999002'),
  'DEMO-BEB-007','Café americano','DEMO: Taza de café caliente',1.80,5.00,50,10,0,'BAR',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-BEB-007');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Bebidas')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Sin marca')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999002'),
  'DEMO-BEB-008','Infusión','DEMO: Té, manzanilla o anís',0.80,3.00,60,10,0,'BAR',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-BEB-008');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Postres')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Postres Demo')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999003'),
  'DEMO-POS-001','Flan casero','DEMO: Porción individual de flan',2.50,6.00,25,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-POS-001');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Postres')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Postres Demo')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999003'),
  'DEMO-POS-002','Torta de chocolate','DEMO: Porción de torta de chocolate',4.00,9.00,20,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-POS-002');

INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,descripcion,
  precio_compra,precio_venta,stock_actual,stock_minimo,version,area_destino,visible_web)
SELECT
  (SELECT MIN(id_categoria) FROM categoria WHERE LOWER(TRIM(nombre_categoria))=LOWER('Postres')),
  (SELECT MIN(id_marca) FROM marca WHERE LOWER(TRIM(nombre_marca))=LOWER('Postres Demo')),
  (SELECT MIN(id_proveedor) FROM proveedor WHERE ruc_dni='20999999003'),
  'DEMO-POS-003','Helado individual','DEMO: Copa individual de helado',2.00,5.00,30,5,0,'COCINA',FALSE
WHERE NOT EXISTS(SELECT 1 FROM producto WHERE codigo_barras='DEMO-POS-003');

  COMMIT;
  DO RELEASE_LOCK(CONCAT(DATABASE(),':pos_datos_demo'));
END$$
DELIMITER ;
CALL cargar_pos_datos_demo();
DROP PROCEDURE cargar_pos_datos_demo;
