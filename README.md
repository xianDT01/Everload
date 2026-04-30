# EverLoad

> ES: Tu centro multimedia personal: descargas, música NAS, radio, herramientas DJ, chat y un escritorio estilo Windows XP para gestionar tu biblioteca.  
> EN: Your personal media center: downloads, NAS music, radio, DJ tools, chat, and an XP-style desktop for managing your library.

EverLoad es una aplicación multimedia pensada para red privada y self-hosting. Empezó como una herramienta para descargar vídeos y audio, pero ahora también incluye biblioteca musical NAS, radio online, decks DJ, herramientas de audio, chat en tiempo real, notificaciones, panel de administración y un modo Windows XP con reproductores y utilidades.

EverLoad is a self-hosted media hub for private networks. It started as a video/audio downloader, but now also includes NAS music management, online radio, DJ decks, audio utilities, real-time chat, notifications, an admin panel, and an XP-style desktop mode with players and tools.

---

## Vista Previa / Preview

<p align="center">
  <img src="docs/assets/HomeEverload.png" alt="EverLoad home screen" width="720">
</p>

<p align="center">
  <img src="docs/assets/Download%20from%20YouTube.png" alt="YouTube downloader" width="170">
  <img src="docs/assets/Download%20from%20Facebook.png" alt="Facebook downloader" width="170">
  <img src="docs/assets/Download%20from%20Instagram.png" alt="Instagram downloader" width="170">
  <img src="docs/assets/Download%20from%20X.png" alt="Twitter X downloader" width="170">
  <img src="docs/assets/Spotify.png" alt="Spotify playlist tools" width="170">
  <img src="docs/assets/TIkTok.png" alt="TikTok downloader" width="170">
</p>

---

## Funciones Principales / Main Features

### Descargas / Downloads

EverLoad permite descargar o procesar contenido desde plataformas populares.

EverLoad can download or process media from popular platforms.

| Plataforma / Platform | Vídeo / Video | Audio / MP3 | Playlists | Notas / Notes |
|---|---:|---:|---:|---|
| YouTube | yes | yes | yes | Búsqueda, vista previa, calidad configurable, guardado en NAS / Search, preview, quality selection, save to NAS |
| Twitter / X | yes | no | no | Vídeos públicos / Public videos |
| Facebook | yes | no | no | Vídeos públicos / Public videos |
| Instagram | yes | no | no | Reels y posts públicos / Public reels and posts |
| TikTok | yes | no | no | Vídeos públicos / Public videos |
| Spotify | no | yes | yes | Resuelve canciones usando YouTube / Resolves tracks through YouTube |

Incluye cola de descargas, historial, notificaciones, guardado en NAS, búsqueda de YouTube y posibilidad de compartir vídeos en chats.

Includes download queue, history, notifications, NAS saving, YouTube search, and sharing YouTube videos into chats.

### NAS Music

Biblioteca musical en red para navegar, reproducir y gestionar música almacenada en rutas NAS configuradas.

Network music library for browsing, playing, and managing music stored in configured NAS paths.

- Exploración de carpetas y rutas NAS / NAS path and folder browsing.
- Subida, descarga ZIP, copia, movimiento, renombrado y borrado / Upload, ZIP download, copy, move, rename, delete.
- Streaming con soporte HTTP range / Streaming with HTTP range support.
- Metadatos ID3: título, artista, álbum, año y carátula / ID3 metadata: title, artist, album, year, cover.
- Edición de etiquetas desde el navegador / Browser-based tag editing.
- Favoritos, historial, cola, shuffle y repeat / Favorites, history, queue, shuffle, repeat.
- Carátulas desde archivos, carpetas o búsqueda iTunes / Covers from files, folders, or iTunes lookup.

### Radio

Modo dedicado para escuchar emisoras nacionales e internacionales.

Dedicated mode for national and international radio stations.

- Prioridad a emisoras nacionales / National stations first.
- Búsqueda global / Global search.
- Filtros por presets y etiquetas / Preset and tag filters.
- Sintonización por URL directa / Direct stream URL tuning.
- Reproducción compartida al navegar / Playback state survives navigation.
- Emisoras guardadas de respaldo / Curated fallback stations.

### Modo Windows XP / Windows XP Mode

Un escritorio estilo Windows XP dentro de EverLoad.

An XP-style desktop inside EverLoad.

- Iconos movibles y selección múltiple con rectángulo azul / Movable icons and multi-select rectangle.
- Menú inicio, barra de tareas, bandeja del sistema y ventanas / Start menu, taskbar, tray, and windows.
- NAS Explorer para archivos y reproducción / NAS Explorer for files and playback.
- Music Manager con metadatos, cola, historial y exportación / Music Manager with metadata, queue, history, exports.
- Bloc de notas con almacenamiento por usuario / Notepad with per-user local storage.
- Calculadora, ecualizador, Snake, Buscaminas, Messenger, YouTube XP, fondos / Calculator, equalizer, Snake, Minesweeper, Messenger, YouTube XP, wallpapers.
- Skins de reproductor: Windows Media Player, Winamp, macOS Music y foobar2000 / Player skins: Windows Media Player, Winamp, macOS Music, foobar2000.

### Cabina DJ / DJ Decks

Modo DJ con dos decks y mezclador central.

DJ mode with two decks and a central mixer.

- Carga desde NAS o búsqueda de YouTube / Load from NAS or YouTube search.
- Vinilos animados con carátulas / Animated vinyl with covers.
- Pitch, crossfader, faders, VU meters, EQ y filtros / Pitch, crossfader, faders, VU meters, EQ, filters.
- Hot cues y ayuda integrada / Hot cues and built-in help.

### Herramientas de Audio / Audio Tools

Utilidades para trabajar con archivos de audio.

Utilities for audio files.

- Información de archivo / File information.
- Conversión de formatos / Format conversion.
- Recorte de audio / Audio trimming.

Requiere FFmpeg disponible en el backend.

Requires FFmpeg on the backend.

### Chat

Chat en tiempo real con conversaciones privadas, grupos y canales de anuncios.

Real-time chat with private conversations, groups, and announcement channels.

- Menciones, emojis, respuestas, búsqueda y copiado / Mentions, emojis, replies, search, copy.
- Tarjetas de YouTube / YouTube cards.
- Confirmaciones de lectura / Read receipts.
- Roles de grupo, avatares, silencios y moderación / Group roles, avatars, muting, moderation.
- Zumbido estilo Messenger / Messenger-style buzz.
- Temas visuales / Visual themes.

### Usuarios, Seguridad y Admin / Users, Security and Admin

- Registro, login JWT y aprobación de cuentas / Register, JWT login, account approval.
- Roles: `ADMIN`, `NAS_USER`, `BASIC_USER`.
- Avatares, cambio de contraseña y presencia online / Avatars, password changes, online presence.
- Panel admin con usuarios, NAS, logs, historial, auditoría, backups, mantenimiento y comprobación de APIs / Admin panel with users, NAS, logs, history, audit, backups, maintenance, API checks.
- Notificaciones, PWA, update banner y offline banner / Notifications, PWA, update banner, offline banner.

---

## Tecnologías / Tech Stack

| Área / Area | Tecnología / Technology |
|---|---|
| Backend | Spring Boot 3.4, Java 17, Spring Security, JPA |
| Frontend | Angular 15, RxJS, Angular CDK/Material, ngx-translate |
| Base de datos / Database | H2 by default |
| Media | yt-dlp, FFmpeg, jaudiotagger, Web Audio API |
| Despliegue / Deployment | Docker Compose, Caddy |
| API Docs | springdoc-openapi / Swagger UI |

---

## Requisitos / Requirements

- Java 17+
- Node.js 18+ o 20+
- npm
- yt-dlp
- FFmpeg
- Docker y Docker Compose para despliegue con el stack incluido

---

## Desarrollo / Development

### Backend

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Backend:

```text
http://localhost:8080
```

### Frontend

```bash
cd everload-front
npm ci
npm start -- --host 127.0.0.1 --port 4200
```

Frontend:

```text
http://localhost:4200
```

### Build

```bash
cd everload-front
npm run build
```

```bash
./mvnw clean package
```

---

## Docker

Levantar la aplicación:

Start the app:

```bash
docker compose up --build -d
```

Siguientes ejecuciones:

Later runs:

```bash
docker compose up -d
```

URLs locales:

Local URLs:

```text
https://localhost
http://localhost
```

Comandos útiles:

Useful commands:

```bash
docker compose ps
docker compose logs --tail=80 everload
docker compose logs --tail=80 frontend
docker compose logs --tail=80 caddy
```

### Variables Principales / Main Variables

| Variable | Uso / Purpose |
|---|---|
| `JWT_SECRET` | Cambiar en producción / Change in production |
| `CADDY_DOMAIN` | Dominio para Caddy / Caddy domain |
| `CADDY_EMAIL` | Email para Let's Encrypt |
| `CORS_ALLOWED_ORIGINS` | Orígenes frontend permitidos / Allowed frontend origins |
| `APP_BACKUP_PATH` | Carpeta de backups / Backup directory |
| `APP_MAINTENANCE_FLAG` | Flag de mantenimiento / Maintenance flag |
| `APP_CONFIG_PATH` | Configuración persistente / Persistent config |

Ver `.env.example` y `docker-compose.yml` para más detalle.

See `.env.example` and `docker-compose.yml` for details.

### Montar Música NAS / Mount NAS Music

Windows:

```yaml
volumes:
  - C:/Users/YourUser/Music:/app/nas_storage:rw
```

Linux/macOS:

```yaml
volumes:
  - /path/to/music:/app/nas_storage:rw
```

Después añade `/app/nas_storage` desde el panel de administración, en rutas NAS.

Then add `/app/nas_storage` from the admin panel under NAS paths.

---

## Rutas Principales / Main Routes

| Ruta / Route | Función / Feature |
|---|---|
| `/` | Home |
| `/login` | Login |
| `/register` | Registro / Register |
| `/radio` | Radio |
| `/nas-music` | Biblioteca NAS / NAS music library |
| `/nas-music?mode=deck` | Cabina DJ / DJ decks |
| `/audio-tools` | Herramientas de audio / Audio tools |
| `/chat` | Chat |
| `/profile` | Perfil / Profile |
| `/about-app` | Acerca de / About |
| `/admin-config` | Panel admin / Admin panel |

---

## API

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

Grupos principales:

Main groups:

| Grupo / Group | Base path |
|---|---|
| Auth | `/api/auth` |
| Downloads | `/api/downloadVideo`, `/api/downloadMusic`, `/api/downloadTwitter`, etc. |
| YouTube search | `/api/youtube` |
| Spotify tools | `/api/spotify` |
| NAS | `/api/nas` |
| NAS yt-dlp jobs | `/api/nas/ytdlp` |
| Music | `/api/music` |
| Favorites/history | `/api/library` |
| Audio tools | `/api/audio` |
| Chat | `/api/chat` |
| Notifications | `/api/notifications` |
| Presence | `/api/presence` |
| Snake scores | `/api/snake` |
| Admin config | `/api/admin/config` |
| Admin users | `/api/admin/users` |
| Admin logs | `/api/admin/logs` |
| Admin backups | `/api/admin/backup` |
| Admin audit | `/api/admin/audit` |
| Admin system | `/api/admin/system` |

---

## Limitaciones Conocidas / Known Limitations

- Instagram, Facebook, Twitter/X y TikTok dependen de contenido público y compatibilidad de yt-dlp.  
  Instagram, Facebook, Twitter/X, and TikTok depend on public content and yt-dlp compatibility.
- Spotify se resuelve mediante YouTube; EverLoad no descarga directamente desde Spotify.  
  Spotify is resolved through YouTube; EverLoad does not download directly from Spotify.
- Algunas radios pueden fallar si cambian URL, bloquean navegador o rechazan CORS.  
  Some radio streams may fail if URLs change, browser playback is blocked, or CORS is rejected.
- La búsqueda de carátulas iTunes y YouTube DJ requieren Internet.  
  iTunes cover lookup and YouTube DJ loading require internet access.
- FFmpeg debe estar disponible para conversión y recorte de audio.  
  FFmpeg must be available for audio conversion and trimming.
- La base H2 incluida es cómoda para self-hosting, pero conviene hacer backups.  
  The bundled H2 database is convenient for self-hosting, but regular backups are recommended.

---

## Estructura / Repository Notes

- Backend: `src/main/java/com/EverLoad/everload`
- Frontend: `everload-front/src/app`
- Traducciones / i18n: `everload-front/src/assets/i18n`
- Docker: `docker-compose.yml`, `Dockerfile`, `Caddyfile`
- Capturas / screenshots: `docs/assets`

---

## Licencia / License

MIT License. Consulta [LICENSE](./LICENSE).

MIT License. See [LICENSE](./LICENSE).

Creado por / Created by **Xian Duan Taboada**.
