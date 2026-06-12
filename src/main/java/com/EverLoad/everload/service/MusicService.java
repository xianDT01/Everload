package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.MusicMetadataDto;
import com.EverLoad.everload.dto.PagedMusicResult;
import com.EverLoad.everload.model.NasPath;
import com.EverLoad.everload.model.TrackMetadataCache;
import com.EverLoad.everload.repository.NasPathRepository;
import com.EverLoad.everload.repository.TrackMetadataCacheRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MusicService {

    private final NasService nasService;
    private final NasPathRepository nasPathRepository;
    private final TrackMetadataCacheRepository metadataCacheRepo;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final List<String> AUDIO_EXTENSIONS =
            Arrays.asList("mp3", "flac", "m4a", "wav", "ogg", "aac", "opus", "wma", "alac");

    private static final String DJ_CACHE_DIR = "./downloads/dj_cache/";
    private static final String TRANSCODE_CACHE_DIR = "./downloads/transcode-cache/";
    private static final Set<String> LOSSLESS_EXTS = Set.of("flac", "wav", "aiff", "aif", "alac");
    private static final Set<String> ALREADY_OPUS = Set.of("ogg", "opus");
    private static final long BROWSE_RESULT_TTL_MS = 5 * 60_000L;
    private final ConcurrentHashMap<String, Object[]> browseResultCache = new ConcurrentHashMap<>();
    private final java.util.Set<String> transcoding = ConcurrentHashMap.newKeySet();
    private final ExecutorService transcodePool = Executors.newFixedThreadPool(2);
    private static final long STREAM_CHUNK_SIZE_BYTES = 8L * 1024L * 1024L;
    private static final int STREAM_BUFFER_SIZE_BYTES = 256 * 1024;
    private static final Pattern HLS_SEGMENT_NAME = Pattern.compile("[A-Za-z0-9._-]+\\.(ts|m4s|aac|vtt)");
    private static final int SEARCH_SCAN_LIMIT = 20000;
    private static final int SEARCH_CACHE_CHUNK_SIZE = 700;
    private static final int SEARCH_DEEP_METADATA_LIMIT = 350;
    private static final long DIRECTORY_LISTING_CACHE_TTL_MS = 15_000L;
    private static final int DIRECTORY_LISTING_CACHE_MAX = 512;
    private static final int METADATA_WARMUP_LIMIT_PER_PAGE = 80;

    @Value("${music.hls.cache-dir:./hls-cache}")
    private String hlsCacheDir;

    @Value("${music.hls.min-duration-seconds:1200}")
    private int hlsMinDurationSeconds;

    @Value("${music.hls.min-size-bytes:83886080}")
    private long hlsMinSizeBytes;

    @Value("${avatar.storage.path:./avatars}")
    private String avatarStoragePath;

    private final Map<String, HlsCacheJob> hlsJobs = new ConcurrentHashMap<>();
    private final Map<String, CachedDirectoryListing> directoryListingCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<String>> artistImageLookupCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<String>> albumCoverLookupCache = new ConcurrentHashMap<>();
    private final Set<String> metadataWarmupInFlight = ConcurrentHashMap.newKeySet();
    private final Set<Long> libraryIndexInFlight = ConcurrentHashMap.newKeySet();
    private final ExecutorService hlsExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "everload-hls-cache");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService metadataExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "everload-metadata-cache");
        t.setDaemon(true);
        return t;
    });

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns `count` random audio tracks (with covers preferred) across all NAS paths.
     * Walks up to 4 levels deep and stops collecting after 300 candidates for performance.
     */
    public List<MusicMetadataDto> getRandomTracks(int count) {
        List<NasPath> paths = nasPathRepository.findAll();
        List<MusicMetadataDto> candidates = new ArrayList<>();

        for (NasPath nasPath : paths) {
            File root = new File(nasPath.getPath());
            if (!root.exists() || !root.isDirectory() || !root.canRead()) continue;
            collectAudioFiles(root, nasPath.getId(), root.toPath(), candidates, 4, 300);
        }

        List<MusicMetadataDto> withCovers = candidates.stream()
                .filter(MusicMetadataDto::isHasCover)
                .collect(Collectors.toList());

        List<MusicMetadataDto> pool = withCovers.isEmpty() ? candidates : withCovers;
        Collections.shuffle(pool);
        return pool.stream().limit(count).collect(Collectors.toList());
    }

    public Map<String, Object> getLibraryOverview(Long pathId, int limit) {
        List<MusicMetadataDto> tracks = metadataCacheRepo.findByNasPathId(pathId).stream()
                .sorted(Comparator
                        .comparing((TrackMetadataCache c) -> safeLower(c.getArtist()))
                        .thenComparing(c -> safeLower(c.getAlbum()))
                        .thenComparing(c -> safeLower(c.getTitle())))
                .limit(Math.max(1, limit))
                .map(this::dtoFromCache)
                .collect(Collectors.toList());

        if (tracks.isEmpty()) {
            startLibraryIndex(pathId);
        }

        return Map.of(
                "tracks", tracks,
                "indexing", libraryIndexInFlight.contains(pathId)
        );
    }

    public List<MusicMetadataDto> getRecentTracks(Long pathId, int limit) {
        List<MusicMetadataDto> tracks = metadataCacheRepo.findByNasPathId(pathId).stream()
                .sorted(Comparator.comparingLong(TrackMetadataCache::getLastModified).reversed())
                .limit(Math.max(1, limit))
                .map(this::dtoFromCache)
                .collect(Collectors.toList());

        if (tracks.isEmpty()) {
            startLibraryIndex(pathId);
        }

        return tracks;
    }

    public Map<String, Object> startLibraryIndex(Long pathId) {
        Path base = nasService.getBasePath(pathId);
        if (!Files.isDirectory(base) || !Files.isReadable(base)) {
            throw new IllegalArgumentException("Ruta NAS no accesible");
        }

        if (!libraryIndexInFlight.add(pathId)) {
            return Map.of("started", false, "indexing", true);
        }

        metadataExecutor.submit(() -> {
            try {
                indexLibrary(pathId, base);
            } finally {
                libraryIndexInFlight.remove(pathId);
            }
        });

        return Map.of("started", true, "indexing", true);
    }

    public List<MusicMetadataDto> getCachedTracksByArtist(Long pathId, String artist, List<String> aliases, int limit) {
        List<String> keys = new ArrayList<>();
        if (artist != null && !artist.isBlank()) keys.add(normalizeSearchText(artist));
        if (aliases != null) {
            aliases.stream()
                    .filter(a -> a != null && !a.isBlank())
                    .map(this::normalizeSearchText)
                    .filter(a -> !a.isBlank())
                    .forEach(keys::add);
        }
        keys = keys.stream().filter(k -> !k.isBlank()).distinct().collect(Collectors.toList());
        if (keys.isEmpty()) return Collections.emptyList();

        List<String> finalKeys = keys;
        return metadataCacheRepo.findByNasPathId(pathId).stream()
                .filter(c -> artistParts(c.getArtist()).stream().anyMatch(finalKeys::contains))
                .sorted(Comparator
                        .comparing((TrackMetadataCache c) -> safeLower(c.getAlbum()))
                        .thenComparing(c -> safeLower(c.getTitle()))
                        .thenComparing(c -> safeLower(c.getRelativePath())))
                .limit(Math.max(1, limit))
                .map(this::dtoFromCache)
                .collect(Collectors.toList());
    }

    public Map<String, Object> clearMemoryCaches() {
        int imgCount = artistImageLookupCache.size();
        int dirCount = directoryListingCache.size();
        int coverCount = albumCoverLookupCache.size();
        artistImageLookupCache.clear();
        directoryListingCache.clear();
        albumCoverLookupCache.clear();
        return Map.of(
                "artistImageCacheCleared", imgCount,
                "directoryListingCacheCleared", dirCount,
                "albumCoverCacheCleared", coverCount
        );
    }

    public Map<String, Object> lookupArtistImage(String artist) {
        String normalized = normalizeSearchText(artist);
        if (normalized.isBlank() || normalized.length() < 2 || isSuspiciousArtistName(normalized)) {
            return Map.of("found", false);
        }

        // Only successful lookups are cached — failures are always retried
        Optional<String> inCache = artistImageLookupCache.get(normalized);
        if (inCache != null) {
            return inCache.<Map<String, Object>>map(url -> Map.of("found", true, "imageUrl", url))
                    .orElseGet(() -> Map.of("found", false));
        }

        String safeFilename = normalized.replace(' ', '_') + ".jpg";
        Path autoDir = getArtistAutoImageDir();
        Path filePath = autoDir.resolve(safeFilename).normalize();
        if (filePath.startsWith(autoDir) && Files.exists(filePath)) {
            String localUrl = "/api/music/artist-auto-image/" + safeFilename;
            artistImageLookupCache.put(normalized, Optional.of(localUrl));
            return Map.of("found", true, "imageUrl", localUrl);
        }

        try {
            String url = "https://api.deezer.com/search/artist?q=" + encodeUrl(artist) + "&limit=8";
            Map<?, ?> response = restTemplate.getForObject(url, Map.class);
            Object data = response != null ? response.get("data") : null;
            if (data instanceof List<?> artists) {
                String fallbackImage = "";
                for (Object item : artists) {
                    if (!(item instanceof Map<?, ?> artistMap)) continue;
                    String name = stringValue(artistMap.get("name"));
                    String image = firstNonBlank(
                            stringValue(artistMap.get("picture_xl")),
                            stringValue(artistMap.get("picture_big")),
                            stringValue(artistMap.get("picture_medium"))
                    );
                    if (image.isBlank()) continue;
                    if (normalizeSearchText(name).equals(normalized)) {
                        String local = downloadAutoImage(image, safeFilename, autoDir);
                        if (!local.isBlank()) {
                            artistImageLookupCache.put(normalized, Optional.of(local));
                            return Map.of("found", true, "imageUrl", local);
                        }
                    }
                    if (fallbackImage.isBlank()) fallbackImage = image;
                }
                if (!fallbackImage.isBlank()) {
                    String local = downloadAutoImage(fallbackImage, safeFilename, autoDir);
                    if (!local.isBlank()) {
                        artistImageLookupCache.put(normalized, Optional.of(local));
                        return Map.of("found", true, "imageUrl", local);
                    }
                }
            }
        } catch (Exception ignored) {}

        // Don't cache failure — will be retried on next request
        return Map.of("found", false);
    }

    private String downloadAutoImage(String deezorUrl, String filename, Path dir) {
        try {
            Files.createDirectories(dir);
            byte[] bytes = restTemplate.getForObject(deezorUrl, byte[].class);
            if (bytes == null || bytes.length == 0) return "";
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(dir)) return "";
            Files.write(target, bytes);
            return "/api/music/artist-auto-image/" + filename;
        } catch (Exception ignored) {}
        return "";
    }

    public Path getArtistAutoImageDir() {
        return Path.of(avatarStoragePath, "artists-auto").normalize();
    }

    public Path getAlbumCoverAutoDir() {
        return Path.of(avatarStoragePath, "covers-auto").normalize();
    }

    public Map<String, Object> lookupAlbumCover(String artist, String album) {
        String normArtist = normalizeSearchText(artist == null ? "" : artist);
        String normAlbum = normalizeSearchText(album == null ? "" : album);
        if (normAlbum.isBlank()) return Map.of("found", false);

        String cacheKey = normArtist + "|" + normAlbum;
        Optional<String> cached = albumCoverLookupCache.computeIfAbsent(cacheKey, k -> {
            String namePart = (normArtist.isBlank() ? normAlbum : normArtist + "__" + normAlbum).replace(' ', '_');
            if (namePart.length() > 180) namePart = namePart.substring(0, 180);
            String safeFilename = namePart + ".jpg";

            Path coverDir = getAlbumCoverAutoDir();
            Path filePath = coverDir.resolve(safeFilename).normalize();
            if (filePath.startsWith(coverDir) && Files.exists(filePath)) {
                return Optional.of("/api/music/album-auto-cover/" + safeFilename);
            }
            try {
                String mbQuery = "release:" + encodeUrl(normAlbum)
                        + (normArtist.isBlank() ? "" : "+artist:" + encodeUrl(normArtist));
                String mbUrl = "https://musicbrainz.org/ws/2/release/?query=" + mbQuery + "&fmt=json&limit=5";

                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set("User-Agent", "EverLoad/1.0 (music-player; contact@everload.app)");
                var req = new org.springframework.http.HttpEntity<>(headers);
                var resp = restTemplate.exchange(mbUrl, org.springframework.http.HttpMethod.GET, req, Map.class);
                Map<?, ?> body = resp.getBody();
                Object releases = body != null ? body.get("releases") : null;
                if (!(releases instanceof List<?> list) || list.isEmpty()) return Optional.empty();

                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> release)) continue;
                    String mbid = stringValue(release.get("id"));
                    if (mbid.isBlank()) continue;
                    try {
                        String coverUrl = "https://coverartarchive.org/release/" + mbid + "/front-250";
                        byte[] bytes = restTemplate.getForObject(coverUrl, byte[].class);
                        if (bytes != null && bytes.length > 5000) {
                            String local = downloadAlbumCoverImage(bytes, safeFilename, coverDir);
                            if (!local.isBlank()) return Optional.of(local);
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            return Optional.empty();
        });

        return cached
                .<Map<String, Object>>map(url -> Map.of("found", true, "imageUrl", url))
                .orElseGet(() -> Map.of("found", false));
    }

    private String downloadAlbumCoverImage(byte[] bytes, String filename, Path dir) {
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(dir)) return "";
            Files.write(target, bytes);
            return "/api/music/album-auto-cover/" + filename;
        } catch (Exception ignored) {}
        return "";
    }

    public int purgeOrphanedAutoImages() {
        Path autoDir = getArtistAutoImageDir();
        if (!Files.exists(autoDir)) return 0;
        // Build the set of filenames that correspond to artists still in the cache
        Set<String> activeFilenames = metadataCacheRepo.findAll().stream()
                .map(entry -> normalizeSearchText(entry.getArtist()))
                .filter(n -> !n.isBlank())
                .map(n -> n.replace(' ', '_') + ".jpg")
                .collect(Collectors.toSet());
        int removed = 0;
        try (var stream = Files.list(autoDir)) {
            for (Path file : stream.toList()) {
                if (!Files.isRegularFile(file)) continue;
                if (!activeFilenames.contains(file.getFileName().toString())) {
                    Files.deleteIfExists(file);
                    artistImageLookupCache.entrySet().removeIf(e ->
                            e.getValue().map(u -> u.endsWith(file.getFileName().toString())).orElse(false));
                    removed++;
                }
            }
        } catch (Exception ignored) {}
        return removed;
    }

    private void indexLibrary(Long pathId, Path base) {
        File root = base.toFile();
        List<File> audioFiles = new ArrayList<>();
        collectAudioFilesForSearch(root, audioFiles, SEARCH_SCAN_LIMIT);
        Map<String, TrackMetadataCache> cacheMap = batchFetchCacheChunked(pathId, audioFiles, base);
        Set<String> foundPaths = new HashSet<>();

        for (File file : audioFiles) {
            String relPath = relativePath(base, file);
            foundPaths.add(relPath);
            TrackMetadataCache cached = cacheMap.get(relPath);
            if (validCache(cached, file) != null) continue;
            buildDto(file, base, pathId, Collections.singletonMap(relPath, cached), true);
        }

        try {
            List<TrackMetadataCache> stale = metadataCacheRepo.findByNasPathId(pathId).stream()
                    .filter(entry -> !foundPaths.contains(entry.getRelativePath()))
                    .collect(Collectors.toList());
            if (!stale.isEmpty()) metadataCacheRepo.deleteAll(stale);
        } catch (Exception ignored) {}
    }

    private void collectAudioFiles(File dir, Long pathId, Path base, List<MusicMetadataDto> out, int maxDepth, int maxFiles) {
        if (maxDepth < 0 || out.size() >= maxFiles) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (out.size() >= maxFiles) break;
            if (f.isDirectory()) {
                collectAudioFiles(f, pathId, base, out, maxDepth - 1, maxFiles);
            } else if (f.isFile() && isAudio(f)) {
                MusicMetadataDto dto = buildDto(f, base, pathId, null);
                dto.setNasPathId(pathId);
                out.add(dto);
            }
        }
    }

    /**
     * Returns directories + a page of audio files under pathId/subPath with extracted ID3 metadata.
     * Directories are always included on page 0 (no ID3 reading needed, fast).
     * Audio tracks are read in batches of `size` to avoid blocking on large folders.
     */
    public PagedMusicResult listFilesWithMetadata(Long pathId, String subPath, int page, int size) {
        String cacheKey = pathId + "|" + subPath + "|" + page + "|" + size;
        Object[] cached = browseResultCache.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - (long) cached[1] < BROWSE_RESULT_TTL_MS) {
            return (PagedMusicResult) cached[0];
        }

        Path target = nasService.resolveValidatedPath(pathId, subPath);
        Path base   = nasService.getBasePath(pathId);

        File dir = target.toFile();
        if (!dir.exists() || !dir.isDirectory()) return new PagedMusicResult(Collections.emptyList(), 0, page, size);
        if (!dir.canRead()) throw new SecurityException("Sin permisos de lectura en: " + target);

        CachedDirectoryListing listing = getCachedDirectoryListing(pathId, subPath, dir);
        List<File> dirs = listing.dirs;
        List<File> audioFiles = listing.audioFiles;

        int totalTracks = audioFiles.size();
        int fromIdx = page * size;
        int toIdx   = Math.min(fromIdx + size, totalTracks);

        List<MusicMetadataDto> items = new ArrayList<>();
        if (page == 0) {
            dirs.stream().map(f -> buildDto(f, base)).forEach(items::add);
        }
        if (fromIdx < totalTracks) {
            List<File> pageFiles = audioFiles.subList(fromIdx, toIdx);
            Map<String, TrackMetadataCache> cacheMap = batchFetchCache(pathId, pageFiles, base);
            pageFiles.stream().map(f -> buildDto(f, base, pathId, cacheMap, false)).forEach(items::add);
            warmMetadataCacheAsync(pathId, pageFiles, base, cacheMap);
        }

        PagedMusicResult result = new PagedMusicResult(items, totalTracks, page, size);
        if (browseResultCache.size() > 500) browseResultCache.clear();
        browseResultCache.put(cacheKey, new Object[]{result, System.currentTimeMillis()});
        return result;
    }

    public void invalidateBrowseCache(Long pathId) {
        browseResultCache.keySet().removeIf(k -> k.startsWith(pathId + "|"));
    }

    /**
     * Writes audio bytes directly to the HTTP response, supporting Range requests.
     * Bypasses Spring MVC's ResourceRegion/message-converter to avoid version-specific issues.
     */
    public void streamAudioToResponse(Long pathId, String relativePath,
                                      String rangeHeader, HttpServletResponse response) throws IOException {
        streamAudioToResponse(pathId, relativePath, rangeHeader, "original", response);
    }

    public Map<String, Object> prepareHlsStream(Long pathId, String relativePath) {
        File file = resolveFile(pathId, relativePath);
        HlsCacheJob job = buildHlsJob(pathId, relativePath, file);

        if (!job.eligible) {
            return hlsJobResponse(job);
        }

        if (isHlsReady(job)) {
            job.status = "READY";
            job.progress = 100;
            job.error = null;
            return hlsJobResponse(job);
        }

        if (!"RUNNING".equals(job.status)) {
            startHlsJob(job, file);
        }

        return hlsJobResponse(job);
    }

    public Map<String, Object> getHlsStatus(Long pathId, String relativePath) {
        File file = resolveFile(pathId, relativePath);
        HlsCacheJob job = buildHlsJob(pathId, relativePath, file);
        if (isHlsReady(job)) {
            job.status = "READY";
            job.progress = 100;
            job.error = null;
        }
        return hlsJobResponse(job);
    }

    public String getHlsPlaylist(Long pathId, String relativePath, String token) throws IOException {
        File file = resolveFile(pathId, relativePath);
        HlsCacheJob job = buildHlsJob(pathId, relativePath, file);
        if (!isHlsReady(job)) {
            throw new IllegalStateException("HLS todavia no esta preparado");
        }

        String playlist = Files.readString(job.dir.resolve("index.m3u8"), StandardCharsets.UTF_8);
        String pathIdParam = String.valueOf(pathId);
        String subPathParam = encodeUrl(relativePath);
        String tokenParam = token != null && !token.isBlank() ? "&token=" + encodeUrl(token) : "";

        return Arrays.stream(playlist.split("\\R", -1))
                .map(line -> {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return line;
                    return "/api/music/hls/segment?pathId=" + pathIdParam
                            + "&subPath=" + subPathParam
                            + "&segment=" + encodeUrl(trimmed)
                            + tokenParam;
                })
                .collect(Collectors.joining("\n"));
    }

    public void streamHlsSegmentToResponse(Long pathId, String relativePath, String segment,
                                           HttpServletResponse response) throws IOException {
        File file = resolveFile(pathId, relativePath);
        HlsCacheJob job = buildHlsJob(pathId, relativePath, file);
        if (!isHlsReady(job)) throw new IllegalStateException("HLS no preparado");
        if (segment == null || !HLS_SEGMENT_NAME.matcher(segment).matches()) {
            throw new SecurityException("Segmento HLS invalido");
        }

        Path segmentPath = job.dir.resolve(segment).normalize();
        if (!segmentPath.startsWith(job.dir) || !Files.exists(segmentPath) || !Files.isRegularFile(segmentPath)) {
            throw new IllegalArgumentException("Segmento HLS no encontrado");
        }

        response.setHeader("Cache-Control", "private, max-age=86400");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType(segment.endsWith(".aac") ? "audio/aac" : "video/mp2t");
        response.setContentLengthLong(Files.size(segmentPath));

        try (OutputStream out = response.getOutputStream()) {
            Files.copy(segmentPath, out);
            out.flush();
        } catch (IOException e) {
            if (isClientAbort(e)) return;
            throw e;
        }
    }

    private HlsCacheJob buildHlsJob(Long pathId, String relativePath, File file) {
        String key = hlsCacheKey(pathId, relativePath, file);
        return hlsJobs.computeIfAbsent(key, ignored -> {
            int duration = readDurationSeconds(file);
            boolean eligible = duration >= hlsMinDurationSeconds || file.length() >= hlsMinSizeBytes;
            Path dir = Path.of(hlsCacheDir).resolve(key).normalize();
            HlsCacheJob job = new HlsCacheJob();
            job.key = key;
            job.dir = dir;
            job.durationSeconds = duration;
            job.fileSizeBytes = file.length();
            job.eligible = eligible;
            job.status = eligible ? (Files.exists(dir.resolve("index.m3u8")) ? "READY" : "IDLE") : "DIRECT";
            job.progress = "READY".equals(job.status) ? 100 : 0;
            return job;
        });
    }

    private Map<String, Object> hlsJobResponse(HlsCacheJob job) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", job.key);
        body.put("eligible", job.eligible);
        body.put("status", job.status);
        body.put("ready", "READY".equals(job.status));
        body.put("progress", job.progress);
        body.put("durationSeconds", job.durationSeconds);
        body.put("fileSizeBytes", job.fileSizeBytes);
        if (job.error != null && !job.error.isBlank()) body.put("error", job.error);
        return body;
    }

    private boolean isHlsReady(HlsCacheJob job) {
        return job.eligible && Files.exists(job.dir.resolve("index.m3u8"));
    }

    private void startHlsJob(HlsCacheJob job, File file) {
        job.status = "RUNNING";
        job.progress = Math.max(job.progress, 5);
        job.error = null;

        hlsExecutor.submit(() -> {
            Path tmpDir = Path.of(hlsCacheDir).resolve(job.key + ".tmp").normalize();
            try {
                deleteDirectory(tmpDir);
                Files.createDirectories(tmpDir);

                Path playlist = tmpDir.resolve("index.m3u8");
                Path segmentPattern = tmpDir.resolve("seg_%05d.ts");
                List<String> cmd = Arrays.asList(
                        "ffmpeg", "-y",
                        "-i", file.getAbsolutePath(),
                        "-vn",
                        "-map", "0:a:0",
                        "-c:a", "aac",
                        "-b:a", "160k",
                        "-ac", "2",
                        "-ar", "44100",
                        "-f", "hls",
                        "-hls_time", "6",
                        "-hls_playlist_type", "vod",
                        "-hls_flags", "independent_segments",
                        "-hls_segment_filename", segmentPattern.toString(),
                        playlist.toString()
                );

                runHlsFfmpeg(cmd, job);

                if (!Files.exists(playlist)) {
                    throw new IOException("ffmpeg no genero la playlist HLS");
                }

                deleteDirectory(job.dir);
                Files.createDirectories(job.dir.getParent());
                Files.move(tmpDir, job.dir, StandardCopyOption.REPLACE_EXISTING);
                job.status = "READY";
                job.progress = 100;
            } catch (Exception e) {
                job.status = "FAILED";
                job.progress = 0;
                job.error = e.getMessage();
                try { deleteDirectory(tmpDir); } catch (IOException ignored) {}
                log.warn("HLS cache failed for {}: {}", file.getName(), e.getMessage());
            }
        });
    }

    private void runHlsFfmpeg(List<String> cmd, HlsCacheJob job) throws IOException, InterruptedException {
        log.info("Preparing HLS stream: {}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                updateHlsProgressFromFfmpeg(line, job);
                log.debug("hls-ffmpeg: {}", line);
            }
        }

        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("ffmpeg termino con codigo " + exit);
        }
    }

    private void updateHlsProgressFromFfmpeg(String line, HlsCacheJob job) {
        int idx = line.indexOf("time=");
        if (idx < 0 || job.durationSeconds <= 0) return;
        int end = line.indexOf(' ', idx + 5);
        String time = line.substring(idx + 5, end > idx ? end : line.length()).trim();
        int seconds = parseFfmpegTimeSeconds(time);
        if (seconds <= 0) return;
        int pct = Math.max(5, Math.min(95, (int) ((seconds * 100.0) / job.durationSeconds)));
        job.progress = Math.max(job.progress, pct);
    }

    private int parseFfmpegTimeSeconds(String value) {
        try {
            String[] parts = value.split(":");
            if (parts.length != 3) return 0;
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            double seconds = Double.parseDouble(parts[2]);
            return (int) Math.floor(hours * 3600 + minutes * 60 + seconds);
        } catch (Exception e) {
            return 0;
        }
    }

    private int readDurationSeconds(File file) {
        try {
            AudioFile af = AudioFileIO.read(file);
            return af.getAudioHeader().getTrackLength();
        } catch (Exception e) {
            return 0;
        }
    }

    private String hlsCacheKey(Long pathId, String relativePath, File file) {
        String raw = pathId + "|" + relativePath + "|" + file.lastModified() + "|" + file.length();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < Math.min(hash.length, 16); i++) {
                out.append(String.format("%02x", hash[i]));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private String encodeUrl(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            List<Path> sorted = paths
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
            for (Path path : sorted) {
                Files.deleteIfExists(path);
            }
        }
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
        } catch (Exception ignored) {}
        // Fallback: look for cover image in the same directory
        File dir = file.getParentFile();
        if (dir != null && dir.isDirectory()) {
            byte[] fallback = readCoverImageFile(dir);
            if (fallback != null) return fallback;
        }
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

        String[] cmd = {
            "yt-dlp",
            "--js-runtimes", "nodejs",
            "--ignore-errors",
            "-x", "--audio-format", "mp3", "--audio-quality", "0",
            "--embed-thumbnail",
            "--embed-metadata",
            "--parse-metadata", "%(title)s:%(meta_title)s",
            "--parse-metadata", "%(uploader)s:%(meta_artist)s",
            "--no-playlist",
            "-o", DJ_CACHE_DIR + "%(id)s.%(ext)s",
            "https://www.youtube.com/watch?v=" + videoId
        };

        log.info("[DJ Cache] Ejecutando: {}", String.join(" ", cmd));

        try {
            ProcessBuilder djPb = new ProcessBuilder(cmd);
            Process process = djPb.start();

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

    // ── Transcode-to-Opus streaming (Spotify-like quality tiers) ─────────────

    public void streamAudioToResponse(Long pathId, String relativePath,
                                      String rangeHeader, String quality,
                                      HttpServletResponse response) throws IOException {
        File file = resolveFile(pathId, relativePath);
        if (quality == null || quality.isBlank() || "original".equals(quality)) {
            streamFileToResponse(file, rangeHeader, response);
            return;
        }
        int bitrateKbps = switch (quality) {
            case "low"  -> 96;
            case "high" -> 192;
            default     -> 128; // normal
        };
        String ext = getExtension(file.getName());
        // Skip transcode if already Ogg/Opus (already low-size), or high quality on non-lossless
        if (ALREADY_OPUS.contains(ext) || ("high".equals(quality) && !LOSSLESS_EXTS.contains(ext))) {
            streamFileToResponse(file, rangeHeader, response);
            return;
        }
        try {
            File cached = getTranscodeCache(pathId, relativePath, quality);
            if (cached.exists()) {
                streamFileToResponse(cached, rangeHeader, response);
            } else {
                // Serve original immediately — start background transcode for next play
                streamFileToResponse(file, rangeHeader, response);
                String jobKey = cached.getName();
                if (transcoding.add(jobKey)) {
                    final int bps = bitrateKbps;
                    transcodePool.submit(() -> {
                        try { transcodeToOggOpus(file, cached, bps); }
                        catch (Exception ex) { log.warn("Background transcode failed for {}: {}", file.getName(), ex.getMessage()); }
                        finally { transcoding.remove(jobKey); }
                    });
                }
            }
        } catch (Exception e) {
            log.warn("Stream with quality failed for {}, falling back to original: {}", file.getName(), e.getMessage());
            streamFileToResponse(file, rangeHeader, response);
        }
    }

    private File getTranscodeCache(Long pathId, String relativePath, String quality) {
        try {
            String key = pathId + ":" + relativePath + ":" + quality;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String hash = HexFormat.of().formatHex(md.digest(key.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
            File dir = new File(TRANSCODE_CACHE_DIR);
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, hash + "_" + quality + ".ogg");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private void transcodeToOggOpus(File input, File output, int bitrateKbps) throws IOException, InterruptedException {
        File tmp = new File(output.getPath() + ".tmp");
        String[] cmd = {
            "ffmpeg", "-y",
            "-i", input.getAbsolutePath(),
            "-vn",
            "-c:a", "libopus",
            "-b:a", bitrateKbps + "k",
            "-ac", "2",
            "-ar", "48000",
            "-f", "ogg",
            tmp.getAbsolutePath()
        };
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getInputStream().transferTo(OutputStream.nullOutputStream());
        int exit = p.waitFor();
        if (exit != 0 || !tmp.exists()) { tmp.delete(); throw new IOException("ffmpeg exit=" + exit); }
        Files.move(tmp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.info("Transcoded {} → {}kbps Opus ({}MB)", input.getName(), bitrateKbps, output.length() / 1_048_576);
    }

    private static String getExtension(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).toLowerCase() : "";
    }

    /** Cleanup transcode cache files older than 7 days (called by scheduler). */
    public void cleanTranscodeCache() {
        File dir = new File(TRANSCODE_CACHE_DIR);
        if (!dir.exists()) return;
        long cutoff = System.currentTimeMillis() - 7L * 86_400_000L;
        File[] files = dir.listFiles();
        if (files == null) return;
        int deleted = 0;
        for (File f : files) { if (f.lastModified() < cutoff) { f.delete(); deleted++; } }
        if (deleted > 0) log.info("Transcode cache: deleted {} stale files", deleted);
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
        response.setHeader("Cache-Control", "private, max-age=3600");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType(contentType);

        long start = 0;
        long end   = fileLength - 1;
        boolean partial = false;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            try {
                String rangeSpec = rangeHeader.substring(6).split(",", 2)[0].trim();
                String[] parts   = rangeSpec.split("-", 2);
                boolean openEnded = parts.length < 2 || parts[1].isEmpty();

                if (parts[0].isEmpty() && parts.length > 1 && !parts[1].isEmpty()) {
                    long suffixLength = Long.parseLong(parts[1]);
                    start = Math.max(fileLength - suffixLength, 0);
                    end = fileLength - 1;
                    openEnded = false;
                } else {
                    start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
                    end = openEnded ? fileLength - 1 : Long.parseLong(parts[1]);
                }

                end = Math.min(end, fileLength - 1);
                if (openEnded) {
                    end = Math.min(start + STREAM_CHUNK_SIZE_BYTES - 1, fileLength - 1);
                }
                partial = true;
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileLength);
                response.setContentLengthLong(0);
                return;
            }
        }

        if (fileLength <= 0 || start < 0 || start >= fileLength || end < start) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + fileLength);
            response.setContentLengthLong(0);
            return;
        }

        if (partial) {
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
            byte[] buf       = new byte[STREAM_BUFFER_SIZE_BYTES];
            long   remaining = length;
            int    read;
            while (remaining > 0 &&
                   (read = raf.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                out.write(buf, 0, read);
                remaining -= read;
            }
            out.flush();
        } catch (IOException e) {
            if (isClientAbort(e)) return;
            throw e;
        }
    }

    private boolean isClientAbort(IOException e) {
        String className = e.getClass().getName();
        String message = Optional.ofNullable(e.getMessage()).orElse("").toLowerCase(Locale.ROOT);
        return className.contains("ClientAbortException")
                || message.contains("broken pipe")
                || message.contains("connection reset")
                || message.contains("forcibly closed")
                || message.contains("abort")
                || message.contains("anulada")
                || message.contains("restablecida")
                || message.contains("cerrada");
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
            updateMetadataCache(pathId, relativePath, file, title, artist, album, year, af);
        } catch (Exception e) {
            throw new RuntimeException("No se pudieron actualizar los metadatos: " + e.getMessage());
        }
    }

    public Map<String, Object> fillYoutubeMetadataBulk(Long pathId, String subPath, int limit, boolean onlyMissing) {
        Path base = nasService.getBasePath(pathId);
        Path startPath = (subPath != null && !subPath.isBlank())
                ? nasService.resolveValidatedPath(pathId, subPath)
                : nasService.resolveValidatedPath(pathId, "");

        File startDir = startPath.toFile();
        if (!startDir.exists() || !startDir.isDirectory()) {
            return Map.of("processed", 0, "updated", 0, "skipped", 0, "failed", 0);
        }

        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<File> audioFiles = new ArrayList<>();
        collectAudioFilesForSearch(startDir, audioFiles, SEARCH_SCAN_LIMIT);

        int processed = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, String>> items = new ArrayList<>();

        for (File file : audioFiles) {
            if (processed >= safeLimit) break;
            processed++;
            String relPath = relativePath(base, file);
            try {
                AudioFile af = AudioFileIO.read(file);
                Tag tag = af.getTagOrCreateDefault();
                String existingTitle = Optional.ofNullable(tag.getFirst(FieldKey.TITLE)).orElse("");
                String existingArtist = Optional.ofNullable(tag.getFirst(FieldKey.ARTIST)).orElse("");
                String existingAlbum = Optional.ofNullable(tag.getFirst(FieldKey.ALBUM)).orElse("");

                boolean suspiciousArtist = isSuspiciousArtistName(existingArtist);
                if (onlyMissing && !existingTitle.isBlank() && !existingArtist.isBlank() && !suspiciousArtist && !existingAlbum.isBlank()) {
                    skipped++;
                    continue;
                }

                String query = !existingTitle.isBlank()
                        ? ((existingArtist.isBlank() || suspiciousArtist) ? existingTitle : existingArtist + " " + existingTitle)
                        : stripExtension(file.getName());
                YoutubeMetadata metadata = lookupYoutubeMetadata(query);
                if (metadata == null || metadata.title().isBlank()) {
                    skipped++;
                    continue;
                }

                boolean changed = false;
                if (!onlyMissing || existingTitle.isBlank()) {
                    tag.setField(FieldKey.TITLE, metadata.title());
                    changed = true;
                }
                if (!onlyMissing || existingArtist.isBlank() || suspiciousArtist) {
                    tag.setField(FieldKey.ARTIST, metadata.artist());
                    changed = true;
                }
                if (!onlyMissing || existingAlbum.isBlank()) {
                    tag.setField(FieldKey.ALBUM, metadata.album());
                    changed = true;
                }

                if (changed) {
                    af.setTag(tag);
                    AudioFileIO.write(af);
                    updateMetadataCache(pathId, relPath, file,
                            tag.getFirst(FieldKey.TITLE),
                            tag.getFirst(FieldKey.ARTIST),
                            tag.getFirst(FieldKey.ALBUM),
                            tag.getFirst(FieldKey.YEAR),
                            af);
                    updated++;
                    items.add(Map.of(
                            "path", relPath,
                            "title", tag.getFirst(FieldKey.TITLE),
                            "artist", tag.getFirst(FieldKey.ARTIST),
                            "album", tag.getFirst(FieldKey.ALBUM)
                    ));
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
            }
        }

        return Map.of(
                "processed", processed,
                "updated", updated,
                "skipped", skipped,
                "failed", failed,
                "items", items
        );
    }

    public Map<String, Object> lookupYoutubeMetadataMap(String query) {
        YoutubeMetadata metadata = lookupYoutubeMetadata(query);
        if (metadata == null) return Map.of("found", false);
        return Map.of(
                "found", true,
                "title", metadata.title(),
                "artist", metadata.artist(),
                "album", metadata.album(),
                "videoId", metadata.videoId(),
                "channelName", metadata.channelName(),
                "rawTitle", metadata.rawTitle()
        );
    }

    private YoutubeMetadata lookupYoutubeMetadata(String query) {
        if (query == null || query.isBlank() || query.length() > 300) return null;
        try {
            String cleanQuery = query
                    .replaceAll("\\.(mp3|flac|m4a|wav|ogg|aac|opus|wma|alac)$", "")
                    .replaceAll("[_\\[\\]{}()]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            ProcessBuilder pb = new ProcessBuilder(
                    "yt-dlp",
                    "--js-runtimes", "nodejs",
                    "--flat-playlist",
                    "--print", "%(title)s\t%(uploader)s\t%(id)s",
                    "--no-warnings",
                    "ytsearch1:" + cleanQuery
            );
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String resultLine;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                resultLine = reader.readLine();
            }
            try (InputStream errStream = process.getErrorStream()) {
                errStream.readAllBytes();
            }
            int exit = process.waitFor();
            if (exit != 0 || resultLine == null || resultLine.isBlank()) return null;

            String[] parts = resultLine.split("\t", 3);
            String rawTitle = parts[0].trim();
            String channelName = parts.length > 1 ? parts[1].trim() : "";
            String videoId = parts.length > 2 ? parts[2].trim() : "";

            String parsedTitle = rawTitle;
            String parsedArtist = cleanYoutubeArtist(channelName);
            int dashIdx = rawTitle.indexOf(" - ");
            if (dashIdx > 0) {
                parsedArtist = rawTitle.substring(0, dashIdx).trim();
                parsedTitle = rawTitle.substring(dashIdx + 3).trim();
            }

            parsedTitle = cleanYoutubeTitle(parsedTitle);
            parsedArtist = cleanYoutubeArtist(parsedArtist);
            String album = parsedArtist.isBlank() ? "YouTube" : parsedArtist;
            return new YoutubeMetadata(parsedTitle, parsedArtist, album, videoId, channelName, rawTitle);
        } catch (Exception e) {
            return null;
        }
    }

    private String cleanYoutubeTitle(String title) {
        if (title == null) return "";
        return title
                .replaceAll("(?i)\\s*\\(?(official\\s*(music\\s*)?video|lyric\\s*video|official\\s*audio|audio\\s*oficial|video\\s*oficial|visualizer|hd|hq|4k)\\)?", "")
                .replaceAll("\\s*[\\[({].*?[\\])}]\\s*$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanYoutubeArtist(String artist) {
        if (artist == null) return "";
        String cleaned = artist
                .replaceAll("(?i)\\s*-?\\s*(topic|official|vevo|music)$", "")
                .replaceAll("(?i)\\s*(official\\s*)?(youtube\\s*)?channel$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return isSuspiciousArtistName(cleaned) ? "" : cleaned;
    }

    private boolean isSuspiciousArtistName(String artist) {
        String normalized = normalizeSearchText(artist);
        if (normalized.isBlank()) return true;
        return normalized.matches(".*\\b(clean edit|audio edit|extended edit|radio edit|lyrics?|lyric video)\\b.*")
                || normalized.matches(".*\\b(vevo|official|topic|records|recordings|music tv|musictv|entertainment|official channel)\\b.*")
                || normalized.equals("dj clean edit")
                || normalized.equals("unknown")
                || normalized.equals("desconocido");
    }

    private void updateMetadataCache(Long pathId, String relativePath, File file, String title, String artist, String album, String year, AudioFile af) {
        try {
            TrackMetadataCache entry = metadataCacheRepo.findByNasPathIdAndRelativePath(pathId, relativePath)
                    .orElseGet(() -> TrackMetadataCache.builder().nasPathId(pathId).relativePath(relativePath).build());
            entry.setLastModified(file.lastModified());
            entry.setTitle(title != null && !title.isBlank() ? title : stripExtension(file.getName()));
            entry.setArtist(artist != null ? artist : "");
            entry.setAlbum(album != null ? album : "");
            entry.setYear(year != null ? year : "");
            entry.setFormat(af.getExt() != null ? af.getExt().toLowerCase() : extension(file.getName()));
            entry.setDuration(af.getAudioHeader() != null ? af.getAudioHeader().getTrackLength() : 0);
            Tag tag = af.getTag();
            entry.setHasCover(tag != null && tag.getFirstArtwork() != null);
            if (tag != null) {
                String bpmStr = tag.getFirst(FieldKey.BPM);
                int bpm = 0;
                if (bpmStr != null && !bpmStr.isBlank()) {
                    try { bpm = Integer.parseInt(bpmStr.trim()); } catch (NumberFormatException ignored) {}
                }
                entry.setBpm(bpm);
            }
            metadataCacheRepo.save(entry);
        } catch (Exception ignored) {}
    }

    private record YoutubeMetadata(String title, String artist, String album, String videoId, String channelName, String rawTitle) {}

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

    // ── Search ────────────────────────────────────────────────────────────────

    public List<MusicMetadataDto> searchMusic(Long pathId, String subPath, String query, int limit) {
        List<String> tokens = searchTokens(query);
        if (tokens.isEmpty()) return Collections.emptyList();

        // ── Fast path: search the indexed metadata cache — no filesystem walk ──
        List<TrackMetadataCache> dbCache = metadataCacheRepo.findByNasPathId(pathId);
        if (!dbCache.isEmpty()) {
            String subPathFilter = (subPath != null && !subPath.isBlank()) ? subPath : null;
            List<Map.Entry<MusicMetadataDto, Integer>> scored = new ArrayList<>();
            for (TrackMetadataCache c : dbCache) {
                if (subPathFilter != null && !c.getRelativePath().startsWith(subPathFilter)) continue;
                MusicMetadataDto dto = dtoFromCache(c);
                dto.setNasPathId(pathId);
                int score = scoreSearchDto(dto, tokens);
                if (score > 0) scored.add(Map.entry(dto, score));
            }
            scored.sort((a, b) -> {
                int cmp = Integer.compare(b.getValue(), a.getValue());
                if (cmp != 0) return cmp;
                String ta = normalizeSearchText(a.getKey().getTitle() != null ? a.getKey().getTitle() : "");
                String tb = normalizeSearchText(b.getKey().getTitle() != null ? b.getKey().getTitle() : "");
                return ta.compareTo(tb);
            });
            return scored.stream().limit(Math.max(1, limit)).map(Map.Entry::getKey).collect(Collectors.toList());
        }

        // ── Slow fallback: filesystem scan (library not yet indexed) ──
        Path base = nasService.getBasePath(pathId);
        Path startPath = (subPath != null && !subPath.isBlank())
                ? nasService.resolveValidatedPath(pathId, subPath)
                : nasService.resolveValidatedPath(pathId, "");

        File startDir = startPath.toFile();
        if (!startDir.exists() || !startDir.isDirectory()) return Collections.emptyList();

        List<File> audioFiles = new ArrayList<>();
        collectAudioFilesForSearch(startDir, audioFiles, SEARCH_SCAN_LIMIT);
        Map<String, TrackMetadataCache> cacheMap = batchFetchCacheChunked(pathId, audioFiles, base);

        List<SearchHit> hits = new ArrayList<>();
        Set<String> hitPaths = new HashSet<>();
        for (File file : audioFiles) {
            String relPath = relativePath(base, file);
            TrackMetadataCache cached = validCache(cacheMap.get(relPath), file);
            int score = scoreSearchHit(file, relPath, cached, tokens);
            if (score > 0) {
                hits.add(new SearchHit(file, relPath, cached, score, null));
                hitPaths.add(relPath);
            }
        }

        int deepReads = 0;
        if (hits.size() < limit) {
            for (File file : audioFiles) {
                if (deepReads >= SEARCH_DEEP_METADATA_LIMIT) break;
                String relPath = relativePath(base, file);
                if (hitPaths.contains(relPath)) continue;
                if (validCache(cacheMap.get(relPath), file) != null) continue;

                deepReads++;
                MusicMetadataDto dto = buildDto(file, base, pathId, cacheMap);
                int score = scoreSearchDto(dto, tokens);
                if (score > 0) {
                    hits.add(new SearchHit(file, relPath, null, score, dto));
                    hitPaths.add(relPath);
                }
            }
        }

        return hits.stream()
                .sorted(Comparator
                        .comparingInt((SearchHit hit) -> hit.score).reversed()
                        .thenComparing(hit -> normalizeSearchText(hit.file.getName())))
                .limit(Math.max(1, limit))
                .map(hit -> {
                    MusicMetadataDto dto = hit.dto != null
                            ? hit.dto
                            : buildDto(hit.file, base, pathId, Collections.singletonMap(hit.relPath, hit.cached));
                    dto.setNasPathId(pathId);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private void collectAudioFilesForSearch(File dir, List<File> results, int limit) {
        if (results.size() >= limit) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(f -> f.getName().toLowerCase()));
        for (File f : files) {
            if (results.size() >= limit) break;
            if (f.isDirectory()) {
                collectAudioFilesForSearch(f, results, limit);
            } else if (f.isFile() && isAudio(f)) {
                results.add(f);
            }
        }
    }

    private int scoreSearchHit(File file, String relPath, TrackMetadataCache cached, List<String> tokens) {
        String name = normalizeSearchText(stripExtension(file.getName()));
        String path = normalizeSearchText(relPath);
        String title = cached != null ? normalizeSearchText(cached.getTitle()) : "";
        String artist = cached != null ? normalizeSearchText(cached.getArtist()) : "";
        String album = cached != null ? normalizeSearchText(cached.getAlbum()) : "";
        return scoreSearchFields(tokens, name, title, artist, album, path);
    }

    private int scoreSearchDto(MusicMetadataDto dto, List<String> tokens) {
        return scoreSearchFields(
                tokens,
                normalizeSearchText(stripExtension(dto.getName())),
                normalizeSearchText(dto.getTitle()),
                normalizeSearchText(dto.getArtist()),
                normalizeSearchText(dto.getAlbum()),
                normalizeSearchText(dto.getPath())
        );
    }

    private int scoreSearchFields(List<String> tokens, String name, String title, String artist, String album, String path) {
        String all = String.join(" ", name, title, artist, album, path).trim();
        if (all.isBlank()) return 0;
        for (String token : tokens) {
            if (!all.contains(token)) return 0;
        }

        String query = String.join(" ", tokens);
        int score = 10;
        if (title.equals(query)) score += 1000;
        if (name.equals(query)) score += 900;
        if (artist.equals(query)) score += 700;
        if (title.startsWith(query)) score += 520;
        if (name.startsWith(query)) score += 470;
        if (artist.startsWith(query)) score += 360;
        if (title.contains(query)) score += 300;
        if (name.contains(query)) score += 260;
        if (artist.contains(query)) score += 220;
        if (album.contains(query)) score += 120;
        if (path.contains(query)) score += 60;

        for (String token : tokens) {
            if (title.startsWith(token)) score += 45;
            else if (title.contains(token)) score += 28;
            if (name.startsWith(token)) score += 40;
            else if (name.contains(token)) score += 24;
            if (artist.startsWith(token)) score += 34;
            else if (artist.contains(token)) score += 20;
            if (album.contains(token)) score += 10;
            if (path.contains(token)) score += 5;
        }
        return score;
    }

    private List<String> searchTokens(String query) {
        String normalized = normalizeSearchText(query);
        if (normalized.isBlank()) return Collections.emptyList();
        return Arrays.stream(normalized.split("\\s+"))
                .filter(token -> token.length() > 1 || normalized.length() == 1)
                .distinct()
                .collect(Collectors.toList());
    }

    private String normalizeSearchText(String text) {
        if (text == null) return "";
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return withoutAccents
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private List<String> artistParts(String artist) {
        String full = normalizeSearchText(artist);
        if (full.isBlank()) return Collections.emptyList();
        List<String> parts = new ArrayList<>();
        parts.add(full);
        Arrays.stream(artist.split("(?i)\\s*(?:,|;|&|\\+|/|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bcon\\b|\\band\\b| y )\\s*"))
                .map(this::normalizeSearchText)
                .filter(part -> !part.isBlank())
                .forEach(parts::add);
        return parts.stream().distinct().collect(Collectors.toList());
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private MusicMetadataDto dtoFromCache(TrackMetadataCache cache) {
        String path = cache.getRelativePath();
        String name = path;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        String title = cache.getTitle() != null && !cache.getTitle().isBlank()
                ? cache.getTitle()
                : stripExtension(name);

        return MusicMetadataDto.builder()
                .name(name)
                .path(path)
                .directory(false)
                .size(0)
                .lastModified(formatDate(cache.getLastModified()))
                .title(title)
                .artist(cache.getArtist() != null ? cache.getArtist() : "")
                .album(cache.getAlbum() != null ? cache.getAlbum() : "")
                .format(cache.getFormat() != null ? cache.getFormat() : extension(name))
                .year(cache.getYear() != null ? cache.getYear() : "")
                .duration(cache.getDuration())
                .hasCover(cache.isHasCover())
                .bpm(cache.getBpm())
                .nasPathId(cache.getNasPathId())
                .build();
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String relativePath(Path base, File file) {
        return base.relativize(file.toPath()).toString().replace("\\", "/");
    }

    private TrackMetadataCache validCache(TrackMetadataCache cached, File file) {
        return cached != null && cached.getLastModified() == file.lastModified() ? cached : null;
    }

    private Map<String, TrackMetadataCache> batchFetchCacheChunked(Long pathId, List<File> files, Path base) {
        Map<String, TrackMetadataCache> out = new HashMap<>();
        for (int i = 0; i < files.size(); i += SEARCH_CACHE_CHUNK_SIZE) {
            List<File> chunk = files.subList(i, Math.min(i + SEARCH_CACHE_CHUNK_SIZE, files.size()));
            out.putAll(batchFetchCache(pathId, chunk, base));
        }
        return out;
    }

    // ── Lyrics ────────────────────────────────────────────────────────────────

    public String findLrcSidecar(Long pathId, String trackRelPath) {
        try {
            Path target = nasService.resolveValidatedPath(pathId, trackRelPath);
            String name = target.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String baseName = dot >= 0 ? name.substring(0, dot) : name;
            Path lrcPath = target.getParent().resolve(baseName + ".lrc");
            if (java.nio.file.Files.exists(lrcPath) && java.nio.file.Files.isReadable(lrcPath)) {
                return java.nio.file.Files.readString(lrcPath, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}
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

    // Directory-only calls (no pathId needed — returns before ID3 reading)
    private MusicMetadataDto buildDto(File f, Path base) {
        return buildDto(f, base, null, null);
    }

    private MusicMetadataDto buildDto(File f, Path base, Long pathId, Map<String, TrackMetadataCache> preloaded) {
        return buildDto(f, base, pathId, preloaded, true);
    }

    private MusicMetadataDto buildDto(File f, Path base, Long pathId, Map<String, TrackMetadataCache> preloaded, boolean allowDiskRead) {
        String relPath = base.relativize(f.toPath()).toString().replace("\\", "/");
        MusicMetadataDto.MusicMetadataDtoBuilder b = MusicMetadataDto.builder()
                .name(f.getName())
                .path(relPath)
                .directory(f.isDirectory())
                .size(f.isFile() ? f.length() : 0)
                .lastModified(formatDate(f.lastModified()));

        if (f.isDirectory()) return b.build();

        long lastMod = f.lastModified();

        // Check cache
        TrackMetadataCache cached = null;
        if (pathId != null) {
            cached = preloaded != null
                    ? preloaded.get(relPath)
                    : metadataCacheRepo.findByNasPathIdAndRelativePath(pathId, relPath).orElse(null);
        }

        if (cached != null && cached.getLastModified() == lastMod) {
            return b.title(cached.getTitle() != null && !cached.getTitle().isBlank() ? cached.getTitle() : stripExtension(f.getName()))
                    .artist(cached.getArtist()  != null ? cached.getArtist()  : "")
                    .album(cached.getAlbum()    != null ? cached.getAlbum()   : "")
                    .format(cached.getFormat()  != null ? cached.getFormat()  : extension(f.getName()))
                    .year(cached.getYear()      != null ? cached.getYear()    : "")
                    .duration(cached.getDuration())
                    .hasCover(cached.isHasCover())
                    .bpm(cached.getBpm())
                    .build();
        }

        // Cache miss or stale — read from disk
        if (!allowDiskRead) {
            return b.title(stripExtension(f.getName()))
                    .artist("")
                    .album("")
                    .format(extension(f.getName()))
                    .year("")
                    .duration(0)
                    .hasCover(false)
                    .bpm(0)
                    .build();
        }

        try {
            AudioFile af = AudioFileIO.read(f);
            String format   = af.getExt().toLowerCase();
            int    duration = af.getAudioHeader().getTrackLength();
            b.format(format).duration(duration);

            String  title   = null;
            String  artist  = "";
            String  album   = "";
            String  year    = "";
            int     bpm     = 0;
            boolean hasCover = false;

            Tag tag = af.getTag();
            if (tag != null) {
                title  = tag.getFirst(FieldKey.TITLE);
                String a = tag.getFirst(FieldKey.ARTIST); if (a != null) artist = a;
                String al = tag.getFirst(FieldKey.ALBUM);  if (al != null) album  = al;
                String y  = tag.getFirst(FieldKey.YEAR);   if (y  != null) year   = y;
                String bpmStr = tag.getFirst(FieldKey.BPM);
                hasCover = tag.getFirstArtwork() != null;
                if (bpmStr != null && !bpmStr.isBlank()) {
                    try { bpm = Integer.parseInt(bpmStr.trim()); } catch (NumberFormatException ignored) {}
                }
            }

            String finalTitle = (title != null && !title.isBlank()) ? title : stripExtension(f.getName());
            b.title(finalTitle).artist(artist).album(album).year(year).hasCover(hasCover).bpm(bpm);

            // Save to cache
            if (pathId != null) {
                TrackMetadataCache entry = cached != null ? cached
                        : TrackMetadataCache.builder().nasPathId(pathId).relativePath(relPath).build();
                entry.setLastModified(lastMod);
                entry.setTitle(finalTitle);
                entry.setArtist(artist);
                entry.setAlbum(album);
                entry.setFormat(format);
                entry.setYear(year);
                entry.setDuration(duration);
                entry.setHasCover(hasCover);
                entry.setBpm(bpm);
                try { metadataCacheRepo.save(entry); } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            b.title(stripExtension(f.getName())).format(extension(f.getName()));
        }

        return b.build();
    }

    private CachedDirectoryListing getCachedDirectoryListing(Long pathId, String subPath, File dir) {
        String key = directoryListingKey(pathId, subPath, dir);
        long now = System.currentTimeMillis();
        long dirLastModified = dir.lastModified();

        CachedDirectoryListing cached = directoryListingCache.get(key);
        if (cached != null
                && cached.dirLastModified == dirLastModified
                && now - cached.loadedAtMillis <= DIRECTORY_LISTING_CACHE_TTL_MS) {
            return cached;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            CachedDirectoryListing empty = new CachedDirectoryListing(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    dirLastModified,
                    now
            );
            directoryListingCache.put(key, empty);
            return empty;
        }

        List<File> dirs = Arrays.stream(files)
                .filter(File::isDirectory)
                .sorted(Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableList());

        List<File> audioFiles = Arrays.stream(files)
                .filter(f -> f.isFile() && isAudio(f))
                .sorted(Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableList());

        trimDirectoryListingCacheIfNeeded();
        CachedDirectoryListing listing = new CachedDirectoryListing(dirs, audioFiles, dirLastModified, now);
        directoryListingCache.put(key, listing);
        return listing;
    }

    private void warmMetadataCacheAsync(Long pathId, List<File> pageFiles, Path base, Map<String, TrackMetadataCache> cacheMap) {
        int scheduled = 0;
        for (File file : pageFiles) {
            if (scheduled >= METADATA_WARMUP_LIMIT_PER_PAGE) return;

            String relPath = relativePath(base, file);
            TrackMetadataCache cached = cacheMap.get(relPath);
            if (validCache(cached, file) != null) continue;

            String key = pathId + "\u0000" + relPath + "\u0000" + file.lastModified();
            if (!metadataWarmupInFlight.add(key)) continue;

            scheduled++;
            metadataExecutor.submit(() -> {
                try {
                    buildDto(file, base, pathId, null, true);
                } finally {
                    metadataWarmupInFlight.remove(key);
                }
            });
        }
    }

    private String directoryListingKey(Long pathId, String subPath, File dir) {
        return pathId + "\u0000" + (subPath == null ? "" : subPath) + "\u0000" + dir.getAbsolutePath();
    }

    private void trimDirectoryListingCacheIfNeeded() {
        if (directoryListingCache.size() < DIRECTORY_LISTING_CACHE_MAX) return;
        int toRemove = Math.max(1, DIRECTORY_LISTING_CACHE_MAX / 10);
        directoryListingCache.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().loadedAtMillis))
                .limit(toRemove)
                .map(Map.Entry::getKey)
                .forEach(directoryListingCache::remove);
    }

    private Map<String, TrackMetadataCache> batchFetchCache(Long pathId, List<File> files, Path base) {
        List<String> paths = files.stream()
                .map(f -> base.relativize(f.toPath()).toString().replace("\\", "/"))
                .collect(Collectors.toList());
        return metadataCacheRepo.findByNasPathIdAndRelativePathIn(pathId, paths)
                .stream()
                .collect(Collectors.toMap(TrackMetadataCache::getRelativePath, c -> c));
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

    private static class HlsCacheJob {
        String key;
        Path dir;
        String status;
        int progress;
        int durationSeconds;
        long fileSizeBytes;
        boolean eligible;
        String error;
    }

    private static class CachedDirectoryListing {
        final List<File> dirs;
        final List<File> audioFiles;
        final long dirLastModified;
        final long loadedAtMillis;

        CachedDirectoryListing(List<File> dirs, List<File> audioFiles, long dirLastModified, long loadedAtMillis) {
            this.dirs = dirs;
            this.audioFiles = audioFiles;
            this.dirLastModified = dirLastModified;
            this.loadedAtMillis = loadedAtMillis;
        }
    }

    private static class SearchHit {
        final File file;
        final String relPath;
        final TrackMetadataCache cached;
        final int score;
        final MusicMetadataDto dto;

        SearchHit(File file, String relPath, TrackMetadataCache cached, int score, MusicMetadataDto dto) {
            this.file = file;
            this.relPath = relPath;
            this.cached = cached;
            this.score = score;
            this.dto = dto;
        }
    }
}
