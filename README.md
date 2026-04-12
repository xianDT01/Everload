# EverLoad - Descarga Música y Videos de Internet

**EverLoad** es una aplicación desarrollada en **Spring Boot** y **Angular 16** que permite descargar fácilmente videos y audios desde las plataformas más populares, todo de forma rápida, segura y desde tu red privada. Incluye además un sistema de chat, reproductor NAS y modo DJ con decks profesionales.

**EverLoad** is an app built with **Spring Boot** and **Angular 16** that allows you to easily download videos and audio from the most popular platforms, quickly, securely, and within your private network. It also features a chat system, NAS music player, and a professional DJ deck mode.

**EverLoad** é unha aplicación feita con **Spring Boot** e **Angular 16** que permite descargar vídeos e audios das plataformas máis populares de forma rápida, segura e desde a túa rede privada. Inclúe ademais un sistema de chat, reprodutor NAS e modo DJ con decks profesionais.

------------------------------------------------------------------------

## 🖼️ Vista previa de la aplicación

<p align="center">
  <img src="docs/assets/HomeEverload.png" alt="Pantalla principal" width="600"/>
</p>

<p align="center">
  <img src="docs/assets/Download%20from%20YouTube.png" alt="YouTube" width="200"/>
  <img src="docs/assets/Download%20from%20Facebook.png" alt="Facebook" width="200"/>
  <img src="docs/assets/Download%20from%20Instagram.png" alt="Instagram" width="200"/>
  <img src="docs/assets/Download%20from%20X.png" alt="Twitter/X" width="200"/>
  <img src="docs/assets/Spotify.png" alt="Spotify" width="200"/>
  <img src="docs/assets/TIkTok.png" alt="TikTok" width="200">
</p>

------------------------------------------------------------------------

## 🚀 Características / Features / Características

### Descarga de contenido / Content Download
-   📺 **YouTube**: descarga / download / descarga de vídeos e audios (MP3).
    ➕ También se pueden descargar playlists completas.
-   🐦 **Twitter/X**: vídeos públicos.
-   📘 **Facebook**: vídeos públicos.
-   📸 **Instagram**: Reels e posts públicos.
-   🎧 **Spotify**: descarga automática de canciones a partir de una playlist.
-   🎵 **TikTok**: descarga directa pegando la URL.

### Sistema y plataforma / System & Platform
-   🌐 Frontend multilingüe: ES, EN, GL.
-   📦 Backend con API REST.
-   🖼️ UI responsive y temática por plataforma.
-   🐳 Preparado para Docker con HTTPS automático via Caddy.
-   📜 Documentación Swagger integrada.
-   👤 Sistema de usuarios con avatares y roles (USER / ADMIN).
-   💬 Chat en tiempo real con notificaciones, reply y búsqueda.
-   🔔 Indicador de presencia y "último visto" por usuario.

### 🎵 NAS Music — Reproductor de biblioteca local
-   Navega por la música almacenada en el servidor (NAS) desde el navegador.
-   Vista de biblioteca estilo Spotify: artistas, álbumes, canciones.
-   Soporte de metadatos: título, artista, álbum, carátula embebida.
-   Acceso desde el menú lateral (requiere permiso NAS o rol ADMIN).

### 🎛️ DJ Deck Mode — Modo de cabinas profesional
-   **Dos decks independientes** (A y B) con:
    -   Reproductor de archivos NAS y **búsqueda de YouTube** integrada.
    -   Vinilo animado con portada de álbum (caratula local o via iTunes API).
    -   Control de velocidad: pitch slider (±6%) + doble clic para reset.
    -   Espectro de audio en tiempo real (Web Audio API).
-   **Mezclador central**:
    -   Crossfader con curva equal-power.
    -   Faders de canal independientes con VU meters.
    -   Ecualizador de 3 bandas (HI / MID / LOW) por canal.
    -   Filtro de barrido LP→HP por canal (fuera del EQ para mayor claridad visual).
-   **Carga inteligente de pistas**:
    -   Doble clic en cualquier pista para cargarla al deck inactivo automáticamente.
    -   Historial de pistas reproducidas.
    -   Navegación por carpetas NAS, biblioteca local y resultados de YouTube.
-   **Carátulas automáticas**: si el archivo no tiene portada embebida, se consulta
    la API pública de iTunes para obtener la imagen de alta resolución.
-   **Modal de ayuda** (`?`): explica cada control (crossfader, EQ, filtro, pitch, atajos).
-   Acceso directo desde el menú lateral en la pantalla de inicio.

### 🛡️ Panel de administración / Admin Panel
-   Configuración de claves (API Keys, Client ID/Secret).
-   Actualización de `yt-dlp`.
-   Limpiar temporales e historial.
-   Ver y filtrar logs en tiempo real.
-   Comprobar estado de APIs externas (YouTube, Spotify, TikTok, Facebook, Instagram).
-   Gestión de rutas NAS y permisos de usuarios.

------------------------------------------------------------------------

## 🛠️ Requisitos / Requirements / Requisitos

-   Java 17+
-   Node.js 18+ + Angular CLI 16
-   [`yt-dlp`](https://github.com/yt-dlp/yt-dlp)
-   Docker + Docker Compose (para despliegue con HTTPS)

------------------------------------------------------------------------

## ▶️ Ejecución Backend / Run Backend / Execución do Backend

```bash
mvn clean package
java -jar target/everload-1.0.0.jar
```

-   Backend en `http://localhost:8080`

------------------------------------------------------------------------

## 🌐 Ejecución Frontend / Run Frontend / Execución do Frontend

```bash
cd everload-front
npm install
ng serve
```

-   Frontend en `http://localhost:4200`

------------------------------------------------------------------------

## 🐳 Docker — Despliegue con HTTPS / Docker Deployment with HTTPS

EverLoad incluye un stack Docker con **Caddy** como reverse proxy, que proporciona HTTPS automático.

### Levantar la aplicación

```bash
# Primera vez (o tras cambios en el código)
docker compose up --build -d

# Ejecuciones posteriores
docker compose up -d
```

La aplicación quedará disponible en:
-   `https://localhost` (con certificado auto-firmado de Caddy CA)
-   `http://localhost` → redirige automáticamente a HTTPS

### Variables de entorno (`.env` o `docker compose`)

| Variable | Por defecto | Descripción |
|---|---|---|
| `CADDY_DOMAIN` | `localhost` | Dominio. Usa tu dominio real para Let's Encrypt. |
| `CADDY_EMAIL` | `admin@example.com` | Email para Let's Encrypt. |
| `JWT_SECRET` | (dev key) | **Cambia en producción.** |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200,...` | Orígenes CORS permitidos. |

### Volúmenes persistentes

| Volumen Docker | Descripción |
|---|---|
| `everload-db` | Base de datos H2 |
| `everload-avatars` | Avatares de usuario |
| `everload-nas` | Almacenamiento NAS (monta tu disco aquí) |
| `caddy-data` | Certificados TLS — **hacer backup en producción** |
| `caddy-config` | Caché de configuración de Caddy |

### Montar una carpeta local como NAS (Windows)

Para usar tu carpeta de música local como NAS, modifica el volumen en `docker-compose.yml`:

```yaml
volumes:
  - C:/Users/TuUsuario/Music/Everload:/app/nas_storage:ro
  # ...
```

> **Requisito Windows**: en Docker Desktop → Settings → Resources → File Sharing,
> añade la ruta `C:\Users\TuUsuario\Music\Everload` antes de reiniciar los contenedores.
> Sin esto, el contenedor verá la carpeta vacía o devolverá error 403.

### Montar una carpeta local como NAS (Linux/Mac)

```yaml
volumes:
  - /ruta/a/tu/musica:/app/nas_storage:ro
  # ...
```

Después configura esa ruta en el panel de administración de EverLoad.

------------------------------------------------------------------------

## 🧰 Panel de administración / Admin Panel

EverLoad incluye un **panel de administración completo** accesible desde la interfaz web. Permite gestionar todo sin tocar archivos manualmente.

### 🔑 Configuración interna (`config.json`)
-   `clientId`, `clientSecret`, `apiKey`

### ⬆️ Actualización de `yt-dlp`
-   Ejecuta `yt-dlp -U` directamente en el servidor.

### 🧹 Limpieza de temporales
-   Elimina carpetas `./downloads/tmp-*`.

### 🗑️ Limpieza del historial
-   Limpia `downloads_history.json`.

### 📜 Gestión de logs
-   Ver las últimas líneas del archivo `everload.log`.
-   Filtrar por texto.
-   Limpiar el log desde un botón.

### ✅ Comprobación del estado de las APIs externas
Pruebas automáticas de: YouTube, Spotify, TikTok, Facebook, Instagram.
Devuelven `OK` o `ERROR` con detalles si falla.

> El panel está diseñado para uso local. No se recomienda exponerlo a Internet.

------------------------------------------------------------------------

## 🔗 API Endpoints

### 📥 YouTube
-   Vídeo: `GET /api/downloadVideo?videoId=ID&resolution=1080`
-   Música: `GET /api/downloadMusic?videoId=ID&format=mp3`

### 🐦 Twitter/X
`GET /api/downloadTwitter?url=URL`

### 📘 Facebook
`GET /api/downloadFacebook?url=URL`

### 📸 Instagram
`GET /api/downloadInstagram?url=URL`

### 🎵 TikTok
`GET /api/downloadTikTok?url=URL`

### 🎧 Spotify
`POST /api/spotify/playlist`

```json
{
  "playlistUrl": "https://open.spotify.com/playlist/..."
}
```

### 🎵 NAS Music
-   `GET /api/nas/browse?path=...` — Navegar por carpetas
-   `GET /api/nas/stream?path=...` — Reproducir archivo de audio
-   `GET /api/nas/metadata?path=...` — Metadatos de pista (título, artista, álbum)
-   `GET /api/nas/cover?path=...` — Carátula embebida en el archivo

------------------------------------------------------------------------

## 🔧 Admin API

### ⚙️ Configuración (`config.json`)
-   GET `/api/admin/config`
-   POST `/api/admin/config`

### ⬆️ Actualizar yt-dlp
-   POST `/api/admin/update-yt-dlp`

### 🧹 Temporales
-   GET `/api/admin/clear-temp`

### 🗂️ Historial
-   GET `/api/admin/history`
-   DELETE `/api/admin/history/clear`

### 📜 Logs
-   GET `/api/admin/logs?lines=100&filter=text`
-   POST `/api/admin/logs/clear`

### 🌐 Test APIs externas
-   `/api/admin/test-api/youtube`
-   `/api/admin/test-api/spotify`
-   `/api/admin/test-api/tiktok`
-   `/api/admin/test-api/facebook`
-   `/api/admin/test-api/instagram`

------------------------------------------------------------------------

## 📜 Swagger

`http://localhost:8080/swagger-ui.html`

------------------------------------------------------------------------

## 👤 Autor

**Xián Duán Taboada**
🔗 GitHub: https://github.com/xianDT01

------------------------------------------------------------------------

## ⚠️ Limitaciones

-   Instagram, Facebook y Twitter/X: solo contenido público.
-   Spotify: no descarga desde Spotify, busca los títulos en YouTube.
-   La carátula automática via iTunes API requiere conexión a Internet desde el navegador.
-   El modo DJ con YouTube requiere conexión a Internet para la carga de vídeos.

------------------------------------------------------------------------

## 📝 Licencia

Proyecto bajo licencia MIT.
Puedes usarlo, modificarlo y distribuirlo con atribución:
**Xián Duán Taboada -- xiandt01@gmail.com**

Consulta [LICENSE](./LICENSE) para más detalles.
