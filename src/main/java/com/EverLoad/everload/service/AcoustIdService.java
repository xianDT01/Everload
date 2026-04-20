package com.EverLoad.everload.service;

import com.EverLoad.everload.config.AdminConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcoustIdService {

    private final AdminConfigService adminConfigService;
    private final NasService nasService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record FingerprintResult(
            boolean found,
            String title,
            String artist,
            String album,
            boolean coverEmbedded,
            boolean tagsUpdated,
            String error
    ) {}

    public FingerprintResult identify(Long pathId, String relativePath) {
        String apiKey;
        try {
            apiKey = adminConfigService.getAcoustidApiKey();
        } catch (Exception e) {
            return fail("Error al leer la API key de AcoustID");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return fail("API key de AcoustID no configurada");
        }

        File file;
        try {
            file = nasService.resolveValidatedPath(pathId, relativePath).toFile();
        } catch (Exception e) {
            return fail("Archivo no accesible: " + relativePath);
        }

        // 1. Generar huella con fpcalc
        FpcalcResult fp = runFpcalc(file);
        if (fp == null) return fail("fpcalc no disponible o error al procesar el archivo");

        // 2. Consultar AcoustID
        JsonNode acoustidResult = queryAcoustId(fp.fingerprint(), fp.duration(), apiKey);
        if (acoustidResult == null) return fail("No se pudo conectar con AcoustID");

        JsonNode results = acoustidResult.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return new FingerprintResult(false, null, null, null, false, false, "Canción no encontrada en AcoustID");
        }

        // Mejor resultado (mayor score)
        JsonNode best = null;
        double bestScore = 0;
        for (JsonNode r : results) {
            double score = r.path("score").asDouble(0);
            if (score > bestScore) { bestScore = score; best = r; }
        }
        if (best == null || bestScore < 0.7) {
            return new FingerprintResult(false, null, null, null, false, false,
                    "Coincidencia insuficiente (score=" + String.format("%.2f", bestScore) + ")");
        }

        // 3. Extraer metadatos del primer recording
        JsonNode recordings = best.path("recordings");
        if (!recordings.isArray() || recordings.isEmpty()) {
            return new FingerprintResult(false, null, null, null, false, false, "Sin grabaciones en el resultado");
        }

        JsonNode rec = recordings.get(0);
        String title  = rec.path("title").asText(null);
        String artist = extractArtist(rec);
        String album  = extractAlbum(rec);
        String releaseGroupId = extractReleaseGroupId(rec);

        // 4. Escribir tags en el archivo si hay datos nuevos
        boolean tagsUpdated = writeTags(file, title, artist, album);

        // 5. Intentar obtener y embeber portada desde Cover Art Archive
        boolean coverEmbedded = false;
        if (releaseGroupId != null) {
            coverEmbedded = embedCoverArt(file, releaseGroupId);
        }

        log.info("[AcoustID] {} → title={} artist={} album={} cover={} score={}",
                file.getName(), title, artist, album, coverEmbedded, String.format("%.2f", bestScore));

        return new FingerprintResult(true, title, artist, album, coverEmbedded, tagsUpdated, null);
    }

    // ── fpcalc ────────────────────────────────────────────────────────────────

    private record FpcalcResult(String fingerprint, int duration) {}

    private FpcalcResult runFpcalc(File file) {
        try {
            Process p = new ProcessBuilder("fpcalc", "-json", file.getAbsolutePath())
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                output = r.lines().collect(java.util.stream.Collectors.joining("\n"));
            }
            int exit = p.waitFor();
            if (exit != 0 || output.isBlank()) return null;
            JsonNode json = mapper.readTree(output);
            String fp  = json.path("fingerprint").asText(null);
            int    dur = json.path("duration").asInt(0);
            if (fp == null || dur == 0) return null;
            return new FpcalcResult(fp, dur);
        } catch (Exception e) {
            log.warn("[AcoustID] fpcalc error: {}", e.getMessage());
            return null;
        }
    }

    // ── AcoustID API ──────────────────────────────────────────────────────────

    private JsonNode queryAcoustId(String fingerprint, int duration, String apiKey) {
        try {
            String url = "https://api.acoustid.org/v2/lookup"
                    + "?client=" + apiKey
                    + "&meta=recordings+releasegroups+compress"
                    + "&duration=" + duration
                    + "&fingerprint=" + fingerprint;
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .GET().timeout(Duration.ofSeconds(15)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return mapper.readTree(resp.body());
        } catch (Exception e) {
            log.warn("[AcoustID] API error: {}", e.getMessage());
            return null;
        }
    }

    // ── Cover Art Archive ─────────────────────────────────────────────────────

    private boolean embedCoverArt(File audioFile, String releaseGroupId) {
        try {
            String url = "https://coverartarchive.org/release-group/" + releaseGroupId + "/front-500";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .GET().timeout(Duration.ofSeconds(20)).build();
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200 || resp.body() == null || resp.body().length == 0) return false;

            byte[] imageBytes = resp.body();
            AudioFile af = AudioFileIO.read(audioFile);
            Tag tag = af.getTagOrCreateDefault();

            // Crear artwork desde bytes
            File tmpImg = File.createTempFile("cover-", ".jpg");
            try {
                Files.write(tmpImg.toPath(), imageBytes);
                Artwork artwork = ArtworkFactory.createArtworkFromFile(tmpImg);
                tag.deleteArtworkField();
                tag.setField(artwork);
            } finally {
                tmpImg.delete();
            }

            af.setTag(tag);
            AudioFileIO.write(af);
            return true;
        } catch (Exception e) {
            log.warn("[AcoustID] No se pudo embeber portada para release-group {}: {}", releaseGroupId, e.getMessage());
            return false;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private boolean writeTags(File file, String title, String artist, String album) {
        if (title == null && artist == null && album == null) return false;
        try {
            AudioFile af = AudioFileIO.read(file);
            Tag tag = af.getTagOrCreateDefault();
            boolean changed = false;
            if (title != null && !title.isBlank()) {
                String existing = tag.getFirst(FieldKey.TITLE);
                if (existing == null || existing.isBlank()) { tag.setField(FieldKey.TITLE, title); changed = true; }
            }
            if (artist != null && !artist.isBlank()) {
                String existing = tag.getFirst(FieldKey.ARTIST);
                if (existing == null || existing.isBlank()) { tag.setField(FieldKey.ARTIST, artist); changed = true; }
            }
            if (album != null && !album.isBlank()) {
                String existing = tag.getFirst(FieldKey.ALBUM);
                if (existing == null || existing.isBlank()) { tag.setField(FieldKey.ALBUM, album); changed = true; }
            }
            if (changed) { af.setTag(tag); AudioFileIO.write(af); }
            return changed;
        } catch (Exception e) {
            log.warn("[AcoustID] No se pudieron escribir tags en {}: {}", file.getName(), e.getMessage());
            return false;
        }
    }

    private String extractArtist(JsonNode rec) {
        JsonNode artists = rec.path("artists");
        if (artists.isArray() && !artists.isEmpty()) {
            return artists.get(0).path("name").asText(null);
        }
        return null;
    }

    private String extractAlbum(JsonNode rec) {
        JsonNode rgs = rec.path("releasegroups");
        if (rgs.isArray() && !rgs.isEmpty()) {
            return rgs.get(0).path("title").asText(null);
        }
        return null;
    }

    private String extractReleaseGroupId(JsonNode rec) {
        JsonNode rgs = rec.path("releasegroups");
        if (rgs.isArray() && !rgs.isEmpty()) {
            return rgs.get(0).path("id").asText(null);
        }
        return null;
    }

    private FingerprintResult fail(String error) {
        log.warn("[AcoustID] {}", error);
        return new FingerprintResult(false, null, null, null, false, false, error);
    }
}
