"""Prueba HTTP con datos desechables. Ejecutar SOLO sobre una base de pruebas.
Usa POS_TEST_PASSWORD y --url; conserva los registros creados para inspección.
No requiere dependencias externas.
"""
import argparse
import datetime
import http.cookiejar
import json
import os
import urllib.error
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument("--url", required=True)
parser.add_argument("--user", default="admin")
args = parser.parse_args()
password = os.environ["POS_TEST_PASSWORD"]

class Client:
    def __init__(self):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(self, method, path, data=None, expected=200):
        headers = {"Content-Type": "application/json"}
        if method != "GET":
            token = self.call("GET", "/api/auth/csrf")
            headers[token["headerName"]] = token["token"]
        request = urllib.request.Request(args.url + path, headers=headers, method=method,
            data=None if data is None else json.dumps(data).encode("utf-8"))
        try:
            with self.opener.open(request, timeout=20) as response:
                status, body = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, body = error.code, error.read()
        assert status == expected, (method, path, status, body.decode("utf-8"))
        return json.loads(body) if body else None

    def login(self, username, secret):
        return self.call("POST", "/api/auth/login", {"usuario": username, "contrasena": secret})

suffix = uuid.uuid4().hex[:10]
admin = Client()
admin.login(args.user, password)
assert Client().call("GET", "/api/mesas", expected=401)["status"] == 401
role_ids = {role["nombre"]: role["id"] for role in admin.call("GET", "/api/roles")}
users = {}
for role in ("MOZO", "CAJA", "COCINERO"):
    username = "qa_" + role.lower() + "_" + suffix
    admin.call("POST", "/api/usuarios", {"usuario": username, "contrasena": password,
        "nombreCompleto": "QA " + role, "estado": "activo", "rolIds": [role_ids[role]]}, expected=201)
    users[role] = Client()
    users[role].login(username, password)
mozo, caja, cook = users["MOZO"], users["CAJA"], users["COCINERO"]
mozo.call("GET", "/api/marketing", expected=403)
caja.call("GET", "/api/configuracion", expected=403)
caja.call("POST", "/api/ventas", {}, expected=403)

area = admin.call("POST", "/api/areas", {"nombre": "QA " + suffix, "estado": "ACTIVA"}, expected=201)
mesa = admin.call("POST", "/api/mesas", {"numero": max(m["numero"] for m in admin.call("GET", "/api/mesas")) + 1,
    "capacidad": 4, "areaId": area["id"]}, expected=201)
assert mozo.call("PATCH", f"/api/mesas/{mesa['id']}/abrir", {})["estado"] == "OCUPADA"
company = admin.call("POST", "/api/configuracion", {"ruc": str(int(suffix, 16)), "razonSocial": "Empresa QA " + suffix,
    "direccion": "Dirección de prueba"}, expected=201)
client = admin.call("POST", "/api/clientes", {"numeroDocumento": str(int(suffix, 16)),
    "nombresRazonSocial": "Cliente QA " + suffix, "fechaNacimiento": datetime.date.today().replace(year=2000).isoformat()}, expected=201)
provider = admin.call("POST", "/api/proveedores", {"rucDni": str(int(suffix, 16)), "razonSocial": "Proveedor QA"}, expected=201)
category = admin.call("GET", "/api/categorias")[0]
brand = admin.call("GET", "/api/marcas")[0]
products = []
for index in range(2):
    products.append(admin.call("POST", "/api/productos", {"categoria": {"id": category["id"]}, "marca": {"id": brand["id"]},
        "proveedor": {"id": provider["id"]}, "codigoBarras": f"QA-{suffix}-{index}", "nombre": f"Producto QA {index}",
        "precioCompra": 1, "precioVenta": 10, "stockActual": 5, "stockMinimo": 0}, expected=201))
receipt = next(r for r in admin.call("GET", "/api/tipos-comprobante") if r["nombre"] == "BOLETA")
payload = {"empresaId": company["id"], "clienteId": client["id"], "tipoComprobanteId": receipt["id"],
    "numeroComprobante": "QA-" + suffix, "mesaId": mesa["id"], "items": [{"productoId": p["id"], "cantidad": 2} for p in products]}
sale = mozo.call("POST", "/api/ventas", payload, expected=201)
assert sale["mesa"]["estado"] == "ATENDIENDO"
mozo.call("PATCH", f"/api/ventas/{sale['id']}/cerrar", {"metodoPago": "tarjeta"}, expected=403)
caja.call("PATCH", f"/api/mesas/{mesa['id']}/liberar", {}, expected=409)
def stock(product):
    return next(p for p in admin.call("GET", "/api/productos") if p["id"] == product["id"])["stockActual"]

assert all(stock(p) == 3 for p in products)
# El primer descuento de un adicional debe revertirse si la segunda línea no tiene stock.
mozo.call("PATCH", f"/api/ventas/{sale['id']}/items", {"items": [
    {"productoId": products[0]["id"], "cantidad": 1}, {"productoId": products[1]["id"], "cantidad": 4}]}, expected=409)
assert all(stock(p) == 3 for p in products)
sale = mozo.call("PATCH", f"/api/ventas/{sale['id']}/items", {"items": [{"productoId": products[0]["id"], "cantidad": 1}]})
assert len(sale["detalles"]) == 3 and stock(products[0]) == 2
for path in ("/api/ventas/abiertas", "/api/pedidos-online", "/api/mesas", "/api/productos", "/api/marketing"):
    cook.call("GET", path, expected=403)
cook.call("POST", f"/api/ventas/{sale['id']}/pagos", {}, expected=403)
mozo.call("GET", "/api/cocina/items", expected=403)
queue = cook.call("GET", "/api/cocina/items")
assert all("total" not in item and "cliente" not in item for item in queue)
assert any(item["mesaNumero"] == mesa["numero"] for item in queue)


def deliver(order):
    for item in order["detalles"]:
        path = f"/api/cocina/items/{item['id']}/estado"
        cook.call("PATCH", path, {"estado": "LISTO", "estadoActual": "PENDIENTE"}, expected=409)
        cook.call("PATCH", path, {"estado": "EN_PREPARACION", "estadoActual": "PENDIENTE"})
        cook.call("PATCH", path, {"estado": "LISTO", "estadoActual": "PENDIENTE"}, expected=409)
        cook.call("PATCH", path, {"estado": "LISTO", "estadoActual": "EN_PREPARACION"})
        ready = caja.call("GET", f"/api/ventas/{order['id']}")
        assert next(d for d in ready["detalles"] if d["id"] == item["id"])["estadoPreparacion"] == "LISTO"
        caja.call("PATCH", f"/api/ventas/items/{item['id']}/servir", {"estado": "SERVIDO", "estadoActual": "LISTO"})


def payment(amount, method="tarjeta", received=None):
    return {"monto": amount, "metodoPago": method, "montoRecibido": received, "claveOperacion": str(uuid.uuid4())}

caja.call("PATCH", f"/api/ventas/{sale['id']}/cerrar", {}, expected=409)
mozo.call("POST", f"/api/ventas/{sale['id']}/pagos", payment(20), expected=403)
first_payment = payment(20, "efectivo", 25)
sale = caja.call("POST", f"/api/ventas/{sale['id']}/pagos", first_payment, expected=201)
assert sale["estadoCuenta"] == "PAGADA_PARCIALMENTE" and float(sale["saldoPendiente"]) == 39
assert float(sale["pagos"][0]["vuelto"]) == 5
sale = caja.call("POST", f"/api/ventas/{sale['id']}/pagos", first_payment, expected=201)
assert len(sale["pagos"]) == 1
caja.call("POST", f"/api/ventas/{sale['id']}/pagos", payment(40), expected=409)
caja.call("POST", f"/api/ventas/{sale['id']}/pagos", payment(5, "efectivo", 1), expected=400)
sale = caja.call("POST", f"/api/ventas/{sale['id']}/pagos", payment(39), expected=201)
assert sale["estadoCuenta"] == "CERRADA" and sale["estado"] == "ABIERTA"
caja.call("PATCH", f"/api/ventas/{sale['id']}/cerrar", {}, expected=409)
deliver(sale)
closed = caja.call("PATCH", f"/api/ventas/{sale['id']}/cerrar", {})
assert closed["estado"] == "CERRADA" and closed["mesa"]["estado"] == "LIBRE"
assert stock(products[0]) == 2 and stock(products[1]) == 3
caja.call("PATCH", f"/api/ventas/{sale['id']}/cerrar", {}, expected=409)
stats = next(c for c in admin.call("GET", "/api/clientes") if c["id"] == client["id"])
assert stats["frecuenciaVisitas"] == 1 and float(stats["totalGastado"]) == 59
assert len(admin.call("GET", f"/api/marketing/clientes/{client['id']}/consumo")) == 2
assert any(c["id"] == client["id"] for c in admin.call("GET", "/api/marketing/cumpleaneros"))
stats["frecuenciaVisitas"] = 999
stats["totalGastado"] = 999
updated = admin.call("PUT", f"/api/clientes/{client['id']}", stats)
assert updated["frecuenciaVisitas"] == 1 and float(updated["totalGastado"]) == 59

for index, initial in enumerate((None, 5, 11.80)):
    online = {"empresaId": company["id"], "clienteId": client["id"], "tipoComprobanteId": receipt["id"],
        "numeroComprobante": f"QA-ON-{suffix}-{index}", "tipoEntrega": "DELIVERY", "direccionEnvio": "Destino QA",
        "items": [{"productoId": products[1]["id"], "cantidad": 1}], "pagoTotal": initial == 11.80,
        "pagoInicial": None if initial is None else payment(initial)}
    mozo.call("POST", "/api/pedidos-online", online, expected=403)
    order = caja.call("POST", "/api/pedidos-online", online, expected=201)
    assert order["mesa"] is None and order["origenPedido"] == "ONLINE" and order["estado"] == "ABIERTA"
    assert order["estadoCuenta"] == ("ABIERTA" if initial is None else "PAGADA_PARCIALMENTE" if initial == 5 else "CERRADA")
    assert any(o["id"] == order["id"] for o in caja.call("GET", "/api/pedidos-online"))
    assert any(item["ventaId"] == order["id"] and item["origenPedido"] == "ONLINE" for item in cook.call("GET", "/api/cocina/items"))
    if initial == 11.80:
        deliver(order)
        caja.call("PATCH", f"/api/ventas/{order['id']}/cerrar", {})
        assert not any(o["id"] == order["id"] for o in caja.call("GET", "/api/pedidos-online"))
assert stock(products[1]) == 0
# Sobrepago inicial: se revierte la venta y el descuento.
invalid = {**online, "numeroComprobante": f"QA-INVALID-{suffix}", "items": [{"productoId": products[0]["id"], "cantidad": 1}],
    "pagoTotal": False, "pagoInicial": payment(12)}
caja.call("POST", "/api/pedidos-online", invalid, expected=409)
assert stock(products[0]) == 2
admin.call("POST", "/api/auth/logout", {}, expected=204)
admin.call("GET", "/api/mesas", expected=401)
print("PASS HTTP: sesión/CSRF, roles incluidos cocina, stock al pedir, rollback, abonos, reintentos, cierre, KDS, online flexible y fidelización.")
