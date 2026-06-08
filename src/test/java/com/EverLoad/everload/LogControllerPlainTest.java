package com.EverLoad.everload;
import com.EverLoad.everload.controller.LogController;
import org.junit.jupiter.api.*;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LogControllerPlainTest {
    private final Path logPath = Path.of("everload.log");
    private LogController controller;

    @BeforeEach
    void setup() throws Exception {
        controller = new LogController(new com.EverLoad.everload.service.LogService());
        // Crear log con contido
        Files.writeString(logPath,
                "Linea 1\nLinea 2\nLinea 3 contiene ERROR\nLinea 4\nLinea 5\n");
    }

    @AfterEach
    void tearDown() {
        // Best-effort cleanup: on Windows the active Logback appender (configured via
        // logging.file.name=everload.log) can hold this same file open when Spring-context
        // tests have run earlier in the same JVM, making delete() throw. Truncating in place
        // works regardless and a failed cleanup must not fail the test itself.
        try {
            if (Files.exists(logPath)) Files.write(logPath, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {}
    }

    @Test
    void getLogs_devuelveUltimasLineas() {
        ResponseEntity<List<String>> resp = controller.getLogs(3, null);
        assertEquals(200, resp.getStatusCodeValue());
        List<String> lines = resp.getBody();
        assertNotNull(lines);
        assertEquals(3, lines.size());
        assertEquals("Linea 3 contiene ERROR", lines.get(0));
        assertEquals("Linea 4", lines.get(1));
        assertEquals("Linea 5", lines.get(2));
    }

    @Test
    void getLogs_filtraPorTexto() {
        ResponseEntity<List<String>> resp = controller.getLogs(10, "error");
        List<String> lines = resp.getBody();
        assertNotNull(lines);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toLowerCase().contains("error"));
    }

    @Test
    void clearLog_vaciaFichero() throws Exception {
        assertTrue(Files.size(logPath) > 0);
        ResponseEntity<String> resp = controller.clearLog();
        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(0, Files.size(logPath));
    }
}