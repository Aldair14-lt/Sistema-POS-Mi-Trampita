-- Ejecutar una sola vez sobre una base existente.
CREATE TABLE IF NOT EXISTS mesa (
    id_mesa SERIAL PRIMARY KEY,
    numero_mesa INT NOT NULL UNIQUE,
    capacidad INT NOT NULL DEFAULT 4,
    estado_mesa VARCHAR(20) NOT NULL DEFAULT 'LIBRE'
);

ALTER TABLE venta
    ADD COLUMN IF NOT EXISTS estado_venta VARCHAR(20) NOT NULL DEFAULT 'CERRADA';
ALTER TABLE venta ADD COLUMN IF NOT EXISTS id_mesa INT NULL;
CREATE INDEX IF NOT EXISTS idx_venta_mesa_estado ON venta (id_mesa, estado_venta);

UPDATE venta
SET estado_venta = 'CERRADA'
WHERE estado_venta IS NULL;

ALTER TABLE venta
    ALTER COLUMN estado_venta SET DEFAULT 'ABIERTA';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_venta_estado'
          AND conrelid = 'venta'::regclass
    ) THEN
        ALTER TABLE venta
            ADD CONSTRAINT ck_venta_estado
            CHECK (estado_venta IN ('ABIERTA', 'CERRADA', 'ANULADA'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_venta_mesa'
    ) THEN
        ALTER TABLE venta ADD CONSTRAINT fk_venta_mesa
            FOREIGN KEY (id_mesa) REFERENCES mesa (id_mesa) ON UPDATE CASCADE;
    END IF;
END $$;
