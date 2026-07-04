package com.EverLoad.everload.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Cubre las validaciones de entrada de DownloadService: todo lo que llega a
 * yt-dlp pasa antes por aquí, así que estos rechazos son la barrera contra
 * IDs manipulados, formatos raros y URLs de dominios no permitidos (SSRF).
 *
 * Solo se prueban los caminos de rechazo — los válidos lanzarían yt-dlp real.
 */
class DownloadServiceTest {

    private DownloadService downloadService;

    @BeforeEach
    void setUp() {
        downloadService = new DownloadService(
                mock(DownloadHistoryService.class), mock(NasService.class), mock(MusicService.class));
        // Campo @Value — sin contexto Spring queda a 0 y el pool no puede crearse
        org.springframework.test.util.ReflectionTestUtils.setField(downloadService, "maxConcurrent", 2);
        downloadService.init();
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

    // ── Estado de jobs ────────────────────────────────────────────────────────

    @Test
    void jobInexistenteDevuelveNullY404() {
        assertNull(downloadService.getDirectDownloadJob("no-existe"));
        assertEquals(HttpStatus.NOT_FOUND,
                downloadService.downloadQueuedFile("no-existe").getStatusCode());
    }
}
