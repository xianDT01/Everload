# Etapa 1: Build de Angular
FROM node:20 AS frontend-build
WORKDIR /app
COPY everload-front/ ./everload-front/
RUN cd everload-front && npm install && npm run build -- --configuration production --base-href=/

# Etapa 2: Build del backend con el frontend embebido
FROM maven:3.9.5-eclipse-temurin-21 AS backend-build
WORKDIR /app
COPY . .
COPY --from=frontend-build /app/everload-front/dist/everload-front/ src/main/resources/static/
RUN ./mvnw clean package -DskipTests

# Etapa final: Imagen ligera solo con el JAR
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Instalamos yt-dlp y dependencias necesarias
RUN apt-get update && \
    apt-get install -y wget ffmpeg python3 && \
    wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -O /usr/local/bin/yt-dlp && \
    chmod a+rx /usr/local/bin/yt-dlp && \
    ln -s /usr/local/bin/yt-dlp /usr/bin/yt-dlp && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Decimos a Spring dónde está yt-dlp
ENV everload.ytdlp.path=/usr/local/bin/yt-dlp

# Copiamos el JAR desde el build anterior
COPY --from=backend-build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
