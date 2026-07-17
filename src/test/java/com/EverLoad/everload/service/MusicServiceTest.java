package com.EverLoad.everload.service;

import com.EverLoad.everload.repository.NasPathRepository;
import com.EverLoad.everload.repository.TrackMetadataCacheRepository;
import com.EverLoad.everload.model.TrackMetadataCache;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers the cover-art fallback and quality-aware streaming logic added to
 * MusicService — both run as plain unit tests against a mocked NasService so no
 * NAS volume, ffmpeg or HTTP server is required.
 */
class MusicServiceTest {

    @TempDir
    Path tempDir;

    private NasService nasService;
    private TrackMetadataCacheRepository metadataRepository;
    private RestTemplate restTemplate;
    private MusicService musicService;

    @BeforeEach
    void setUp() {
        nasService = mock(NasService.class);
        metadataRepository = mock(TrackMetadataCacheRepository.class);
        restTemplate = mock(RestTemplate.class);
        musicService = new MusicService(nasService, mock(NasPathRepository.class), metadataRepository,
                restTemplate);
        ReflectionTestUtils.setField(musicService, "avatarStoragePath", tempDir.toString());
    }

    private void stubResolvedFile(String fileName, byte[] content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.write(file, content);
        when(nasService.resolveValidatedPath(eq(1L), eq(fileName))).thenReturn(file);
    }

    // ── getCoverArt fallback ──────────────────────────────────────────────────

    @Test
    void getCoverArt_fallsBackToCoverJpgInSameDirectory() throws Exception {
        byte[] coverBytes = "fake-jpeg-bytes".getBytes();
        Files.write(tempDir.resolve("cover.jpg"), coverBytes);
        stubResolvedFile("track.mp3", "not a real mp3".getBytes());

        byte[] result = musicService.getCoverArt(1L, "track.mp3");

        assertArrayEquals(coverBytes, result);
    }

    @Test
    void getCoverArt_fallsBackToFolderPngWhenCoverJpgMissing() throws Exception {
        byte[] folderBytes = "fake-png-bytes".getBytes();
        Files.write(tempDir.resolve("folder.png"), folderBytes);
        stubResolvedFile("track.flac", "not a real flac".getBytes());

        byte[] result = musicService.getCoverArt(1L, "track.flac");

        assertArrayEquals(folderBytes, result);
    }

    @Test
    void getCoverArt_returnsNullWhenNoEmbeddedArtAndNoCoverFile() throws Exception {
        stubResolvedFile("track.mp3", "not a real mp3".getBytes());

        assertArrayEquals(new byte[0], musicService.getCoverArt(1L, "track.mp3"));
    }

    @Test
    void getCoverArt_returnsNullForNonAudioFile() throws Exception {
        stubResolvedFile("readme.txt", "hello".getBytes());

        assertArrayEquals(new byte[0], musicService.getCoverArt(1L, "readme.txt"));
        // Must short-circuit before ever resolving a directory cover fallback.
        verify(nasService, atMostOnce()).resolveValidatedPath(eq(1L), eq("readme.txt"));
    }

    @Test
    void getFolderCoverArt_checksSubfoldersWhenRootHasNoCover() throws Exception {
        Path albumDir = Files.createDirectory(tempDir.resolve("album"));
        Path discDir = Files.createDirectory(albumDir.resolve("disc-1"));
        byte[] coverBytes = "subfolder-cover".getBytes();
        Files.write(discDir.resolve("cover.jpg"), coverBytes);
        when(nasService.resolveValidatedPath(1L, "album")).thenReturn(albumDir);

        assertArrayEquals(coverBytes, musicService.getFolderCoverArt(1L, "album"));
    }

    // ── streamAudioToResponse — branches that must NOT touch the transcode pool ─

    @Test
    void streamAudioToResponse_originalQuality_servesFileDirectly() throws Exception {
        byte[] audioBytes = "0123456789".getBytes();
        stubResolvedFile("song.mp3", audioBytes);
        MockHttpServletResponse response = new MockHttpServletResponse();

        musicService.streamAudioToResponse(1L, "song.mp3", null, "original", response);

        assertEquals(200, response.getStatus());
        assertArrayEquals(audioBytes, response.getContentAsByteArray());
        assertEquals("bytes", response.getHeader("Accept-Ranges"));
    }

    @Test
    void streamAudioToResponse_blankQuality_servesFileDirectly() throws Exception {
        byte[] audioBytes = "abcdefghij".getBytes();
        stubResolvedFile("song2.mp3", audioBytes);
        MockHttpServletResponse response = new MockHttpServletResponse();

        musicService.streamAudioToResponse(1L, "song2.mp3", null, "", response);

        assertEquals(200, response.getStatus());
        assertArrayEquals(audioBytes, response.getContentAsByteArray());
    }

    @Test
    void deleteCacheFileReportsWhetherAFileWasRemoved() throws Exception {
        Path cacheFile = Files.writeString(tempDir.resolve("stale.ogg"), "audio");

        assertEquals(true, ReflectionTestUtils.invokeMethod(musicService, "deleteCacheFile", cacheFile));
        assertEquals(false, ReflectionTestUtils.invokeMethod(musicService, "deleteCacheFile", cacheFile));
    }

    @Test
    void libraryViewsMapCachedTracksWithoutStartingIndexer() {
        TrackMetadataCache cached = cachedTrack("albums/song.mp3", "Song", "Artist");
        when(metadataRepository.findOverviewSlice(eq(1L), any())).thenReturn(List.of(cached));
        when(metadataRepository.findByNasPathIdOrderByLastModifiedDesc(eq(1L), any())).thenReturn(List.of(cached));

        Map<String, Object> overview = musicService.getLibraryOverview(1L, 10);
        List<?> recent = musicService.getRecentTracks(1L, 10);

        assertEquals(1, ((List<?>) overview.get("tracks")).size());
        assertEquals(false, overview.get("indexing"));
        assertEquals(1, recent.size());
    }

    @Test
    void cachedArtistTracksMatchAliasesAndUseStableOrdering() {
        TrackMetadataCache first = cachedTrack("b/second.mp3", "Second", "Guest");
        first.setAlbum("Beta");
        TrackMetadataCache second = cachedTrack("a/first.mp3", "First", "Main feat. Guest");
        second.setAlbum("Alpha");
        when(metadataRepository.findByNasPathId(1L)).thenReturn(List.of(first, second));

        List<?> result = musicService.getCachedTracksByArtist(1L, "Missing", List.of("Guest", "guest"), 10);

        assertEquals(2, result.size());
    }

    @Test
    void searchHelpersNormalizeSplitAndParseSafely() {
        assertEquals(List.of("cancion", "unica"),
                ReflectionTestUtils.invokeMethod(musicService, "searchTokens", "Canción canción única"));
        assertEquals(List.of("main feat guest", "main", "guest"),
                ReflectionTestUtils.invokeMethod(musicService, "artistParts", "Main feat. Guest"));
        assertEquals(List.of(), ReflectionTestUtils.invokeMethod(musicService, "artistParts", (Object) null));
        assertEquals(128, (int) ReflectionTestUtils.invokeMethod(musicService, "parseBpm", "128"));
        assertEquals(0, (int) ReflectionTestUtils.invokeMethod(musicService, "parseBpm", "invalid"));
        assertEquals("1\u0000music\u0000" + tempDir.toFile().getAbsolutePath(),
                ReflectionTestUtils.invokeMethod(musicService, "directoryListingKey", 1L, "music", tempDir.toFile()));
    }

    @Test
    void emptyNasRepositoryProducesNoRandomTracks() {
        assertEquals(List.of(), musicService.getRandomTracks(5));
    }

    @Test
    void artistImageUsesLocalFileAndThenMemoryCache() throws Exception {
        Path artistDir = Files.createDirectories(tempDir.resolve("artists-auto"));
        Files.writeString(artistDir.resolve("david_guetta.jpg"), "image");

        Map<String, Object> first = musicService.lookupArtistImage("David Guetta");
        Files.delete(artistDir.resolve("david_guetta.jpg"));
        Map<String, Object> cached = musicService.lookupArtistImage("David Guetta");

        assertEquals(true, first.get("found"));
        assertEquals(first.get("imageUrl"), cached.get("imageUrl"));
    }

    @Test
    void failedArtistProviderIsRememberedWithoutSecondRequest() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new IllegalStateException("provider down"));

        assertEquals(false, musicService.lookupArtistImage("Unavailable Artist").get("found"));
        assertEquals(false, musicService.lookupArtistImage("Unavailable Artist").get("found"));

        verify(restTemplate, times(1)).getForObject(anyString(), eq(Map.class));
    }

    @Test
    void albumCoverUsesLocalCacheFile() throws Exception {
        Path coverDir = Files.createDirectories(tempDir.resolve("covers-auto"));
        Files.writeString(coverDir.resolve("artist__album.jpg"), "cover");

        Map<String, Object> result = musicService.lookupAlbumCover("Artist", "Album");

        assertEquals(true, result.get("found"));
        assertEquals("/api/music/album-auto-cover/artist__album.jpg", result.get("imageUrl"));
    }

    @Test
    void purgeOrphanedAutoImagesKeepsArtistsStillInMetadata() throws Exception {
        Path artistDir = Files.createDirectories(tempDir.resolve("artists-auto"));
        Files.writeString(artistDir.resolve("active_artist.jpg"), "keep");
        Files.writeString(artistDir.resolve("orphan.jpg"), "delete");
        TrackMetadataCache active = cachedTrack("song.mp3", "Song", "Active Artist");
        when(metadataRepository.findAll()).thenReturn(List.of(active));

        int removed = musicService.purgeOrphanedAutoImages();

        assertEquals(1, removed);
        assertTrue(Files.exists(artistDir.resolve("active_artist.jpg")));
        assertFalse(Files.exists(artistDir.resolve("orphan.jpg")));
    }

    @Test
    void purgeOrphanedAutoImagesContainsInvalidStorageDirectory() throws Exception {
        Files.writeString(tempDir.resolve("artists-auto"), "not a directory");
        when(metadataRepository.findAll()).thenReturn(List.of());

        assertEquals(0, musicService.purgeOrphanedAutoImages());
    }

    @Test
    void artistProviderResultIsDownloadedAndCachedLocally() {
        Map<String, Object> artist = Map.of(
                "name", "Aitana",
                "picture_xl", "https://images.test/aitana.jpg");
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("data", List.of(artist)));
        when(restTemplate.getForObject("https://images.test/aitana.jpg", byte[].class))
                .thenReturn("image-data".getBytes());

        Map<String, Object> result = musicService.lookupArtistImage("Aitana");

        assertEquals(true, result.get("found"));
        assertTrue(Files.exists(tempDir.resolve("artists-auto/aitana.jpg")));
    }

    @Test
    void albumProviderResultDownloadsCoverArt() {
        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("releases", List.of(Map.of("id", "release-1")))));
        when(restTemplate.getForObject(
                "https://coverartarchive.org/release/release-1/front-250", byte[].class))
                .thenReturn(new byte[6000]);

        Map<String, Object> result = musicService.lookupAlbumCover("Artist", "Remote Album");

        assertEquals(true, result.get("found"));
        assertTrue(Files.exists(tempDir.resolve("covers-auto/artist__remote_album.jpg")));
    }

    @Test
    void libraryIndexStartsOnceAndPrunesStaleMetadata() throws Exception {
        Path library = Files.createDirectory(tempDir.resolve("library"));
        TrackMetadataCache stale = cachedTrack("missing.mp3", "Missing", "Artist");
        ExecutorService executor = mock(ExecutorService.class);
        Future<?> future = mock(Future.class);
        when(nasService.getBasePath(1L)).thenReturn(library);
        when(metadataRepository.findByNasPathId(1L)).thenReturn(List.of(stale));
        doReturn(future).when(executor).submit(any(Runnable.class));
        ReflectionTestUtils.setField(musicService, "metadataExecutor", executor);

        Map<String, Object> started = musicService.startLibraryIndex(1L);
        Map<String, Object> duplicate = musicService.startLibraryIndex(1L);

        assertEquals(true, started.get("started"));
        assertEquals(false, duplicate.get("started"));
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).submit(task.capture());
        task.getValue().run();
        verify(metadataRepository).deleteAll(List.of(stale));
    }

    @Test
    void libraryIndexRejectsUnreadablePath() {
        when(nasService.getBasePath(2L)).thenReturn(tempDir.resolve("missing"));

        assertThrows(IllegalArgumentException.class, () -> musicService.startLibraryIndex(2L));
    }

    @Test
    void searchMusicUsesIndexedMetadataAndHonorsLimit() {
        TrackMetadataCache beta = cachedTrack("album/beta.mp3", "Beta Song", "Artist");
        TrackMetadataCache alpha = cachedTrack("album/alpha.mp3", "Alpha Song", "Artist");
        when(metadataRepository.findByNasPathId(1L)).thenReturn(List.of(beta, alpha));

        List<?> results = musicService.searchMusic(1L, "album", "song", 1);

        assertEquals(1, results.size());
        assertEquals("Alpha Song", ((com.EverLoad.everload.dto.MusicMetadataDto) results.get(0)).getTitle());
    }

    @Test
    void searchMusicFallsBackToFilesystemWhenIndexIsEmpty() throws Exception {
        Files.writeString(tempDir.resolve("matching-track.mp3"), "not-real-audio");
        when(metadataRepository.findByNasPathId(1L)).thenReturn(List.of());
        when(metadataRepository.findByNasPathIdAndRelativePathIn(anyLong(), anyList())).thenReturn(List.of());
        when(nasService.getBasePath(1L)).thenReturn(tempDir);
        when(nasService.resolveValidatedPath(1L, "")).thenReturn(tempDir);

        List<com.EverLoad.everload.dto.MusicMetadataDto> results =
                musicService.searchMusic(1L, "", "matching", 5);

        assertEquals(1, results.size());
        assertEquals("matching-track", results.get(0).getTitle());
    }

    @Test
    void invalidAndOutOfBoundsRangesReturnRequestedRangeNotSatisfiable() throws Exception {
        stubResolvedFile("range-errors.mp3", "0123456789".getBytes());
        MockHttpServletResponse invalid = new MockHttpServletResponse();
        MockHttpServletResponse outOfBounds = new MockHttpServletResponse();

        musicService.streamAudioToResponse(1L, "range-errors.mp3", "bytes=abc-def", "original", invalid);
        musicService.streamAudioToResponse(1L, "range-errors.mp3", "bytes=20-30", "original", outOfBounds);

        assertEquals(416, invalid.getStatus());
        assertEquals("bytes */10", invalid.getHeader("Content-Range"));
        assertEquals(416, outOfBounds.getStatus());
        assertEquals("bytes */10", outOfBounds.getHeader("Content-Range"));
    }

    @Test
    void lyricsSidecarReturnsNullWhenPathResolutionFails() {
        when(nasService.resolveValidatedPath(1L, "missing.mp3"))
                .thenThrow(new IllegalArgumentException("outside root"));

        assertNull(musicService.findLrcSidecar(1L, "missing.mp3"));
    }

    @Test
    void albumProviderFailuresReturnStableNotFoundResult() {
        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenThrow(new IllegalStateException("provider down"));

        assertEquals(false, musicService.lookupAlbumCover("Artist", "Provider Failure").get("found"));
    }

    @Test
    void failedCoverDownloadContinuesThroughRemainingReleases() {
        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("releases", List.of(Map.of("id", "broken-release")))));
        when(restTemplate.getForObject(
                "https://coverartarchive.org/release/broken-release/front-250", byte[].class))
                .thenThrow(new IllegalStateException("image unavailable"));

        assertEquals(false, musicService.lookupAlbumCover("Artist", "Missing Cover").get("found"));
    }

    @Test
    void artistImagePersistenceFailureReturnsNotFound() throws Exception {
        Path storageFile = Files.writeString(tempDir.resolve("storage-file"), "not a directory");
        ReflectionTestUtils.setField(musicService, "avatarStoragePath", storageFile.toString());
        Map<String, Object> artist = Map.of(
                "name", "Broken Storage Artist",
                "picture_xl", "https://images.test/artist.jpg");
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("data", List.of(artist)));
        when(restTemplate.getForObject("https://images.test/artist.jpg", byte[].class))
                .thenReturn("image".getBytes());

        assertEquals(false, musicService.lookupArtistImage("Broken Storage Artist").get("found"));
    }

    @Test
    void albumCoverPersistenceFailureReturnsNotFound() throws Exception {
        Path storageFile = Files.writeString(tempDir.resolve("storage-file"), "not a directory");
        ReflectionTestUtils.setField(musicService, "avatarStoragePath", storageFile.toString());
        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("releases", List.of(Map.of("id", "release-storage")))));
        when(restTemplate.getForObject(
                "https://coverartarchive.org/release/release-storage/front-250", byte[].class))
                .thenReturn(new byte[6000]);

        assertEquals(false, musicService.lookupAlbumCover("Artist", "Storage Failure").get("found"));
    }

    @Test
    void libraryIndexToleratesStaleMetadataQueryFailure() throws Exception {
        Path library = Files.createDirectory(tempDir.resolve("library"));
        ExecutorService executor = mock(ExecutorService.class);
        Future<?> future = mock(Future.class);
        when(nasService.getBasePath(1L)).thenReturn(library);
        when(metadataRepository.findByNasPathId(1L)).thenThrow(new IllegalStateException("database unavailable"));
        doReturn(future).when(executor).submit(any(Runnable.class));
        ReflectionTestUtils.setField(musicService, "metadataExecutor", executor);

        assertEquals(true, musicService.startLibraryIndex(1L).get("started"));
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).submit(task.capture());
        assertDoesNotThrow(task.getValue()::run);
    }

    @Test
    void emptyFolderHasNoCoverArt() throws Exception {
        Path empty = Files.createDirectory(tempDir.resolve("empty-album"));
        when(nasService.resolveValidatedPath(1L, "empty-album")).thenReturn(empty);

        assertArrayEquals(new byte[0], musicService.getFolderCoverArt(1L, "empty-album"));
    }

    @Test
    void backgroundTranscodeIsDeduplicatedAndFailureIsContained() throws Exception {
        byte[] audio = "lossless-audio".getBytes();
        stubResolvedFile("background-failure-unique.flac", audio);
        ReflectionTestUtils.setField(musicService, "ffmpegPath", "definitely-missing-everload-ffmpeg");
        ExecutorService executor = mock(ExecutorService.class);
        Future<?> future = mock(Future.class);
        doReturn(future).when(executor).submit(any(Runnable.class));
        ReflectionTestUtils.setField(musicService, "transcodePool", executor);
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse duplicate = new MockHttpServletResponse();

        musicService.streamAudioToResponse(1L, "background-failure-unique.flac", null, "normal", first);
        musicService.streamAudioToResponse(1L, "background-failure-unique.flac", null, "normal", duplicate);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).submit(task.capture());
        assertDoesNotThrow(task.getValue()::run);
        assertArrayEquals(audio, first.getContentAsByteArray());
        assertArrayEquals(audio, duplicate.getContentAsByteArray());
    }

    @Test
    void rejectedBackgroundTranscodeFallsBackToOriginalAgain() throws Exception {
        byte[] audio = "fallback-audio".getBytes();
        stubResolvedFile("rejected-transcode.flac", audio);
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.submit(any(Runnable.class))).thenThrow(new RejectedExecutionException("stopped"));
        ReflectionTestUtils.setField(musicService, "transcodePool", executor);
        MockHttpServletResponse response = new MockHttpServletResponse();

        musicService.streamAudioToResponse(1L, "rejected-transcode.flac", null, "normal", response);

        assertEquals(audio.length * 2, response.getContentAsByteArray().length);
    }

    @Test
    void failedTranscodeRemovesTemporaryOutput() throws Exception {
        Path input = Files.writeString(tempDir.resolve("input.flac"), "audio");
        File inputFile = input.toFile();
        File output = tempDir.resolve("output.ogg").toFile();
        ReflectionTestUtils.setField(musicService, "ffmpegPath", "where.exe");

        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                musicService, "transcodeToOggOpus", inputFile, output, 96));

        assertFalse(Files.exists(tempDir.resolve("output.ogg.tmp")));
    }

    @Test
    void deleteCacheFileReturnsFalseForNonEmptyDirectory() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("non-empty-cache"));
        Files.writeString(directory.resolve("entry"), "data");

        Boolean deleted = ReflectionTestUtils.invokeMethod(musicService, "deleteCacheFile", directory);

        assertFalse(deleted);
        assertTrue(Files.exists(directory));
    }

    @Test
    void diskMetadataReadParsesBpmAndContainsCacheFailure() throws Exception {
        Path track = Files.writeString(tempDir.resolve("metadata.mp3"), "audio");
        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);
        Tag tag = mock(Tag.class);
        when(audioFile.getExt()).thenReturn("MP3");
        when(audioFile.getAudioHeader()).thenReturn(header);
        when(header.getTrackLength()).thenReturn(180);
        when(audioFile.getTag()).thenReturn(tag);
        when(tag.getFirst(FieldKey.TITLE)).thenReturn("Title");
        when(tag.getFirst(FieldKey.ARTIST)).thenReturn("Artist");
        when(tag.getFirst(FieldKey.ALBUM)).thenReturn("Album");
        when(tag.getFirst(FieldKey.YEAR)).thenReturn("2026");
        when(tag.getFirst(FieldKey.BPM)).thenReturn("124");
        when(metadataRepository.findByNasPathIdAndRelativePath(1L, "metadata.mp3"))
                .thenReturn(Optional.empty());
        when(metadataRepository.save(any(TrackMetadataCache.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        try (MockedStatic<AudioFileIO> audioFiles = mockStatic(AudioFileIO.class)) {
            audioFiles.when(() -> AudioFileIO.read(track.toFile())).thenReturn(audioFile);

            com.EverLoad.everload.dto.MusicMetadataDto result = ReflectionTestUtils.invokeMethod(
                    musicService, "buildDto", track.toFile(), tempDir, 1L, null, true);

            assertEquals("Title", result.getTitle());
            assertEquals(124, result.getBpm());
        }
    }

    @Test
    void metadataCacheUpdateParsesBpmAndContainsRepositoryFailure() {
        File file = tempDir.resolve("cache-update.mp3").toFile();
        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);
        Tag tag = mock(Tag.class);
        when(audioFile.getExt()).thenReturn("MP3");
        when(audioFile.getAudioHeader()).thenReturn(header);
        when(header.getTrackLength()).thenReturn(90);
        when(audioFile.getTag()).thenReturn(tag);
        when(tag.getFirst(FieldKey.BPM)).thenReturn("126");
        when(metadataRepository.findByNasPathIdAndRelativePath(1L, "cache-update.mp3"))
                .thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(musicService, "updateMetadataCache",
                1L, "cache-update.mp3", file, "Title", "Artist", "Album", "2026", audioFile);

        ArgumentCaptor<TrackMetadataCache> entry = ArgumentCaptor.forClass(TrackMetadataCache.class);
        verify(metadataRepository).save(entry.capture());
        assertEquals(126, entry.getValue().getBpm());

        when(metadataRepository.findByNasPathIdAndRelativePath(1L, "failed.mp3"))
                .thenThrow(new IllegalStateException("database unavailable"));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(musicService, "updateMetadataCache",
                1L, "failed.mp3", file, "Title", "Artist", "Album", "2026", audioFile));
    }

    @Test
    void ensureMetadataContainsInvalidAudioAndScannedCacheCanPersist() throws Exception {
        File invalidAudio = Files.writeString(tempDir.resolve("invalid-tags.mp3"), "audio").toFile();
        assertDoesNotThrow(() -> musicService.ensureMetadata(invalidAudio, "Title", "Artist"));

        TrackMetadataCache entry = cachedTrack("saved.mp3", "Saved", "Artist");
        ReflectionTestUtils.invokeMethod(musicService, "saveScannedMetadata", entry);

        verify(metadataRepository).save(entry);
    }

    @Test
    void metadataWarmupSchedulesStaleFileAndClearsInFlightMarker() throws Exception {
        File file = Files.writeString(tempDir.resolve("warmup.mp3"), "audio").toFile();
        ExecutorService executor = mock(ExecutorService.class);
        Future<?> future = mock(Future.class);
        doReturn(future).when(executor).submit(any(Runnable.class));
        ReflectionTestUtils.setField(musicService, "metadataExecutor", executor);

        ReflectionTestUtils.invokeMethod(
                musicService, "warmMetadataCacheAsync", 1L, List.of(file), tempDir, Map.of());

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).submit(task.capture());
        task.getValue().run();
    }

    @Test
    void interruptedYoutubeLookupPreservesInterruptAndReturnsNotFound() throws Exception {
        ReflectionTestUtils.setField(musicService, "ytDlpPath", "yt-dlp-test");
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor()).thenThrow(new InterruptedException("stop"));

        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                ProcessBuilder.class,
                (builder, context) -> {
                    when(builder.redirectErrorStream(false)).thenReturn(builder);
                    when(builder.start()).thenReturn(process);
                })) {
            Map<String, Object> result = musicService.lookupYoutubeMetadataMap("query");

            assertEquals(false, result.get("found"));
            assertTrue(Thread.currentThread().isInterrupted());
            Thread.interrupted();
        }
    }

    @Test
    void interruptedBackgroundTranscodePreservesInterruptFlag() throws Exception {
        stubResolvedFile("interrupted-background.flac", "audio".getBytes());
        ExecutorService executor = mock(ExecutorService.class);
        Future<?> future = mock(Future.class);
        doReturn(future).when(executor).submit(any(Runnable.class));
        ReflectionTestUtils.setField(musicService, "transcodePool", executor);
        musicService.streamAudioToResponse(
                1L, "interrupted-background.flac", null, "normal", new MockHttpServletResponse());
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).submit(task.capture());
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor()).thenThrow(new InterruptedException("stop"));

        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                ProcessBuilder.class,
                (builder, context) -> {
                    when(builder.redirectErrorStream(true)).thenReturn(builder);
                    when(builder.start()).thenReturn(process);
                })) {
            task.getValue().run();
        }

        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @Test
    void youtubeBulkMetadataBuildsQueriesWithAndWithoutArtist() throws Exception {
        Path first = Files.writeString(tempDir.resolve("first.mp3"), "audio");
        Files.writeString(tempDir.resolve("second.mp3"), "audio");
        when(nasService.getBasePath(1L)).thenReturn(tempDir);
        when(nasService.resolveValidatedPath(1L, "")).thenReturn(tempDir);
        ReflectionTestUtils.setField(musicService, "ytDlpPath", "yt-dlp-test");
        AudioFile firstAudio = metadataAudioFile("Song One", "", "");
        AudioFile secondAudio = metadataAudioFile("Song Two", "Real Artist", "");

        try (MockedStatic<AudioFileIO> audioFiles = mockStatic(AudioFileIO.class);
             MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                     ProcessBuilder.class,
                     (builder, context) -> {
                         Process process = mock(Process.class);
                         when(process.getInputStream()).thenReturn(new ByteArrayInputStream(
                                 "New Artist - New Title\tUploader\tvideo-id\n".getBytes()));
                         when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
                         when(process.waitFor()).thenReturn(0);
                         when(builder.redirectErrorStream(false)).thenReturn(builder);
                         when(builder.start()).thenReturn(process);
                     })) {
            audioFiles.when(() -> AudioFileIO.read(any(File.class))).thenAnswer(invocation ->
                    invocation.<File>getArgument(0).equals(first.toFile()) ? firstAudio : secondAudio);

            Map<String, Object> result = musicService.fillYoutubeMetadataBulk(1L, "", 10, true);

            assertEquals(2, result.get("processed"));
            assertEquals(2, result.get("updated"));
        }
    }

    @Test
    void interruptedDjCachePreparationPreservesInterrupt() throws Exception {
        ReflectionTestUtils.setField(musicService, "ytDlpPath", "yt-dlp-test");
        Path cacheDir = Path.of("./downloads/dj_cache").toAbsolutePath().normalize();
        boolean cacheDirectoryExisted = Files.exists(cacheDir);
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor()).thenThrow(new InterruptedException("stop"));

        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                ProcessBuilder.class,
                (builder, context) -> {
                    when(builder.redirectErrorStream(true)).thenReturn(builder);
                    when(builder.start()).thenReturn(process);
                })) {
            assertThrows(RuntimeException.class, () -> musicService.prepareYoutubeTrack("interrupt-test-video"));
            assertTrue(Thread.currentThread().isInterrupted());
            Thread.interrupted();
        } finally {
            if (!cacheDirectoryExisted && Files.isDirectory(cacheDir)) {
                try (var entries = Files.list(cacheDir)) {
                    if (entries.findAny().isEmpty()) Files.deleteIfExists(cacheDir);
                }
            }
        }
    }

    @Test
    void folderCoverRejectsMissingDirectoryAndRegularFile() throws Exception {
        Path missing = tempDir.resolve("missing-folder");
        Path regularFile = Files.writeString(tempDir.resolve("not-a-folder"), "data");
        when(nasService.resolveValidatedPath(1L, "missing-folder")).thenReturn(missing);
        when(nasService.resolveValidatedPath(1L, "not-a-folder")).thenReturn(regularFile);

        assertArrayEquals(new byte[0], musicService.getFolderCoverArt(1L, "missing-folder"));
        assertArrayEquals(new byte[0], musicService.getFolderCoverArt(1L, "not-a-folder"));
    }

    @Test
    void bpmAndDirectoryKeyHelpersCoverBlankAndNullValues() {
        assertEquals(0, (int) ReflectionTestUtils.invokeMethod(musicService, "parseBpm", (Object) null));
        assertEquals(0, (int) ReflectionTestUtils.invokeMethod(musicService, "parseBpm", " "));
        String nullSubPath = ReflectionTestUtils.invokeMethod(
                musicService, "directoryListingKey", 1L, null, tempDir.toFile());

        assertTrue(nullSubPath.contains("1"));
    }

    @Test
    void cachedArtistLookupDropsKeysThatNormalizeToBlank() {
        assertEquals(List.of(), musicService.getCachedTracksByArtist(1L, "!!!", null, 10));
    }

    private AudioFile metadataAudioFile(String title, String artist, String album) {
        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);
        Tag tag = mock(Tag.class);
        when(audioFile.getTagOrCreateDefault()).thenReturn(tag);
        when(audioFile.getTag()).thenReturn(tag);
        when(audioFile.getExt()).thenReturn("mp3");
        when(audioFile.getAudioHeader()).thenReturn(header);
        when(header.getTrackLength()).thenReturn(180);
        when(tag.getFirst(FieldKey.TITLE)).thenReturn(title);
        when(tag.getFirst(FieldKey.ARTIST)).thenReturn(artist);
        when(tag.getFirst(FieldKey.ALBUM)).thenReturn(album);
        when(tag.getFirst(FieldKey.YEAR)).thenReturn("2026");
        when(tag.getFirst(FieldKey.BPM)).thenReturn("120");
        return audioFile;
    }

    private TrackMetadataCache cachedTrack(String path, String title, String artist) {
        return TrackMetadataCache.builder()
                .nasPathId(1L)
                .relativePath(path)
                .title(title)
                .artist(artist)
                .album("")
                .format("mp3")
                .year("")
                .duration(120)
                .lastModified(1L)
                .build();
    }

    @Test
    void streamAudioToResponse_alreadyOpus_skipsTranscodeAndServesOriginal() throws Exception {
        byte[] audioBytes = "opus-bytes".getBytes();
        stubResolvedFile("track.opus", audioBytes);
        MockHttpServletResponse response = new MockHttpServletResponse();

        musicService.streamAudioToResponse(1L, "track.opus", null, "normal", response);

        assertEquals(200, response.getStatus());
        assertArrayEquals(audioBytes, response.getContentAsByteArray());
    }

    @Test
    void streamAudioToResponse_highQualityOnNonLossless_skipsTranscodeAndServesOriginal() throws Exception {
        byte[] audioBytes = "mp3-bytes-not-lossless".getBytes();
        stubResolvedFile("track.mp3", audioBytes);
        MockHttpServletResponse response = new MockHttpServletResponse();

        musicService.streamAudioToResponse(1L, "track.mp3", null, "high", response);

        assertEquals(200, response.getStatus());
        assertArrayEquals(audioBytes, response.getContentAsByteArray());
    }

    @Test
    void streamAudioToResponse_supportsRangeRequests() throws Exception {
        byte[] audioBytes = "0123456789".getBytes();
        stubResolvedFile("ranged.mp3", audioBytes);
        MockHttpServletResponse response = new MockHttpServletResponse();

        musicService.streamAudioToResponse(1L, "ranged.mp3", "bytes=2-5", "original", response);

        assertEquals(206, response.getStatus());
        assertEquals("bytes 2-5/10", response.getHeader("Content-Range"));
        assertArrayEquals("2345".getBytes(), response.getContentAsByteArray());
    }

    @Test
    void streamAudioToResponse_missingFile_throwsIllegalArgumentException() {
        File missing = tempDir.resolve("missing.mp3").toFile();
        when(nasService.resolveValidatedPath(eq(1L), eq("missing.mp3"))).thenReturn(missing.toPath());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(IllegalArgumentException.class, () ->
                musicService.streamAudioToResponse(1L, "missing.mp3", null, "original", response));
    }
}
