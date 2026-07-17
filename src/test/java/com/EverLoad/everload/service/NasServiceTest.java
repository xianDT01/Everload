package com.EverLoad.everload.service;

import com.EverLoad.everload.model.NasPath;
import com.EverLoad.everload.repository.FavoriteTrackRepository;
import com.EverLoad.everload.repository.NasPathRepository;
import com.EverLoad.everload.repository.PlaybackHistoryRepository;
import com.EverLoad.everload.repository.TrackMetadataCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Cubre las garantías de seguridad del explorador NAS (path traversal) y la
 * consistencia con la BD (cascadas al renombrar/eliminar), que son los flujos
 * con más riesgo de regresión al refactorizar.
 */
class NasServiceTest {

    @TempDir
    Path nasRoot;

    private NasPathRepository nasPathRepository;
    private FavoriteTrackRepository favoriteRepo;
    private PlaybackHistoryRepository historyRepo;
    private TrackMetadataCacheRepository cacheRepo;
    private NasService nasService;

    @BeforeEach
    void setUp() {
        nasPathRepository = mock(NasPathRepository.class);
        favoriteRepo = mock(FavoriteTrackRepository.class);
        historyRepo = mock(PlaybackHistoryRepository.class);
        cacheRepo = mock(TrackMetadataCacheRepository.class);
        nasService = new NasService(nasPathRepository, favoriteRepo, historyRepo, cacheRepo);

        NasPath path = NasPath.builder().id(1L).name("Música").path(nasRoot.toString()).build();
        when(nasPathRepository.findById(1L)).thenReturn(Optional.of(path));
    }

    // ── Path traversal ────────────────────────────────────────────────────────

    @Test
    void listFiles_rechazaEscaparDeLaRaiz() {
        assertThrows(SecurityException.class, () -> nasService.listFiles(1L, "../fuera"));
    }

    @Test
    void resolveValidatedPath_rechazaTraversalYAceptaSubrutasNormales() throws Exception {
        Files.createDirectories(nasRoot.resolve("album"));

        assertThrows(SecurityException.class,
                () -> nasService.resolveValidatedPath(1L, "album/../../secreto"));
        assertEquals(nasRoot.resolve("album"), nasService.resolveValidatedPath(1L, "album"));
    }

    @Test
    void saveToNas_rechazaSubPathConTraversal() throws Exception {
        Path tmp = Files.writeString(nasRoot.resolve("tmp-src.mp3"), "audio");

        assertThrows(SecurityException.class,
                () -> nasService.saveToNas(1L, "../../fuera", tmp, "cancion.mp3"));
    }

    @Test
    void saveToNas_guardaYSanitizaElNombre() throws Exception {
        Path tmp = Files.writeString(nasRoot.resolve("tmp-src.mp3"), "audio");

        String saved = nasService.saveToNas(1L, "", tmp, "mi:can*cion?.mp3");

        Path dest = Path.of(saved);
        assertTrue(Files.exists(dest));
        assertTrue(dest.startsWith(nasRoot));
        assertFalse(dest.getFileName().toString().matches(".*[:*?].*"),
                "el nombre guardado no debe contener caracteres peligrosos");
    }

    // ── Borrado con cascada en BD ─────────────────────────────────────────────

    @Test
    void deleteFileOrFolder_borraDelDiscoYLimpiaFavoritosHistorialYCache() throws Exception {
        Path album = Files.createDirectories(nasRoot.resolve("album"));
        Files.writeString(album.resolve("pista.mp3"), "audio");

        nasService.deleteFileOrFolder(1L, "album");

        assertFalse(Files.exists(album), "la carpeta debe desaparecer del disco");
        verify(favoriteRepo).deleteByPathPrefix(1L, "album", "album/%");
        verify(historyRepo).deleteByPathPrefix(1L, "album", "album/%");
        verify(cacheRepo).deleteByPathPrefix(1L, "album", "album/%");
    }

    @Test
    void deleteFileOrFolder_nuncaBorraLaRaiz() {
        assertThrows(SecurityException.class, () -> nasService.deleteFileOrFolder(1L, ""));
        verify(favoriteRepo, never()).deleteByPathPrefix(anyLong(), anyString(), anyString());
    }

    // ── Renombrado con cascada ────────────────────────────────────────────────

    @Test
    void renameFileOrFolder_preservaExtensionYActualizaBd() throws Exception {
        Path album = Files.createDirectories(nasRoot.resolve("album"));
        Files.writeString(album.resolve("vieja.mp3"), "audio");

        String nuevo = nasService.renameFileOrFolder(1L, "album/vieja.mp3", "nueva");

        assertEquals("album/nueva.mp3", nuevo);
        assertTrue(Files.exists(album.resolve("nueva.mp3")));
        verify(favoriteRepo).renamePathPrefix(eq(1L), eq("album/vieja.mp3"),
                eq("album/vieja.mp3/%"), eq("album/vieja.mp3".length()), eq("album/nueva.mp3"));
        verify(historyRepo).renamePathPrefix(anyLong(), anyString(), anyString(), anyInt(), anyString());
        verify(cacheRepo).renamePathPrefix(anyLong(), anyString(), anyString(), anyInt(), anyString());
    }

    // ── Subidas ───────────────────────────────────────────────────────────────

    @Test
    void uploadMusicFiles_rechazaExtensionesNoDeAudio() {
        MockMultipartFile exe = new MockMultipartFile("file", "virus.exe",
                "application/octet-stream", new byte[]{1, 2, 3});

        List<Map<String, Object>> results = nasService.uploadMusicFiles(1L, "", List.of(exe), null);

        assertEquals(1, results.size());
        assertEquals("error", results.get(0).get("status"));
        assertFalse(Files.exists(nasRoot.resolve("virus.exe")));
    }

    @Test
    void uploadMusicFiles_guardaAudioPermitido() {
        MockMultipartFile mp3 = new MockMultipartFile("file", "tema.mp3",
                "audio/mpeg", "audio".getBytes());

        List<Map<String, Object>> results = nasService.uploadMusicFiles(1L, "", List.of(mp3), null);

        assertEquals("ok", results.get(0).get("status"));
        assertTrue(Files.exists(nasRoot.resolve("tema.mp3")));
    }

    @Test
    void pathAndFileListingsMapRepositoryAndFilesystemEntries() throws Exception {
        NasPath path = NasPath.builder().id(1L).name("Music").path(nasRoot.toString()).build();
        when(nasPathRepository.findAll()).thenReturn(List.of(path));
        Files.createDirectory(nasRoot.resolve("Album"));
        Files.writeString(nasRoot.resolve("track.mp3"), "audio");

        assertEquals(1, nasService.getAllPaths().size());
        var files = nasService.listFiles(1L, "");

        assertEquals(2, files.size());
        assertTrue(files.get(0).isDirectory());
        assertEquals("track.mp3", files.get(1).getName());
    }

    @Test
    void mutatingOperationsRejectUnknownNasPath() {
        when(nasPathRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> nasService.createFolder(99L, "", "folder"));
        assertThrows(IllegalArgumentException.class, () -> nasService.moveFileOrFolder(99L, "a", "b"));
        assertThrows(IllegalArgumentException.class,
                () -> nasService.saveFolderCover(99L, "", new byte[]{1}, "image/jpeg"));
    }
}
