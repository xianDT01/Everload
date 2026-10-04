package com.everload.everload.service;

import com.everload.everload.repository.NasPathRepository;
import com.everload.everload.repository.TrackMetadataCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MusicCoverUploadTest {

    @TempDir
    Path tempDir;

    private NasService nasService;
    private MusicService musicService;

    @BeforeEach
    void setUp() {
        nasService = mock(NasService.class);
        musicService = new MusicService(nasService, mock(NasPathRepository.class),
                mock(TrackMetadataCacheRepository.class), mock(RestTemplate.class));
    }

    @Test
    void rejectsOversizedImageBeforeResolvingAudioFile() {
        assertThrows(IllegalArgumentException.class,
                () -> musicService.updateCoverArt(1L, "song.mp3", new byte[10 * 1024 * 1024 + 1]));

        verifyNoInteractions(nasService);
    }

    @Test
    void rejectsBytesThatAreNotAnImage() throws Exception {
        Path audio = Files.write(tempDir.resolve("song.mp3"), new byte[]{1, 2, 3});
        when(nasService.resolveValidatedPath(1L, "song.mp3")).thenReturn(audio);

        assertThrows(IllegalArgumentException.class,
                () -> musicService.updateCoverArt(1L, "song.mp3", new byte[]{1, 2, 3, 4}));
    }
}
