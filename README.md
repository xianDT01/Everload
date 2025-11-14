# EverLoad - Descarga Música y Videos de Internet

**EverLoad** es una aplicación desarrollada en **Spring Boot** y
**Angular 15** que permite descargar fácilmente videos y audios desde
las plataformas más populares, todo de forma rápida, segura y desde tu
red privada.

**EverLoad** is an app built with **Spring Boot** and **Angular 15**
that allows you to easily download videos and audio from the most
popular platforms, quickly, securely, and within your private network.

**EverLoad** é unha aplicación feita con **Spring Boot** e **Angular
15** que permite descargar vídeos e audios das plataformas máis
populares de forma rápida, segura e desde a túa rede privada.

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
```

------------------------------------------------------------------------

## 🚀 Características / Features / Características

-   📺 **YouTube**: descarga / download / descarga de vídeos e audios
    (MP3).\
    ➕ También se pueden descargar playlists completas.
-   🐦 **Twitter/X**: vídeos públicos.
-   📘 **Facebook**: vídeos públicos.
-   📸 **Instagram**: Reels e posts públicos.
-   🎧 **Spotify**: descarga automática de canciones a partir de una
    playlist.
-   🎵 **TikTok**: descarga directa pegando la URL.
-   🌐 Frontend multilingüe: ES, EN, GL.
-   📦 Backend con API REST.
-   🖼️ UI responsive y temática por plataforma.
-   🐳 Preparado para Docker.
-   📜 Documentación Swagger integrada.
-   🛡️ **Panel de administración** avanzado para gestión interna:
    -   Configuración de claves (API Keys, Client ID/Secret).
    -   Actualización de `yt-dlp`.
    -   Limpiar temporales.
    -   Limpiar historial.
    -   Ver y filtrar logs.
    -   Comprobar estado de APIs externas (YouTube, Spotify, TikTok,
        Facebook, Instagram).

------------------------------------------------------------------------

## 🛠️ Requisitos / Requirements / Requisitos

-   Java 17+
-   Node.js + Angular CLI
-   [`yt-dlp`](https://github.com/yt-dlp/yt-dlp)

------------------------------------------------------------------------

## ▶️ Ejecución Backend / Run Backend / Execución do Backend

``` bash
mvn clean package
java -jar target/everload-1.0.0.jar
```

-   Backend en `http://localhost:8080`

------------------------------------------------------------------------

## 🌐 Ejecución Frontend / Run Frontend / Execución do Frontend

``` bash
cd everload-front
npm install
ng serve
```

-   Frontend en `http://localhost:4200`

------------------------------------------------------------------------

## 🧰 Panel de administración / Admin Panel / Panel de administración

EverLoad incluye un **panel de administración completo** accesible desde
la interfaz web.\
Permite gestionar todo sin tocar archivos manualmente:

### 🔑 Configuración interna (`config.json`)

-   `clientId`
-   `clientSecret`
-   `apiKey`

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

Pruebas automáticas de: - YouTube\
- Spotify\
- TikTok\
- Facebook\
- Instagram
- Youtube

Devuelven `OK` o `ERROR` con detalles si falla.

> El panel está diseñado para uso local. No se recomienda exponerlo.

------------------------------------------------------------------------

## 🔗 API Endpoints

### 📥 YouTube

-   Vídeo:\
    `GET /api/downloadVideo?videoId=ID&resolution=1080`
-   Música:\
    `GET /api/downloadMusic?videoId=ID&format=mp3`

### 🐦 Twitter/X

`GET /api/downloadTwitter?url=URL`

### 📘 Facebook

`GET /api/downloadFacebook?url=URL`

### 📸 Instagram

`GET /api/downloadInstagram?url=URL`

### 🎵 TikTok

`GET /api/downloadTikTok?url=URL`

### 🎧 Spotify

`POST /api/spotify/playlist`\
Body:

``` json
{
  "playlistUrl": "https://open.spotify.com/playlist/..."
}
```

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

**Xián Duán Taboada**\
🔗 GitHub: https://github.com/xianDT01

------------------------------------------------------------------------

## ⚠️ Limitaciones

-   Instagram, Facebook y Twitter/X: solo contenido público.\
-   Spotify: no descarga desde Spotify, busca los títulos en YouTube.

------------------------------------------------------------------------

## 📝 Licencia

Proyecto bajo licencia MIT.\
Puedes usarlo, modificarlo y distribuirlo con atribución:\
**Xián Duán Taboada -- xiandt01@gmail.com**

Consulta [LICENSE](./LICENSE) para más detalles.
