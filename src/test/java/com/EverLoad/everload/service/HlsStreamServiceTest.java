package com.EverLoad.everload.service;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cubre la lógica de elegibilidad HLS (solo audios largos/grandes) y las
 * barreras de la fase de servido, sin lanzar ffmpeg (los archivos de prueba
 * nunca superan los umbrales).
 */
class HlsStreamServiceTest {

    @TempDir
    Path tempDir;

    private NasService nasService;
    private HlsStreamService hlsService;

    @BeforeEach
    void setUp() {
        nasService = mock(NasService.class);
        hlsService = new HlsStreamService(nasService);
        ReflectionTestUtils.setField(hlsService, "hlsCacheDir", tempDir.resolve("hls-cache").toString());
        ReflectionTestUtils.setField(hlsService, "hlsMinDurationSeconds", 1200);
        ReflectionTestUtils.setField(hlsService, "hlsMinSizeBytes", 80L * 1024 * 1024);
    }

    private void stubTrack(String name, byte[] content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.write(file, content);
        when(nasService.resolveValidatedPath(eq(1L), eq(name))).thenReturn(file);
    }

    @Test
    void audioCortoYPequenoNoEsElegible_seSirveDirecto() throws Exception {
        stubTrack("corta.mp3", "audio pequeño".getBytes());

        Map<String, Object> result = hlsService.prepareHlsStream(1L, "corta.mp3");

        assertEquals(false, result.get("eligible"));
        assertEquals("DIRECT", result.get("status"));
        assertEquals(false, result.get("ready"));
        assertFalse(Files.exists(tempDir.resolve("hls-cache")),
                "no debe crearse caché ni lanzarse ffmpeg para audios directos");
    }

    @Test
    void statusDeAudioNoElegibleEsEstable() throws Exception {
        stubTrack("corta.mp3", "audio".getBytes());

        assertEquals("DIRECT", hlsService.getHlsStatus(1L, "corta.mp3").get("status"));
        assertEquals("DIRECT", hlsService.getHlsStatus(1L, "corta.mp3").get("status"));
    }

    @Test
    void playlistYSegmentosExigenCachePreparada() throws Exception {
        stubTrack("corta.mp3", "audio".getBytes());
        HttpServletResponse response = new MockHttpServletResponse();

        assertThrows(IllegalStateException.class,
                () -> hlsService.getHlsPlaylist(1L, "corta.mp3", null));
        assertThrows(IllegalStateException.class,
                () -> hlsService.streamHlsSegmentToResponse(1L, "corta.mp3", "seg_00001.ts", response));
    }

    @Test
    void archivoInaccesibleRechazado() {
        when(nasService.resolveValidatedPath(eq(1L), eq("fantasma.mp3")))
                .thenReturn(tempDir.resolve("fantasma.mp3"));

        assertThrows(IllegalArgumentException.class,
                () -> hlsService.prepareHlsStream(1L, "fantasma.mp3"));
    }

    @Test
    void traversalDelNasSePropagaComoSecurityException() {
        when(nasService.resolveValidatedPath(eq(1L), eq("../etc/passwd")))
                .thenThrow(new SecurityException("Acceso denegado"));

        assertThrows(SecurityException.class,
                () -> hlsService.prepareHlsStream(1L, "../etc/passwd"));
    }
}
