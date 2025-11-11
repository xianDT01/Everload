# =========================
# 🧱 Etapa 1: Build del Frontend (Angular)
# =========================
FROM node:20 AS frontend-build
WORKDIR /app
COPY everload-front/ ./everload-front/
RUN cd everload-front \
 && npm ci \
 && npm run build -- --configuration production --base-href=/

# =========================
# ⚙️ Etapa 2: Build del Backend (Spring Boot)
# =========================
FROM maven:3.9.5-eclipse-temurin-21 AS backend-build
WORKDIR /app
COPY . .

# Asegura permisos para el wrapper, por si existe
RUN chmod +x mvnw || true

# Copia el build del front (funciona para Angular 16 y 17)
COPY --from=frontend-build /app/everload-front/dist/ /tmp/dist/
RUN mkdir -p src/main/resources/static && \
    if [ -d /tmp/dist/everload-front/browser ]; then \
        echo "📦 Copiando build desde /browser..."; \
        cp -r /tmp/dist/everload-front/browser/* src/main/resources/static/; \
    else \
        echo "📦 Copiando build desde dist/everload-front/..."; \
        cp -r /tmp/dist/everload-front/* src/main/resources/static/; \
    fi

# Compila el backend (usa mvnw si está, si no usa mvn)
RUN ./mvnw clean package -DskipTests || mvn clean package -DskipTests

# =========================
# 🚀 Etapa 3: Imagen final ligera (runtime)
# =========================
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Instala yt-dlp + ffmpeg + python3
RUN apt-get update && \
    apt-get install -y wget ffmpeg python3 && \
    wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -O /usr/local/bin/yt-dlp && \
    chmod a+rx /usr/local/bin/yt-dlp && \
    ln -s /usr/local/bin/yt-dlp /usr/bin/yt-dlp && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Variable para que el backend sepa dónde está yt-dlp
ENV everload.ytdlp.path=/usr/local/bin/yt-dlp

# Prepara config.json (para AdminConfigService)
RUN echo '{"clientId":"","clientSecret":"","apiKey":""}' > /app/config.json && \
    chmod 666 /app/config.json

# Copia el .jar compilado desde la etapa anterior
COPY --from=backend-build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
