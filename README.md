<p align="center">
  <img src="everload-front/src/assets/EverloadLogo.png" alt="EverLoad" width="180">
</p>

<h1 align="center">EverLoad</h1>

<p align="center">
  Tu centro multimedia personal, abierto y autoalojado.<br>
  <em>Your open, self-hosted personal media hub.</em>
</p>

<p align="center">
  <a href="https://github.com/xianDT01/Everload/commits/main"><img src="https://img.shields.io/github/last-commit/xianDT01/Everload?style=flat-square" alt="Último commit"></a>
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/Spring_Boot-3.4.3-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 3.4.3">
  <img src="https://img.shields.io/badge/Angular-17.3-DD0031?style=flat-square&logo=angular&logoColor=white" alt="Angular 17.3">
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker Compose">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/xianDT01/Everload?style=flat-square" alt="MIT License"></a>
</p>

<p align="center">
  <a href="https://everload.duckdns.org"><strong>Instancia en producción</strong></a>
  ·
  <a href="#inicio-rápido"><strong>Inicio rápido</strong></a>
  ·
  <a href="#galería"><strong>Galería</strong></a>
  ·
  <a href="#desarrollo"><strong>Desarrollo</strong></a>
</p>

<p align="center">
  <img src="docs/assets/everload-home.png" alt="Pantalla principal de EverLoad" width="900">
</p>

## Qué es EverLoad

EverLoad reúne descargas multimedia, música local y NAS, YouTube Music, radio online, herramientas de audio y una cabina DJ en una sola aplicación web. Está diseñada para ejecutarse en tu propia red o servidor, conservar tus archivos bajo tu control y funcionar como PWA en escritorio y móvil.

El proyecto comenzó como un descargador de vídeo y ha crecido hasta convertirse en un ecosistema multimedia completo, con perfiles, permisos, notificaciones, administración, automatizaciones y una aplicación Android complementaria.

> EverLoad is a self-hosted media platform for downloading, organizing, discovering and playing media from a single interface.

## Funciones principales

| Área | Capacidades |
| --- | --- |
| **Modern y Flow** | Reproductor musical con dos vistas de inicio, artistas, álbumes, favoritos, historial, cola, letras LRC, crossfade, ecualizador y más de 20 temas claros y oscuros. |
| **Playlists** | Creación, importación desde YouTube o Spotify, duplicación, orden aleatorio inteligente, enlaces compartidos y colaboración entre usuarios. |
| **YouTube Music** | Búsqueda, novedades, charts, álbumes, artistas, mixes, radio automática y streaming bajo demanda. |
| **Biblioteca NAS** | Exploración, subida, streaming con rangos HTTP, ZIP, copiar, mover, renombrar, borrar y edición de metadatos ID3. |
| **Descargas** | Vídeo y audio desde YouTube, X/Twitter, Facebook, Instagram y TikTok al dispositivo; incorporación de música al NAS desde Modern. |
| **Radio online** | Emisoras nacionales e internacionales, búsqueda global, filtros, URL directa, soporte HLS y alternativas ante enlaces caídos. |
| **Cabina DJ** | Dos decks, mezclador, pitch, crossfader, EQ, filtros, hot cues, VU meters y carga desde NAS o YouTube. |
| **Herramientas de audio** | Conversión, compresión, recorte e inspección de MP3, M4A, WAV, OGG, AAC, FLAC, OPUS y WMA. |
| **Modo Windows** | Escritorio inspirado en Windows XP con explorador NAS, Winamp, Music Manager, ventanas redimensionables, utilidades, juegos y Messenger. |
| **Chat y presencia** | Conversaciones privadas, grupos, canales de anuncios, presencia de usuarios, avisos de mensajes, contenido de YouTube compartido y zumbidos. |
| **Usuarios y administración** | JWT, roles, aprobación y reactivación de cuentas, perfiles, avatares, métricas de recursos, historial de descargas por usuario, auditoría, backups y mantenimiento. |
| **Experiencia instalada** | PWA, distribución de la APK Android, traducciones ES/EN/GL, avisos offline, notificaciones y controles multimedia del sistema. |

## Música y biblioteca

### Modern y Flow

Modern organiza la biblioteca, artistas, álbumes, playlists, favoritos y actividad en una interfaz musical. Flow ofrece una vista alternativa del inicio con tarjetas de recomendaciones, sin sustituir Modern ni crear una biblioteca independiente.

- Inicio personalizable y recomendaciones basadas en la actividad del usuario. Las cuentas nuevas pueden descubrir lo más escuchado por la comunidad mientras construyen sus propios gustos.
- Biblioteca completa paginada, con búsqueda y ordenación por título, artista o fecha, sin limitar la colección a una muestra inicial.
- Indexación en segundo plano, caché del catálogo y carga progresiva del inicio para reducir las esperas al consultar bibliotecas grandes.
- Reproducción con cola, favoritos, repetición, mezcla aleatoria, ecualizador, crossfade y letras cuando estén disponibles.
- Avisos de reproducción con identidad de EverLoad y carátula cuando exista. Al pulsar el aviso se abre el reproductor.
- Integración con Media Session para mostrar título, artista, álbum, carátula, progreso y controles compatibles en el navegador o sistema operativo.

### Playlists y descargas al servidor

- Importación de playlists accesibles de YouTube y Spotify; las pistas de Spotify se resuelven mediante YouTube, no como audio descargado desde Spotify.
- Duplicación, reproducción con orden aleatorio inteligente, playlists públicas y compartidas, y gestión de colaboradores.
- Enlaces a playlists dentro de EverLoad: compartir el enlace no elimina los requisitos de sesión ni los permisos de acceso.
- Búsqueda de canciones para añadir, con cancelación de solicitudes anteriores al cambiar la consulta o salir de la playlist.
- Desde las descargas de Modern se puede buscar en YouTube, explorar las carpetas de un NAS con permiso de escritura, crear carpetas y elegir el destino antes de encolar una descarga.

El descargador independiente está orientado a guardar archivos en el **dispositivo**. La incorporación de música a la biblioteca del **servidor/NAS** se realiza desde el reproductor Modern.

### Organización y propiedades

El explorador NAS permite trabajar con archivos y carpetas. En el modo Windows, Music Manager y las propiedades de las pistas reúnen la gestión musical: edición de título, artista, álbum y año, búsqueda de metadatos y subida de una carátula desde el equipo, según los permisos del usuario.

Los datos corregidos se sincronizan con las referencias guardadas en la biblioteca personal. Las comprobaciones de disponibilidad permiten identificar pistas a las que ya no se puede acceder, en lugar de tratarlas como canciones reproducibles. La ruta NAS debe permanecer accesible y los cambios externos pueden requerir una nueva indexación.

## Radio online

El catálogo utiliza Radio Browser y una selección de emisoras nacionales. Permite buscar por nombre, filtrar por etiquetas o ámbito geográfico y reproducir una URL directa.

- Servidores alternativos y tiempos de espera para consultar el catálogo.
- Enlaces recientes del catálogo para actualizar emisoras fijadas, conservando alternativas guardadas.
- Reproducción de emisiones de audio directo y HLS mediante `hls.js` cuando el navegador lo permite, con alternativa nativa en dispositivos compatibles.
- Cambio de enlace ante errores o conexiones sin respuesta, sin dejar el reproductor cargando indefinidamente.
- Aislamiento de los eventos de cada intento para que un error antiguo no interrumpa una emisora recién seleccionada.

La disponibilidad de las emisoras depende de sus proveedores. EverLoad no evita restricciones geográficas, certificados inválidos ni bloqueos de reproducción impuestos por terceros.

## Escritorio y administración

El modo Windows recrea una experiencia de escritorio inspirada en XP: menú Inicio con usuario y avatar, iconos organizados, ventanas con foco y tamaño ajustables, Winamp, explorador NAS y utilidades. La bandeja incluye volumen y calendario; la información de red es una recreación visual, no un administrador de la conexión del equipo.

El panel de administración incorpora:

- Resumen de usuarios, descargas recientes y actividad por días.
- CPU del proceso, uso de memoria de la JVM, tamaño de la base de datos y espacio del volumen. Estas métricas corresponden al entorno de EverLoad, no a todos los contenedores del servidor.
- Historial de descargas con identificación del usuario, auditoría y logs.
- Aprobación de cuentas, gestión de roles, listado de cuentas inactivas, reactivación y eliminación.
- Gestión de rutas NAS e integraciones, copias de seguridad y modo mantenimiento.
- Mantenimiento y limpieza del historial de grupos de chat.
- Publicación de la APK Android, con versión, requisitos y notas de actualización visibles en la página de descarga.

## PWA y Android

La web puede instalarse como PWA en navegadores compatibles. EverLoad también dispone de un cliente Android complementario, cuyo código se mantiene por separado; este repositorio incluye los servicios y la página para distribuir su APK.

La instalación como PWA no implica descargar automáticamente la biblioteca NAS para escucharla sin conexión. La reproducción desde el servidor necesita acceso a él. El aspecto y los controles de las notificaciones multimedia dependen del navegador y del sistema operativo; la web no puede imponer sus colores o diseño nativo.

## Galería

### Modern y YouTube Music

<table>
  <tr>
    <td width="50%"><img src="docs/assets/everload-modern-player.png" alt="Inicio del reproductor Modern"></td>
    <td width="50%"><img src="docs/assets/everload-youtube-music.png" alt="Exploración de YouTube Music"></td>
  </tr>
  <tr>
    <td align="center"><strong>Biblioteca personal y reproductor moderno</strong></td>
    <td align="center"><strong>Descubrimiento y streaming musical</strong></td>
  </tr>
</table>

### Descargas y radio

<table>
  <tr>
    <td width="50%"><img src="docs/assets/everload-youtube-downloader.png" alt="Descargador de YouTube"></td>
    <td width="50%"><img src="docs/assets/everload-radio.png" alt="Radio online de EverLoad"></td>
  </tr>
  <tr>
    <td align="center"><strong>Descargas de vídeo y audio al dispositivo</strong></td>
    <td align="center"><strong>Catálogo nacional, búsqueda mundial y URL directa</strong></td>
  </tr>
</table>

### Herramientas de audio

<p align="center">
  <img src="docs/assets/everload-audio-tools.png" alt="Conversión y recorte de audio" width="760">
</p>

## Automatización con Telegram

EverLoad también ha servido como backend para un bot de Telegram orquestado con n8n. La automatización interpreta comandos, solicita contenido a la API y devuelve al usuario los archivos procesados.

<table>
  <tr>
    <td width="50%"><img src="docs/assets/everload-telegram-bot.png" alt="Bot de EverLoad en Telegram"></td>
    <td width="50%"><img src="docs/assets/everload-n8n-workflow.png" alt="Workflow de Telegram en n8n"></td>
  </tr>
</table>

## Arquitectura

```mermaid
flowchart LR
    Client[Web / PWA / Android] -->|HTTPS| Caddy[Caddy]
    Caddy --> App[Spring Boot]
    App --> UI[Angular SPA]
    App --> DB[(H2 persistente)]
    App --> NAS[(Biblioteca NAS)]
    App --> Media[yt-dlp / FFmpeg / BotGuard]
    App --> External[YouTube Music / Spotify / Radio]
```

| Capa | Tecnología |
| --- | --- |
| Frontend | Angular 17.3, Angular Material/CDK, RxJS, ngx-translate, HLS.js, Web Audio API |
| Backend | Java 17, Spring Boot 3.4.3, Spring Security, Spring Data JPA, WebSocket |
| Multimedia | yt-dlp, FFmpeg, Chromaprint, jaudiotagger y RustyPipe BotGuard |
| Persistencia | H2 en volumen persistente, biblioteca NAS y backups en el host |
| Operación | Docker Compose, Caddy, HTTPS automático y health checks |
| API | REST, OpenAPI y Swagger UI |

## Inicio rápido

### Requisitos

- Docker Desktop o Docker Engine con Compose.
- Puertos `80` y `443` libres.
- Una carpeta del host para música y otra para backups.

### 1. Preparar el entorno

```bash
git clone https://github.com/xianDT01/Everload.git
cd Everload
cp .env.example .env
```

En PowerShell:

```powershell
Copy-Item .env.example .env
```

Edita `.env` y define, como mínimo, un secreto JWT aleatorio:

```env
JWT_SECRET=una_clave_aleatoria_de_32_caracteres_o_mas
CADDY_DOMAIN=localhost
CADDY_EMAIL=admin@example.com
```

También debes adaptar en `docker-compose.yml` las dos rutas del host montadas como biblioteca NAS y directorio de backups.

### 2. Construir y arrancar

```bash
docker compose up -d --build
```

Comprueba el estado:

```bash
docker compose ps
docker compose logs --tail=100 everload
```

La aplicación estará disponible en `https://localhost`. Caddy utilizará un certificado local, por lo que el navegador puede mostrar un aviso la primera vez.

### Producción

Configura un dominio real en `.env` y apunta sus registros DNS al servidor:

```env
CADDY_DOMAIN=everload.example.com
CADDY_EMAIL=you@example.com
CORS_ALLOWED_ORIGINS=https://everload.example.com
```

Caddy solicitará y renovará automáticamente el certificado TLS cuando los puertos `80` y `443` sean accesibles desde Internet.

## Configuración

| Variable | Descripción |
| --- | --- |
| `JWT_SECRET` | Obligatoria. Secreto para firmar tokens JWT; usa al menos 32 caracteres aleatorios. |
| `CADDY_DOMAIN` | Dominio público o `localhost` para desarrollo. |
| `CADDY_EMAIL` | Correo utilizado para certificados TLS. |
| `CORS_ALLOWED_ORIGINS` | Lista separada por comas de orígenes permitidos. |
| `APP_CONFIG_PATH` | Configuración persistente de integraciones y APIs. |
| `APP_BACKUP_PATH` | Directorio persistente de backups. |
| `APP_MAINTENANCE_FLAG` | Archivo que controla el modo mantenimiento. |

Consulta [`.env.example`](.env.example) y [`docker-compose.yml`](docker-compose.yml) para ver la configuración completa.

## Desarrollo

### Backend

```bash
./mvnw spring-boot:run
```

En Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

El backend y Swagger quedan disponibles en:

- Aplicación/API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

### Frontend

```bash
cd everload-front
npm ci
npm start -- --host 127.0.0.1 --port 4200
```

Angular estará disponible en `http://localhost:4200` y redirigirá las llamadas API al backend de desarrollo.

### Pruebas

```bash
./mvnw test
```

```bash
cd everload-front
npm test -- --watch=false --browsers=ChromeHeadless
```

## API principal

| Recurso | Base path |
| --- | --- |
| Autenticación | `/api/auth` |
| Descargas | `/api/downloadVideo`, `/api/downloadMusic` y proveedores específicos |
| YouTube y YouTube Music | `/api/youtube`, `/api/ytmusic` |
| Spotify | `/api/spotify` |
| NAS y streaming | `/api/nas`, `/api/music` |
| Biblioteca personal | `/api/library` |
| Audio | `/api/audio` |
| Chat y presencia | `/api/chat`, `/api/presence` |
| Notificaciones | `/api/notifications` |
| Distribución Android | `/api/app-release/android` |
| Administración | `/api/admin/*` |

## Estructura del repositorio

```text
Everload/
├── everload-front/                 Angular SPA
├── src/main/java/com/EverLoad/     Backend Spring Boot
├── src/main/resources/             Configuración y frontend compilado
├── docs/assets/                    Capturas y recursos del README
├── Dockerfile                      Build multi-stage de producción
├── docker-compose.yml              Aplicación y proxy HTTPS
├── Caddyfile                       Reverse proxy y TLS
└── pom.xml                         Build Maven
```

## Idiomas

La interfaz incluye traducciones para:

- Español (`es`)
- English (`en`)
- Galego (`gl`)

Los catálogos se encuentran en `everload-front/src/assets/i18n`.

## Seguridad y datos

- No publiques el archivo `.env` ni claves de APIs.
- EverLoad no incluye credenciales de administrador predeterminadas.
- Limita las rutas NAS montadas a los directorios que realmente necesite la aplicación.
- Haz copias periódicas del volumen de base de datos, avatares, configuración y certificados de Caddy.
- Para una instalación expuesta a Internet, mantén Docker, Java, yt-dlp y las imágenes base actualizadas.

## Limitaciones conocidas

- Las descargas dependen de la disponibilidad pública del contenido y de la compatibilidad de yt-dlp con cada plataforma.
- Spotify se utiliza para resolver metadatos y pistas mediante YouTube; no se descarga audio directamente desde Spotify.
- Algunas emisoras bloquean reproducción web, cambian sus URLs o aplican restricciones geográficas.
- YouTube Music, búsqueda de carátulas, radio y automatizaciones externas requieren conexión a Internet.
- H2 es práctico para self-hosting personal, pero requiere una estrategia de backups fiable.

## Licencia

Distribuido bajo la [licencia MIT](LICENSE).

Creado y mantenido por **Xián Duán Taboada**.
