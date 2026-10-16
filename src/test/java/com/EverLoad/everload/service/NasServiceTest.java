package com.everload.everload.service;

import com.everload.everload.model.NasPath;
import com.everload.everload.dto.NasPathDto;
import com.everload.everload.event.NasFilesChangedEvent;
import com.everload.everload.repository.FavoriteTrackRepository;
import com.everload.everload.repository.NasPathRepository;
import com.everload.everload.repository.PlaybackHistoryRepository;
import com.everload.everload.repository.PlaylistTrackRepository;
import com.everload.everload.repository.TrackMetadataCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;

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
    private PlaylistTrackRepository playlistTrackRepo;
    private ApplicationEventPublisher eventPublisher;
    private NasService nasService;

    @BeforeEach
    void setUp() {
        nasPathRepository = mock(NasPathRepository.class);
        favoriteRepo = mock(FavoriteTrackRepository.class);
        historyRepo = mock(PlaybackHistoryRepository.class);
        cacheRepo = mock(TrackMetadataCacheRepository.class);
        playlistTrackRepo = mock(PlaylistTrackRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        nasService = new NasService(nasPathRepository, favoriteRepo, historyRepo, cacheRepo, playlistTrackRepo,
                eventPublisher);

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
    void resolveValidatedPath_reparaIdAntiguoCuandoSoloHayUnaRutaLegible() throws Exception {
        Files.createDirectories(nasRoot.resolve("album"));
        NasPath currentPath = NasPath.builder().id(68L).name("Música actual").path(nasRoot.toString()).build();
        when(nasPathRepository.findById(35L)).thenReturn(Optional.empty());
        when(nasPathRepository.findAll()).thenReturn(List.of(currentPath));

        assertEquals(nasRoot.resolve("album"), nasService.resolveValidatedPath(35L, "album"));
    }

    @Test
    void resolveValidatedPath_noAdivinaRutaAntiguaSiHayVariasRutasLegibles() {
        NasPath anotherPath = NasPath.builder().id(2L).name("Otra").path(nasRoot.toString()).build();
        when(nasPathRepository.findById(35L)).thenReturn(Optional.empty());
        when(nasPathRepository.findAll()).thenReturn(List.of(
                NasPath.builder().id(1L).name("Música").path(nasRoot.toString()).build(), anotherPath));

        assertThrows(IllegalArgumentException.class, () -> nasService.resolveValidatedPath(35L, "album"));
    }

    @Test
    void trackAvailabilityResolvesNasOnceAndStillChecksDeletionAndTraversal() throws Exception {
        Path audio = Files.writeString(nasRoot.resolve("track.mp3"), "audio");
        var available = nasService.trackAvailability(1L);
        assertTrue(available.test("track.mp3"));
        assertFalse(available.test("../outside.mp3"));
        assertFalse(available.test("missing.mp3"));
        assertFalse(available.test(null));
        assertFalse(available.test(""));
        Files.delete(audio);
        assertFalse(available.test("track.mp3"));
        verify(nasPathRepository, times(1)).findById(1L);
    }

    @Test
    void isTrackAvailable_distingueArchivoExistenteDeRutaRota() throws Exception {
        Files.writeString(nasRoot.resolve("pista.mp3"), "audio");

        assertTrue(nasService.isTrackAvailable(1L, "pista.mp3"));
        assertFalse(nasService.isTrackAvailable(1L, "movida/pista.mp3"));
        assertFalse(nasService.isTrackAvailable(1L, "../fuera.mp3"));
    }

    @Test
    void isTrackAvailable_noAdivinaCuandoLaRutaAntiguaEsAmbigua() throws Exception {
        Path anotherRoot = Files.createDirectory(nasRoot.resolve("other-root"));
        Files.writeString(anotherRoot.resolve("pista.mp3"), "other audio");
        NasPath replacement = NasPath.builder().id(68L).name("Nueva ruta").path(anotherRoot.toString()).build();
        when(nasPathRepository.findById(35L)).thenReturn(Optional.empty());
        when(nasPathRepository.findAll()).thenReturn(List.of(
                NasPath.builder().id(1L).name("Original").path(nasRoot.toString()).build(), replacement));

        assertFalse(nasService.isTrackAvailable(35L, "pista.mp3"));
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
        assertTrue(dest.getFileName().toString().chars().noneMatch(ch -> ch == ':' || ch == '*' || ch == '?'),
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
        verify(favoriteRepo).renamePathPrefix(1L, "album/vieja.mp3",
                "album/vieja.mp3/%", "album/vieja.mp3".length(), "album/nueva.mp3");
        verify(historyRepo).renamePathPrefix(anyLong(), anyString(), anyString(), anyInt(), anyString());
        verify(cacheRepo).renamePathPrefix(anyLong(), anyString(), anyString(), anyInt(), anyString());
        verify(playlistTrackRepo).renamePathPrefix(1L, "album/vieja.mp3",
                "album/vieja.mp3/%", "album/vieja.mp3".length(), "album/nueva.mp3");
    }

    @Test
    void renameFileOrFolder_revierteElMovimientoSiFallaLaActualizacionDeReferencias() throws Exception {
        Files.writeString(nasRoot.resolve("original.mp3"), "audio");
        doThrow(new IllegalStateException("database unavailable"))
                .when(playlistTrackRepo).renamePathPrefix(anyLong(), anyString(), anyString(), anyInt(), anyString());

        assertThrows(IllegalStateException.class, () -> nasService.renameFileOrFolder(1L, "original.mp3", "new"));

        assertTrue(Files.exists(nasRoot.resolve("original.mp3")));
        assertFalse(Files.exists(nasRoot.resolve("new.mp3")));
    }

    @Test
    void renameFileOrFolder_conservaErrorDeRollbackComoCausaSuprimida() throws Exception {
        Files.writeString(nasRoot.resolve("original.mp3"), "audio");
        doAnswer(invocation -> {
            Files.writeString(nasRoot.resolve("original.mp3"), "collision");
            throw new IllegalStateException("database unavailable");
        }).when(playlistTrackRepo).renamePathPrefix(anyLong(), anyString(), anyString(), anyInt(), anyString());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> nasService.renameFileOrFolder(1L, "original.mp3", "new"));

        assertEquals("database unavailable", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(Files.exists(nasRoot.resolve("new.mp3")));
        assertTrue(Files.exists(nasRoot.resolve("original.mp3")));
    }

    @Test
    void moveFileOrFolder_actualizaLasReferenciasDeLaCancion() throws Exception {
        Files.createDirectories(nasRoot.resolve("archive"));
        Files.writeString(nasRoot.resolve("song.mp3"), "audio");

        nasService.moveFileOrFolder(1L, "song.mp3", "archive");

        assertTrue(Files.exists(nasRoot.resolve("archive/song.mp3")));
        verify(favoriteRepo).renamePathPrefix(1L, "song.mp3", "song.mp3/%",
                "song.mp3".length(), "archive/song.mp3");
        verify(historyRepo).renamePathPrefix(1L, "song.mp3", "song.mp3/%",
                "song.mp3".length(), "archive/song.mp3");
        verify(cacheRepo).renamePathPrefix(1L, "song.mp3", "song.mp3/%",
                "song.mp3".length(), "archive/song.mp3");
        verify(playlistTrackRepo).renamePathPrefix(1L, "song.mp3", "song.mp3/%",
                "song.mp3".length(), "archive/song.mp3");
    }

    @Test
    void moveFileOrFolder_actualizaTambienRutasDeSubcarpetas() throws Exception {
        Files.createDirectories(nasRoot.resolve("source/sub"));
        Files.createDirectories(nasRoot.resolve("archive"));
        Files.writeString(nasRoot.resolve("source/sub/song.mp3"), "audio");

        nasService.moveFileOrFolder(1L, "source", "archive");

        assertTrue(Files.exists(nasRoot.resolve("archive/source/sub/song.mp3")));
        verify(playlistTrackRepo).renamePathPrefix(1L, "source", "source/%", 6, "archive/source");
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
        verify(cacheRepo).deleteByNasPathIdAndRelativePath(1L, "tema.mp3");
        verify(eventPublisher).publishEvent(any(NasFilesChangedEvent.class));
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

    @Test
    void createFolder_publicaEventoParaInvalidarListadosEnCache() {
        nasService.createFolder(1L, "", "nueva");

        assertTrue(Files.isDirectory(nasRoot.resolve("nueva")));
        verify(eventPublisher).publishEvent(new NasFilesChangedEvent(1L));
    }

    @Test
    void downloadFolderZipToResponse_incluyeContenidoYConservaRutasRelativas() throws Exception {
        Path album = Files.createDirectories(nasRoot.resolve("album/sub"));
        Files.writeString(album.resolve("tema.mp3"), "audio");
        MockHttpServletResponse response = new MockHttpServletResponse();

        nasService.downloadFolderZipToResponse(1L, "album", response);

        assertEquals("application/zip", response.getContentType());
        assertEquals("attachment; filename=\"album.zip\"", response.getHeader("Content-Disposition"));
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var entry = zip.getNextEntry();
            assertNotNull(entry);
            assertEquals("album/sub/tema.mp3", entry.getName());
            assertEquals("audio", new String(zip.readAllBytes()));
            assertNull(zip.getNextEntry());
        }
    }

    @Test
    void downloadFolderZipToResponse_rechazaRutaQueNoEsCarpeta() throws Exception {
        Files.writeString(nasRoot.resolve("tema.mp3"), "audio");

        assertThrows(IllegalArgumentException.class,
                () -> nasService.downloadFolderZipToResponse(1L, "tema.mp3", new MockHttpServletResponse()));
    }

    @Test
    void copyFileTo_copiaArchivoYArbolDeCarpeta() throws Exception {
        Path destination = Files.createDirectory(nasRoot.resolve("destination"));
        Path sourceFolder = Files.createDirectories(nasRoot.resolve("album/sub"));
        Files.writeString(sourceFolder.resolve("tema.mp3"), "audio");
        Files.writeString(nasRoot.resolve("single.mp3"), "single");

        nasService.copyFileTo(1L, "single.mp3", 1L, "destination");
        nasService.copyFileTo(1L, "album", 1L, "destination");

        assertEquals("single", Files.readString(destination.resolve("single.mp3")));
        assertEquals("audio", Files.readString(destination.resolve("album/sub/tema.mp3")));
    }

    @Test
    void copyFileTo_rechazaOrigenInexistenteDestinoNoCarpetaYAutocopiaAnidada() throws Exception {
        Path album = Files.createDirectories(nasRoot.resolve("album/sub"));
        Files.writeString(album.resolve("tema.mp3"), "audio");
        Files.writeString(nasRoot.resolve("not-a-folder"), "file");

        assertThrows(IllegalArgumentException.class,
                () -> nasService.copyFileTo(1L, "missing.mp3", 1L, ""));
        assertThrows(IllegalArgumentException.class,
                () -> nasService.copyFileTo(1L, "album/sub/tema.mp3", 1L, "not-a-folder"));
        assertThrows(IllegalArgumentException.class,
                () -> nasService.copyFileTo(1L, "album", 1L, "album/sub"));
    }

    @Test
    void saveFolderCover_guardaFormatoElegidoYRechazaCarpetaInvalida() throws Exception {
        Path album = Files.createDirectory(nasRoot.resolve("album"));

        nasService.saveFolderCover(1L, "album", new byte[]{1, 2, 3}, "image/png");

        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(album.resolve("cover.png")));
        assertThrows(SecurityException.class,
                () -> nasService.saveFolderCover(1L, "../outside", new byte[]{1}, "image/jpeg"));
        assertThrows(IllegalArgumentException.class,
                () -> nasService.saveFolderCover(1L, "missing", new byte[]{1}, "image/jpeg"));
    }

    @Test
    void createPath_resuelveDentroDeBasePersisteYPublicaEvento() throws Exception {
        Path allowedRoot = Files.createDirectory(nasRoot.resolve("storage"));
        Files.createDirectory(allowedRoot.resolve("music"));
        ReflectionTestUtils.setField(nasService, "nasStorageBase", allowedRoot.toString());
        NasPath saved = NasPath.builder().id(7L).name("Music").path(allowedRoot.resolve("music").toString())
                .description("Local library").build();
        when(nasPathRepository.save(any(NasPath.class))).thenReturn(saved);
        NasPathDto dto = NasPathDto.builder().name("Music").path("music").description("Local library").build();

        NasPathDto result = nasService.createPath(dto);

        assertEquals(7L, result.getId());
        assertEquals(allowedRoot.resolve("music").toString(), result.getPath());
        assertTrue(result.isReadable());
        assertTrue(result.isWritable());
        verify(nasPathRepository).save(argThat(path -> path.getName().equals("Music")
                && path.getPath().equals(allowedRoot.resolve("music").toString())));
        verify(eventPublisher).publishEvent(new NasFilesChangedEvent(7L));
    }

    @Test
    void createPath_rechazaDuplicadosYSalidasDeLaBase() throws Exception {
        Path allowedRoot = Files.createDirectory(nasRoot.resolve("storage"));
        ReflectionTestUtils.setField(nasService, "nasStorageBase", allowedRoot.toString());
        NasPathDto dto = NasPathDto.builder().name("Music").path("music").build();
        when(nasPathRepository.existsByName("Music")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> nasService.createPath(dto));
        when(nasPathRepository.existsByName("Music")).thenReturn(false);
        dto.setPath("../outside");
        assertThrows(SecurityException.class, () -> nasService.createPath(dto));
        verify(nasPathRepository, never()).save(any());
    }

    @Test
    void deletePath_eliminaYPublicaEventoSoloParaRutasExistentes() {
        when(nasPathRepository.existsById(1L)).thenReturn(true);
        when(nasPathRepository.existsById(99L)).thenReturn(false);

        nasService.deletePath(1L);

        verify(nasPathRepository).deleteById(1L);
        verify(eventPublisher).publishEvent(new NasFilesChangedEvent(1L));
        assertThrows(IllegalArgumentException.class, () -> nasService.deletePath(99L));
        verify(nasPathRepository, never()).deleteById(99L);
    }
}
