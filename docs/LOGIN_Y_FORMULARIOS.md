# Login y formularios compartidos

El login y los formularios para crear o editar usuarios permiten mostrar u ocultar la contraseña mediante el icono de ojo. Todos los campos de contraseña actuales utilizan el mismo componente. La visibilidad comienza oculta y se reinicia al cerrar el formulario.

| Archivo | Responsabilidad |
| --- | --- |
| `frontend/src/components/PasswordField.jsx` | Etiqueta, ID único, visibilidad, ayuda y autocompletado de contraseñas |
| `frontend/src/pages/Login.jsx` | Pantalla de acceso, validación y envío único |
| `frontend/src/components/ResourceForm.jsx` | Formularios compartidos de todos los catálogos y administración |
| `frontend/src/components/ResourceTable.jsx` | Tablas accesibles y eliminaciones protegidas contra doble envío |
| `frontend/src/hooks/useDialog.js` | Foco dentro del diálogo, Escape y restauración del foco cuando el control sigue disponible |
| `frontend/src/styles/password-field.css` | Campo con espacio para el ojo, botón táctil de 44 px y foco visible |
| `frontend/src/styles/resource-form.css` | Presentación de los formularios compartidos |
| `frontend/src/styles.css` | Prioridad de los diálogos sobre la barra lateral, incluyendo móvil |
| `frontend/src/App.jsx` | Navegación y carga de catálogos, descartando respuestas anteriores o de vistas cerradas |

El botón del ojo es `type="button"`, tiene nombre accesible y `aria-controls`; funciona con teclado y no dispara el submit. Su icono es decorativo para lectores de pantalla. No modifica el valor ni elimina espacios. El login utiliza `autocomplete="current-password"`; crear y editar usuarios utiliza `new-password`. Al editar, dejar el campo vacío conserva la contraseña existente. No se precargan ni se muestran hashes del servidor.

Los formularios bloquean campos, roles, selectores y cierre durante un guardado. El login y las eliminaciones tienen guardas inmediatas contra solicitudes repetidas. Las contraseñas nuevas respetan el mínimo de ocho caracteres y el máximo de 72 bytes UTF-8 utilizado por el backend. Los errores se anuncian con `role="alert"` y se enlazan con los campos.

Los diálogos mantienen Tab/Shift+Tab dentro de la ventana y admiten Escape cuando no hay un envío en curso. La revisión visual detectó una superposición de la barra lateral durante el cambio a móvil; se corrigió la capa común de los diálogos.

Estas mejoras del formulario común se aplican a Usuarios, Productos, Clientes, Proveedores, Categorías, Marcas, Áreas, Mesas, Configuración y Comprobantes. Roles conserva su vista de consulta. No requieren cambios SQL ni del contrato de autenticación; las verificaciones se realizaron sobre una base QA.

## Verificación

- Siete pruebas frontend aprobadas y build de producción correcto.
- Edge: mostrar/ocultar con mouse y teclado sin enviar el login; valores con espacios intactos.
- Login, creación y eliminación únicos bajo latencia simulada.
- Validación UTF-8, campos bloqueados, foco y edición sin cambiar contraseña; login posterior con la misma credencial.
- Vistas de 1440, 320 y 390 px; diálogos accesibles por encima del menú lateral.
- Regresión de productos con decimales, comandas adicionales, abonos y recuperación tras respuestas incompletas.

Evidencias: [`docs/validation/passwords/`](validation/passwords/). Las capturas muestran las contraseñas ocultas.

Desde `frontend`, ejecuta `npm.cmd test` y `npm.cmd run build`. Si Windows mantiene un `dist` anterior bloqueado, verifica en una salida nueva con `npm.cmd run build -- --outDir dist-verificacion-nueva`.

La prueba de navegador es `frontend/scripts/verify-passwords.cjs`. Desde la raíz, con `POS_UI_URL`, `PLAYWRIGHT_MODULE`, `POS_TEST_PASSWORD` y Vite/backend conectados a QA, ejecuta `node frontend/scripts/verify-passwords.cjs`. Crea y elimina un usuario de prueba; no debe ejecutarse contra la base operativa.
