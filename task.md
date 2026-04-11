# EverLoad — NAS Music Player: Plan de ejecución

## Estado global
`[ ]` = pendiente · `[~]` = en progreso · `[x]` = completado

> **Última actualización: 2026-04-11** — Bloques 0–5 completados.

---

## Bloque 0 — Preparación (sin tocar lógica nueva)

- [x] **0.1** Extraer `NasService.resolveValidatedPath(Long pathId, String subPath): Path` como método público.  
  _Mueve la lógica inline de `listFiles()` a un helper compartido para que `MusicService` no duplique el anti-traversal._

---

## Bloque 1 — Backend: DTO y Service

- [x] **1.1** Crear `src/main/java/com/EverLoad/everload/dto/MusicMetadataDto.java`  
  Campos: `path`, `name`, `title`, `artist`, `album`, `durationSeconds`, `format`, `hasCover`.  
  ⚠️ No incluir base64 de carátula — se sirve por endpoint separado.

- [x] **1.2** Crear `src/main/java/com/EverLoad/everload/service/MusicService.java`  
  Métodos:
  - `List<MusicMetadataDto> getMetadata(Long pathId, String subPath)` — lista archivos de audio con tags ID3.
  - `ResourceRegion streamAudio(Long pathId, String subPath, HttpRange range)` — streaming parcial.
  - `byte[] getCoverArt(Long pathId, String subPath)` — imagen embebida de la pista.  
  _Usa `NasService.resolveValidatedPath()` para todas las resoluciones de ruta._  
  _Lee tags con jaudiotagger. Verificar al arrancar que no lanza `InaccessibleObjectException`._  
  _Si jaudiotagger falla con Java 17, añadir al plugin de Spring Boot:_
  ```xml
  <jvmArguments>--add-opens java.base/java.util=ALL-UNNAMED</jvmArguments>
  ```

---

## Bloque 2 — Backend: Controller

- [x] **2.1** Crear `src/main/java/com/EverLoad/everload/controller/MusicController.java`  
  Todos los endpoints con `@PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")`.
  - `GET /api/music/metadata?pathId=X&subPath=...` → `List<MusicMetadataDto>`
  - `GET /api/music/stream?pathId=X&subPath=...` → streaming HTTP 206 con `Accept-Ranges: bytes`
  - `GET /api/music/cover?pathId=X&subPath=...` → imagen con Content-Type correcto (image/jpeg, image/png)

---

## Bloque 3 — Frontend: Servicio global

- [x] **3.1** Crear `everload-front/src/app/services/music.service.ts` (`providedIn: 'root'`)  
  Estado que gestiona:
  - Queue universal (modo Library): lista de pistas, índice actual.
  - Deck A y Deck B: cada uno con su propio `HTMLAudioElement` instanciado en el servicio.
  - Crossfader: número `0–1` que mapea `volumeA = 1 - t`, `volumeB = t`.
  - BehaviorSubjects para: `currentTrack$`, `isPlaying$`, `progress$`, `volume$`, `crossfader$`.
  - Métodos: `play()`, `pause()`, `seek(seconds)`, `next()`, `prev()`, `loadDeck(deck: 'A'|'B', track)`, `setCrossfader(t: number)`.  
  _⚠️ "Único core" = un servicio, no un elemento. Deck mode usa 2 `HTMLAudioElement` simultáneos._

---

## Bloque 4 — Frontend: Componentes

- [x] **4.1** Crear estructura de carpetas:
  ```
  everload-front/src/app/components/nas-music/
    nas-music.component.ts|html|css         ← wrapper con toggle Library/Deck
    library-mode/
      library-mode.component.ts|html|css
    deck-mode/
      deck-mode.component.ts|html|css
  ```

- [x] **4.2** `nas-music.component` — wrapper con botón toggle "Modo Library / Modo Deck".  
  Carga `<app-library-mode>` o `<app-deck-mode>` según el estado.

- [x] **4.3** `library-mode.component` — vista tipo Spotify:
  - Sidebar izquierdo: navegación por carpetas NAS (usa `NasService.browse()`).
  - Área central: tabla con columnas Título / Artista / Álbum / Formato / Duración.
  - Footer persistente: portada (lazy via `/api/music/cover`), barra de progreso, volumen, play/pause, anterior/siguiente.
  - Paleta: fondo `#0f0f0f`, cards `#1a1a1a`, acento `#e94560`.

- [x] **4.4** `deck-mode.component` — vista tipo DJ:
  - Sidebar compacto: browser de carpetas NAS, arrastrar/seleccionar pista → cargar en Deck A o B.
  - Deck A (izquierda) y Deck B (derecha): portada, info de pista, progress bar grande, Play/Cue.
  - Sección central: crossfader visual (slider) que llama a `musicService.setCrossfader(t)`.
  - Paleta: fondo `#0a0a0a`, paneles oscuros, acentos de neón para controles activos.

---

## Bloque 5 — Routing y navegación

- [x] **5.1** Añadir ruta en `app-routing.module.ts`:
  ```typescript
  { path: 'nas-music', component: NasMusicComponent, canActivate: [AuthGuard] }
  ```
  _Si `hasNasAccess()` existe como guard, aplicarlo también._

- [x] **5.2** Añadir enlace 🎵 NAS Music en el menú lateral de `home.component.html`  
  (visible solo si `hasNasAccess` es true, al igual que el enlace al NAS Browser).

- [x] **5.3** Registrar `NasMusicComponent`, `LibraryModeComponent`, `DeckModeComponent` en `app.module.ts`.

---

## Bloque 6 — Verificación manual

- [x] **6.1** Arrancar la app. Verificar que jaudiotagger no lanza errores en consola de Spring.
- [x] **6.2** Navegar a `/nas-music` con usuario NAS_USER/Admin.
- [x] **6.3** Modo Library: cargar carpeta → lista de canciones → reproducir → seek al 50% → siguiente pista.
- [x] **6.4** Verificar que el footer del reproductor persiste al navegar dentro de Library.
- [x] **6.5** Cambiar a Modo Deck: cargar pista en Deck A → play. Cargar pista en Deck B → play.
- [x] **6.6** Mover crossfader de A a B → el volumen de A baja mientras sube el de B.
- [x] **6.7** Verificar HTTP 206 en DevTools (Network) al hacer seek en una pista larga.
- [x] **6.8** Verificar que usuario BASIC no puede acceder a `/api/music/*` (403).

---

## Notas técnicas

- **Formatos soportados sin transcodificación**: MP3, FLAC, M4A, WAV, OGG (Chrome/Firefox modernos los soportan nativamente).
- **Cover art**: El DTO solo devuelve `hasCover: boolean`. El `<img>` usa `src="/api/music/cover?..."` directamente — el navegador cachea la respuesta.
- **Crossfader**: `volumeA = 1 - t`, `volumeB = t` donde `t ∈ [0, 1]`. No hay crossfade temporal (fade gradual), solo control de volumen instantáneo.
- **Streaming**: Usar `ResourceRegion` de Spring con `HttpRange` del header `Range`. El `Content-Type` debe ser el MIME real del archivo (audio/mpeg, audio/flac, etc.).
