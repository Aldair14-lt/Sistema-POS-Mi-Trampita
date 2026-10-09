# Datos de ejemplo del POS

Los datos están integrados directamente en **los dos scripts SQL**: `database/scripts/pos_postgresql.sql` y `database/scripts/pos_mysql.sql`, en la sección **12. DATOS DE EJEMPLO**. Ejecutar el archivo completo en una base nueva instala el esquema hasta versión 11 y carga el catálogo. En una base existente con versión 11, ejecutar únicamente la sección 12; no repetir el instalador completo.

Las bases locales `pos_db` también recibieron este catálogo el 9 de octubre de 2026. La base MySQL no existía y se instaló; PostgreSQL ya tenía versión 11 y conservó su esquema. La revisión posterior de scripts y funciones se ejecutó en bases QA independientes.

Cada base quedó con:

| Catálogo | Total |
| --- | ---: |
| Productos con precio de compra, precio de venta y stock | 24 |
| Categorías | 6 |
| Marcas | 4 |
| Proveedores ficticios | 3 |
| Clientes ficticios | 6 |
| Empresa de demostración | 1 |
| Áreas | 4 |
| Mesas | 12 |
| Tipos de comprobante | 3 |

Los productos incluyen lomo saltado, arroz con pollo, entradas, parrillas, combos, bebidas y postres. Los 8 productos de bebidas tienen destino `BAR`; los otros 16 tienen destino `COCINA`. Los precios están en soles y los códigos empiezan por `DEMO-`.

Los nombres de clientes y proveedores empiezan por `DEMO`. Sus documentos, teléfonos, direcciones y correos son ficticios. La empresa también es de demostración: configurar los datos reales en el sistema antes de emitir comprobantes reales. Las fechas de nacimiento permiten explorar el módulo de marketing; las métricas de visitas y gasto empiezan en cero.

La carga conserva los registros existentes, precios, stock, usuarios, contraseñas y estados de mesas. Solo agrega lo que falta. La empresa demo se crea únicamente si no existe ninguna empresa. Los productos nuevos tienen `version=0` y `visible_web=false`; no se activa ninguna tienda pública. No se crean ventas, pagos, comprobantes emitidos ni sesiones de caja.

## Repetir o personalizar la carga

El catálogo se puede personalizar en la sección 12 de cada script SQL. La herramienta opcional `database/cargar_datos_demo.py` **lee esa sección del script correspondiente**, guarda un respaldo y aplica únicamente los datos, sin duplicar la definición en un JSON ni en un tercer archivo SQL. Usa los clientes nativos `psql`/`pg_dump` o `mysql`/`mysqldump` instalados, sin dependencias Python adicionales. Busca los binarios en `PATH` o en las rutas locales PostgreSQL 17 y MySQL 9.6.

Ejecutar desde `D:\SistemaPOSMiTrampita\Sistema-POS-Mi-Trampita` en PowerShell. Para PostgreSQL:

```powershell
$env:DB_USERNAME = 'postgres'
$claveDemo = Read-Host 'Contraseña de PostgreSQL' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $claveDemo).Password
python database\cargar_datos_demo.py --engine postgresql --database pos_db --apply
Remove-Item Env:DB_USERNAME,Env:DB_PASSWORD
```

Para MySQL:

```powershell
$env:DB_USERNAME = 'root'
$claveDemo = Read-Host 'Contraseña de MySQL' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $claveDemo).Password
python database\cargar_datos_demo.py --engine mysql --database pos_db --apply
Remove-Item Env:DB_USERNAME,Env:DB_PASSWORD
```

Sin `--apply`, solo inspecciona la base. La herramienta no instala ni migra esquemas: exige versión 11 y admite únicamente `pos_db` o bases de prueba `pos_kds_qa_*`. Para personalizar registros ya cargados, usar los formularios del POS: editar el SQL y repetir la carga no sobrescribe registros existentes.

Cada aplicación guarda un respaldo completo y un reporte JSON en `.local/backups/datos_demo/`, carpeta excluida de Git. Las contraseñas de conexión se reciben por entorno y no se guardan en los archivos del proyecto. Las copias de respaldo sí contienen los hashes existentes de las cuentas.

## Verificación realizada

La instalación completa con datos y las migraciones 05–11 pasaron `verify_schema_05.py` en ambos motores. Se modificaron precios, stock, versiones y métricas de clientes en QA y se repitió la sección 12 dos veces: conservó esos cambios, los hashes de contraseñas y las cantidades de registros. La herramienta opcional también verifica huellas de todas las filas originales. Consulta [la revisión funcional y sus evidencias](REVISION_FUNCIONAL_20261009.md).
