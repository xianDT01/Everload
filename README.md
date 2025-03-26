# EverLoad - Descarga Música y Videos de Internet

**EverLoad** es una aplicación desarrollada en **Spring Boot** y **Angular 15** que permite descargar fácilmente videos y audios desde las plataformas más populares, todo de forma rápida, segura y desde tu red privada.

---

**EverLoad** is an app built with **Spring Boot** and **Angular 15** that allows you to easily download videos and audio from the most popular platforms, quickly, securely, and within your private network.

---

**EverLoad** é unha aplicación feita con **Spring Boot** e **Angular 15** que permite descargar vídeos e audios das plataformas máis populares de forma rápida, segura e desde a túa rede privada.

---

## 🚀 Características / Features / Características

- 📺 **YouTube**: descarga/download/descarga de vídeos e audios (MP3).
- 🐦 **Twitter/X**: vídeos públicos / public videos / vídeos públicos.
- 📘 **Facebook**: vídeos públicos / public videos / vídeos públicos.
- 📸 **Instagram**: Reels e posts públicos / public Reels and posts / Reels e publicacións públicas.
- 🌐 Frontend multilingüe / Multilingual frontend / Frontend multilingüe (ES, EN, GL).
- 📦 Backend con API REST en Spring Boot.
- 🖼️ UI responsive e temática por plataforma.
- 🐳 Preparado para Docker / Docker-ready / Preparado para Docker.
- 📜 Documentación con Swagger.

---

## 🛠️ Requisitos / Requirements / Requisitos

- Java 17+
- Node.js + Angular CLI
- [`yt-dlp`](https://github.com/yt-dlp/yt-dlp) instalado / installed / instalado

---

## ▶️ Ejecución Backend / Run Backend / Execución do Backend

```bash
mvn clean package
java -jar target/everload-1.0.0.jar
```

- ES: Backend disponible en `http://localhost:8080`
- EN: Backend available at `http://localhost:8080`
- GL: Backend dispoñible en `http://localhost:8080`

---

## 🌐 Ejecución Frontend / Run Frontend / Execución do Frontend

```bash
cd everload-front
npm install
ng serve
```

- ES: Frontend disponible en `http://localhost:4200`
- EN: Frontend available at `http://localhost:4200`
- GL: Frontend dispoñible en `http://localhost:4200`

---

## 🔗 API Endpoints

### 📥 YouTube

- 🎥 Video:  
  `GET /api/downloadVideo?videoId=VIDEO_ID&resolution=1080`

- 🎧 Música / Music:  
  `GET /api/downloadMusic?videoId=VIDEO_ID&format=mp3`

### 🐦 Twitter/X

`GET /api/downloadTwitter?url=URL_TWEET`

### 📘 Facebook

`GET /api/downloadFacebook?url=URL_VIDEO`

### 📸 Instagram

`GET /api/downloadInstagram?url=URL_REEL`

---

## 📜 Swagger

`http://localhost:8080/swagger-ui.html`

---

## 👤 Autor

**Xián Duán Taboada**  
🔗 [GitHub](https://github.com/xianDT01)

---

## ⚠️ Limitaciones / Limitations / Limitacións

- Instagram: Solo se permiten Reels y publicaciones públicas.  
  Only public Reels and posts are supported.  
  Só se permiten Reels e publicacións públicas.

- Facebook: Solo vídeos públicos.  
  Public videos only.  
  Só vídeos públicos.

- Twitter/X: Solo contenido público.  
  Public content only.  
  Só contido público.

---
