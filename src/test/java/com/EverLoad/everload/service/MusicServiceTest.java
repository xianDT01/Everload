package com.EverLoad.everload.service;

import com.EverLoad.everload.repository.NasPathRepository;
import com.EverLoad.everload.repository.TrackMetadataCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

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
    private MusicService musicService;

    @BeforeEach
    void setUp() {
        nasService = mock(NasService.class);
        musicService = new MusicService(nasService, mock(NasPathRepository.class), mock(TrackMetadataCacheRepository.class),
                new org.springframework.web.client.RestTemplate());
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

        assertNull(musicService.getCoverArt(1L, "track.mp3"));
    }

    @Test
    void getCoverArt_returnsNullForNonAudioFile() throws Exception {
        stubResolvedFile("readme.txt", "hello".getBytes());

        assertNull(musicService.getCoverArt(1L, "readme.txt"));
        // Must short-circuit before ever resolving a directory cover fallback.
        verify(nasService, atMostOnce()).resolveValidatedPath(eq(1L), eq("readme.txt"));
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
