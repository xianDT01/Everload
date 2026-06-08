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

# Embed the current git commit hash so the app knows what revision is deployed.
# Passed from docker-compose (or deploy script) via --build-arg GIT_COMMIT=$(git rev-parse HEAD).
ARG GIT_COMMIT=unknown
RUN echo "$GIT_COMMIT" > src/main/resources/git-commit.txt

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

# Versión del binario rustypipe-botguard a instalar (ver releases en Codeberg).
ARG BOTGUARD_VERSION=v0.1.2

# Instala yt-dlp + ffmpeg + python3 + node.js (runtime JS para yt-dlp) + chromaprint
# + rustypipe-botguard: genera los tokens PO que permiten resolver streams sin
# pasar por el fallback yt-dlp (mucho más lento, ~7s por canción al arrancar).
RUN apt-get update && \
    apt-get install -y wget xz-utils ffmpeg python3 nodejs libchromaprint-tools && \
    wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -O /usr/local/bin/yt-dlp && \
    chmod a+rx /usr/local/bin/yt-dlp && \
    ln -s /usr/local/bin/yt-dlp /usr/bin/yt-dlp && \
    wget -q "https://codeberg.org/ThetaDev/rustypipe-botguard/releases/download/${BOTGUARD_VERSION}/rustypipe-botguard-${BOTGUARD_VERSION}-x86_64-unknown-linux-gnu.tar.xz" -O /tmp/botguard.tar.xz && \
    mkdir -p /tmp/botguard && tar -xJf /tmp/botguard.tar.xz -C /tmp/botguard && \
    find /tmp/botguard -type f -name 'rustypipe-botguard*' -exec install -m 0755 {} /usr/local/bin/rustypipe-botguard \; && \
    ln -s /usr/local/bin/rustypipe-botguard /usr/bin/rustypipe-botguard && \
    rm -rf /tmp/botguard /tmp/botguard.tar.xz && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Variables para que el backend sepa dónde están yt-dlp y rustypipe-botguard
ENV everload.ytdlp.path=/usr/local/bin/yt-dlp
ENV YTMUSIC_BOTGUARD_PATH=/usr/local/bin/rustypipe-botguard

# Copia el .jar compilado desde la etapa anterior
COPY --from=backend-build /app/target/*.jar app.jar

EXPOSE 8080

# Healthcheck: Spring Boot is up when /actuator/health returns 200.
# start-period gives the JVM + yt-dlp install time to finish before checks count.
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
