package com.everload.everload.service;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import com.everload.everload.model.Download;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

/**
 * Cubre las validaciones de entrada de DownloadService: cada dato que llega a
 * yt-dlp pasa antes por aquí, así que estos rechazos son la barrera contra
 * IDs manipulados, formatos raros y URLs de dominios no permitidos (SSRF).
 *
 * Solo se prueban los caminos de rechazo — los válidos lanzarían yt-dlp real.
 */
class DownloadServiceTest {

    private DownloadService downloadService;
    private MusicService musicService;
    private DownloadHistoryService historyService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        musicService = mock(MusicService.class);
        historyService = mock(DownloadHistoryService.class);
        downloadService = new DownloadService(
                historyService, mock(NasService.class), musicService);
        // Campo @Value — sin contexto Spring queda a 0 y el pool no puede crearse
        org.springframework.test.util.ReflectionTestUtils.setField(downloadService, "maxConcurrent", 2);
        downloadService.init();
    }

    @AfterEach
    void tearDown() {
        downloadService.shutdown();
    }

    // ── Validación de videoId ─────────────────────────────────────────────────

    @Test
    void downloadVideo_rechazaVideoIdInvalido() {
        assertEquals(HttpStatus.BAD_REQUEST,
                downloadService.downloadVideo("id-corto", "720").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                downloadService.downloadVideo("--flag-inyectada", "720").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                downloadService.downloadVideo(null, "720").getStatusCode());
    }

    @Test
    void downloadVideo_rechazaResolucionFueraDeLista() {
        assertEquals(HttpStatus.BAD_REQUEST,
                downloadService.downloadVideo("dQw4w9WgXcQ", "999").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                downloadService.downloadVideo("dQw4w9WgXcQ", "720; rm -rf /").getStatusCode());
    }

    @Test
    void downloadMusic_rechazaFormatoNoPermitido() {
        assertEquals(HttpStatus.BAD_REQUEST,
                downloadService.downloadMusic("dQw4w9WgXcQ", "exe").getStatusCode());
    }

    @Test
    void queueMusicDownload_rechazaIdYFormatoInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> downloadService.queueMusicDownload("abc", "mp3"));
        assertThrows(IllegalArgumentException.class,
                () -> downloadService.queueMusicDownload("dQw4w9WgXcQ", "sh"));
    }

    @Test
    void queueNasSave_exigeRutaDeNas() {
        assertThrows(IllegalArgumentException.class,
                () -> downloadService.queueNasSave("dQw4w9WgXcQ", "mp3", null, ""));
    }

    // ── Validación de dominio en URLs (anti-SSRF) ─────────────────────────────

    @Test
    void urlConDominioPermitidoEnLaQueryNoEngañaAlFiltro() {
        // Antes se validaba con url.contains("twitter.com") y esto pasaba
        assertEquals(HttpStatus.BAD_REQUEST,
                downloadService.downloadTwitterVideo("https://evil.com/?x=twitter.com").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                downloadService.downloadTwitterVideo("https://twitter.com.evil.com/status/1").getStatusCode());
    }

    @Test
    void urlConEsquemaNoHttpRechazada() {
        assertEquals(HttpStatus.BAD_REQUEST,
                downloadService.downloadTikTokVideo("ftp://tiktok.com/video").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                downloadService.downloadInstagramVideo("javascript:alert(1)//instagram.com").getStatusCode());
    }

    @Test
    void urlVaciaONulaRechazada() {
        assertEquals(HttpStatus.BAD_REQUEST, downloadService.downloadFacebookVideo("").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, downloadService.downloadFacebookVideo(null).getStatusCode());
    }

    @Test
    void playlistDeDominioAjenoRechazada() {
        assertEquals(HttpStatus.BAD_REQUEST,
                downloadService.getPlaylistVideos("https://evil.com/playlist?list=youtube.com").getStatusCode());
    }

    @Test
    void runYtDlpConsumesEveryPathFromMultiMediaPosts() throws Exception {
        File first = Files.writeString(tempDir.resolve("first.mp4"), "first").toFile();
        File second = Files.writeString(tempDir.resolve("second.mp4"), "second").toFile();
        Process process = mock(Process.class);
        String output = first.getAbsolutePath() + "\n" + second.getAbsolutePath() + "\n";
        when(process.getInputStream()).thenReturn(
                new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor()).thenReturn(0);
        IntConsumer progress = mock(IntConsumer.class);

        File result;
        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                ProcessBuilder.class, (builder, context) -> when(builder.start()).thenReturn(process))) {
            result = ReflectionTestUtils.invokeMethod(
                    downloadService, "runYtDlp", (Object) new String[]{"yt-dlp-test"}, progress);
        }

        assertEquals(first, result);
        verify(musicService).ensureMetadata(first, "first.mp4", "");
    }

    @Test
    void runYtDlpRejectsFailedProcessAndMissingOutputFile() throws Exception {
        Process failed = mock(Process.class);
        when(failed.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(failed.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(failed.waitFor()).thenReturn(2);
        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                ProcessBuilder.class, (builder, context) -> when(builder.start()).thenReturn(failed))) {
            var thrown = assertThrows(java.lang.reflect.UndeclaredThrowableException.class,
                    () -> ReflectionTestUtils.invokeMethod(downloadService, "runYtDlp",
                            (Object) new String[]{"yt-dlp-test"}, (IntConsumer) p -> {}));
            assertInstanceOf(java.io.IOException.class, thrown.getUndeclaredThrowable());
        }

        Process missingOutput = mock(Process.class);
        when(missingOutput.getInputStream()).thenReturn(new ByteArrayInputStream(
                tempDir.resolve("missing.mp3").toString().getBytes(StandardCharsets.UTF_8)));
        when(missingOutput.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(missingOutput.waitFor()).thenReturn(0);
        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                ProcessBuilder.class, (builder, context) -> when(builder.start()).thenReturn(missingOutput))) {
            var thrown = assertThrows(java.lang.reflect.UndeclaredThrowableException.class,
                    () -> ReflectionTestUtils.invokeMethod(downloadService, "runYtDlp",
                            (Object) new String[]{"yt-dlp-test"}, (IntConsumer) p -> {}));
            assertInstanceOf(java.io.IOException.class, thrown.getUndeclaredThrowable());
        }
    }

    @Test
    void validSocialMediaUrlReachesDownloaderAndReportsProcessFailure() throws Exception {
        Process failed = mock(Process.class);
        when(failed.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(failed.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(failed.waitFor()).thenReturn(1);

        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                ProcessBuilder.class, (builder, context) -> when(builder.start()).thenReturn(failed))) {
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                    downloadService.downloadTwitterVideo("https://x.com/user/status/123").getStatusCode());
        }

        verify(historyService, never()).recordDownload(org.mockito.ArgumentMatchers.any(Download.class));
    }

    @Test
    void deviceAudioRefreshesRejectedMediaLinkAndRecordsSuccessOnce() throws Exception {
        File file = Files.writeString(tempDir.resolve("audio.mp3"), "audio").toFile();
        var job = new DownloadService.DirectDownloadJob("refresh-job", "jNQXAC9IVRw", "mp3", "test-user");
        ReflectionTestUtils.setField(downloadService, "ytDlpPath", "yt-dlp-test");
        Process failed = downloadProcess("", "ERROR: unable to download video data: HTTP Error 403: Forbidden", 0);
        Process success = downloadProcess(file.getAbsolutePath(), "", 0);

        try (MockedConstruction<ProcessBuilder> builders = mockConstruction(ProcessBuilder.class,
                (builder, context) -> when(builder.start()).thenReturn(context.getCount() == 1 ? failed : success))) {
            ReflectionTestUtils.invokeMethod(downloadService, "executeQueuedMusicDownload", job);
            assertEquals(2, builders.constructed().size());
        }

        assertEquals(DownloadService.DirectDownloadStatus.DONE, job.status);
        assertEquals(file.getAbsolutePath(), job.filePath);
        verify(historyService).recordDownload(org.mockito.ArgumentMatchers.any(Download.class));
        verify(musicService).ensureMetadata(file, "audio.mp3", "");
        assertEquals(2, ((java.util.concurrent.Semaphore)
                ReflectionTestUtils.getField(downloadService, "downloadSemaphore")).availablePermits());
    }

    @Test
    void deviceAudioStopsAfterOneRefreshAndDoesNotExposeRawError() throws Exception {
        var job = new DownloadService.DirectDownloadJob("failed-job", "jNQXAC9IVRw", "mp3", "test-user");
        ReflectionTestUtils.setField(downloadService, "ytDlpPath", "yt-dlp-test");
        String error = "ERROR: unable to download video data: HTTP Error 403: Forbidden https://secret.invalid/token";
        Process first = downloadProcess("", error, 1);
        Process second = downloadProcess("", error, 1);
        try (MockedConstruction<ProcessBuilder> builders = mockConstruction(ProcessBuilder.class,
                (builder, context) -> when(builder.start()).thenReturn(context.getCount() == 1 ? first : second))) {
            ReflectionTestUtils.invokeMethod(downloadService, "executeQueuedMusicDownload", job);
            assertEquals(2, builders.constructed().size());
        }
        assertEquals(DownloadService.DirectDownloadStatus.ERROR, job.status);
        assertTrue(job.error.contains("HTTP 403"));
        assertFalse(job.error.contains("secret.invalid"));
        verify(historyService, never()).recordDownload(org.mockito.ArgumentMatchers.any(Download.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ERROR: Private video", "ERROR: Video unavailable",
            "ERROR: Requested format is not available", "ERROR: HTTP Error 403: Forbidden", ""})
    void deviceAudioDoesNotRetryOtherFailures(String error) throws Exception {
        var job = new DownloadService.DirectDownloadJob("permanent-job", "jNQXAC9IVRw", "mp3", "test-user");
        ReflectionTestUtils.setField(downloadService, "ytDlpPath", "yt-dlp-test");
        Process failed = downloadProcess("", error, 1);
        try (MockedConstruction<ProcessBuilder> builders = mockConstruction(ProcessBuilder.class,
                (builder, context) -> when(builder.start()).thenReturn(failed))) {
            ReflectionTestUtils.invokeMethod(downloadService, "executeQueuedMusicDownload", job);
            assertEquals(1, builders.constructed().size());
        }
        assertEquals(DownloadService.DirectDownloadStatus.ERROR, job.status);
        verify(historyService, never()).recordDownload(org.mockito.ArgumentMatchers.any(Download.class));
    }

    private Process downloadProcess(String output, String error, int exitCode) throws Exception {
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(error.getBytes(StandardCharsets.UTF_8)));
        when(process.waitFor()).thenReturn(exitCode);
        return process;
    }

    // ── Estado de jobs ────────────────────────────────────────────────────────

    @Test
    void preparedFileRemainsAvailableForRepeatedDeliveryAndRetry() throws Exception {
        File file = Files.writeString(tempDir.resolve("100% Music.mp3"), "audio").toFile();
        var job = new DownloadService.DirectDownloadJob("retry-job", "jNQXAC9IVRw", "mp3", "test-user");
        job.status = DownloadService.DirectDownloadStatus.DONE;
        job.completedAt = System.currentTimeMillis();
        job.filePath = file.getAbsolutePath();
        job.filename = file.getName();
        @SuppressWarnings("unchecked")
        var jobs = (java.util.Map<String, DownloadService.DirectDownloadJob>)
                ReflectionTestUtils.getField(downloadService, "directDownloadJobs");
        jobs.put(job.jobId, job);

        var first = downloadService.downloadQueuedFile(job.jobId);
        var retry = downloadService.downloadQueuedFile(job.jobId);

        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(HttpStatus.OK, retry.getStatusCode());
        assertEquals(file, retry.getBody().getFile());
        assertSame(job, downloadService.getDirectDownloadJob(job.jobId));
        assertTrue(file.exists());
        verify(historyService, never()).recordDownload(org.mockito.ArgumentMatchers.any(Download.class));
    }

    @Test
    void retentionStillPrunesExpiredPreparedFiles() throws Exception {
        Path folder = Files.createDirectory(tempDir.resolve("expired"));
        File file = Files.writeString(folder.resolve("expired.mp3"), "audio").toFile();
        var job = new DownloadService.DirectDownloadJob("expired-job", "jNQXAC9IVRw", "mp3", "test-user");
        job.status = DownloadService.DirectDownloadStatus.DONE;
        job.completedAt = System.currentTimeMillis() - java.util.concurrent.TimeUnit.HOURS.toMillis(2);
        job.filePath = file.getAbsolutePath();
        @SuppressWarnings("unchecked")
        var jobs = (java.util.Map<String, DownloadService.DirectDownloadJob>)
                ReflectionTestUtils.getField(downloadService, "directDownloadJobs");
        jobs.put(job.jobId, job);

        ReflectionTestUtils.invokeMethod(downloadService, "pruneOldJobs");

        assertNull(downloadService.getDirectDownloadJob(job.jobId));
        assertFalse(file.exists());
    }

    @Test
    void jobInexistenteDevuelveNullY404() {
        assertNull(downloadService.getDirectDownloadJob("no-existe"));
        assertEquals(HttpStatus.NOT_FOUND,
                downloadService.downloadQueuedFile("no-existe").getStatusCode());
    }
}
