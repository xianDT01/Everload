package com.EverLoad.everload.service;

import com.EverLoad.everload.config.AdminConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcoustIdServiceTest {

    @TempDir
    Path tempDir;

    private AdminConfigService configService;
    private NasService nasService;
    private AcoustIdService service;

    @BeforeEach
    void setUp() {
        configService = mock(AdminConfigService.class);
        nasService = mock(NasService.class);
        service = new AcoustIdService(configService, nasService);
    }

    @AfterEach
    void clearInterruptedFlag() {
        Thread.interrupted();
    }

    @Test
    void identifyReportsUnavailableFpcalcWithoutStartingARealTool() throws Exception {
        Path audio = Files.writeString(tempDir.resolve("track.mp3"), "audio");
        when(configService.getAcoustidApiKey()).thenReturn("key");
        when(nasService.resolveValidatedPath(1L, "track.mp3")).thenReturn(audio);
        ReflectionTestUtils.setField(service, "fpcalcPath", tempDir.resolve("missing-fpcalc").toString());

        AcoustIdService.FingerprintResult result = service.identify(1L, "track.mp3");

        assertFalse(result.found());
        assertTrue(result.error().contains("fpcalc"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void acoustIdQueryPreservesInterruptAndReturnsNull() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("stop"));
        ReflectionTestUtils.setField(service, "httpClient", client);

        JsonNode result = ReflectionTestUtils.invokeMethod(service, "queryAcoustId", "fingerprint", 42, "key");

        assertNull(result);
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void coverDownloadPreservesInterruptAndReturnsFalse() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("stop"));
        ReflectionTestUtils.setField(service, "httpClient", client);

        Boolean result = ReflectionTestUtils.invokeMethod(
                service, "embedCoverArt", tempDir.resolve("track.mp3").toFile(), "release-id");

        assertFalse(result);
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void fpcalcInterruptionPreservesFlagAndReturnsNull() throws Exception {
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream("{}".getBytes()));
        when(process.waitFor()).thenThrow(new InterruptedException("stop"));

        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                ProcessBuilder.class,
                (builder, context) -> {
                    when(builder.redirectErrorStream(true)).thenReturn(builder);
                    when(builder.start()).thenReturn(process);
                })) {
            Object result = ReflectionTestUtils.invokeMethod(
                    service, "runFpcalc", tempDir.resolve("track.mp3").toFile());

            assertNull(result);
            assertTrue(Thread.currentThread().isInterrupted());
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void coverDownloadEmbedsArtworkAndRemovesTemporaryImage() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("jpeg-data".getBytes());
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        ReflectionTestUtils.setField(service, "httpClient", client);
        AudioFile audioFile = mock(AudioFile.class);
        Tag tag = mock(Tag.class);
        Artwork artwork = mock(Artwork.class);
        when(audioFile.getTagOrCreateDefault()).thenReturn(tag);
        Path artworkDir = Path.of("./downloads/.artwork-tmp").toAbsolutePath().normalize();
        boolean directoryExisted = Files.exists(artworkDir);

        try (MockedStatic<AudioFileIO> audioFiles = mockStatic(AudioFileIO.class);
             MockedStatic<ArtworkFactory> artworkFactory = mockStatic(ArtworkFactory.class)) {
            audioFiles.when(() -> AudioFileIO.read(any(java.io.File.class))).thenReturn(audioFile);
            artworkFactory.when(() -> ArtworkFactory.createArtworkFromFile(any(java.io.File.class)))
                    .thenReturn(artwork);

            Boolean embedded = ReflectionTestUtils.invokeMethod(
                    service, "embedCoverArt", tempDir.resolve("track.mp3").toFile(), "release-id");

            assertTrue(embedded);
            verify(tag).setField(artwork);
            try (var temporaryImages = Files.list(artworkDir)) {
                assertTrue(temporaryImages.noneMatch(path -> path.getFileName().toString().startsWith("cover-")));
            }
        } finally {
            if (!directoryExisted) Files.deleteIfExists(artworkDir);
        }
    }
}
