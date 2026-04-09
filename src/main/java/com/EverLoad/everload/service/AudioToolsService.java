package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.AudioInfoDto;
import com.EverLoad.everload.model.Download;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioToolsService {

    private static final String DOWNLOADS_DIR = "./downloads/";
    private static final long MAX_FILE_SIZE_BYTES = 500L * 1024 * 1024; // 500 MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "mp3", "m4a", "wav", "ogg", "aac", "flac", "opus", "wma", "mp4"
    );
    private static final Set<String> ALLOWED_OUTPUT_FORMATS = Set.of(
            "mp3", "m4a", "wav", "ogg", "aac", "flac"
    );
    private static final Set<String> LOSSLESS_FORMATS = Set.of("wav", "flac");

    private final DownloadHistoryService downloadHistoryService;

    // ── Public API ────────────────────────────────────────────────────────────

    public AudioInfoDto getAudioInfo(MultipartFile file) throws IOException {
        validateFile(file);
        String tempDir = createTempDir();
        File tempFile = saveToTempDir(file, tempDir);
        try {
            return probeAudioInfo(tempFile, file.getOriginalFilename());
        } finally {
            scheduleCleanup(tempDir);
        }
    }

    public ResponseEntity<FileSystemResource> convertAudio(
            MultipartFile file, String targetFormat, String bitrate) {
        validateFile(file);
        validateOutputFormat(targetFormat);

        String tempDir = createTempDir();
        File inputFile = saveToTempDir(file, tempDir);
        String baseName = getBaseName(file.getOriginalFilename());
        String outputName = baseName + "_converted." + targetFormat;
        File outputFile = new File(tempDir + outputName);

        List<String> cmd = buildConvertCommand(inputFile, outputFile, targetFormat, bitrate);
        int exitCode = runFfmpeg(cmd, tempDir);
        if (exitCode != 0 || !outputFile.exists()) {
            scheduleCleanup(tempDir);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        downloadHistoryService.recordDownload(new Download(outputName, "audio", "AudioTools"));
        return sendFile(outputFile);
    }

    public ResponseEntity<FileSystemResource> trimAudio(
            MultipartFile file, double startSec, double endSec) {
        validateFile(file);
        if (endSec <= startSec || startSec < 0) {
            return ResponseEntity.badRequest().build();
        }

        String tempDir = createTempDir();
        File inputFile = saveToTempDir(file, tempDir);
        String ext = getExtension(file.getOriginalFilename());
        String baseName = getBaseName(file.getOriginalFilename());
        String startLabel = String.format("%.0f", startSec);
        String endLabel   = String.format("%.0f", endSec);
        String outputName = baseName + "_trim_" + startLabel + "-" + endLabel + "s." + ext;
        File outputFile   = new File(tempDir + outputName);

        List<String> cmd = buildTrimCommand(inputFile, outputFile, startSec, endSec);
        int exitCode = runFfmpeg(cmd, tempDir);
        if (exitCode != 0 || !outputFile.exists()) {
            scheduleCleanup(tempDir);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        downloadHistoryService.recordDownload(new Download(outputName, "audio", "AudioTools"));
        return sendFile(outputFile);
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    private List<String> buildConvertCommand(File input, File output, String format, String bitrate) {
        List<String> cmd = new ArrayList<>(Arrays.asList("ffmpeg", "-y", "-i", input.getAbsolutePath(), "-vn"));

        switch (format) {
            case "mp3":
                cmd.addAll(Arrays.asList("-acodec", "libmp3lame"));
                addBitrate(cmd, bitrate);
                break;
            case "m4a":
                cmd.addAll(Arrays.asList("-acodec", "aac"));
                addBitrate(cmd, bitrate);
                break;
            case "aac":
                cmd.addAll(Arrays.asList("-acodec", "aac", "-f", "adts"));
                addBitrate(cmd, bitrate);
                break;
            case "ogg":
                cmd.addAll(Arrays.asList("-acodec", "libvorbis"));
                addBitrate(cmd, bitrate);
                break;
            case "wav":
                cmd.addAll(Arrays.asList("-acodec", "pcm_s16le"));
                break;
            case "flac":
                cmd.addAll(Arrays.asList("-acodec", "flac"));
                break;
        }

        cmd.add(output.getAbsolutePath());
        return cmd;
    }

    private List<String> buildTrimCommand(File input, File output, double start, double end) {
        return Arrays.asList(
                "ffmpeg", "-y",
                "-i", input.getAbsolutePath(),
                "-ss", String.valueOf(start),
                "-to", String.valueOf(end),
                "-c", "copy",
                "-avoid_negative_ts", "make_zero",
                output.getAbsolutePath()
        );
    }

    private void addBitrate(List<String> cmd, String bitrate) {
        if (bitrate != null && !bitrate.isBlank()) {
            cmd.addAll(Arrays.asList("-ab", bitrate));
        }
    }

    // ── FFprobe ───────────────────────────────────────────────────────────────

    private AudioInfoDto probeAudioInfo(File file, String originalFilename) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "error",
                    "-show_entries", "format=duration,size,bit_rate,format_name",
                    "-show_entries", "stream=codec_name,sample_rate,channels",
                    "-of", "default=noprint_wrappers=1",
                    file.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            Map<String, String> props = new LinkedHashMap<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) props.putIfAbsent(parts[0].trim(), parts[1].trim());
                }
            }
            proc.waitFor();

            double duration  = parseDouble(props.get("duration"));
            long size        = parseLong(props.get("size"), file.length());
            int bitrateKbps  = (int) (parseLong(props.get("bit_rate"), 0) / 1000);
            int sampleRate   = parseInt(props.get("sample_rate"));
            int channels     = parseInt(props.get("channels"));
            String format    = props.getOrDefault("format_name", getExtension(originalFilename));
            if (format.contains(",")) format = format.split(",")[0];

            return AudioInfoDto.builder()
                    .filename(sanitizeName(originalFilename))
                    .formatName(format)
                    .extension(getExtension(originalFilename))
                    .durationSeconds(duration)
                    .fileSizeBytes(size)
                    .bitrateKbps(bitrateKbps)
                    .sampleRate(sampleRate)
                    .channels(channels)
                    .build();

        } catch (Exception e) {
            log.error("ffprobe error for file {}: {}", file.getName(), e.getMessage());
            return AudioInfoDto.builder()
                    .filename(sanitizeName(originalFilename))
                    .extension(getExtension(originalFilename))
                    .fileSizeBytes(file.length())
                    .build();
        }
    }

    // ── FFmpeg runner ─────────────────────────────────────────────────────────

    private int runFfmpeg(List<String> cmd, String tempDir) {
        log.info("▶ ffmpeg: {}", String.join(" ", cmd));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        log.debug("ffmpeg: {}", line);
                    }
                } catch (IOException ignored) {}
            }).start();

            int exit = proc.waitFor();
            if (exit != 0) log.warn("ffmpeg exited with code {}", exit);
            return exit;
        } catch (IOException | InterruptedException e) {
            log.error("Error running ffmpeg: {}", e.getMessage());
            return -1;
        }
    }

    // ── File helpers ──────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds maximum size of 500 MB");
        }
        String ext = getExtension(file.getOriginalFilename()).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("File format not allowed: " + ext);
        }
        validateMagicBytes(file, ext);
    }

    /**
     * Validates the file's actual content against known audio format signatures.
     * Prevents disguised executable uploads (e.g., a .exe renamed to .mp3).
     * Formats without a universal magic bytes signature (m4a, aac, opus, wma, mp4)
     * are allowed through — ffprobe will reject them during processing if corrupt.
     */
    private void validateMagicBytes(MultipartFile file, String ext) {
        try {
            byte[] header = new byte[12];
            int read = file.getInputStream().read(header);
            if (read < 4) throw new IllegalArgumentException("File too small to be a valid audio file");

            switch (ext) {
                case "mp3":
                    // ID3 tag or raw MPEG sync (0xFF 0xFB/0xFA/0xF3)
                    boolean isId3   = header[0] == 'I' && header[1] == 'D' && header[2] == '3';
                    boolean isMpeg  = (header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0;
                    if (!isId3 && !isMpeg)
                        throw new IllegalArgumentException("File content does not match MP3 format");
                    break;
                case "flac":
                    if (!(header[0]=='f' && header[1]=='L' && header[2]=='a' && header[3]=='C'))
                        throw new IllegalArgumentException("File content does not match FLAC format");
                    break;
                case "wav":
                    if (!(header[0]=='R' && header[1]=='I' && header[2]=='F' && header[3]=='F'))
                        throw new IllegalArgumentException("File content does not match WAV format");
                    break;
                case "ogg":
                    if (!(header[0]=='O' && header[1]=='g' && header[2]=='g' && header[3]=='S'))
                        throw new IllegalArgumentException("File content does not match OGG format");
                    break;
                // m4a, aac, opus, wma, mp4 — no universal magic bytes; let ffprobe validate
                default:
                    break;
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not read magic bytes for validation: {}", e.getMessage());
            // Don't block the upload — ffprobe will reject corrupt files later
        }
    }

    private void validateOutputFormat(String format) {
        if (!ALLOWED_OUTPUT_FORMATS.contains(format)) {
            throw new IllegalArgumentException("Output format not supported: " + format);
        }
    }

    private String createTempDir() {
        // Use absolute path — transferTo/Files.copy may fail with relative paths
        File dir = new File(DOWNLOADS_DIR + "audio-tmp-" + UUID.randomUUID()).getAbsoluteFile();
        dir.mkdirs();
        return dir.getAbsolutePath() + "/";
    }

    private File saveToTempDir(MultipartFile file, String tempDir) {
        String safeName = sanitizeName(file.getOriginalFilename());
        if (safeName == null || safeName.isBlank()) safeName = "audio_input";
        // Ensure extension is preserved
        if (!safeName.contains(".") && file.getOriginalFilename() != null) {
            safeName += "." + getExtension(file.getOriginalFilename());
        }
        File dest = new File(tempDir + safeName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to save uploaded file '{}': {}", safeName, e.getMessage());
            throw new RuntimeException("Failed to save uploaded file", e);
        }
        log.info("📥 Saved upload: {} ({} bytes)", dest.getAbsolutePath(), dest.length());
        return dest;
    }

    private ResponseEntity<FileSystemResource> sendFile(File file) {
        HttpHeaders headers = new HttpHeaders();
        String safeName = sanitizeName(file.getName());
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.length()));

        log.info("📤 Sending audio file: {}", file.getAbsolutePath());
        scheduleCleanup(file.getParent() + "/");

        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(file));
    }

    private void scheduleCleanup(String dirPath) {
        new Thread(() -> {
            try {
                Thread.sleep(15000);
                Files.walk(Path.of(dirPath))
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(f -> {
                            if (!f.delete()) log.debug("Could not delete: {}", f.getAbsolutePath());
                        });
            } catch (InterruptedException | IOException e) {
                log.warn("Cleanup failed for {}: {}", dirPath, e.getMessage());
            }
        }).start();
    }

    // ── String helpers ────────────────────────────────────────────────────────

    private String getExtension(String filename) {
        if (filename == null) return "audio";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "audio";
    }

    private String getBaseName(String filename) {
        if (filename == null) return "audio";
        int dot = filename.lastIndexOf('.');
        String name = dot >= 0 ? filename.substring(0, dot) : filename;
        return sanitizeName(name);
    }

    private String sanitizeName(String name) {
        if (name == null) return "audio";
        return name.replaceAll("[^a-zA-Z0-9._\\-() ]", "_").trim();
    }

    private double parseDouble(String s) {
        try { return s != null ? Double.parseDouble(s) : 0.0; } catch (NumberFormatException e) { return 0.0; }
    }

    private long parseLong(String s, long fallback) {
        try { return s != null ? Long.parseLong(s) : fallback; } catch (NumberFormatException e) { return fallback; }
    }

    private int parseInt(String s) {
        try { return s != null ? Integer.parseInt(s) : 0; } catch (NumberFormatException e) { return 0; }
    }
}
