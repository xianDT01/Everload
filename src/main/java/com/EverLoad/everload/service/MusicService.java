package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.MusicMetadataDto;
import lombok.RequiredArgsConstructor;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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

    /** 1 MB streaming chunks */
    private static final long CHUNK_SIZE = 1_000_000L;

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
     * Returns a {@link ResourceRegion} for the requested byte range, or the full first chunk
     * when no Range header is present.
     */
    public ResourceRegion streamAudio(Long pathId, String relativePath, HttpHeaders requestHeaders) {
        File file = resolveFile(pathId, relativePath);
        Resource resource = new FileSystemResource(file);

        try {
            long contentLength = resource.contentLength();
            List<HttpRange> ranges = requestHeaders.getRange();

            if (ranges.isEmpty()) {
                long length = Math.min(CHUNK_SIZE, contentLength);
                return new ResourceRegion(resource, 0, length);
            }

            HttpRange range = ranges.get(0);
            long start  = range.getRangeStart(contentLength);
            long end    = range.getRangeEnd(contentLength);
            long length = Math.min(CHUNK_SIZE, end - start + 1);
            return new ResourceRegion(resource, start, length);

        } catch (IOException e) {
            throw new RuntimeException("Error leyendo archivo de audio", e);
        }
    }

    /** Returns the full Resource (used for content-type detection or full-file delivery). */
    public Resource getAudioResource(Long pathId, String relativePath) {
        return new FileSystemResource(resolveFile(pathId, relativePath));
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

                b.title(title  != null && !title.isBlank()  ? title  : stripExtension(f.getName()));
                b.artist(artist != null ? artist : "");
                b.album(album   != null ? album  : "");
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