package com.EverLoad.everload.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class YtDlpAdminControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void emptyAndMissingExecutableReturnConfigurationErrors() {
        YtDlpAdminController controller = controller(" ");
        assertEquals(HttpStatus.BAD_REQUEST, controller.updateYtDlp().getStatusCode());

        controller = controller(tempDir.resolve("missing-yt-dlp").toString());
        assertEquals(HttpStatus.NOT_FOUND, controller.updateYtDlp().getStatusCode());
    }

    @Test
    void invalidExecutableReturnsInternalServerError() throws Exception {
        Path invalidExecutable = Files.writeString(tempDir.resolve("fake-yt-dlp.exe"), "not an executable");
        assertTrue(invalidExecutable.toFile().setExecutable(true) || invalidExecutable.toFile().canExecute());
        YtDlpAdminController controller = controller(invalidExecutable.toString());

        ResponseEntity<String> response = controller.updateYtDlp();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void interruptedUpdatePreservesFlagAndReturnsServiceUnavailable() throws Exception {
        Path executable = Files.writeString(tempDir.resolve("yt-dlp.exe"), "binary");
        assertTrue(executable.toFile().setExecutable(true) || executable.toFile().canExecute());
        YtDlpAdminController controller = controller(executable.toString());
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor()).thenThrow(new InterruptedException("stop"));

        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                ProcessBuilder.class,
                (builder, context) -> {
                    when(builder.directory(executable.getParent().toFile())).thenReturn(builder);
                    when(builder.redirectErrorStream(true)).thenReturn(builder);
                    when(builder.start()).thenReturn(process);
                })) {
            ResponseEntity<String> response = controller.updateYtDlp();

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
            assertTrue(Thread.currentThread().isInterrupted());
            Thread.interrupted();
        }
    }

    private YtDlpAdminController controller(String path) {
        YtDlpAdminController controller = new YtDlpAdminController();
        ReflectionTestUtils.setField(controller, "ytDlpPath", path);
        return controller;
    }
}
