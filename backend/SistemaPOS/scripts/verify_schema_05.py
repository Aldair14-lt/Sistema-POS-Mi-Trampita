"""Prueba instalación, preflight, históricos y segunda ejecución en una BD NUEVA.

Requiere psql/mysql instalado. DB_USERNAME/DB_PASSWORD por entorno.
La base debe empezar por pos_kds_qa_; se conserva para las pruebas HTTP.
Nunca aplica migraciones a pos_db ni borra bases existentes.
"""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--engine', choices=['postgresql', 'mysql'], required=True)
parser.add_argument('--database', required=True)
args = parser.parse_args()
assert re.fullmatch(r'pos_kds_qa_[a-z0-9_]+', args.database), 'Nombre de base de pruebas inválido'
root = Path(__file__).resolve().parents[3]
scripts = root / 'database/scripts'
env = os.environ.copy()
password = env.get('DB_PASSWORD', '')
pg = args.engine == 'postgresql'
binary = shutil.which('psql' if pg else 'mysql') or (
    r'C:\Program Files\PostgreSQL\17\bin\psql.exe' if pg else r'C:\Program Files\MySQL\MySQL Server 9.6\bin\mysql.exe')
env['PGPASSWORD' if pg else 'MYSQL_PWD'] = password
user = env.get('DB_USERNAME', 'postgres' if pg else 'root')
port = env.get('DB_PORT', '5432' if pg else '3306')


def run(sql, database=None, fail=False):
    command = [binary, '-h', 'localhost', '-p', port, '-U', user, '-v', 'ON_ERROR_STOP=1', '-X', '-A', '-t', '-d', database or 'postgres'] if pg else [
        binary, '--host=127.0.0.1', '--port=' + port, '--user=' + user, '--default-character-set=utf8mb4', '--batch', '--skip-column-names']
    if database and not pg:
        command.append(database)
    result = subprocess.run(command, input=sql, text=True, encoding='utf-8', capture_output=True, env=env)
    if fail:
        assert result.returncode != 0, 'Se esperaba que la migración rechazara esta ejecución'
        return result.stderr
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout.strip()


run(f'CREATE DATABASE {args.database}' + ('' if pg else ' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci') + ';')
consolidated = (scripts / f'pos_{args.engine}.sql').read_text(encoding='utf-8')
marker = '-- 05. MIGRACIÓN DE BASE EXISTENTE / CUENTAS, ONLINE Y COCINA.'
base, remaining = consolidated.split(marker, 1)
migration, migration06 = remaining.split('-- 06. MIGRACIÓN DE BASE EXISTENTE / CAJA, WHATSAPP Y COMPROBANTES.', 1)
migration06, migration07 = migration06.split('-- 07. FACTURACIÓN EN CAJA Y CANCELACIONES.', 1)
migration07 = migration07.split('\n', 1)[1]
if not pg:
    base = base.replace('`pos_db`', '`' + args.database + '`')
run(base, args.database)
assert 'Primero aplica' in run(migration06, args.database, fail=True)
if not pg:
    run('DROP PROCEDURE IF EXISTS migrar_pos_06;', args.database)
seed = """
INSERT INTO empresa(ruc,razon_social,direccion) VALUES('20999999991','Prueba migración','Prueba');
INSERT INTO clientes(numero_documento,nombres_razon_social) VALUES('99999999','Cliente migración');
INSERT INTO tipo_comprobante(nombre_tipo,serie) VALUES('BOLETA','B001');
INSERT INTO categoria(nombre_categoria) VALUES('Migración');
INSERT INTO marca(nombre_marca) VALUES('Migración');
INSERT INTO proveedor(ruc_dni,razon_social) VALUES('99999998','Proveedor migración');
INSERT INTO producto(id_categoria,id_marca,id_proveedor,codigo_barras,nombre_producto,precio_venta,stock_actual)
SELECT c.id_categoria,m.id_marca,p.id_proveedor,'QA-MIG','Plato migración',10,10
FROM categoria c,marca m,proveedor p WHERE c.nombre_categoria='Migración' AND m.nombre_marca='Migración' AND p.ruc_dni='99999998';
INSERT INTO ventas(id_empresa,id_usuario,id_cliente,id_tipo_comprobante,mesa_id,numero_comprobante,subtotal,igv_impuesto,total,estado_venta,fecha_cobro)
SELECT e.id_empresa,u.id_usuario,c.id_cliente,t.id_tipo_comprobante,m.id,'QA-MIG-ABIERTA',20,3.60,23.60,'ABIERTA',NULL
FROM empresa e,usuario u,clientes c,tipo_comprobante t,mesas m
WHERE e.ruc='20999999991' AND u.usuario='admin' AND c.numero_documento='99999999' AND t.serie='B001' AND m.numero=1;
INSERT INTO ventas(id_empresa,id_usuario,id_cliente,id_tipo_comprobante,numero_comprobante,subtotal,igv_impuesto,total,estado_venta,fecha_cobro)
SELECT e.id_empresa,u.id_usuario,c.id_cliente,t.id_tipo_comprobante,'QA-MIG-CERRADA',10,1.80,11.80,'CERRADA',CURRENT_TIMESTAMP
FROM empresa e,usuario u,clientes c,tipo_comprobante t
WHERE e.ruc='20999999991' AND u.usuario='admin' AND c.numero_documento='99999999' AND t.serie='B001';
INSERT INTO detalle_venta(id_venta,id_producto,cantidad,precio_unitario,subtotal)
SELECT v.id_venta,p.id_producto,CASE WHEN v.estado_venta='ABIERTA' THEN 2 ELSE 1 END,10,
CASE WHEN v.estado_venta='ABIERTA' THEN 20 ELSE 10 END
FROM ventas v,producto p WHERE v.numero_comprobante LIKE 'QA-MIG-%' AND p.codigo_barras='QA-MIG';
UPDATE mesas SET estado='ATENDIENDO' WHERE numero=1;
UPDATE producto SET stock_actual=1 WHERE codigo_barras='QA-MIG';
"""
run(seed, args.database)
assert 'Stock insuficiente' in run(migration, args.database, fail=True)
assert run("SELECT COUNT(*) FROM information_schema.columns WHERE table_name='ventas' AND column_name='estado_cuenta' AND " +
           ("table_catalog=current_database()" if pg else 'table_schema=DATABASE()') + ';', args.database) == '0'
run("UPDATE producto SET stock_actual=10 WHERE codigo_barras='QA-MIG';", args.database)
run(migration, args.database)
assert run("SELECT stock_actual FROM producto WHERE codigo_barras='QA-MIG';", args.database) == '8'
assert run('SELECT COUNT(*) FROM pagos_venta;', args.database) == '1'
assert run("SELECT estado_cuenta FROM ventas WHERE numero_comprobante='QA-MIG-CERRADA';", args.database) == 'CERRADA'
assert run("SELECT estado_preparacion FROM detalle_venta d JOIN ventas v ON v.id_venta=d.id_venta WHERE v.numero_comprobante='QA-MIG-CERRADA';", args.database) == 'SERVIDO'
assert run("SELECT estado_preparacion FROM detalle_venta d JOIN ventas v ON v.id_venta=d.id_venta WHERE v.numero_comprobante='QA-MIG-ABIERTA';", args.database) == 'PENDIENTE'
assert 'ya fue aplicada' in run(migration, args.database, fail=True)
assert run("SELECT stock_actual FROM producto WHERE codigo_barras='QA-MIG';", args.database) == '8'
if not pg:
    run('DROP PROCEDURE IF EXISTS migrar_pos_05;', args.database)

run("UPDATE ventas SET numero_comprobante='B001-00000042' WHERE numero_comprobante='QA-MIG-CERRADA';", args.database)
run("UPDATE detalle_venta SET estado_preparacion='EN_PREPARACION' WHERE estado_preparacion='PENDIENTE';", args.database)
run(migration06, args.database)
assert run("SELECT stock_actual FROM producto WHERE codigo_barras='QA-MIG';", args.database) == '8'
assert run('SELECT COUNT(*) FROM pagos_venta;', args.database) == '1'
assert run("SELECT ultimo_correlativo FROM tipo_comprobante WHERE serie='B001';", args.database) == '42'
assert run("SELECT COUNT(*) FROM detalle_venta WHERE estado_preparacion='PREPARANDO';", args.database) == '1'
assert run("SELECT COUNT(*) FROM comprobantes;", args.database) == '0'
assert 'ya fue aplicada' in run(migration06, args.database, fail=True)
assert run("SELECT stock_actual FROM producto WHERE codigo_barras='QA-MIG';", args.database) == '8'
if not pg:
    run('DROP PROCEDURE IF EXISTS migrar_pos_06;', args.database)

# La migración fiscal preserva fotografías históricas y no repone stock ya descontado.
run("""INSERT INTO comprobantes(id_venta,id_tipo_comprobante,tipo,serie,correlativo,
 empresa_ruc,empresa_nombre,empresa_direccion,cliente_documento,cliente_nombre,
 subtotal,igv,total,fecha_emision)
 SELECT v.id_venta,v.id_tipo_comprobante,'BOLETA','B001',42,'20999999991','Prueba','Prueba',
 '99999999','Cliente histórico',v.subtotal,v.igv_impuesto,v.total,CURRENT_TIMESTAMP
 FROM ventas v WHERE v.numero_comprobante='B001-00000042';""", args.database)
run(migration07, args.database)
assert run("SELECT tipo_comprobante FROM comprobantes;", args.database) == 'BOLETA'
assert run("SELECT dni FROM comprobantes;", args.database) == '99999999'
assert run("SELECT metodo_pago FROM pagos_venta;", args.database) == 'EFECTIVO'
assert run("SELECT stock_actual FROM producto WHERE codigo_barras='QA-MIG';", args.database) == '8'
assert 'ya fue aplicada' in run(migration07, args.database, fail=True)
run("UPDATE detalle_venta SET estado_preparacion='CANCELADO';", args.database, fail=True)
assert run("SELECT COUNT(*) FROM detalle_venta WHERE estado_preparacion='CANCELADO';", args.database) == '0'
if not pg:
    run('DROP PROCEDURE IF EXISTS migrar_pos_07;', args.database)

# La instalación completa también debe funcionar ejecutando un solo archivo.
full_database = args.database + '_full'
run(f'CREATE DATABASE {full_database}' + ('' if pg else ' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci') + ';')
full_script = consolidated if pg else consolidated.replace('`pos_db`', '`' + full_database + '`')
run(full_script, full_database)
assert run("SELECT COUNT(*) FROM pos_migraciones WHERE version='05_cuentas_online_cocina';", full_database) == '1'
assert run("SELECT COUNT(*) FROM rol WHERE nombre_rol='COCINERO';", full_database) == '1'
assert run('SELECT COUNT(*) FROM pagos_venta;', full_database) == '0'
assert run("SELECT COUNT(*) FROM pos_migraciones WHERE version='06_caja_whatsapp_comprobantes';", full_database) == '1'
assert run('SELECT COUNT(*) FROM comprobantes;', full_database) == '0'
assert run("SELECT COUNT(*) FROM pos_migraciones WHERE version='07_caja_fiscal_cancelaciones';", full_database) == '1'
print(f'PASS {args.engine}: instalación completa, migraciones 05/06/07, históricos, pagos, auditoría de cancelaciones y reejecución protegida. BD: {args.database}')
