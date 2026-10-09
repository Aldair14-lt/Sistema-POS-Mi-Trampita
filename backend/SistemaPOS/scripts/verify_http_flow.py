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
import concurrent.futures
import threading

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
for role in ("MOZO", "CAJA", "COCINERO", "BARTENDER"):
    username = "qa_" + role.lower() + "_" + suffix
    admin.call("POST", "/api/usuarios", {"usuario": username, "contrasena": password,
        "nombreCompleto": "QA " + role, "estado": "activo", "rolIds": [role_ids[role]]}, expected=201)
    users[role] = Client()
    users[role].login(username, password)
mozo, caja, cook = users["MOZO"], users["CAJA"], users["COCINERO"]
bar = users["BARTENDER"]
bar.call("GET", "/api/cocina/items", expected=403)
cook.call("GET", "/api/bar/items", expected=403)
mozo.call("GET", "/api/marketing", expected=403)
caja.call("GET", "/api/configuracion", expected=403)
caja.call("POST", "/api/ventas", {}, expected=400)

area = admin.call("POST", "/api/areas", {"nombre": "QA " + suffix, "estado": "ACTIVA"}, expected=201)
mesa = admin.call("POST", "/api/mesas", {"numero": max(m["numero"] for m in admin.call("GET", "/api/mesas")) + 1,
    "capacidad": 4, "areaId": area["id"]}, expected=201)
assert mozo.call("PATCH", f"/api/mesas/{mesa['id']}/abrir", {})["estado"] == "OCUPADA"
company = admin.call("POST", "/api/configuracion", {"ruc": str(int(suffix, 16)), "razonSocial": "Empresa QA " + suffix,
    "direccion": "Dirección de prueba"}, expected=201)
turno = caja.call("POST", "/api/caja/sesiones/abrir", {"empresaId": company["id"], "montoInicial": 100, "claveOperacion": str(uuid.uuid4())})
client = admin.call("POST", "/api/clientes", {"numeroDocumento": str(int(suffix, 16)),
    "nombresRazonSocial": "Cliente QA " + suffix, "fechaNacimiento": datetime.date.today().replace(year=2000).isoformat()}, expected=201)
provider = admin.call("POST", "/api/proveedores", {"rucDni": str(int(suffix, 16)), "razonSocial": "Proveedor QA"}, expected=201)
category = admin.call("GET", "/api/categorias")[0]
brand = admin.call("GET", "/api/marcas")[0]
products = []
for index in range(2):
    products.append(admin.call("POST", "/api/productos", {"categoria": {"id": category["id"]}, "marca": {"id": brand["id"]},
        "proveedor": {"id": provider["id"]}, "codigoBarras": f"QA-{suffix}-{index}", "nombre": f"Producto QA {index}",
        "precioCompra": 1, "precioVenta": 10, "stockActual": 5, "stockMinimo": 0, "areaDestino": "COCINA" if index == 0 else "BAR"}, expected=201))
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
mozo.call("PATCH", f"/api/ventas/{sale['id']}/items", {"claveOperacion": str(uuid.uuid4()), "items": [
    {"productoId": products[0]["id"], "cantidad": 1}, {"productoId": products[1]["id"], "cantidad": 4}]}, expected=409)
assert all(stock(p) == 3 for p in products)
additional = {"claveOperacion": str(uuid.uuid4()), "items": [{"productoId": products[0]["id"], "cantidad": 1}]}
sale = mozo.call("PATCH", f"/api/ventas/{sale['id']}/items", additional)
sale = mozo.call("PATCH", f"/api/ventas/{sale['id']}/items", additional)
assert len(sale["detalles"]) == 3 and stock(products[0]) == 2
assert len([d for d in sale['detalles'] if d['claveComanda'] == additional['claveOperacion']]) == 1
for path in ("/api/caja/resumen", "/api/operacion/eventos", "/api/ventas/abiertas", "/api/pedidos-online", "/api/mesas", "/api/productos", "/api/marketing"):
    cook.call("GET", path, expected=403)
cook.call("POST", f"/api/ventas/{sale['id']}/pagos", {}, expected=403)
cook.call("POST", f"/api/ventas/{sale['id']}/cobrar", {}, expected=403)
mozo.call("GET", "/api/cocina/items", expected=403)
queue = cook.call("GET", "/api/cocina/items")
assert all("total" not in item and "cliente" not in item for item in queue)
assert any(item["mesaNumero"] == mesa["numero"] for item in queue)


def deliver(order):
    for item in order["detalles"]:
        ruta, station = ("bar", bar) if item["areaDestino"] == "BAR" else ("cocina", cook)
        path = f"/api/{ruta}/items/{item['id']}/estado"
        station.call("PATCH", path, {"estado": "LISTO", "estadoActual": "PENDIENTE"}, expected=409)
        station.call("PATCH", path, {"estado": "PREPARANDO", "estadoActual": "PENDIENTE"})
        station.call("PATCH", path, {"estado": "LISTO", "estadoActual": "PENDIENTE"}, expected=409)
        station.call("PATCH", path, {"estado": "LISTO", "estadoActual": "PREPARANDO"})
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
sale = mozo.call("PATCH", f"/api/ventas/{sale['id']}/solicitar-cuenta", {})
assert sale["cuentaSolicitada"]
assert any(v["id"] == sale["id"] for v in caja.call("GET", "/api/caja/resumen")["mesasPorCobrar"])
closed = caja.call("PATCH", f"/api/ventas/{sale['id']}/cerrar", {})
assert closed["estado"] == "CERRADA" and closed["mesa"]["estado"] == "LIBRE"
assert closed["comprobante"]["numero"].startswith(receipt["serie"] + "-")
assert len(closed["comprobante"]["detalles"]) == 3
assert caja.call("POST", f"/api/ventas/{sale['id']}/cobrar", {})["comprobante"]["id"] == closed["comprobante"]["id"]
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
    assert any(item["ventaId"] == order["id"] and item["origenPedido"] == "ONLINE" for item in bar.call("GET", "/api/bar/items"))
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
# SSE: la cocina recibe solo una invalidación tras confirmar la comanda.
def wait_event(client, path, started):
    request = urllib.request.Request(args.url + path, headers={"Accept": "text/event-stream"})
    with client.opener.open(request, timeout=15) as response:
        assert response.status == 200 and response.headers["Content-Type"].startswith("text/event-stream")
        started.set()
        while True:
            line = response.readline().decode().strip()
            if line == "event:actualizar":
                return response.readline().decode().strip()

whatsapp = {"empresaId": company["id"], "clienteId": client["id"], "tipoComprobanteId": receipt["id"],
    "numeroComprobante": f"WA-{suffix}", "tipoEntrega": "DELIVERY", "direccion": "Destino WhatsApp",
    "telefono": "999888777", "items": [{"productoId": products[0]["id"], "cantidad": 1}],
    "pagoInicial": payment(5, "yape"), "pagoTotal": False}
caja.call("POST", "/api/ventas/whatsapp", {**whatsapp, "telefono": ""}, expected=400)
caja.call("POST", "/api/ventas/whatsapp", {**whatsapp, "direccion": ""}, expected=400)
assert stock(products[0]) == 2
started = threading.Event()
with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
    stream = executor.submit(wait_event, cook, "/api/cocina/eventos", started)
    assert started.wait(10), "El stream de cocina no inició"
    order = caja.call("POST", "/api/ventas/whatsapp", whatsapp, expected=201)
    assert stream.result(timeout=15) == "data:1"
assert order["origenPedido"] == "WHATSAPP" and order["telefonoEntrega"] == "999888777"
assert float(order["saldoPendiente"]) == 6.80 and stock(products[0]) == 1
mozo.call("POST", "/api/ventas", {**whatsapp, "origenPedido": "WEB"}, expected=403)
partial = payment(1, "plin")
order = caja.call("POST", f"/api/ventas/{order['id']}/cobrar", {"pago": partial})
assert order["estado"] == "ABIERTA" and order["comprobante"] is None
last_payment = payment(5.80, "yape")
paid = caja.call("POST", f"/api/ventas/{order['id']}/cobrar", {"pago": last_payment})
assert paid["estado"] == "CERRADA" and paid["comprobante"]
retry = caja.call("POST", f"/api/ventas/{order['id']}/cobrar", {"pago": last_payment})
assert retry["comprobante"]["id"] == paid["comprobante"]["id"] and len(retry["pagos"]) == 3
assert any(item["ventaId"] == order["id"] for item in cook.call("GET", "/api/cocina/items"))
assert any(v["id"] == order["id"] for v in caja.call("GET", "/api/caja/resumen")["pedidosOnline"])
deliver(order)
assert not any(v["id"] == order["id"] for v in caja.call("GET", "/api/pedidos-online"))
# La impresión utiliza una fotografía fiscal aunque se edite la empresa.
company["razonSocial"] = "Empresa editada después de emitir"
admin.call("PUT", f"/api/configuracion/{company['id']}", company)
assert caja.call("GET", f"/api/ventas/{order['id']}")["comprobante"]["empresaNombre"] == "Empresa QA " + suffix
web = {**whatsapp, "numeroComprobante": f"WEB-{suffix}", "origenPedido": "WEB", "tipoEntrega": "RECOJO",
    "direccion": None, "pagoInicial": None}
web_order = caja.call("POST", "/api/ventas", web, expected=201)
assert web_order["origenPedido"] == "WEB" and web_order["estadoCuenta"] == "ABIERTA"
assert stock(products[0]) == 0
# Cancelación lógica: idempotente, con stock devuelto y permisos verificados.
cancel_path = f"/api/ventas/items/{web_order['detalles'][0]['id']}/cancelar"
cook.call("PATCH", cancel_path, {"motivo": "Cliente cancela"}, expected=403)
caja.call("PATCH", cancel_path, {"motivo": " "}, expected=400)
canceled = caja.call("PATCH", cancel_path, {"motivo": "Cliente cancela"})
assert canceled["estado"] == "ANULADA" and float(canceled["total"]) == 0
caja.call("PATCH", cancel_path, {"motivo": "Reintento"})
assert stock(products[0]) == 1
mozo.call("GET", "/api/pedidos-online", expected=403)
mozo.call("GET", f"/api/ventas/{web_order['id']}", expected=403)
# Comprobante seleccionado al cobrar; no modifica el cliente de la comanda.
invoice_order = caja.call("POST", "/api/ventas", {**web, "numeroComprobante": f"FISCAL-{suffix}"}, expected=201)
item_id = invoice_order["detalles"][0]["id"]
cook.call("PATCH", f"/api/cocina/items/{item_id}/estado", {"estado": "PREPARANDO", "estadoActual": "PENDIENTE"})
caja.call("PATCH", f"/api/ventas/items/{item_id}/cancelar", {"motivo": "Ya empezó cocina"}, expected=409)
invoice_payment = payment(11.80, "EFECTIVO", 20)
fiscal = {"tipoComprobante": "FACTURA", "factura": {"ruc": "20123456789", "razonSocial": "Empresa fiscal QA", "direccionFiscal": "Av. Prueba 123"}}
invalid_fiscal = {**fiscal, "factura": {**fiscal["factura"], "razonSocial": " "}}
invoice_path = f"/api/ventas/{invoice_order['id']}/cobrar"
caja.call("POST", invoice_path, {"pago": invoice_payment, "facturacion": invalid_fiscal}, expected=400)
assert not caja.call("GET", f"/api/ventas/{invoice_order['id']}")["pagos"]
emitted = caja.call("POST", invoice_path, {"pago": invoice_payment, "facturacion": fiscal})
assert emitted["comprobante"]["tipoComprobante"] == "FACTURA" and emitted["comprobante"]["ruc"] == "20123456789"
assert emitted["comprobante"]["razonSocial"] == "Empresa fiscal QA"
assert emitted["pagos"][0]["metodoPago"] == "EFECTIVO" and float(emitted["pagos"][0]["vuelto"]) == 8.20
retried = caja.call("POST", invoice_path, {"pago": invoice_payment, "facturacion": fiscal})
assert len(retried["pagos"]) == 1 and retried["comprobante"]["id"] == emitted["comprobante"]["id"]
cook.call("PATCH", f"/api/cocina/items/{item_id}/estado", {"estado": "LISTO", "estadoActual": "PREPARANDO"})
assert any(i["id"] == item_id and i["estadoPreparacion"] == "LISTO" for i in cook.call("GET", "/api/cocina/items"))
caja.call("PATCH", f"/api/ventas/items/{item_id}/servir", {"estado": "SERVIDO", "estadoActual": "LISTO"})
assert not any(i["id"] == item_id for i in cook.call("GET", "/api/cocina/items"))
cierre = caja.call("POST", f"/api/caja/sesiones/{turno['id']}/cerrar", {"efectivoDeclarado": 131.8, "observaciones": "QA completo"})
assert float(cierre["totalCobrado"]) == 99.4 and float(cierre["efectivoEsperado"]) == 131.8
assert float(cierre["diferencia"]) == 0
assert caja.call("GET", f"/api/caja/sesiones/actual?empresaId={company['id']}") is None
pendiente = next(o for o in caja.call("GET", "/api/pedidos-online") if float(o["saldoPendiente"]) > 0)
caja.call("POST", f"/api/ventas/{pendiente['id']}/pagos", payment(1), expected=409)
admin.call("GET", "/api/marketing/proximos-cumpleaneros")
admin.call("GET", "/api/marketing/inactivos")
admin.call("GET", "/api/marketing/mejores")
admin.call("POST", "/api/auth/logout", {}, expected=204)
admin.call("GET", "/api/mesas", expected=401)
print("PASS HTTP: sesión/CSRF, roles Cocina/Bar, stock, rollback, abonos, reintentos, arqueo de turnos, KDS, WhatsApp/Web, factura, vuelto, cancelaciones, SSE y marketing.")
