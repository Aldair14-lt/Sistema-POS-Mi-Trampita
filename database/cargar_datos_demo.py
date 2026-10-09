"""Carga aditiva en el POS existente. Usa clientes nativos, sin dependencias Python.

DB_USERNAME / DB_PASSWORD / DB_HOST / DB_PORT por entorno. No instala esquemas.
Respalda antes de aplicar y verifica conservación de todas las filas originales.
"""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SEED_MARKER = '-- 12. DATOS DE EJEMPLO.'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--engine', choices=['postgresql', 'mysql'], required=True)
    parser.add_argument('--database', required=True)
    parser.add_argument('--apply', action='store_true', help='Aplicar; sin esta opción solo inspecciona')
    args = parser.parse_args()
    if not re.fullmatch(r'pos_db|pos_kds_qa_[a-z0-9_]+', args.database):
        parser.error('Solo se permite pos_db o una base QA pos_kds_qa_*')
    if 'DB_PASSWORD' not in os.environ:
        parser.error('Define DB_PASSWORD en el entorno; nunca se guarda en el proyecto')
    pg = args.engine == 'postgresql'
    user = os.environ.get('DB_USERNAME', 'postgres' if pg else 'root')
    host = os.environ.get('DB_HOST', 'localhost' if pg else '127.0.0.1')
    port = os.environ.get('DB_PORT', '5432' if pg else '3306')
    env = os.environ.copy()
    env['PGPASSWORD' if pg else 'MYSQL_PWD'] = env['DB_PASSWORD']
    env['PGCLIENTENCODING'] = 'UTF8'

    def binary(name):
        base = Path(r'C:\Program Files\PostgreSQL\17\bin' if pg else
                    r'C:\Program Files\MySQL\MySQL Server 9.6\bin')
        path = shutil.which(name) or str(base / (name + '.exe'))
        if not Path(path).is_file():
            raise RuntimeError(f'Cliente no encontrado: {name}; agrégalo al PATH')
        return path

    connection = (['-h', host, '-p', port, '-U', user] if pg else
                  ['--host=' + host, '--port=' + port, '--user=' + user])
    command = ([binary('psql'), *connection, '-X', '-A', '-t', '-v', 'ON_ERROR_STOP=1',
                '-d', args.database] if pg else
               [binary('mysql'), *connection, '--default-character-set=utf8mb4',
                '--batch', '--raw', '--skip-column-names', args.database])

    def run(sql):
        result = subprocess.run(command, input=sql, encoding='utf-8', capture_output=True, env=env)
        if result.returncode:
            raise RuntimeError(result.stderr.strip())
        return result.stdout.strip()

    def quote(value):
        return "'" + str(value).replace("'", "''") + "'"

    def ident(value):
        mark = '"' if pg else '`'
        return mark + value.replace(mark, mark * 2) + mark

    scope = "table_schema='public'" if pg else 'table_schema=DATABASE()'
    tables = run(f"SELECT table_name FROM information_schema.tables WHERE {scope} "
                 "AND table_type='BASE TABLE' ORDER BY table_name;").splitlines()
    required = {'producto', 'categoria', 'marca', 'proveedor', 'clientes', 'empresa',
                'areas', 'mesas', 'usuario', 'pos_migraciones', 'tipo_comprobante'}
    if not required.issubset(tables):
        raise RuntimeError('La base no tiene el esquema POS completo; no se insertó nada')
    versions = run('SELECT version FROM pos_migraciones;').splitlines()
    if '11_ventas_sin_identificacion' not in versions:
        raise RuntimeError('Aplica primero las migraciones pendientes hasta 11')

    snapshot_queries = []
    for table in tables:
        if pg:
            row = 'row_to_json(t)'
            expression = f"json_build_object('tabla',{quote(table)},'fila',{row})::text"
        else:
            columns = run(f"SELECT column_name FROM information_schema.columns WHERE {scope} "
                          f"AND table_name={quote(table)} ORDER BY ordinal_position;").splitlines()
            row = 'JSON_OBJECT(' + ','.join(f'{quote(c)},t.{ident(c)}' for c in columns) + ')'
            expression = f"JSON_OBJECT('tabla',{quote(table)},'fila',{row})"
        snapshot_queries.append(f'SELECT {expression} FROM {ident(table)} t')

    def snapshot():
        records = Counter()
        counts = Counter({t: 0 for t in tables})
        for line in run(' UNION ALL '.join(snapshot_queries) + ';').splitlines():
            row = json.loads(line)
            canonical = json.dumps(row['fila'], sort_keys=True, ensure_ascii=False)
            records[(row['tabla'], hashlib.sha256(canonical.encode('utf-8')).hexdigest())] += 1
            counts[row['tabla']] += 1
        return records, counts

    before, before_counts = snapshot()
    print(json.dumps({'motor': args.engine, 'base': args.database,
                      'antes': dict(before_counts)}, ensure_ascii=True))
    if not args.apply:
        print('Inspección completada; usa --apply para respaldar y cargar los datos.')
        return

    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    backup_dir = ROOT / '.local' / 'backups' / 'datos_demo'
    backup_dir.mkdir(parents=True, exist_ok=True)
    backup = backup_dir / f'{args.engine}_{args.database}_{stamp}.sql'
    dump = ([binary('pg_dump'), *connection, '--no-owner', '--no-acl', args.database] if pg else
            [binary('mysqldump'), *connection, '--default-character-set=utf8mb4',
             '--single-transaction', '--routines', '--triggers', '--no-tablespaces',
             '--set-gtid-purged=OFF', args.database])
    with backup.open('xb') as output:
        result = subprocess.run(dump, stdout=output, stderr=subprocess.PIPE, env=env)
    if result.returncode or backup.stat().st_size == 0:
        raise RuntimeError('Falló el respaldo; no se insertó nada: ' + result.stderr.decode('utf-8', errors='replace'))

    consolidated = (ROOT / 'database' / 'scripts' / f'pos_{args.engine}.sql').read_text(encoding='utf-8')
    if consolidated.count(SEED_MARKER) != 1:
        raise RuntimeError('La sección 12 no se encontró una sola vez; no se insertó nada')
    run(SEED_MARKER + consolidated.split(SEED_MARKER, 1)[1])
    after, after_counts = snapshot()
    if before - after:
        raise RuntimeError('Verificación: cambió una fila original. Revisa el respaldo: ' + str(backup))
    allowed = {'categoria', 'marca', 'proveedor', 'clientes', 'empresa', 'areas', 'mesas', 'producto', 'tipo_comprobante'}
    if any(table not in allowed for table, digest in (after - before)):
        raise RuntimeError('Verificación: se modificó una tabla fuera del catálogo')
    report = {'motor': args.engine, 'base': args.database, 'respaldo': str(backup),
              'agregados': {t: after_counts[t] - before_counts[t] for t in sorted(allowed)},
              'totales': dict(after_counts), 'filas_originales_preservadas': True,
              'usuarios_y_contrasenas_preservados': True}
    evidence = backup.with_suffix('.json')
    evidence.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=True))


if __name__ == '__main__':
    main()
