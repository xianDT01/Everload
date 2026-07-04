package com.EverLoad.everload.service;

import com.EverLoad.everload.config.JacksonConfig;
import com.EverLoad.everload.model.Download;
import com.EverLoad.everload.repository.DownloadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifica la migración única del downloads_history.json legado a la BD,
 * incluido el formato de fecha real que producía la app antigua (con sufijo Z),
 * usando el MISMO ObjectMapper que configura JacksonConfig en producción.
 */
class DownloadHistoryServiceTest {

    @TempDir
    Path tempDir;

    private DownloadRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(DownloadRepository.class);
    }

    private DownloadHistoryService serviceWithLegacy(String legacyPath) {
        return new DownloadHistoryService(repository, new JacksonConfig().objectMapper(), legacyPath);
    }

    @Test
    void importaElJsonLegadoConFechasEnFormatoRealYRenombraElArchivo() throws Exception {
        // Formato exacto que escribía la app antigua (ISO con offset Z y nanos)
        Path legacy = tempDir.resolve("downloads_history.json");
        Files.writeString(legacy, """
                [ {
                  "title" : "videoId=GBV5TYVNHsc",
                  "type" : "music",
                  "platform" : "YouTube",
                  "createdAt" : "2026-04-24T23:47:01.429748668Z"
                }, {
                  "title" : "Otra cancion.mp3",
                  "type" : "music (NAS)",
                  "platform" : "YouTube",
                  "createdAt" : "2026-05-01T10:00:00Z"
                } ]""");
        when(repository.count()).thenReturn(0L);

        serviceWithLegacy(legacy.toString()).importLegacyJsonHistory();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Download>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals("videoId=GBV5TYVNHsc", captor.getValue().get(0).getTitle());
        assertNotNull(captor.getValue().get(0).getCreatedAt(), "la fecha con Z debe parsear");

        assertFalse(Files.exists(legacy), "el JSON original se renombra tras importar");
        assertTrue(Files.exists(tempDir.resolve("downloads_history.json.imported")));
    }

    @Test
    void noReimportaSiLaBdYaTieneDatos() throws Exception {
        Path legacy = tempDir.resolve("downloads_history.json");
        Files.writeString(legacy, "[]");
        when(repository.count()).thenReturn(5L);

        serviceWithLegacy(legacy.toString()).importLegacyJsonHistory();

        verify(repository, never()).saveAll(any());
        assertTrue(Files.exists(legacy), "sin importación no se toca el archivo");
    }

    @Test
    void jsonCorruptoNoRompeElArranqueNiBorraElArchivo() throws Exception {
        Path legacy = tempDir.resolve("downloads_history.json");
        Files.writeString(legacy, "{esto no es json valido");
        when(repository.count()).thenReturn(0L);

        assertDoesNotThrow(() -> serviceWithLegacy(legacy.toString()).importLegacyJsonHistory());

        verify(repository, never()).saveAll(any());
        assertTrue(Files.exists(legacy), "ante un fallo el archivo queda intacto para revisarlo");
    }

    @Test
    void sinArchivoLegadoNoHaceNada() {
        serviceWithLegacy(tempDir.resolve("no-existe.json").toString()).importLegacyJsonHistory();
        verify(repository, never()).saveAll(any());
    }
}
