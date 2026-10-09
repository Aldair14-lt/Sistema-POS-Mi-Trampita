"""Flujo web público real. Ejecutar SOLO contra backends QA con --qa y POS_TEST_PASSWORD."""
import argparse
import http.cookiejar
import json
import os
import urllib.error
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--url', required=True)
parser.add_argument('--qa', action='store_true', required=True)
args = parser.parse_args()
assert args.qa and args.url.startswith(('http://127.0.0.1:', 'http://localhost:')), 'Usar un backend QA local'
password = os.environ['POS_TEST_PASSWORD']

class Client:
    def __init__(self, public=False):
        self.public = public
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
    def call(self, method, path, data=None, expected=200):
        headers = {'Content-Type': 'application/json'}
        if method != 'GET' and not self.public:
            csrf = self.call('GET', '/api/auth/csrf')
            headers[csrf['headerName']] = csrf['token']
        request = urllib.request.Request(args.url + path, method=method, headers=headers,
            data=json.dumps(data).encode() if data is not None else None)
        try:
            with self.opener.open(request, timeout=20) as response:
                status, body = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, body = error.code, error.read()
        assert status == expected, (method, path, status, body.decode())
        return json.loads(body) if body else None

admin, customer = Client(), Client(public=True)
admin.call('POST', '/api/auth/login', {'usuario': 'admin', 'contrasena': password})
suffix = uuid.uuid4().hex[:8]
number = int(suffix, 16) % 100000000
company = admin.call('POST', '/api/configuracion', {'ruc': '20'+str(number).zfill(8)+'1',
    'razonSocial': 'RECREO QA WEB '+suffix, 'nombreComercial': 'Recreo Mi Trampita',
    'direccion': 'Av. Los Jardines 123', 'telefono': '999888777'}, expected=201)
slug = 'qa-web-'+suffix
admin.call('PUT', f"/api/web/configuracion/{company['id']}", {'slug': slug, 'activa': True, 'recojo': True, 'delivery': True, 'mensaje': 'Recojo y delivery'})
provider = admin.call('POST', '/api/proveedores', {'rucDni': '10'+str(number).zfill(8)+'1', 'razonSocial': 'Proveedor web QA'}, expected=201)
category = admin.call('GET', '/api/categorias')[0]
products = []
for index, area in enumerate(['COCINA', 'BAR', 'COCINA']):
    products.append(admin.call('POST', '/api/productos', {'categoria': {'id': category['id']}, 'proveedor': {'id': provider['id']},
        'codigoBarras': 'QA-WEB-'+suffix+'-'+str(index), 'nombre': 'Producto web '+suffix+' '+str(index),
        'precioCompra': 1, 'precioVenta': 10, 'stockActual': 5, 'stockMinimo': 0, 'visibleWeb': index < 2, 'areaDestino': area}, expected=201))
base = '/api/public/tiendas/'+slug
menu = customer.call('GET', base+'/menu')
menu_ids = {p['id'] for p in menu['productos']}
assert products[0]['id'] in menu_ids and products[2]['id'] not in menu_ids
assert not {'precioCompra', 'proveedor', 'stockActual'} & set(next(p for p in menu['productos'] if p['id'] == products[0]['id']))
payload = {'claveOperacion': str(uuid.uuid4()), 'nombre': 'Comprador web QA', 'dni': str(number).zfill(8), 'telefono': '999888777',
    'tipoEntrega': 'DELIVERY', 'direccion': 'Av. QA 123', 'observaciones': 'Llamar al llegar',
    'items': [{'productoId': p['id'], 'cantidad': 1, 'observaciones': 'Sin hielo' if p['areaDestino'] == 'BAR' else 'Sin aji'} for p in products[:2]],
    'totalEsperado': 23.60, 'total': 0, 'pagoInicial': {'monto': 1000}}
received = customer.call('POST', base+'/pedidos', payload, expected=201)
assert received['estado'] == 'PENDIENTE' and float(received['total']) == 23.60
assert customer.call('POST', base+'/pedidos', payload, expected=201)['codigoSeguimiento'] == received['codigoSeguimiento']
altered = dict(payload, nombre='Otro comprador')
customer.call('POST', base+'/pedidos', altered, expected=409)
assert not list(customer.jar), 'El API público no debe crear una sesión de Caja'
stock = lambda product: next(p for p in admin.call('GET', '/api/productos') if p['id'] == product['id'])['stockActual']
assert stock(products[0]) == 5
request = next(s for s in admin.call('GET', '/api/web/solicitudes') if s['referencia'] == received['referencia'])
receipt = next(t for t in admin.call('GET', '/api/tipos-comprobante') if t['nombre'] == 'BOLETA')
sale = admin.call('PATCH', f"/api/web/solicitudes/{request['id']}/aceptar", {'tipoComprobanteId': receipt['id']})
assert sale['origenPedido'] == 'WEB' and not sale['pagos'] and float(sale['saldoPendiente']) == 23.60
assert sale['observacionesPedido'] == 'Llamar al llegar'
assert admin.call('PATCH', f"/api/web/solicitudes/{request['id']}/aceptar", {'tipoComprobanteId': receipt['id']})['id'] == sale['id']
assert stock(products[0]) == 4
status_path = base+'/pedidos/'+received['codigoSeguimiento']
assert customer.call('GET', status_path)['estado'] == 'ACEPTADO'
for item in sale['detalles']:
    station = 'bar' if item['areaDestino'] == 'BAR' else 'cocina'
    for previous, state in [('PENDIENTE','PREPARANDO'),('PREPARANDO','LISTO')]:
        admin.call('PATCH', f"/api/{station}/items/{item['id']}/estado", {'estado': state, 'estadoActual': previous})
assert customer.call('GET', status_path)['estado'] == 'LISTO'
for item in sale['detalles']:
    admin.call('PATCH', f"/api/ventas/items/{item['id']}/servir", {'estado': 'SERVIDO', 'estadoActual': 'LISTO'})
assert customer.call('GET', status_path)['estado'] == 'ENTREGADO'
for path in ['/api/web/solicitudes', '/api/web/configuracion', '/api/productos']:
    customer.call('GET', path, expected=401)
payload['claveOperacion'] = str(uuid.uuid4())
rejected = customer.call('POST', base+'/pedidos', payload, expected=201)
pending = next(s for s in admin.call('GET', '/api/web/solicitudes') if s['referencia'] == rejected['referencia'])
admin.call('PATCH', f"/api/web/solicitudes/{pending['id']}/rechazar", {'motivo': 'Fuera de cobertura'}, expected=204)
assert customer.call('GET', base+'/pedidos/'+rejected['codigoSeguimiento'])['motivo'] == 'Fuera de cobertura'
print('PASS web publico: menu filtrado, precios de servidor, sin sesion, reintentos, confirmacion unica, stock, KDS, seguimiento, rechazo y acceso privado protegido.')
