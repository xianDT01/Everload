package com.EverLoad.everload.service;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
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

    @Test
    void initialHlsStatusDistinguishesDirectIdleAndReady() throws Exception {
        Path cacheDir = tempDir.resolve("cache-key");

        assertEquals("DIRECT", ReflectionTestUtils.invokeMethod(hlsService, "initialHlsStatus", false, cacheDir));
        assertEquals("IDLE", ReflectionTestUtils.invokeMethod(hlsService, "initialHlsStatus", true, cacheDir));

        Files.createDirectories(cacheDir);
        Files.writeString(cacheDir.resolve("index.m3u8"), "#EXTM3U");
        assertEquals("READY", ReflectionTestUtils.invokeMethod(hlsService, "initialHlsStatus", true, cacheDir));
    }

    @Test
    void readyCacheRewritesPlaylistAndStreamsSegment() throws Exception {
        byte[] audio = "long-audio".getBytes();
        stubTrack("mix.mp3", audio);
        ReflectionTestUtils.setField(hlsService, "hlsMinSizeBytes", 0L);
        Path source = tempDir.resolve("mix.mp3");
        String key = ReflectionTestUtils.invokeMethod(
                hlsService, "hlsCacheKey", 1L, "mix.mp3", source.toFile());
        Path cacheDir = tempDir.resolve("hls-cache").resolve(key);
        Files.createDirectories(cacheDir);
        Files.writeString(cacheDir.resolve("index.m3u8"), "#EXTM3U\nseg_00001.ts\n");
        byte[] segment = "segment-data".getBytes();
        Files.write(cacheDir.resolve("seg_00001.ts"), segment);

        Map<String, Object> prepared = hlsService.prepareHlsStream(1L, "mix.mp3");
        Map<String, Object> status = hlsService.getHlsStatus(1L, "mix.mp3");
        String playlist = hlsService.getHlsPlaylist(1L, "mix.mp3", "token value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        hlsService.streamHlsSegmentToResponse(1L, "mix.mp3", "seg_00001.ts", response);

        assertEquals("READY", prepared.get("status"));
        assertEquals(100, prepared.get("progress"));
        assertEquals("READY", status.get("status"));
        assertTrue(playlist.contains("segment=seg_00001.ts"));
        assertTrue(playlist.contains("token=token+value"));
        assertArrayEquals(segment, response.getContentAsByteArray());
    }

    @Test
    void failedFfmpegStartMarksJobFailedAndCleansTemporaryDirectory() throws Exception {
        stubTrack("eligible.mp3", "audio".getBytes());
        ReflectionTestUtils.setField(hlsService, "hlsMinSizeBytes", 0L);
        ReflectionTestUtils.setField(hlsService, "ffmpegPath", "definitely-missing-everload-ffmpeg");
        ExecutorService executor = mock(ExecutorService.class);
        Future<?> future = mock(Future.class);
        doReturn(future).when(executor).submit(any(Runnable.class));
        ReflectionTestUtils.setField(hlsService, "hlsExecutor", executor);

        Map<String, Object> started = hlsService.prepareHlsStream(1L, "eligible.mp3");
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).submit(task.capture());
        task.getValue().run();
        Map<String, Object> failed = hlsService.getHlsStatus(1L, "eligible.mp3");

        assertEquals("RUNNING", started.get("status"));
        assertEquals("FAILED", failed.get("status"));
        assertEquals(0, failed.get("progress"));
        assertTrue(failed.containsKey("error"));
        try (var paths = Files.list(tempDir.resolve("hls-cache"))) {
            assertEquals(0, paths.count());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulFfmpegProcessPublishesReadyCache() throws Exception {
        stubTrack("successful.mp3", "audio".getBytes());
        ReflectionTestUtils.setField(hlsService, "hlsMinSizeBytes", 0L);
        ExecutorService executor = mock(ExecutorService.class);
        Future<?> future = mock(Future.class);
        doReturn(future).when(executor).submit(any(Runnable.class));
        ReflectionTestUtils.setField(hlsService, "hlsExecutor", executor);
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream("time=00:00:10.00\n".getBytes()));
        when(process.waitFor()).thenReturn(0);

        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                ProcessBuilder.class,
                (builder, context) -> {
                    List<String> command = (List<String>) context.arguments().get(0);
                    Path playlist = Path.of(command.get(command.size() - 1));
                    when(builder.redirectErrorStream(true)).thenReturn(builder);
                    when(builder.start()).thenAnswer(invocation -> {
                        Files.writeString(playlist, "#EXTM3U\nseg_00001.ts\n");
                        Files.writeString(playlist.getParent().resolve("seg_00001.ts"), "segment");
                        return process;
                    });
                })) {
            hlsService.prepareHlsStream(1L, "successful.mp3");
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).submit(task.capture());
            task.getValue().run();
        }

        Map<String, Object> status = hlsService.getHlsStatus(1L, "successful.mp3");
        assertEquals("READY", status.get("status"));
        assertEquals(100, status.get("progress"));
    }

    @Test
    void interruptedFfmpegProcessFailsJobAndDestroysProcess() throws Exception {
        stubTrack("interrupted.mp3", "audio".getBytes());
        ReflectionTestUtils.setField(hlsService, "hlsMinSizeBytes", 0L);
        ExecutorService executor = mock(ExecutorService.class);
        Future<?> future = mock(Future.class);
        doReturn(future).when(executor).submit(any(Runnable.class));
        ReflectionTestUtils.setField(hlsService, "hlsExecutor", executor);
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor()).thenThrow(new InterruptedException("stop"));
        when(process.destroyForcibly()).thenReturn(process);

        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                ProcessBuilder.class,
                (builder, context) -> {
                    when(builder.redirectErrorStream(true)).thenReturn(builder);
                    when(builder.start()).thenReturn(process);
                })) {
            hlsService.prepareHlsStream(1L, "interrupted.mp3");
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).submit(task.capture());
            task.getValue().run();
        }

        Map<String, Object> status = hlsService.getHlsStatus(1L, "interrupted.mp3");
        assertEquals("FAILED", status.get("status"));
        assertEquals("Preparacion HLS interrumpida", status.get("error"));
        assertTrue(Thread.currentThread().isInterrupted());
        verify(process).destroyForcibly();
        Thread.interrupted();
    }

    @Test
    void deleteDirectoryRemovesNestedFilesAndRoot() throws Exception {
        Path nested = Files.createDirectories(tempDir.resolve("delete-me/child"));
        Files.writeString(nested.resolve("segment.ts"), "segment");

        ReflectionTestUtils.invokeMethod(hlsService, "deleteDirectory", tempDir.resolve("delete-me"));

        assertFalse(Files.exists(tempDir.resolve("delete-me")));
    }
}
