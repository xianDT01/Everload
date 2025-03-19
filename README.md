# EverLoad - Descarga Música y Videos de YouTube

EverLoad es una aplicación en Spring Boot y Angular 15 que permite descargar música y videos de YouTube utilizando `yt-dlp`.

## 🚀 Características de la Primera Versión (v1.0.0)

- 📺 Descarga videos en distintas resoluciones.
- 🎵 Descarga música en distintos formatos.
- 🔥 Backend en Spring Boot con APIs REST.
- 🎨 Frontend en Angular 15.
- 📜 Documentación con Swagger.

## 📷 Capturas de Pantalla

### 🎵 Descarga de Música
![Descarga de música](./images/music_download.png)

### 📺 Descarga de Videos
![Descarga de videos](./images/video_download.png)

### 🌟 Interfaz del Frontend
![Interfaz principal](./images/frontend_main.png)
![Interfaz de descarga](./images/frontend_download.png)

## 🛠️ Instalación y Uso

### 🔧 Requisitos

- Java 17
- Node.js y Angular CLI
- `yt-dlp` instalado en el sistema

### ▶️ Ejecución del Backend (Spring Boot)

```bash
mvn clean package
java -jar target/everload-1.0.0.jar
```

El backend corre en `http://localhost:8080`

### 🌐 Ejecución del Frontend (Angular)

```bash
cd everload-front
npm install
ng serve
```

El frontend corre en `http://localhost:4200`

## 🛠️ Endpoints de la API

### 📥 Descargar Video

```http
GET /api/downloadVideo?videoId=VIDEO_ID&resolution=1080
```

### 🎧 Descargar Música

```http
GET /api/downloadMusic?videoId=VIDEO_ID&format=mp3
```

## 📜 Documentación Swagger

Accede a `http://localhost:8080/swagger-ui.html` para ver la documentación interactiva.
