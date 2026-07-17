package com.EverLoad.everload.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AudioToolsServiceTest {

    private AudioToolsService service;

    @BeforeEach
    void setUp() {
        service = new AudioToolsService(mock(DownloadHistoryService.class));
        ReflectionTestUtils.setField(service, "ffmpegPath", "ffmpeg-test");
    }

    @Test
    void validateFileAcceptsKnownAudioSignatures() {
        assertValid("track.mp3", new byte[]{'I', 'D', '3', 0, 0});
        assertValid("track.flac", new byte[]{'f', 'L', 'a', 'C', 0});
        assertValid("track.wav", new byte[]{'R', 'I', 'F', 'F', 0});
        assertValid("track.ogg", new byte[]{'O', 'g', 'g', 'S', 0});
    }

    @Test
    void validateFileRejectsMissingDisguisedAndUnsupportedFiles() {
        MockMultipartFile disguisedMp3 = new MockMultipartFile(
                "file", "track.mp3", "audio/mpeg", new byte[]{1, 2, 3, 4});
        MockMultipartFile executable = new MockMultipartFile(
                "file", "payload.exe", "application/octet-stream", new byte[]{1, 2, 3, 4});
        assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateFile", (Object) null));
        assertThrows(IllegalArgumentException.class,
                () -> validate(disguisedMp3));
        assertThrows(IllegalArgumentException.class,
                () -> validate(executable));
    }

    @Test
    void buildConvertCommandConfiguresEverySupportedCodec() {
        File input = new File("input.wav");
        File output = new File("output.audio");

        assertCommandContains(input, output, "mp3", "192k", "libmp3lame");
        assertCommandContains(input, output, "m4a", "256k", "aac");
        assertCommandContains(input, output, "aac", "128k", "adts");
        assertCommandContains(input, output, "ogg", "192k", "libvorbis");
        assertCommandContains(input, output, "wav", null, "pcm_s16le");
        assertCommandContains(input, output, "flac", null, "flac");
    }

    @Test
    void stringAndNumberHelpersUseSafeFallbacks() {
        assertEquals("song", ReflectionTestUtils.invokeMethod(service, "getBaseName", "song.mp3"));
        assertEquals("audio", ReflectionTestUtils.invokeMethod(service, "getExtension", "README"));
        assertEquals(0, (int) ReflectionTestUtils.invokeMethod(service, "parseInt", "bad"));
        assertEquals(12L, (long) ReflectionTestUtils.invokeMethod(service, "parseLong", "12", 3L));
        assertEquals(3L, (long) ReflectionTestUtils.invokeMethod(service, "parseLong", "bad", 3L));
    }

    @Test
    void internalCommandBuilderRejectsUnknownFormat() {
        File input = new File("input.wav");
        File output = new File("output.bin");

        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(
                service, "buildConvertCommand", input, output, "unknown", null));
    }

    @Test
    void ffmpegRunnerReturnsFailureWhenExecutableCannotStart() {
        int exit = ReflectionTestUtils.invokeMethod(
                service, "runFfmpeg", List.of("definitely-missing-everload-ffmpeg"));

        assertEquals(-1, exit);
    }

    private void assertValid(String filename, byte[] bytes) {
        MockMultipartFile file = new MockMultipartFile("file", filename, "audio/test", bytes);
        assertDoesNotThrow(() -> validate(file));
    }

    private void validate(MockMultipartFile file) {
        ReflectionTestUtils.invokeMethod(service, "validateFile", file);
    }

    @SuppressWarnings("unchecked")
    private void assertCommandContains(File input, File output, String format, String bitrate, String codec) {
        List<String> command = ReflectionTestUtils.invokeMethod(
                service, "buildConvertCommand", input, output, format, bitrate);
        assertEquals("ffmpeg-test", command.get(0));
        assertTrue(command.contains(codec));
        assertEquals(output.getAbsolutePath(), command.get(command.size() - 1));
        if (bitrate == null) {
            assertFalse(command.contains("-ab"));
        } else {
            assertTrue(command.contains(bitrate));
        }
    }
}
