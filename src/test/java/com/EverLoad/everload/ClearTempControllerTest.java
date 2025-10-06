package com.EverLoad.everload;

import com.EverLoad.everload.controller.ClearTempController;
import com.EverLoad.everload.service.DownloadHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@WebMvcTest(ClearTempController.class)
@Import(DownloadHistoryService.class)
class ClearTempControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Path.of("./downloads/tmp-test");
        Files.createDirectories(tempDir);
        // crear un archivo dentro para asegurar que luego se borre
        Files.writeString(tempDir.resolve("dummy.txt"), "hola");
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (Files.exists(Path.of("./downloads"))) {
            Files.walk(Path.of("./downloads"))
                    .map(Path::toFile)
                    .sorted((a, b) -> -a.compareTo(b)) // borrar hijos primero
                    .forEach(File::delete);
        }
    }

}
