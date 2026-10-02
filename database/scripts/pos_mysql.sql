-- ============================================================================
-- POS MI TRAMPITA — INSTALACIÓN COMPLETA PARA MYSQL 8.0.16+
--
-- Base nueva: ejecutar el archivo completo; crea y selecciona pos_db.
-- Base existente: respaldo previo, backend detenido y solo migraciones pendientes
-- (03, 04 y/o 05), nunca la sección de instalación.
-- Si ya tiene 04, seleccionar únicamente la sección 05 hasta el fin del archivo.
-- Si ya tiene 05, no repetir: se protege el inventario contra dobles descuentos.
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
