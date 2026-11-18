package com.everload.everload.controller;

import com.everload.everload.service.NasService;
import com.everload.everload.service.SetupIntegrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/setup")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SetupConfigurationController {
    private final NasService nas;
    private final SetupIntegrationService integrations;

    public record LibraryRequest(@NotBlank @Size(max = 100) String name, @Size(max = 1024) String relativePath) {}
    public record IntegrationRequest(String clientId, String clientSecret, String apiKey, String acoustidApiKey) {
        @Override public String toString() { return "IntegrationRequest[REDACTED]"; }
        Map<String, String> values(String service) {
            Map<String, String> values = new java.util.HashMap<>();
            switch (service) {
                case "spotify" -> { values.put("clientId", clientId); values.put("clientSecret", clientSecret); }
                case "youtube" -> values.put("apiKey", apiKey);
                case "acoustid" -> values.put("acoustidApiKey", acoustidApiKey);
                default -> throw new IllegalArgumentException("SETUP_SERVICE");
            }
            return values;
        }
    }

    @GetMapping("/configuration")
    public ResponseEntity<?> configuration() throws IOException {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("libraries", nas.getAllPaths(), "services", integrations.configured()));
    }

    @GetMapping("/storage")
    public NasService.StorageAccess storage(@RequestParam(defaultValue = "") String path) {
        if (path.length() > 1024) throw new IllegalArgumentException("SETUP_PATH");
        return nas.inspectStorage(path);
    }

    @PostMapping("/library")
    public Object library(@Valid @RequestBody LibraryRequest request) {
        return nas.createSetupLibrary(request.name(), request.relativePath());
    }

    @PostMapping("/integrations/{service}")
    public ResponseEntity<?> save(@PathVariable String service, @RequestBody IntegrationRequest request) throws IOException {
        integrations.save(service, request.values(service));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status", "saved"));
    }

    @PostMapping("/integrations/{service}/test")
    public Map<String, String> test(@PathVariable String service) throws IOException {
        return Map.of("status", integrations.verify(service));
    }

    @ExceptionHandler({IllegalArgumentException.class, SecurityException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class})
    public ResponseEntity<?> invalid(Exception error) {
        return ResponseEntity.badRequest().body(Map.of("error", "SETUP_FIELDS"));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<?> unavailable(IOException error) {
        return ResponseEntity.internalServerError().body(Map.of("error", "SETUP_SAVE_FAILED"));
    }
}
