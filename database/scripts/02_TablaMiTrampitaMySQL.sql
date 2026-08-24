-- -----------------------------------------------------
-- CREACIÓN DE LA BASE DE DATOS
-- -----------------------------------------------------
CREATE DATABASE IF NOT EXISTS `pos_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `pos_db`;

-- -----------------------------------------------------
-- 1. MÓDULO DE ORGANIZACIÓN (TABLA MADRE)
-- -----------------------------------------------------
CREATE TABLE `empresa` (
  `id_empresa` INT AUTO_INCREMENT PRIMARY KEY,
  `ruc` VARCHAR(20) NOT NULL UNIQUE,
  `razon_social` VARCHAR(150) NOT NULL,
  `nombre_comercial` VARCHAR(150) NULL,
  `direccion` TEXT NOT NULL,
  `telefono` VARCHAR(20) NULL,
  `correo` VARCHAR(100) NULL,
  `fecha_registro` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- -----------------------------------------------------
-- 2. MÓDULO DE SEGURIDAD, ROLES Y ACCESOS
-- -----------------------------------------------------
CREATE TABLE `rol` (
  `id_rol` INT AUTO_INCREMENT PRIMARY KEY,
  `nombre_rol` VARCHAR(50) NOT NULL,
  `descripcion` VARCHAR(255) NULL
) ENGINE=InnoDB;

CREATE TABLE `usuario` (
  `id_usuario` INT AUTO_INCREMENT PRIMARY KEY,
  `usuario` VARCHAR(50) NOT NULL UNIQUE,
  `contraseña` VARCHAR(255) NOT NULL,
  `nombre_completo` VARCHAR(150) NOT NULL,
  `correo_electronico` VARCHAR(100) NULL,
  `pin_caja` VARCHAR(255) NULL,
  `estado` ENUM('activo', 'inactivo', 'bloqueado') DEFAULT 'activo',
  `fecha_creacion` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE `usuario_rol` (
  `id_usuario_rol` INT AUTO_INCREMENT PRIMARY KEY,
  `id_usuario` INT NOT NULL,
  `id_rol` INT NOT NULL,
  CONSTRAINT `fk_usuario_rol_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_usuario_rol_rol` FOREIGN KEY (`id_rol`) REFERENCES `rol` (`id_rol`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- -----------------------------------------------------
-- 3. MÓDULO DE PRODUCTOS E INVENTARIO
-- -----------------------------------------------------
CREATE TABLE `categoria` (
  `id_categoria` INT AUTO_INCREMENT PRIMARY KEY,
  `nombre_categoria` VARCHAR(100) NOT NULL,
  `descripcion` VARCHAR(255) NULL
) ENGINE=InnoDB;

CREATE TABLE `marca` (
  `id_marca` INT AUTO_INCREMENT PRIMARY KEY,
  `nombre_marca` VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE `proveedor` (
  `id_proveedor` INT AUTO_INCREMENT PRIMARY KEY,
  `ruc_dni` VARCHAR(20) NOT NULL,
  `razon_social` VARCHAR(150) NOT NULL,
  `telefono` VARCHAR(20) NULL,
  `correo` VARCHAR(100) NULL
) ENGINE=InnoDB;

CREATE TABLE `producto` (
  `id_producto` INT AUTO_INCREMENT PRIMARY KEY,
  `id_categoria` INT NOT NULL,
  `id_marca` INT NOT NULL,
  `id_proveedor` INT NOT NULL,
  `codigo_barras` VARCHAR(50) NOT NULL UNIQUE,
  `nombre_producto` VARCHAR(150) NOT NULL,
  `descripcion` TEXT NULL,
  `precio_compra` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `precio_venta` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `stock_actual` INT NOT NULL DEFAULT 0,
  `stock_minimo` INT NOT NULL DEFAULT 5,
  `fecha_registro` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `fk_producto_categoria` FOREIGN KEY (`id_categoria`) REFERENCES `categoria` (`id_categoria`) ON UPDATE CASCADE,
  CONSTRAINT `fk_producto_marca` FOREIGN KEY (`id_marca`) REFERENCES `marca` (`id_marca`) ON UPDATE CASCADE,
  CONSTRAINT `fk_producto_proveedor` FOREIGN KEY (`id_proveedor`) REFERENCES `proveedor` (`id_proveedor`) ON UPDATE CASCADE
) ENGINE=InnoDB;

-- -----------------------------------------------------
-- 4. MÓDULO DE CLIENTES, VENTAS Y COMPROBANTES
-- -----------------------------------------------------
CREATE TABLE `cliente` (
  `id_cliente` INT AUTO_INCREMENT PRIMARY KEY,
  `numero_documento` VARCHAR(20) NOT NULL,
  `nombres_razon_social` VARCHAR(150) NOT NULL,
  `direccion` VARCHAR(255) NULL,
  `telefono` VARCHAR(20) NULL,
  `correo` VARCHAR(100) NULL
) ENGINE=InnoDB;

CREATE TABLE `tipo_comprobante` (
  `id_tipo_comprobante` INT AUTO_INCREMENT PRIMARY KEY,
  `nombre_tipo` VARCHAR(50) NOT NULL,
  `serie` VARCHAR(10) NOT NULL,
  `descripcion` VARCHAR(255) NULL
) ENGINE=InnoDB;

CREATE TABLE `venta` (
  `id_venta` INT AUTO_INCREMENT PRIMARY KEY,
  `id_empresa` INT NOT NULL,
  `id_usuario` INT NOT NULL,
  `id_cliente` INT NOT NULL,
  `id_tipo_comprobante` INT NOT NULL,
  `numero_comprobante` VARCHAR(50) NOT NULL,
  `subtotal` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `igv_impuesto` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `total` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `metodo_pago` ENUM('efectivo', 'tarjeta', 'transferencia', 'yape_plin') NOT NULL DEFAULT 'efectivo',
  `fecha_venta` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `fk_venta_empresa` FOREIGN KEY (`id_empresa`) REFERENCES `empresa` (`id_empresa`) ON UPDATE CASCADE,
  CONSTRAINT `fk_venta_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`) ON UPDATE CASCADE,
  CONSTRAINT `fk_venta_cliente` FOREIGN KEY (`id_cliente`) REFERENCES `cliente` (`id_cliente`) ON UPDATE CASCADE,
  CONSTRAINT `fk_venta_tipo_comprobante` FOREIGN KEY (`id_tipo_comprobante`) REFERENCES `tipo_comprobante` (`id_tipo_comprobante`) ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `detalle_venta` (
  `id_detalle_venta` INT AUTO_INCREMENT PRIMARY KEY,
  `id_venta` INT NOT NULL,
  `id_producto` INT NOT NULL,
  `cantidad` INT NOT NULL DEFAULT 1,
  `precio_unitario` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `subtotal` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  CONSTRAINT `fk_detalle_venta_venta` FOREIGN KEY (`id_venta`) REFERENCES `venta` (`id_venta`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_detalle_venta_producto` FOREIGN KEY (`id_producto`) REFERENCES `producto` (`id_producto`) ON UPDATE CASCADE
) ENGINE=InnoDB;