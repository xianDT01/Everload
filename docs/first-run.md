# Primera puesta en marcha

Una instancia nueva abre `/setup` antes del login. No se crea una cuenta `admin`
ni una contrasena predeterminada. Las instalaciones que ya tienen un administrador
conservan sus cuentas y no vuelven a ofrecer el alta inicial.

## Docker sin NAS ni claves externas

Define `JWT_SECRET` con un valor aleatorio de al menos 32 bytes en `.env`.
No es una clave de un proveedor: es el secreto local que firma las sesiones.
Para una instalacion independiente y portable:

```sh
docker compose -p everload-new -f compose.first-run.yml up -d --build
docker compose -p everload-new -f compose.first-run.yml exec everload cat /app/data/setup-code
```

Abre `http://localhost:8080`. El puerto de este ejemplo solo se publica en loopback;
no expone la instancia directamente a Internet. El compose existente, con Caddy,
sigue disponible y no ha cambiado sus montajes.

Con el `docker-compose.yml` habitual, el comando mostrado por el asistente es:

```sh
docker compose exec everload cat /app/data/setup-code
```

Usa las mismas opciones `-p` y `-f` con las que arrancaste tu instancia. Si cambias
`APP_SETUP_CODE_PATH`, lee el fichero en esa ruta en lugar de la predeterminada.
El codigo nunca se devuelve por HTTP ni se escribe en los logs. Se guarda con
permisos de propietario en sistemas POSIX, junto a los datos persistentes.
No compartas ni subas este fichero, los volumenes, `.env` o `config.json` a Git.

## Flujo

1. Elige espanol, galego o ingles.
2. Introduce el codigo, usuario, correo electronico y contrasena con confirmacion.
   La ayuda junto al codigo explica como obtenerlo en el servidor. Se requieren al
   menos 12 caracteres y un maximo de 72 bytes, acorde con BCrypt.
3. Puedes anadir una biblioteca o configurar mas tarde.
4. Puedes guardar Spotify, YouTube o AcoustID, todos opcionales. Spotify prueba
   el intercambio real de credenciales por un token. YouTube consulta su API.
   AcoustID se guarda sin fingir una comprobacion aislada que no esta disponible.
   Guardado no significa verificado; un fallo no impide continuar.
   Cada servicio incluye una ayuda desplegable con tres pasos y enlaces oficiales.
   Spotify requiere las credenciales de su aplicacion, YouTube una clave de YouTube
   Data API v3, y AcoustID una clave de aplicacion (client), no la clave de usuario
   utilizada para enviar huellas.
5. El resumen no muestra claves. **Entrar en EverLoad siempre abre el Home**.

El correo indicado se guarda en la cuenta del administrador y aparece en su perfil.
El asistente no envia un mensaje de verificacion ni configura un servidor de correo.
Las cuentas creadas anteriormente no se modifican: su correo se puede editar en el perfil.

## Bibliotecas y solo lectura

El selector navega exclusivamente por `nas.storage.base` (`/app/nas_storage` por
defecto), comprueba acceso real y no permite escapar mediante `..` o enlaces
simbolicos. Elegir una subcarpeta no monta un NAS ni permite explorar el host.

El ejemplo portable utiliza un volumen vacio de solo lectura. Para conectar musica,
sustituye su montaje por una carpeta del host ya preparada:

```yaml
volumes:
  - /ruta/real/de/musica:/app/nas_storage:ro
```

Recrea el contenedor y pulsa **Comprobar acceso**. Una ruta inaccesible se diferencia
de una legible sin escritura. El asistente no puede cambiar los permisos impuestos
por Docker. No se ofrece un falso interruptor de solo lectura: si el volumen es
escribible, se recomienda `:ro` para impedir modificaciones en todos los servicios.

## Cierre y sesiones

La comprobacion del administrador y el consumo del codigo se realizan en una
transaccion con bloqueo de una fila persistente. Solo una peticion concurrente puede
crear la cuenta. El cierre no se revierte al recargar, usar Atras, abrir otra pestana
o eliminar posteriormente al administrador. Tras el alta, biblioteca e integraciones
requieren un JWT de administrador. Si se pierde la sesion, inicia sesion con la
cuenta creada; no se reabre el registro inicial. La pestana original puede retomar
los pasos opcionales en `/setup` mientras conserve el marcador de sesion.

La instalacion no configura dominio, HTTPS ni restauracion. Tampoco modifica el
diseno del panel de administracion ni las preferencias del resto de la aplicacion.

## Recursos visuales

`everload-front/src/assets/images/setup-listening-room.jpg` es la fotografia del
tocadiscos de la maqueta aprobada, limpiada de textos y controles con la herramienta
integrada de generacion/edicion de imagenes y exportada a JPEG. No es un video.
Las transiciones usan CSS y respetan `prefers-reduced-motion`.

El login y registro no incluyen fotos de artistas predeterminadas. Sus seis
posiciones quedan vacias, tambien al restablecerlas o si falla la configuracion.
El conjunto de muestra de versiones anteriores se retira automaticamente; las
imagenes configuradas expresamente por el administrador se conservan.

## Comprobaciones

```sh
mvn test
mvn -DskipTests package
cd everload-front
npx ng test --watch=false --browsers=ChromeHeadless
npm run build
```

El asistente tiene pruebas de alta concurrente, consumo y cierre permanente del
codigo, cuentas existentes, autorizacion de endpoints, ocultacion de secretos en
logs, rutas del volumen, sesiones, pasos opcionales y destino final Home.

Se ha recorrido ademas con navegador y backend real usando una base H2 vacia,
incluyendo codigo incorrecto, otra pestana abierta, recarga, perdida de sesion,
login posterior y rechazo de un segundo administrador. Se han revisado capturas
de escritorio y movil (320 y 390 px), sin desbordamiento horizontal y con movimiento
reducido.

Limites de esta verificacion local en Windows: el estado de volumen de solo
lectura se simula en las pruebas; falta comprobarlo con un montaje Docker `:ro`
real. La prueba de escape por enlace simbolico se omite si el sistema no permite
crear enlaces. Las respuestas de Spotify y YouTube se prueban con HTTP simulado,
no con claves reales. No se ha construido ni desplegado una imagen Docker nueva.
