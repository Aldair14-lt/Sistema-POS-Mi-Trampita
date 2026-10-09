"""CRUD, validación y permisos de catálogos/usuarios/mesas contra un backend QA.

Requiere --qa y POS_TEST_PASSWORD. No ejecutar contra pos_db.
Complementa verify_http_flow.py y verify_public_orders.py.
"""
import argparse
import csv
import datetime
import http.cookiejar
import io
import json
import os
from pathlib import Path
import urllib.error
import urllib.request
import uuid

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--url', required=True)
parser.add_argument('--qa', action='store_true', required=True)
parser.add_argument('--report', required=True)
args = parser.parse_args()
assert args.url.startswith(('http://127.0.0.1:', 'http://localhost:')), 'Usar backend QA local'
password = os.environ['POS_TEST_PASSWORD']
checks = []


class Client:
    def __init__(self):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(self, method, path, data=None, expected=200, csrf=True, raw=False):
        headers = {'Content-Type': 'application/json'}
        if method != 'GET' and csrf:
            token = self.call('GET', '/api/auth/csrf')
            headers[token['headerName']] = token['token']
        request = urllib.request.Request(args.url + path, method=method, headers=headers,
            data=None if data is None else json.dumps(data).encode('utf-8'))
        try:
            with self.opener.open(request, timeout=20) as response:
                status, body = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, body = error.code, error.read()
        assert status == expected, (method, path, status, body.decode('utf-8'))
        checks.append({'method': method, 'path': path, 'status': status})
        return body.decode('utf-8') if raw else (json.loads(body) if body else None)

    def login(self, user='admin', secret=password):
        return self.call('POST', '/api/auth/login', {'usuario': user, 'contrasena': secret})


admin, guest = Client(), Client()
guest.call('GET', '/api/productos', expected=401)
guest.call('POST', '/api/auth/login', {}, expected=400)
guest.call('POST', '/api/auth/login', {'usuario': 'qa_inexistente', 'contrasena': password}, expected=401)
assert 'ADMIN' in admin.login()['roles']
assert admin.call('GET', '/api/auth/me')['usuario'] == 'admin'
roles = {r['nombre']: r['id'] for r in admin.call('GET', '/api/roles')}
assert set(roles) == {'ADMIN', 'MOZO', 'CAJA', 'COCINERO', 'BARTENDER'}
for user in admin.call('GET', '/api/usuarios'):
    assert not any(key in user for key in ['contrasena', 'contraseña', 'pinCaja'])
catalog = admin.call('GET', '/api/productos')
demo = [p for p in catalog if p['codigoBarras'].startswith('DEMO-')]
assert len(demo) == 24 and sum(p['areaDestino'] == 'BAR' for p in demo) == 8
assert all(p['categoria'] and p['marca'] and p['proveedor'] for p in demo)
assert all(not p['visibleWeb'] and p['precioVenta'] > 0 and p['stockActual'] > 0 for p in demo)

suffix = uuid.uuid4().hex[:10]
fixtures = [
    ('categorias', {'nombre': 'Categoría QA ' + suffix, 'descripcion': 'Prueba'}, {'nombre': ''}),
    ('marcas', {'nombre': 'Marca QA ' + suffix}, {'nombre': ''}),
    ('proveedores', {'rucDni': 'QA-' + suffix, 'razonSocial': 'Proveedor QA', 'correo': 'qa@example.com'},
                   {'rucDni': 'QA-' + suffix, 'razonSocial': 'Proveedor', 'correo': 'invalido'}),
    ('clientes', {'numeroDocumento': 'QA-' + suffix, 'nombresRazonSocial': 'Cliente QA',
                  'fechaNacimiento': '1995-10-09'}, {'numeroDocumento': 'Otro', 'nombresRazonSocial': ''}),
    ('configuracion', {'ruc': 'QA-' + suffix, 'razonSocial': 'Empresa QA', 'direccion': 'Dirección QA'}, {'ruc': ''}),
    ('tipos-comprobante', {'nombre': 'NOTA DE VENTA', 'serie': suffix[:8], 'descripcion': 'Prueba'},
                         {'nombre': 'DOCUMENTO INVALIDO', 'serie': 'QAINV'})
]
created = {}
for resource, payload, invalid in fixtures:
    path = '/api/' + resource
    admin.call('POST', path, invalid, expected=400)
    admin.call('POST', path, payload, expected=403, csrf=False)
    original = admin.call('POST', path, payload, expected=201)
    created[resource] = original
    assert any(r['id'] == original['id'] for r in admin.call('GET', path))
    edited = dict(payload)
    key = 'descripcion' if resource in ['categorias', 'tipos-comprobante'] else (
        'nombre' if resource == 'marcas' else 'nombresRazonSocial' if resource == 'clientes' else 'razonSocial')
    edited[key] = 'Registro editado QA ' + suffix
    result = admin.call('PUT', path + '/' + str(original['id']), edited)
    assert result[key] == edited[key]
    admin.call('PUT', path + '/2147483647', edited, expected=404)

customer_path = '/api/clientes/' + str(created['clientes']['id'])
future = (datetime.date.today() + datetime.timedelta(days=3)).isoformat()
admin.call('PUT', customer_path, {**fixtures[3][1], 'fechaNacimiento': future}, expected=400)
metrics = admin.call('PUT', customer_path, {**fixtures[3][1], 'frecuenciaVisitas': 999, 'totalGastado': 99999})
assert metrics['frecuenciaVisitas'] == 0 and metrics['totalGastado'] == 0
admin.call('POST', '/api/clientes', fixtures[3][1], expected=409)

product_payload = {'categoria': {'id': created['categorias']['id']}, 'marca': {'id': created['marcas']['id']},
    'proveedor': {'id': created['proveedores']['id']}, 'codigoBarras': 'QA-CRUD-' + suffix,
    'nombre': 'Producto QA ' + suffix, 'precioCompra': 2, 'precioVenta': 5, 'stockActual': 10,
    'stockMinimo': 2, 'areaDestino': 'COCINA', 'visibleWeb': False}
for field, value in [('stockActual', -1), ('precioVenta', -1), ('areaDestino', 'INVALIDO'),
                     ('categoria', {'id': 0}), ('proveedor', None)]:
    admin.call('POST', '/api/productos', {**product_payload, field: value}, expected=400)
product = admin.call('POST', '/api/productos', product_payload, expected=201)
product_path = '/api/productos/' + str(product['id'])
assert admin.call('GET', product_path)['nombre'] == product_payload['nombre']
admin.call('POST', '/api/productos', product_payload, expected=409)
for resource in ['categorias', 'marcas', 'proveedores']:
    admin.call('DELETE', '/api/' + resource + '/' + str(created[resource]['id']), expected=409)
admin.call('PUT', product_path, product_payload, expected=400)
edited_product = admin.call('PUT', product_path, {**product_payload, 'version': product['version'], 'precioVenta': 7})
assert edited_product['precioVenta'] == 7 and edited_product['version'] > product['version']
admin.call('PUT', product_path, {**product_payload, 'version': product['version']}, expected=409)
assert admin.call('GET', product_path)['precioVenta'] == 7

area = admin.call('POST', '/api/areas', {'nombre': 'Área QA ' + suffix, 'estado': 'ACTIVA'}, expected=201)
area_path = '/api/areas/' + str(area['id'])
admin.call('POST', '/api/areas', {'nombre': area['nombre'], 'estado': 'ACTIVA'}, expected=409)
number = max(m['numero'] for m in admin.call('GET', '/api/mesas')) + 1
table_payload = {'numero': number, 'capacidad': 4, 'areaId': area['id']}
for field, value in [('numero', 0), ('capacidad', 101), ('areaId', None)]:
    admin.call('POST', '/api/mesas', {**table_payload, field: value}, expected=400)
table = admin.call('POST', '/api/mesas', table_payload, expected=201)
table_path = '/api/mesas/' + str(table['id'])
assert len(admin.call('GET', '/api/mesas?areaId=' + str(area['id']))) == 1
admin.call('POST', '/api/mesas', table_payload, expected=409)
assert admin.call('PUT', table_path, {**table_payload, 'capacidad': 6})['capacidad'] == 6
admin.call('DELETE', area_path, expected=409)
assert admin.call('PATCH', table_path + '/abrir', {})['estado'] == 'OCUPADA'
admin.call('PUT', table_path, table_payload, expected=409)
admin.call('PUT', area_path, {'nombre': area['nombre'], 'estado': 'INACTIVA'}, expected=409)
admin.call('DELETE', table_path, expected=409)
assert admin.call('PATCH', table_path + '/liberar', {})['estado'] == 'LIBRE'
admin.call('PUT', area_path, {'nombre': area['nombre'], 'estado': 'INACTIVA'})
admin.call('PATCH', table_path + '/abrir', {}, expected=409)
admin.call('PUT', area_path, {'nombre': area['nombre'], 'estado': 'ACTIVA'})

for path in ['/api/marketing', '/api/marketing/frecuentes', '/api/marketing/cumpleaneros?mes=10',
             '/api/marketing/proximos-cumpleaneros?dias=30', '/api/marketing/inactivos?dias=60',
             '/api/marketing/mejores', '/api/marketing/clientes/' + str(created['clientes']['id']) + '/consumo']:
    admin.call('GET', path)
for path in ['/api/marketing/cumpleaneros?mes=13', '/api/marketing/proximos-cumpleaneros?dias=0',
             '/api/marketing/inactivos?dias=-1', '/api/marketing/exportar.csv?segmento=invalido']:
    admin.call('GET', path, expected=400)
for segment in ['todos', 'cumpleaneros', 'inactivos', 'mejores']:
    export = admin.call('GET', '/api/marketing/exportar.csv?segmento=' + segment, raw=True)
    parsed = list(csv.reader(io.StringIO(export)))
    assert parsed and parsed[0] == ['email', 'phone', 'country']
    assert all(len(row) == 3 for row in parsed)

user_payload = {'usuario': 'qa_crud_' + suffix, 'contrasena': password, 'nombreCompleto': 'Usuario QA',
                'estado': 'activo', 'rolIds': [roles['MOZO']]}
admin.call('POST', '/api/usuarios', {**user_payload, 'contrasena': '123'}, expected=400)
admin.call('POST', '/api/usuarios', {**user_payload, 'rolIds': [roles['COCINERO'], roles['CAJA']]}, expected=400)
user = admin.call('POST', '/api/usuarios', user_payload, expected=201)
user_path = '/api/usuarios/' + str(user['id'])
admin.call('POST', '/api/usuarios', user_payload, expected=409)
employee = Client()
employee.login(user['usuario'])
employee.call('GET', '/api/pos/clientes')
employee.call('GET', '/api/pos/configuracion')
employee.call('GET', '/api/usuarios', expected=403)
employee.call('GET', '/api/marketing', expected=403)
for state in ['inactivo', 'bloqueado']:
    admin.call('PUT', user_path, {**user_payload, 'contrasena': '', 'estado': state})
    employee.call('GET', '/api/auth/me', expected=401)
    denied = Client()
    denied.call('POST', '/api/auth/login', {'usuario': user['usuario'], 'contrasena': password}, expected=401)
admin.call('PUT', user_path, {**user_payload, 'contrasena': '', 'rolIds': [roles['CAJA']]})
assert 'CAJA' in employee.login(user['usuario'])['roles']
employee.call('GET', '/api/caja/resumen')
employee.call('POST', '/api/auth/logout', expected=204)
employee.call('GET', '/api/auth/me', expected=401)

for path in [product_path, table_path, area_path, user_path]:
    admin.call('DELETE', path, expected=204)
    admin.call('DELETE', path, expected=404)
for resource in reversed(list(created)):
    path = '/api/' + resource + '/' + str(created[resource]['id'])
    admin.call('DELETE', path, expected=204)
    admin.call('DELETE', path, expected=404)
admin.call('GET', product_path, expected=404)
assert len([p for p in admin.call('GET', '/api/productos') if p['codigoBarras'].startswith('DEMO-')]) == 24
report = Path(args.report)
report.parent.mkdir(parents=True, exist_ok=True)
report.write_text(json.dumps({'passed': True, 'url': args.url, 'requests': len(checks),
    'modules': ['catálogos CRUD', 'productos e inventario', 'áreas y mesas', 'marketing y CSV',
                'usuarios, estados y roles', 'sesión, CSRF y logout', 'datos SQL de ejemplo'],
    'checks': checks}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'PASS catalogos: {len(checks)} solicitudes; CRUD, validaciones, permisos, inventario, mesas, marketing y usuarios.')
