# EverLoad

> Your personal media center: downloads, NAS music, radio, DJ tools, chat, and an XP-style desktop for managing your library.

EverLoad is a private-network media hub built with **Spring Boot 3**, **Angular 15**, and **yt-dlp**. It started as a downloader, but it has grown into a broader multimedia app: social video downloads, NAS music playback and management, live radio, DJ decks, audio utilities, real-time chat, notifications, and a full admin console.

The app is designed for self-hosting at home or on a trusted server.

---

## Preview

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

## What EverLoad Can Do

### Downloads

EverLoad can download or process media from:

| Platform | Video | Audio / MP3 | Playlists | Notes |
|---|---:|---:|---:|---|
| YouTube | yes | yes | yes | Search, preview, quality selection, save to NAS |
| Twitter / X | yes | no | no | Public videos |
| Facebook | yes | no | no | Public videos |
| Instagram | yes | no | no | Public reels/posts |
| TikTok | yes | no | no | Public videos |
| Spotify | no | yes | yes | Resolves tracks through YouTube |

Extra download features:

- Queue/status tracking for downloads.
- Browser download or direct save to configured NAS storage.
- Download history with platform, type, title, and date.
- Notifications when jobs complete or fail.
- YouTube video search from inside the app.
- Share YouTube videos into chat groups.

### NAS Music

EverLoad includes a full NAS music library:

- Browse configured NAS paths and folders.
- Upload music files and folders while preserving structure.
- Download individual files or folders as ZIP.
- Create, rename, move, delete, and copy files/folders.
- Stream audio with HTTP range support.
- Read and edit ID3 metadata: title, artist, album, year, cover.
- Folder covers from embedded artwork, images, or custom upload.
- Favorites, listening history, shuffle, repeat, previous/next, and queue.
- Search by filename, title, artist, and album.
- iTunes cover lookup when local artwork is missing.

### Radio

The radio mode is a dedicated section with:

- National stations prioritized.
- Global station search.
- Preset and tag filters.
- Direct stream URL tuning.
- Shared playback state, so radio can keep playing while navigating.
- Curated fallback stations for when online catalogs fail.

### Windows XP Mode

EverLoad also includes a playful desktop mode inspired by Windows XP:

- Movable desktop icons and multi-select selection rectangle.
- Start menu, taskbar, system tray, volume controls, and window management.
- NAS Explorer window for file browsing and playback.
- Music Manager for current track metadata, queue, history, and exports.
- Notepad with per-user local "disk" storage.
- Calculator, equalizer, Snake, Minesweeper, Messenger, YouTube XP downloader, wallpaper settings.
- Player skins: Windows Media Player, Winamp-style, macOS Music-style, and foobar2000-style.
- XP sounds, notifications, screensaver, and a small hidden BSOD gag.

### DJ Decks

The DJ mode provides two decks and a mixer:

- Load tracks from NAS or YouTube search.
- Animated vinyl with album artwork.
- Pitch control and reset.
- Crossfader with equal-power curve.
- Channel faders, VU meters, EQ, and filters.
- Hot cues and waveform-style visual feedback.
- Help modal explaining the controls.

### Audio Tools

Utility tools for audio files:

- Inspect audio file information.
- Convert audio formats.
- Trim/cut audio clips.

These tools rely on backend audio processing and FFmpeg availability.

### Chat

EverLoad has a real-time chat system:

- Private chats, groups, and announcement channels.
- Mentions, emoji picker, replies, copy message, search.
- YouTube cards inside messages.
- Read receipts in private chat.
- Group avatars, roles, muting, leave/delete actions.
- Messenger-style buzz.
- Multiple visual chat themes.

### Users and Security

- Registration and JWT login.
- Pending approval flow for new accounts.
- Roles: `ADMIN`, `NAS_USER`, `BASIC_USER`.
- Profile avatars and password changes.
- Online/offline presence and privacy for last seen.
- Token revocation on logout.
- Rate limiting and maintenance mode support.

### Admin Panel

Admin-only features include:

- User approval, role changes, revocation, and deletion.
- NAS path management.
- API key/config management.
- Live logs and log cleanup.
- Download history.
- Audit log.
- Chat moderation.
- System information.
- Maintenance mode and scheduled warning toasts.
- Backups: create, list, restore, delete, configure retention.
- External API checks.
- yt-dlp update endpoint.

### Notifications and PWA

- Notification center with unread count.
- Toast notifications for downloads, chat, group invites, and admin notices.
- PWA manifest and service worker.
- Update banner support.
- Offline banner.

### Internationalization

Available languages:

- Spanish (`es`)
- Galician (`gl`)
- English (`en`)

The selected language is stored locally and can be changed without a page reload.

---

## Tech Stack

| Area | Technology |
|---|---|
| Backend | Spring Boot 3.4, Java 17, Spring Security, JPA |
| Frontend | Angular 15, RxJS, Angular CDK/Material, ngx-translate |
| Database | H2 by default |
| Media | yt-dlp, FFmpeg, jaudiotagger, Web Audio API |
| Deployment | Docker Compose, Caddy reverse proxy |
| Docs/API | springdoc-openapi / Swagger UI |

---

## Requirements

- Java 17+
- Node.js 18+ or 20+
- npm
- yt-dlp
- FFmpeg
- Docker and Docker Compose for the bundled deployment stack

---

## Development

### Backend

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Backend runs at:

```text
http://localhost:8080
```

### Frontend

```bash
cd everload-front
npm ci
npm start -- --host 127.0.0.1 --port 4200
```

Frontend runs at:

```text
http://localhost:4200
```

### Production Build

```bash
cd everload-front
npm run build
```

```bash
./mvnw clean package
```

---

## Docker Deployment

EverLoad ships with Docker Compose and Caddy.

```bash
docker compose up --build -d
```

Later runs:

```bash
docker compose up -d
```

Default local URLs:

```text
https://localhost
http://localhost
```

`http://localhost` is redirected through Caddy when the proxy is enabled.

### Useful Docker Commands

```bash
docker compose ps
docker compose logs --tail=80 everload
docker compose logs --tail=80 frontend
docker compose logs --tail=80 caddy
docker compose up --build -d
```

---

## Configuration

Important environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | dev value | Change this in production |
| `CADDY_DOMAIN` | `localhost` | Domain for Caddy |
| `CADDY_EMAIL` | `admin@example.com` | Email for Let's Encrypt |
| `CORS_ALLOWED_ORIGINS` | local dev origins | Allowed frontend origins |
| `APP_BACKUP_PATH` | `/app/backups` | Backup directory |
| `APP_MAINTENANCE_FLAG` | `/app/data/maintenance.flag` | Maintenance flag file |
| `APP_CONFIG_PATH` | `/app/data/config.json` | Persistent app config |

See `.env.example` and `docker-compose.yml` for the full deployment shape.

### NAS Mount Example

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

Then add `/app/nas_storage` from the Admin Panel under NAS paths.

---

## Main Routes

| Route | Feature |
|---|---|
| `/` | Home |
| `/login` | Login |
| `/register` | Register |
| `/radio` | Radio |
| `/nas-music` | NAS music library |
| `/nas-music?mode=deck` | DJ decks |
| `/audio-tools` | Audio tools |
| `/chat` | Chat |
| `/profile` | User profile |
| `/about-app` | About |
| `/admin-config` | Admin panel |

---

## API Overview

Swagger UI is available in development at:

```text
http://localhost:8080/swagger-ui.html
```

Some important endpoint groups:

| Group | Base Path |
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

## Known Limitations

- Instagram, Facebook, Twitter/X, and TikTok support depends on public content and yt-dlp compatibility.
- Spotify downloads are resolved through YouTube; EverLoad does not download directly from Spotify.
- Radio streams can fail if a station changes its URL, blocks browser playback, or rejects CORS.
- iTunes cover lookup and YouTube DJ loading require internet access from the browser/server.
- FFmpeg must be available for audio conversion and trimming.
- The bundled H2 database is convenient for home/self-hosted use, but production deployments should back it up regularly.

---

## Repository Notes

- Backend source: `src/main/java/com/EverLoad/everload`
- Frontend source: `everload-front/src/app`
- i18n files: `everload-front/src/assets/i18n`
- Docker stack: `docker-compose.yml`, `Dockerfile`, `Caddyfile`
- App screenshots: `docs/assets`

---

## License

MIT License. See [LICENSE](./LICENSE).

Created by **Xian Duan Taboada**.
