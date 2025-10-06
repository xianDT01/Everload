package com.EverLoad.everload.controller;

import com.EverLoad.everload.config.AdminConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Admin Config", description = "Gestión de la configuración interna de la aplicación (API Keys, Client IDs, etc.)")
@RestController
@RequestMapping("/api/admin/config")
public class AdminController {

    private final AdminConfigService configService;

    public AdminController(AdminConfigService configService) {
        this.configService = configService;
    }

    @Operation(
            summary = "Obtener configuración actual",
            description = """
                    Devuelve las claves y valores almacenados en el archivo `config.json` 
                    que se utiliza para la configuración interna de EverLoad (por ejemplo, 
                    API keys, Client IDs, secretos, etc.).
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Configuración obtenida correctamente",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = Map.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "clientId": "your-client-id",
                                              "clientSecret": "your-secret",
                                              "apiKey": "your-youtube-api-key"
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(responseCode = "500", description = "Error al leer el archivo de configuración")
            }
    )
    @GetMapping
    public ResponseEntity<Map<String, String>> getConfig() {
        try {
            return ResponseEntity.ok(configService.getConfig());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(
            summary = "Actualizar configuración",
            description = """
                    Permite actualizar los valores del archivo `config.json`.
                    Si el archivo existe, se sobrescribe con los nuevos valores recibidos.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "clientId": "new-client-id",
                                      "clientSecret": "new-client-secret",
                                      "apiKey": "new-youtube-api-key"
                                    }
                                    """)
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Configuración actualizada correctamente"),
                    @ApiResponse(responseCode = "500", description = "Error al escribir el archivo de configuración")
            }
    )
    @PostMapping
    public ResponseEntity<Void> updateConfig(@RequestBody Map<String, String> newConfig) {
        try {
            configService.updateConfig(newConfig);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
