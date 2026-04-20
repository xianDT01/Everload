package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.MusicMetadataDto;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MusicService {

    private final NasService nasService;

    private static final List<String> AUDIO_EXTENSIONS =
            Arrays.asList("mp3", "flac", "m4a", "wav", "ogg", "aac", "opus", "wma", "alac");

    private static final String DJ_CACHE_DIR = "./downloads/dj_cache/";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns all directories + audio files under pathId/subPath with extracted ID3 metadata.
     */
    public List<MusicMetadataDto> listFilesWithMetadata(Long pathId, String subPath) {
        Path target = nasService.resolveValidatedPath(pathId, subPath);
        Path base   = nasService.getBasePath(pathId);

        File dir = target.toFile();
        if (!dir.exists() || !dir.isDirectory()) return Collections.emptyList();
        if (!dir.canRead()) throw new SecurityException("Sin permisos de lectura en: " + target);

        File[] files = dir.listFiles();
        if (files == null) return Collections.emptyList();

        return Arrays.stream(files)
                .filter(f -> f.isDirectory() || isAudio(f))
                .map(f -> buildDto(f, base))
                .sorted((a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                })
                .collect(Collectors.toList());
    }

    /**
     * Writes audio bytes directly to the HTTP response, supporting Range requests.
     * Bypasses Spring MVC's ResourceRegion/message-converter to avoid version-specific issues.
     */
    public void streamAudioToResponse(Long pathId, String relativePath,
                                      String rangeHeader, HttpServletResponse response) throws IOException {
        File file = resolveFile(pathId, relativePath);
        streamFileToResponse(file, rangeHeader, response);
    }

    /** Returns raw bytes of the embedded cover art, or null if not present. */
    public byte[] getCoverArt(Long pathId, String relativePath) {
        File file = resolveFile(pathId, relativePath);
        if (!isAudio(file)) return null;
        try {
            AudioFile af = AudioFileIO.read(file);
            Tag tag = af.getTag();
            if (tag != null && tag.getFirstArtwork() != null) {
                return tag.getFirstArtwork().getBinaryData();
            }
        } catch (Exception ignored) { /* file may have no tags */ }
        return null;
    }

    /** Returns cover art bytes for a folder, with priority:
     *  1. cover.jpg / cover.png file in the folder
     *  2. Embedded ID3 art from any audio file directly in the folder
     *  3. Same two checks applied to immediate subfolders (one level deep)
     */
    public byte[] getFolderCoverArt(Long pathId, String relativePath) {
        Path target = nasService.resolveValidatedPath(pathId, relativePath);
        File dir = target.toFile();
        if (!dir.exists() || !dir.isDirectory() || !dir.canRead()) return null;

        // 1. Explicit cover image file
        byte[] explicit = readCoverImageFile(dir);
        if (explicit != null) return explicit;

        File[] files = dir.listFiles();
        if (files == null) return null;

        // 2. Embedded art from audio files at root of folder
        for (File f : files) {
            if (f.isFile() && isAudio(f)) {
                String sub = buildSubPath(relativePath, f.getName());
                byte[] cover = getCoverArt(pathId, sub);
                if (cover != null && cover.length > 0) return cover;
            }
        }

        // 3. Fall back: check one level of subfolders
        for (File sub : files) {
            if (!sub.isDirectory() || !sub.canRead()) continue;
            byte[] explicit2 = readCoverImageFile(sub);
            if (explicit2 != null) return explicit2;
            File[] subFiles = sub.listFiles();
            if (subFiles == null) continue;
            for (File f : subFiles) {
                if (f.isFile() && isAudio(f)) {
                    String subRel = buildSubPath(relativePath, sub.getName() + "/" + f.getName());
                    byte[] cover = getCoverArt(pathId, subRel);
                    if (cover != null && cover.length > 0) return cover;
                }
            }
        }
        return null;
    }

    private byte[] readCoverImageFile(File dir) {
        for (String name : new String[]{"cover.jpg", "cover.png", "folder.jpg", "folder.png"}) {
            File img = new File(dir, name);
            if (img.exists() && img.isFile() && img.canRead()) {
                try { return java.nio.file.Files.readAllBytes(img.toPath()); }
                catch (IOException ignored) {}
            }
        }
        return null;
    }

    private String buildSubPath(String base, String name) {
        return (base != null && !base.isEmpty()) ? base + "/" + name : name;
    }

    // ── YouTube DJ Cache API ──────────────────────────────────────────────────

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MusicService.class);

    public void prepareYoutubeTrack(String videoId) {
        // Sanitize videoId — only allow alphanumeric, hyphens, underscores
        if (!videoId.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("videoId inválido: " + videoId);
        }

        File cacheDir = new File(DJ_CACHE_DIR);
        if (!cacheDir.exists()) cacheDir.mkdirs();

        File outputFile = new File(DJ_CACHE_DIR + videoId + ".mp3");
        if (outputFile.exists() && outputFile.length() > 0) {
            log.info("[DJ Cache] Ya cacheado: {}", videoId);
            return;
        }

        // Use Runtime.exec with single string — same pattern as DownloadService
        String command = String.format(
                "yt-dlp --ignore-errors -x --audio-format mp3 -o %s%%(id)s.%%(ext)s https://www.youtube.com/watch?v=%s",
                DJ_CACHE_DIR, videoId
        );

        log.info("[DJ Cache] Ejecutando: {}", command);

        try {
            Process process = Runtime.getRuntime().exec(command);

            // Consume stdout in a separate thread (same pattern as DownloadService)
            BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            new Thread(() -> {
                String line;
                try {
                    while ((line = errorReader.readLine()) != null) {
                        log.info("[yt-dlp DJ stderr] {}", line);
                    }
                } catch (IOException e) { /* ignore */ }
            }).start();

            String line;
            while ((line = outputReader.readLine()) != null) {
                log.info("[yt-dlp DJ stdout] {}", line);
            }

            int exitCode = process.waitFor();
            outputReader.close();
            errorReader.close();

            log.info("[DJ Cache] yt-dlp exit code: {} para videoId={}", exitCode, videoId);

            if (exitCode != 0) {
                throw new RuntimeException("yt-dlp terminó con código " + exitCode + " para videoId=" + videoId);
            }
            if (!outputFile.exists() || outputFile.length() == 0) {
                throw new RuntimeException("El archivo mp3 no se generó para videoId=" + videoId);
            }

            log.info("[DJ Cache] ✅ Listo: {} ({} bytes)", outputFile.getName(), outputFile.length());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Fallo al ejecutar yt-dlp para DJ Cache", e);
        }
    }

    public void streamYoutubeAudioToResponse(String videoId,
                                              String rangeHeader, HttpServletResponse response) throws IOException {
        File file = new File(DJ_CACHE_DIR + videoId + ".mp3");
        if (!file.exists()) throw new IllegalArgumentException("Archivo no encontrado en caché: " + videoId);
        streamFileToResponse(file, rangeHeader, response);
    }

    // ── Core streaming ────────────────────────────────────────────────────────

    /**
     * Writes a file (or byte range) directly to the HTTP response.
     * Supports the Range request header for seeking / progressive streaming.
     */
    private void streamFileToResponse(File file, String rangeHeader, HttpServletResponse response) throws IOException {
        long fileLength = file.length();
        String contentType = MediaTypeFactory
                .getMediaType(new FileSystemResource(file))
                .orElse(MediaType.APPLICATION_OCTET_STREAM)
                .toString();

        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType(contentType);

        long start = 0;
        long end   = fileLength - 1;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String rangeSpec = rangeHeader.substring(6);          // e.g. "0-" or "0-999999"
            String[] parts   = rangeSpec.split("-", 2);
            start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
            end   = (parts.length > 1 && !parts[1].isEmpty()) ? Long.parseLong(parts[1]) : fileLength - 1;
            end   = Math.min(end, fileLength - 1);

            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }

        long length = end - start + 1;
        response.setContentLengthLong(length);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             OutputStream out     = response.getOutputStream()) {
            raf.seek(start);
            byte[] buf       = new byte[65536]; // 64 KB buffer
            long   remaining = length;
            int    read;
            while (remaining > 0 &&
                   (read = raf.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                out.write(buf, 0, read);
                remaining -= read;
            }
        }
    }

    // ── Metadata write ────────────────────────────────────────────────────────

    public void updateMetadata(Long pathId, String relativePath, String title, String artist, String album, String year) {
        File file = resolveFile(pathId, relativePath);
        if (!isAudio(file)) throw new IllegalArgumentException("No es un archivo de audio");
        try {
            AudioFile af = AudioFileIO.read(file);
            Tag tag = af.getTagOrCreateDefault();
            if (title  != null) tag.setField(FieldKey.TITLE,  title);
            if (artist != null) tag.setField(FieldKey.ARTIST, artist);
            if (album  != null) tag.setField(FieldKey.ALBUM,  album);
            if (year   != null) tag.setField(FieldKey.YEAR,   year);
            af.setTag(tag);
            AudioFileIO.write(af);
        } catch (Exception e) {
            throw new RuntimeException("No se pudieron actualizar los metadatos: " + e.getMessage());
        }
    }

    // Escribe title/artist solo si faltan — usado tras descargas de YouTube
    public void ensureMetadata(File file, String fallbackTitle, String fallbackArtist) {
        if (!isAudio(file)) return;
        try {
            AudioFile af = AudioFileIO.read(file);
            Tag tag = af.getTagOrCreateDefault();
            boolean changed = false;
            String existingTitle = tag.getFirst(FieldKey.TITLE);
            if (existingTitle == null || existingTitle.isBlank()) {
                tag.setField(FieldKey.TITLE, fallbackTitle);
                changed = true;
            }
            String existingArtist = tag.getFirst(FieldKey.ARTIST);
            if (existingArtist == null || existingArtist.isBlank()) {
                tag.setField(FieldKey.ARTIST, fallbackArtist);
                changed = true;
            }
            if (changed) {
                af.setTag(tag);
                AudioFileIO.write(af);
            }
        } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private File resolveFile(Long pathId, String relativePath) {
        Path target = nasService.resolveValidatedPath(pathId, relativePath);
        File file = target.toFile();
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            throw new IllegalArgumentException("Archivo no accesible: " + relativePath);
        }
        return file;
    }

    private boolean isAudio(File f) {
        if (f.isDirectory()) return false;
        String name = f.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && AUDIO_EXTENSIONS.contains(name.substring(dot + 1));
    }

    private MusicMetadataDto buildDto(File f, Path base) {
        MusicMetadataDto.MusicMetadataDtoBuilder b = MusicMetadataDto.builder()
                .name(f.getName())
                .path(base.relativize(f.toPath()).toString())
                .directory(f.isDirectory())
                .size(f.isFile() ? f.length() : 0)
                .lastModified(formatDate(f.lastModified()));

        if (f.isDirectory()) return b.build();

        try {
            AudioFile af = AudioFileIO.read(f);
            b.format(af.getExt().toLowerCase());
            b.duration(af.getAudioHeader().getTrackLength());

            Tag tag = af.getTag();
            if (tag != null) {
                String title  = tag.getFirst(FieldKey.TITLE);
                String artist = tag.getFirst(FieldKey.ARTIST);
                String album  = tag.getFirst(FieldKey.ALBUM);
                String bpmStr = tag.getFirst(FieldKey.BPM);

                String year   = tag.getFirst(FieldKey.YEAR);

                b.title(title  != null && !title.isBlank()  ? title  : stripExtension(f.getName()));
                b.artist(artist != null ? artist : "");
                b.album(album   != null ? album  : "");
                b.year(year     != null ? year   : "");
                b.hasCover(tag.getFirstArtwork() != null);

                if (bpmStr != null && !bpmStr.isBlank()) {
                    try { b.bpm(Integer.parseInt(bpmStr.trim())); } catch (NumberFormatException ignored) {}
                }
            } else {
                b.title(stripExtension(f.getName()));
            }

        } catch (Exception e) {
            b.title(stripExtension(f.getName()))
             .format(extension(f.getName()));
        }

        return b.build();
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    private String formatDate(long epochMillis) {
        return LocalDateTime
                .ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }
}