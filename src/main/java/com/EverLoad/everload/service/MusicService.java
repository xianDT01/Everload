package com.everload.everload.service;

import com.everload.everload.dto.MusicMetadataDto;
import com.everload.everload.dto.PagedMusicResult;
import com.everload.everload.model.NasPath;
import com.everload.everload.model.TrackMetadataCache;
import com.everload.everload.repository.NasPathRepository;
import com.everload.everload.repository.TrackMetadataCacheRepository;
import com.everload.everload.util.MediaTextCleaner;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MusicService {

    private static final String FOUND_KEY = "found";
    private static final String IMAGE_URL_KEY = "imageUrl";

    private static final byte[] NO_COVER_ART = new byte[0];
    private static final long MAX_COVER_UPLOAD_BYTES = 10L * 1024 * 1024;

    private final NasService nasService;
    private final NasPathRepository nasPathRepository;
    private final TrackMetadataCacheRepository metadataCacheRepo;
    private final RestTemplate restTemplate;

    private static final List<String> AUDIO_EXTENSIONS =
            Arrays.asList("mp3", "flac", "m4a", "wav", "ogg", "aac", "opus", "wma", "alac");

    private static final String DJ_CACHE_DIR = "./downloads/dj_cache/";
    private static final String TRANSCODE_CACHE_DIR = "./downloads/transcode-cache/";
    private static final Set<String> LOSSLESS_EXTS = Set.of("flac", "wav", "aiff", "aif", "alac");
    private static final Set<String> ALREADY_OPUS = Set.of("ogg", "opus");
    private static final Set<String> BROWSER_DIRECT_EXTS = Set.of("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac");
    private static final long BROWSE_RESULT_TTL_MS = 5 * 60_000L;
    private final ConcurrentHashMap<String, Object[]> browseResultCache = new ConcurrentHashMap<>();
    private final java.util.Set<String> transcoding = ConcurrentHashMap.newKeySet();
    private final ExecutorService transcodePool = Executors.newFixedThreadPool(2);
    private static final long STREAM_CHUNK_SIZE_BYTES = 8L * 1024L * 1024L;
    private static final int STREAM_BUFFER_SIZE_BYTES = 256 * 1024;
    private static final int SEARCH_SCAN_LIMIT = 20000;
    private static final int SEARCH_CACHE_CHUNK_SIZE = 700;
    private static final int SEARCH_DEEP_METADATA_LIMIT = 350;
    private static final long DIRECTORY_LISTING_CACHE_TTL_MS = 15_000L;
    private static final int DIRECTORY_LISTING_CACHE_MAX = 512;
    private static final int METADATA_WARMUP_LIMIT_PER_PAGE = 80;
    private static final String INDEXING_FIELD = "indexing";
    private static final String CONTENT_RANGE_HEADER = "Content-Range";
    private static final String CACHE_KEY_SEPARATOR = "\u0000";

    @Value("${avatar.storage.path:./avatars}")
    private String avatarStoragePath;

    @Value("${everload.ytdlp.path:yt-dlp}")
    private String ytDlpPath;

    @Value("${everload.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    private final Map<String, CachedDirectoryListing> directoryListingCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<String>> artistImageLookupCache = new ConcurrentHashMap<>();
    /** Artistas sin imagen en Deezer: se recuerda el fallo 24h para no repetir el lookup en cada visita. */
    private static final long ARTIST_IMAGE_FAILURE_TTL_MS = 24 * 60 * 60 * 1000L;
    private final Map<String, Long> artistImageLookupFailures = new ConcurrentHashMap<>();
    private final Map<String, Optional<String>> albumCoverLookupCache = new ConcurrentHashMap<>();
    private final Set<String> metadataWarmupInFlight = ConcurrentHashMap.newKeySet();
    private final Set<Long> libraryIndexInFlight = ConcurrentHashMap.newKeySet();
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
                .toList();

        List<MusicMetadataDto> pool = withCovers.isEmpty() ? candidates : withCovers;
        Collections.shuffle(pool);
        return pool.stream().limit(count).toList();
    }

    public Map<String, Object> getLibraryOverview(Long pathId, int limit) {
        List<MusicMetadataDto> tracks = metadataCacheRepo
                .findOverviewSlice(pathId, org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(this::dtoFromCache)
                .toList();

        if (tracks.isEmpty()) {
            startLibraryIndex(pathId);
        }

        return Map.of(
                "tracks", tracks,
                INDEXING_FIELD, libraryIndexInFlight.contains(pathId)
        );
    }

    public List<MusicMetadataDto> getRecentTracks(Long pathId, int limit) {
        List<MusicMetadataDto> tracks = metadataCacheRepo
                .findByNasPathIdOrderByLastModifiedDesc(pathId, org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(this::dtoFromCache)
                .toList();

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
            return Map.of("started", false, INDEXING_FIELD, true);
        }

        metadataExecutor.submit(() -> {
            try {
                indexLibrary(pathId, base);
            } finally {
                libraryIndexInFlight.remove(pathId);
            }
        });

        return Map.of("started", true, INDEXING_FIELD, true);
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
        keys = keys.stream().filter(k -> !k.isBlank()).distinct().toList();
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
                .toList();
    }

    public Map<String, Object> clearMemoryCaches() {
        int imgCount = artistImageLookupCache.size();
        int dirCount = directoryListingCache.size();
        int coverCount = albumCoverLookupCache.size();
        artistImageLookupCache.clear();
        artistImageLookupFailures.clear();
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
            return Map.of(FOUND_KEY, false);
        }

        if (artistImageLookupCache.containsKey(normalized)) {
            Optional<String> inCache = artistImageLookupCache.getOrDefault(normalized, Optional.empty());
            return artistImageResult(inCache);
        }

        if (isArtistImageLookupThrottled(normalized)) return Map.of(FOUND_KEY, false);

        String safeFilename = normalized.replace(' ', '_') + ".jpg";
        Path autoDir = getArtistAutoImageDir();
        Optional<String> localImage = findStoredArtistImage(safeFilename, autoDir);
        if (localImage.isPresent()) return cacheArtistImage(normalized, localImage);

        Optional<String> providerImage = findProviderArtistImage(artist, normalized, safeFilename, autoDir);
        if (providerImage.isPresent()) return cacheArtistImage(normalized, providerImage);

        artistImageLookupFailures.put(normalized, System.currentTimeMillis());
        return Map.of(FOUND_KEY, false);
    }

    private Map<String, Object> artistImageResult(Optional<String> image) {
        return image.<Map<String, Object>>map(url -> Map.of(FOUND_KEY, true, IMAGE_URL_KEY, url))
                .orElseGet(() -> Map.of(FOUND_KEY, false));
    }

    private boolean isArtistImageLookupThrottled(String normalizedArtist) {
        Long failedAt = artistImageLookupFailures.get(normalizedArtist);
        if (failedAt == null) return false;
        if (System.currentTimeMillis() - failedAt < ARTIST_IMAGE_FAILURE_TTL_MS) return true;
        artistImageLookupFailures.remove(normalizedArtist);
        return false;
    }

    private Optional<String> findStoredArtistImage(String safeFilename, Path autoDir) {
        Path filePath = autoDir.resolve(safeFilename).normalize();
        if (!filePath.startsWith(autoDir) || !Files.exists(filePath)) return Optional.empty();
        return Optional.of("/api/music/artist-auto-image/" + safeFilename);
    }

    private Map<String, Object> cacheArtistImage(String normalizedArtist, Optional<String> image) {
        artistImageLookupCache.put(normalizedArtist, image);
        return artistImageResult(image);
    }

    private Optional<String> findProviderArtistImage(String artist, String normalizedArtist,
                                                     String safeFilename, Path autoDir) {
        try {
            String url = "https://api.deezer.com/search/artist?q=" + encodeUrl(artist) + "&limit=8";
            Map<?, ?> response = restTemplate.getForObject(url, Map.class);
            Object data = response != null ? response.get("data") : null;
            return selectProviderArtistImage(data, normalizedArtist, safeFilename, autoDir);
        } catch (Exception e) {
            log.debug("Automatic artist image lookup failed for {}: {}", normalizedArtist, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> selectProviderArtistImage(Object data, String normalizedArtist,
                                                       String safeFilename, Path autoDir) {
        if (!(data instanceof List<?> artists)) return Optional.empty();
        String fallbackImage = "";
        for (Object item : artists) {
            ArtistImageCandidate candidate = artistImageCandidate(item);
            if (candidate == null) continue;
            if (normalizeSearchText(candidate.name()).equals(normalizedArtist)) {
                Optional<String> exact = persistArtistImage(candidate.image(), safeFilename, autoDir);
                if (exact.isPresent()) return exact;
            }
            if (fallbackImage.isBlank()) fallbackImage = candidate.image();
        }
        return persistArtistImage(fallbackImage, safeFilename, autoDir);
    }

    private record ArtistImageCandidate(String name, String image) {}

    private ArtistImageCandidate artistImageCandidate(Object item) {
        if (!(item instanceof Map<?, ?> artistMap)) return null;
        String image = firstNonBlank(
                stringValue(artistMap.get("picture_xl")),
                stringValue(artistMap.get("picture_big")),
                stringValue(artistMap.get("picture_medium")));
        return image.isBlank() ? null : new ArtistImageCandidate(stringValue(artistMap.get("name")), image);
    }

    private Optional<String> persistArtistImage(String image, String safeFilename, Path autoDir) {
        if (image == null || image.isBlank()) return Optional.empty();
        String local = downloadAutoImage(image, safeFilename, autoDir);
        return local.isBlank() ? Optional.empty() : Optional.of(local);
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
        } catch (Exception e) {
            log.debug("Could not persist artist image {}: {}", filename, e.getMessage());
        }
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
        if (normAlbum.isBlank()) return Map.of(FOUND_KEY, false);

        String cacheKey = normArtist + "|" + normAlbum;
        Optional<String> cached = albumCoverLookupCache.computeIfAbsent(
                cacheKey, key -> findAlbumCover(normArtist, normAlbum));

        return cached
                .<Map<String, Object>>map(url -> Map.of(FOUND_KEY, true, IMAGE_URL_KEY, url))
                .orElseGet(() -> Map.of(FOUND_KEY, false));
    }

    private Optional<String> findAlbumCover(String normalizedArtist, String normalizedAlbum) {
        String safeFilename = albumCoverFilename(normalizedArtist, normalizedAlbum);
        Path coverDir = getAlbumCoverAutoDir();
        Path filePath = coverDir.resolve(safeFilename).normalize();
        if (filePath.startsWith(coverDir) && Files.exists(filePath)) {
            return Optional.of("/api/music/album-auto-cover/" + safeFilename);
        }
        return findProviderAlbumCover(normalizedArtist, normalizedAlbum, safeFilename, coverDir);
    }

    private String albumCoverFilename(String normalizedArtist, String normalizedAlbum) {
        String namePart = (normalizedArtist.isBlank()
                ? normalizedAlbum : normalizedArtist + "__" + normalizedAlbum).replace(' ', '_');
        if (namePart.length() > 180) namePart = namePart.substring(0, 180);
        return namePart + ".jpg";
    }

    private Optional<String> findProviderAlbumCover(String normalizedArtist, String normalizedAlbum,
                                                    String safeFilename, Path coverDir) {
        try {
            String mbQuery = "release:" + encodeUrl(normalizedAlbum)
                    + (normalizedArtist.isBlank() ? "" : "+artist:" + encodeUrl(normalizedArtist));
            String mbUrl = "https://musicbrainz.org/ws/2/release/?query=" + mbQuery + "&fmt=json&limit=5";
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "EverLoad/1.0 (music-player; contact@everload.app)");
            var request = new org.springframework.http.HttpEntity<>(headers);
            var response = restTemplate.exchange(
                    mbUrl, org.springframework.http.HttpMethod.GET, request, Map.class);
            Map<?, ?> body = response.getBody();
            Object releases = body != null ? body.get("releases") : null;
            if (!(releases instanceof List<?> list) || list.isEmpty()) return Optional.empty();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> release)) continue;
                Optional<String> localCover = downloadProviderAlbumCover(release, safeFilename, coverDir);
                if (localCover.isPresent()) return localCover;
            }
        } catch (Exception e) {
            log.debug("Album cover provider lookup failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> downloadProviderAlbumCover(Map<?, ?> release, String safeFilename, Path coverDir) {
        String mbid = stringValue(release.get("id"));
        if (mbid.isBlank()) return Optional.empty();
        try {
            String coverUrl = "https://coverartarchive.org/release/" + mbid + "/front-250";
            byte[] bytes = restTemplate.getForObject(coverUrl, byte[].class);
            if (bytes == null || bytes.length <= 5000) return Optional.empty();
            String local = downloadAlbumCoverImage(bytes, safeFilename, coverDir);
            return local.isBlank() ? Optional.empty() : Optional.of(local);
        } catch (Exception e) {
            log.debug("Local album cover lookup failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String downloadAlbumCoverImage(byte[] bytes, String filename, Path dir) {
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(dir)) return "";
            Files.write(target, bytes);
            return "/api/music/album-auto-cover/" + filename;
        } catch (Exception e) {
            log.debug("Could not persist album cover {}: {}", filename, e.getMessage());
        }
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
        } catch (Exception e) {
            log.debug("Automatic image cleanup was incomplete: {}", e.getMessage());
        }
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
                    .toList();
            if (!stale.isEmpty()) metadataCacheRepo.deleteAll(stale);
        } catch (Exception e) {
            log.debug("Could not prune stale metadata for NAS path {}: {}", pathId, e.getMessage());
        }
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

    private String encodeUrl(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Returns raw bytes of the embedded cover art, or an empty array if not present. */
    public byte[] getCoverArt(Long pathId, String relativePath) {
        File file = resolveFile(pathId, relativePath);
        if (!isAudio(file)) return NO_COVER_ART;
        try {
            AudioFile af = AudioFileIO.read(file);
            Tag tag = af.getTag();
            if (tag != null && tag.getFirstArtwork() != null) {
                return tag.getFirstArtwork().getBinaryData();
            }
        } catch (Exception e) {
            log.debug("Embedded cover could not be read from {}: {}", file.getName(), e.getMessage());
        }
        // Fallback: look for cover image in the same directory
        File dir = file.getParentFile();
        if (dir != null && dir.isDirectory()) {
            byte[] fallback = readCoverImageFile(dir);
            if (fallback != null) return fallback;
        }
        return NO_COVER_ART;
    }

    public void updateCoverArt(Long pathId, String relativePath, byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0 || imageBytes.length > MAX_COVER_UPLOAD_BYTES) {
            throw new IllegalArgumentException("La imagen debe ocupar entre 1 byte y 10 MB");
        }
        File audioFile = resolveFile(pathId, relativePath);
        if (!isAudio(audioFile)) throw new IllegalArgumentException("No es un archivo de audio");

        Path temporaryImage = null;
        try {
            String format = imageFormat(imageBytes);
            if (format == null) throw new IllegalArgumentException("Solo se admiten imágenes JPG o PNG");
            try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) throw new IllegalArgumentException("La imagen no es válida");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(imageInput, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width > 8000 || height > 8000 || (long) width * height > 40_000_000L
                            || reader.read(0) == null) {
                        throw new IllegalArgumentException("La imagen no es válida o supera las dimensiones permitidas");
                    }
                } finally {
                    reader.dispose();
                }
            }

            temporaryImage = Files.createTempFile("everload-cover-", "." + format);
            Files.write(temporaryImage, imageBytes);

            AudioFile audio = AudioFileIO.read(audioFile);
            Tag tag = audio.getTagOrCreateAndSetDefault();
            Artwork artwork = ArtworkFactory.createArtworkFromFile(temporaryImage.toFile());
            tag.deleteArtworkField();
            tag.setField(artwork);
            audio.setTag(tag);
            AudioFileIO.write(audio);

            Tag savedTag = audio.getTag();
            updateMetadataCache(new MetadataCacheUpdate(pathId, relativePath, audioFile,
                    savedTag == null ? null : savedTag.getFirst(FieldKey.TITLE),
                    savedTag == null ? null : savedTag.getFirst(FieldKey.ARTIST),
                    savedTag == null ? null : savedTag.getFirst(FieldKey.ALBUM),
                    savedTag == null ? null : savedTag.getFirst(FieldKey.YEAR), audio));
            invalidateBrowseCache(pathId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new MusicOperationException("No se pudo guardar la carátula: " + e.getMessage(), e);
        } finally {
            if (temporaryImage != null) {
                try { Files.deleteIfExists(temporaryImage); }
                catch (IOException e) { log.debug("Could not remove temporary cover image: {}", e.getMessage()); }
            }
        }
    }

    private String imageFormat(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47) return "png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8
                && bytes[2] == (byte) 0xff) return "jpg";
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
        if (!dir.exists() || !dir.isDirectory() || !dir.canRead()) return NO_COVER_ART;

        // 1. Explicit cover image file
        byte[] explicit = readCoverImageFile(dir);
        if (explicit.length > 0) return explicit;

        File[] files = dir.listFiles();
        if (files == null) return NO_COVER_ART;

        byte[] embedded = findEmbeddedCover(pathId, relativePath, files);
        if (embedded.length > 0) return embedded;

        return findSubfolderCover(pathId, relativePath, files);
    }

    private byte[] findSubfolderCover(Long pathId, String relativePath, File[] files) {
        for (File sub : files) {
            byte[] cover = findCoverInSubfolder(pathId, relativePath, sub);
            if (cover.length > 0) return cover;
        }
        return NO_COVER_ART;
    }

    private byte[] findCoverInSubfolder(Long pathId, String relativePath, File subfolder) {
        if (!subfolder.isDirectory() || !subfolder.canRead()) return NO_COVER_ART;
        byte[] explicit = readCoverImageFile(subfolder);
        if (explicit.length > 0) return explicit;
        File[] files = subfolder.listFiles();
        if (files == null) return NO_COVER_ART;
        String subPath = buildSubPath(relativePath, subfolder.getName());
        return findEmbeddedCover(pathId, subPath, files);
    }

    private byte[] findEmbeddedCover(Long pathId, String relativePath, File[] files) {
        for (File file : files) {
            if (!file.isFile() || !isAudio(file)) continue;
            byte[] cover = getCoverArt(pathId, buildSubPath(relativePath, file.getName()));
            if (cover != null && cover.length > 0) return cover;
        }
        return NO_COVER_ART;
    }

    private byte[] readCoverImageFile(File dir) {
        for (String name : new String[]{"cover.jpg", "cover.png", "folder.jpg", "folder.png"}) {
            File img = new File(dir, name);
            if (img.exists() && img.isFile() && img.canRead()) {
                try { return java.nio.file.Files.readAllBytes(img.toPath()); }
                catch (IOException e) {
                    log.debug("Cover image {} could not be read: {}", img.getName(), e.getMessage());
                }
            }
        }
        return NO_COVER_ART;
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
            ytDlpPath,
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

        if (log.isInfoEnabled()) {
            log.info("[DJ Cache] Ejecutando: {}", String.join(" ", cmd));
        }

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
                throw new MusicOperationException(
                        "yt-dlp terminó con código " + exitCode + " para videoId=" + videoId);
            }
            if (!outputFile.exists() || outputFile.length() == 0) {
                throw new MusicOperationException("El archivo mp3 no se generó para videoId=" + videoId);
            }

            log.info("[DJ Cache] ✅ Listo: {} ({} bytes)", outputFile.getName(), outputFile.length());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MusicOperationException("Descarga de DJ Cache interrumpida", e);
        } catch (IOException e) {
            throw new MusicOperationException("Fallo al ejecutar yt-dlp para DJ Cache", e);
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
        boolean directPlayable = BROWSER_DIRECT_EXTS.contains(ext);
        boolean needsTranscode = !ALREADY_OPUS.contains(ext)
                && (LOSSLESS_EXTS.contains(ext) || !"high".equals(quality) || !directPlayable);
        if (!needsTranscode) {
            streamFileToResponse(file, rangeHeader, response);
            return;
        }
        try {
            File cached = getTranscodeCache(pathId, relativePath, quality);
            if (cached.exists()) {
                streamFileToResponse(cached, rangeHeader, response);
            } else if (!directPlayable) {
                transcodeToOggOpus(file, cached, bitrateKbps);
                streamFileToResponse(cached, rangeHeader, response);
            } else {
                // Serve original immediately — start background transcode for next play
                streamFileToResponse(file, rangeHeader, response);
                startBackgroundTranscode(file, cached, bitrateKbps);
            }
        } catch (Exception e) {
            log.warn("Stream with quality failed for {}, falling back to original: {}", file.getName(), e.getMessage());
            streamFileToResponse(file, rangeHeader, response);
        }
    }

    private void startBackgroundTranscode(File file, File cached, int bitrateKbps) {
        String jobKey = cached.getName();
        if (!transcoding.add(jobKey)) return;
        transcodePool.submit(() -> {
            try {
                transcodeToOggOpus(file, cached, bitrateKbps);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Background transcode interrupted for {}", file.getName());
            } catch (Exception ex) {
                log.warn("Background transcode failed for {}: {}", file.getName(), ex.getMessage());
            } finally {
                transcoding.remove(jobKey);
            }
        });
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
            ffmpegPath, "-y",
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
        if (exit != 0 || !tmp.exists()) {
            Files.deleteIfExists(tmp.toPath());
            throw new IOException("ffmpeg exit=" + exit);
        }
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
        for (File f : files) {
            if (f.lastModified() < cutoff && deleteCacheFile(f.toPath())) deleted++;
        }
        if (deleted > 0) log.info("Transcode cache: deleted {} stale files", deleted);
    }

    private boolean deleteCacheFile(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("Could not delete stale transcode {}", path, e);
            return false;
        }
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

        ByteRange range;
        try {
            range = parseByteRange(rangeHeader, fileLength);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader(CONTENT_RANGE_HEADER, "bytes */" + fileLength);
            response.setContentLengthLong(0);
            return;
        }
        long start = range.start();
        long end = range.end();

        if (fileLength <= 0 || start < 0 || start >= fileLength || end < start) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader(CONTENT_RANGE_HEADER, "bytes */" + fileLength);
            response.setContentLengthLong(0);
            return;
        }

        if (range.partial()) {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader(CONTENT_RANGE_HEADER, "bytes " + start + "-" + end + "/" + fileLength);
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

    private record ByteRange(long start, long end, boolean partial) {}

    private ByteRange parseByteRange(String rangeHeader, long fileLength) {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return new ByteRange(0, fileLength - 1, false);
        }
        String rangeSpec = rangeHeader.substring(6).split(",", 2)[0].trim();
        String[] parts = rangeSpec.split("-", 2);
        boolean openEnded = parts.length < 2 || parts[1].isEmpty();
        long start;
        long end;
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
        return new ByteRange(start, end, true);
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
            setMetadataField(tag, FieldKey.TITLE, title);
            setMetadataField(tag, FieldKey.ARTIST, artist);
            setMetadataField(tag, FieldKey.ALBUM, album);
            setMetadataField(tag, FieldKey.YEAR, year);
            af.setTag(tag);
            AudioFileIO.write(af);
            updateMetadataCache(new MetadataCacheUpdate(
                    pathId, relativePath, file, title, artist, album, year, af));
            invalidateBrowseCache(pathId);
        } catch (Exception e) {
            throw new MusicOperationException("No se pudieron actualizar los metadatos: " + e.getMessage(), e);
        }
    }

    private void setMetadataField(Tag tag, FieldKey key, String value) throws Exception {
        if (value == null) return;
        if (value.isBlank()) {
            tag.deleteField(key);
        } else {
            tag.setField(key, value.trim());
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

        for (File file : audioFiles.subList(0, Math.min(safeLimit, audioFiles.size()))) {
            processed++;
            String relPath = relativePath(base, file);
            MetadataFillResult result = fillYoutubeMetadata(pathId, file, relPath, onlyMissing);
            if (result.failed()) {
                failed++;
            } else if (result.item() != null) {
                updated++;
                items.add(result.item());
            } else {
                skipped++;
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

    private MetadataFillResult fillYoutubeMetadata(Long pathId, File file,
                                                    String relativePath, boolean onlyMissing) {
        try {
            AudioFile audioFile = AudioFileIO.read(file);
            Tag tag = audioFile.getTagOrCreateDefault();
            String existingTitle = Optional.ofNullable(tag.getFirst(FieldKey.TITLE)).orElse("");
            String existingArtist = Optional.ofNullable(tag.getFirst(FieldKey.ARTIST)).orElse("");
            String existingAlbum = Optional.ofNullable(tag.getFirst(FieldKey.ALBUM)).orElse("");
            boolean suspiciousArtist = isSuspiciousArtistName(existingArtist);
            boolean metadataComplete = !existingTitle.isBlank() && !existingArtist.isBlank()
                    && !suspiciousArtist && !existingAlbum.isBlank();
            if (onlyMissing && metadataComplete) return MetadataFillResult.skipped();

            String query = buildYoutubeMetadataQuery(file, existingTitle, existingArtist, suspiciousArtist);
            YoutubeMetadata metadata = lookupYoutubeMetadata(query);
            if (metadata == null || metadata.title().isBlank()) return MetadataFillResult.skipped();

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
            if (!changed) return MetadataFillResult.skipped();

            audioFile.setTag(tag);
            AudioFileIO.write(audioFile);
            updateMetadataCache(new MetadataCacheUpdate(
                    pathId, relativePath, file,
                    tag.getFirst(FieldKey.TITLE), tag.getFirst(FieldKey.ARTIST),
                    tag.getFirst(FieldKey.ALBUM), tag.getFirst(FieldKey.YEAR), audioFile));
            return MetadataFillResult.updated(Map.of(
                    "path", relativePath,
                    "title", tag.getFirst(FieldKey.TITLE),
                    "artist", tag.getFirst(FieldKey.ARTIST),
                    "album", tag.getFirst(FieldKey.ALBUM)));
        } catch (Exception e) {
            return MetadataFillResult.failure();
        }
    }

    private String buildYoutubeMetadataQuery(File file, String existingTitle,
                                             String existingArtist, boolean suspiciousArtist) {
        if (existingTitle.isBlank()) return stripExtension(file.getName());
        return existingArtist.isBlank() || suspiciousArtist
                ? existingTitle
                : existingArtist + " " + existingTitle;
    }

    private record MetadataFillResult(Map<String, String> item, boolean failed) {
        private static MetadataFillResult updated(Map<String, String> item) {
            return new MetadataFillResult(item, false);
        }

        private static MetadataFillResult skipped() {
            return new MetadataFillResult(null, false);
        }

        private static MetadataFillResult failure() {
            return new MetadataFillResult(null, true);
        }
    }

    public Map<String, Object> lookupYoutubeMetadataMap(String query) {
        YoutubeMetadata metadata = lookupYoutubeMetadata(query);
        if (metadata == null) return Map.of(FOUND_KEY, false);
        return Map.of(
                FOUND_KEY, true,
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
                    ytDlpPath,
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String cleanYoutubeTitle(String title) {
        return MediaTextCleaner.cleanYoutubeTitle(title);
    }

    private String cleanYoutubeArtist(String artist) {
        String cleaned = MediaTextCleaner.cleanYoutubeArtist(artist);
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

    record MetadataCacheUpdate(Long pathId, String relativePath, File file, String title,
                               String artist, String album, String year, AudioFile audioFile) {}

    private void updateMetadataCache(MetadataCacheUpdate update) {
        Long pathId = update.pathId();
        String relativePath = update.relativePath();
        File file = update.file();
        String title = update.title();
        String artist = update.artist();
        String album = update.album();
        String year = update.year();
        AudioFile af = update.audioFile();
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
                entry.setBpm(parseBpm(bpmStr));
            }
            metadataCacheRepo.save(entry);
        } catch (Exception e) {
            log.debug("Metadata cache update failed for {}: {}", relativePath, e.getMessage());
        }
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
        } catch (Exception e) {
            log.debug("Could not write audio metadata to {}: {}", file.getName(), e.getMessage());
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    public List<MusicMetadataDto> searchMusic(Long pathId, String subPath, String query, int limit) {
        List<String> tokens = searchTokens(query);
        if (tokens.isEmpty()) return Collections.emptyList();

        // ── Fast path: search the indexed metadata cache — no filesystem walk ──
        List<TrackMetadataCache> dbCache = metadataCacheRepo.findByNasPathId(pathId);
        if (!dbCache.isEmpty()) {
            return searchIndexedMusic(pathId, subPath, tokens, limit, dbCache);
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

        SearchResults results = collectInitialSearchHits(base, audioFiles, cacheMap, tokens);

        if (results.hits().size() < limit) {
            addDeepSearchHits(pathId, base, audioFiles, cacheMap, tokens, results);
        }

        return results.hits().stream()
                .sorted(Comparator
                        .comparingInt((SearchHit hit) -> hit.score).reversed()
                        .thenComparing(hit -> normalizeSearchText(hit.file.getName())))
                .limit(Math.max(1, limit))
                .map(hit -> toSearchResult(hit, base, pathId))
                .toList();
    }

    private MusicMetadataDto toSearchResult(SearchHit hit, Path base, Long pathId) {
        MusicMetadataDto dto = hit.dto != null
                ? hit.dto
                : buildDto(hit.file, base, pathId, Collections.singletonMap(hit.relPath, hit.cached));
        dto.setNasPathId(pathId);
        return dto;
    }

    private record SearchResults(List<SearchHit> hits, Set<String> paths) {}

    private void addDeepSearchHits(Long pathId, Path base, List<File> audioFiles,
                                   Map<String, TrackMetadataCache> cacheMap, List<String> tokens,
                                   SearchResults results) {
        int deepReads = 0;
        for (File file : audioFiles) {
            if (deepReads >= SEARCH_DEEP_METADATA_LIMIT) break;
            String relPath = relativePath(base, file);
            boolean needsDeepRead = !results.paths().contains(relPath)
                    && validCache(cacheMap.get(relPath), file) == null;
            if (needsDeepRead) {
                deepReads++;
                MusicMetadataDto dto = buildDto(file, base, pathId, cacheMap);
                int score = scoreSearchDto(dto, tokens);
                if (score > 0) {
                    results.hits().add(new SearchHit(file, relPath, null, score, dto));
                    results.paths().add(relPath);
                }
            }
        }
    }

    private SearchResults collectInitialSearchHits(Path base, List<File> audioFiles,
                                                   Map<String, TrackMetadataCache> cacheMap,
                                                   List<String> tokens) {
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
        return new SearchResults(hits, hitPaths);
    }

    private List<MusicMetadataDto> searchIndexedMusic(Long pathId, String subPath, List<String> tokens,
                                                      int limit, List<TrackMetadataCache> dbCache) {
        String subPathFilter = subPath != null && !subPath.isBlank() ? subPath : null;
        List<Map.Entry<MusicMetadataDto, Integer>> scored = new ArrayList<>();
        for (TrackMetadataCache cached : dbCache) {
            if (subPathFilter != null && !cached.getRelativePath().startsWith(subPathFilter)) continue;
            MusicMetadataDto dto = dtoFromCache(cached);
            dto.setNasPathId(pathId);
            int score = scoreSearchDto(dto, tokens);
            if (score > 0) scored.add(Map.entry(dto, score));
        }
        scored.sort(this::compareScoredSearchResults);
        return scored.stream().limit(Math.max(1, limit)).map(Map.Entry::getKey).toList();
    }

    private int compareScoredSearchResults(Map.Entry<MusicMetadataDto, Integer> first,
                                           Map.Entry<MusicMetadataDto, Integer> second) {
        int scoreComparison = Integer.compare(second.getValue(), first.getValue());
        if (scoreComparison != 0) return scoreComparison;
        String firstTitle = normalizeSearchText(first.getKey().getTitle());
        String secondTitle = normalizeSearchText(second.getKey().getTitle());
        return firstTitle.compareTo(secondTitle);
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
        int score = 10 + queryMatchScore(query, name, title, artist, album, path);
        for (String token : tokens) {
            score += tokenMatchScore(token, name, title, artist, album, path);
        }
        return score;
    }

    private int queryMatchScore(String query, String name, String title,
                                String artist, String album, String path) {
        int score = 0;
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
        return score;
    }

    private int tokenMatchScore(String token, String name, String title,
                                String artist, String album, String path) {
        int score = prefixOrContainsScore(title, token, 45, 28)
                + prefixOrContainsScore(name, token, 40, 24)
                + prefixOrContainsScore(artist, token, 34, 20);
        if (album.contains(token)) score += 10;
        if (path.contains(token)) score += 5;
        return score;
    }

    private int prefixOrContainsScore(String value, String token, int prefixScore, int containsScore) {
        if (value.startsWith(token)) return prefixScore;
        return value.contains(token) ? containsScore : 0;
    }

    private List<String> searchTokens(String query) {
        String normalized = normalizeSearchText(query);
        if (normalized.isBlank()) return Collections.emptyList();
        return Arrays.stream(normalized.split("\\s+"))
                .filter(token -> token.length() > 1 || normalized.length() == 1)
                .distinct()
                .toList();
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
        if (artist == null) return Collections.emptyList();
        String full = normalizeSearchText(artist);
        if (full.isBlank()) return Collections.emptyList();
        List<String> parts = new ArrayList<>();
        parts.add(full);
        Arrays.stream(artist.split("(?i)[,;&+/]|\\b(?:feat\\.?|ft\\.?|con|and|y)\\b"))
                .map(this::normalizeSearchText)
                .filter(part -> !part.isBlank())
                .forEach(parts::add);
        return parts.stream().distinct().toList();
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
        } catch (Exception e) {
            log.debug("Could not read lyrics sidecar: {}", e.getMessage());
        }
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
            return buildCachedMetadata(b, cached, f);
        }

        // Cache miss or stale — read from disk
        if (!allowDiskRead) {
            return buildMetadataWithoutDiskRead(b, f);
        }

        try {
            AudioFile af = AudioFileIO.read(f);
            String format   = af.getExt().toLowerCase();
            int    duration = af.getAudioHeader().getTrackLength();
            b.format(format).duration(duration);

            ScannedTagMetadata metadata = readScannedTagMetadata(af.getTag(), f);
            b.title(metadata.title()).artist(metadata.artist()).album(metadata.album())
                    .year(metadata.year()).hasCover(metadata.hasCover()).bpm(metadata.bpm());

            // Save to cache
            if (pathId != null) {
                TrackMetadataCache entry = cached != null ? cached
                        : TrackMetadataCache.builder().nasPathId(pathId).relativePath(relPath).build();
                entry.setLastModified(lastMod);
                entry.setTitle(metadata.title());
                entry.setArtist(metadata.artist());
                entry.setAlbum(metadata.album());
                entry.setFormat(format);
                entry.setYear(metadata.year());
                entry.setDuration(duration);
                entry.setHasCover(metadata.hasCover());
                entry.setBpm(metadata.bpm());
                saveScannedMetadata(entry);
            }

        } catch (Exception e) {
            b.title(stripExtension(f.getName())).format(extension(f.getName()));
        }

        return b.build();
    }

    private MusicMetadataDto buildCachedMetadata(MusicMetadataDto.MusicMetadataDtoBuilder builder,
                                                 TrackMetadataCache cached, File file) {
        String title = cached.getTitle() != null && !cached.getTitle().isBlank()
                ? cached.getTitle() : stripExtension(file.getName());
        return builder.title(title)
                .artist(cached.getArtist() != null ? cached.getArtist() : "")
                .album(cached.getAlbum() != null ? cached.getAlbum() : "")
                .format(cached.getFormat() != null ? cached.getFormat() : extension(file.getName()))
                .year(cached.getYear() != null ? cached.getYear() : "")
                .duration(cached.getDuration())
                .hasCover(cached.isHasCover())
                .bpm(cached.getBpm())
                .build();
    }

    private MusicMetadataDto buildMetadataWithoutDiskRead(MusicMetadataDto.MusicMetadataDtoBuilder builder,
                                                           File file) {
        return builder.title(stripExtension(file.getName()))
                .artist("").album("").format(extension(file.getName())).year("")
                .duration(0).hasCover(false).bpm(0).build();
    }

    private record ScannedTagMetadata(String title, String artist, String album, String year,
                                      int bpm, boolean hasCover) {}

    private ScannedTagMetadata readScannedTagMetadata(Tag tag, File file) {
        if (tag == null) {
            return new ScannedTagMetadata(stripExtension(file.getName()), "", "", "", 0, false);
        }
        String tagTitle = tag.getFirst(FieldKey.TITLE);
        String title = tagTitle != null && !tagTitle.isBlank() ? tagTitle : stripExtension(file.getName());
        String artist = Optional.ofNullable(tag.getFirst(FieldKey.ARTIST)).orElse("");
        String album = Optional.ofNullable(tag.getFirst(FieldKey.ALBUM)).orElse("");
        String year = Optional.ofNullable(tag.getFirst(FieldKey.YEAR)).orElse("");
        int bpm = parseBpm(tag.getFirst(FieldKey.BPM));
        return new ScannedTagMetadata(title, artist, album, year, bpm, tag.getFirstArtwork() != null);
    }

    private int parseBpm(String bpm) {
        if (bpm == null || bpm.isBlank()) return 0;
        try {
            return Integer.parseInt(bpm.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void saveScannedMetadata(TrackMetadataCache entry) {
        try {
            metadataCacheRepo.save(entry);
        } catch (Exception e) {
            log.debug("Could not cache scanned track metadata: {}", e.getMessage());
        }
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
                .toList();

        List<File> audioFiles = Arrays.stream(files)
                .filter(f -> f.isFile() && isAudio(f))
                .sorted(Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)))
                .toList();

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
            if (validCache(cached, file) == null) {
                String key = pathId + CACHE_KEY_SEPARATOR + relPath + CACHE_KEY_SEPARATOR + file.lastModified();
                if (metadataWarmupInFlight.add(key)) {
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
        }
    }

    private String directoryListingKey(Long pathId, String subPath, File dir) {
        return pathId + CACHE_KEY_SEPARATOR + (subPath == null ? "" : subPath)
                + CACHE_KEY_SEPARATOR + dir.getAbsolutePath();
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
                .toList();
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
