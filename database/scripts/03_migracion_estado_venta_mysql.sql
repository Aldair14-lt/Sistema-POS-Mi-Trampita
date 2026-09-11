-- Ejecutar una sola vez sobre una base existente.
CREATE TABLE IF NOT EXISTS mesa (
    id_mesa INT AUTO_INCREMENT PRIMARY KEY,
    numero_mesa INT NOT NULL UNIQUE,
    capacidad INT NOT NULL DEFAULT 4,
    estado_mesa VARCHAR(20) NOT NULL DEFAULT 'LIBRE',
    CONSTRAINT ck_mesa_estado CHECK (estado_mesa IN ('LIBRE', 'OCUPADA', 'RESERVADA'))
) ENGINE=InnoDB;

SET @estado_venta_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'venta'
      AND column_name = 'estado_venta'
);
SET @add_estado_venta_sql = IF(
    @estado_venta_exists = 0,
    'ALTER TABLE venta ADD COLUMN estado_venta VARCHAR(20) NOT NULL DEFAULT ''CERRADA''',
    'SELECT 1'
);
PREPARE add_estado_venta FROM @add_estado_venta_sql;
EXECUTE add_estado_venta;
DEALLOCATE PREPARE add_estado_venta;

ALTER TABLE venta ADD COLUMN id_mesa INT NULL;
ALTER TABLE venta ADD CONSTRAINT fk_venta_mesa
    FOREIGN KEY (id_mesa) REFERENCES mesa (id_mesa) ON UPDATE CASCADE;
CREATE INDEX idx_venta_mesa_estado ON venta (id_mesa, estado_venta);

UPDATE venta
SET estado_venta = 'CERRADA'
WHERE estado_venta IS NULL;

ALTER TABLE venta
    ALTER COLUMN estado_venta SET DEFAULT 'ABIERTA';

ALTER TABLE venta
    ADD CONSTRAINT ck_venta_estado CHECK (estado_venta IN ('ABIERTA', 'CERRADA', 'ANULADA'));
