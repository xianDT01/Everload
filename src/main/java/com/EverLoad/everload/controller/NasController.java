package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.NasFileDto;
import com.EverLoad.everload.dto.NasPathDto;
import com.EverLoad.everload.service.NasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "NAS", description = "Gestión de rutas NAS y explorador de archivos")
@RestController
@RequestMapping("/api/nas")
@RequiredArgsConstructor
public class NasController {

    private final NasService nasService;

    // ── Configuración de rutas NAS (solo ADMIN) ───────────────────────────────

    @Operation(summary = "Listar rutas NAS configuradas")
    @GetMapping("/paths")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NasPathDto>> getPaths() {
        return ResponseEntity.ok(nasService.getAllPaths());
    }

    @Operation(summary = "Crear nueva ruta NAS (solo ADMIN)")
    @PostMapping("/paths")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createPath(@RequestBody NasPathDto dto) {
        try {
            return ResponseEntity.ok(nasService.createPath(dto));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Eliminar ruta NAS (solo ADMIN)")
    @DeleteMapping("/paths/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deletePath(@PathVariable Long id) {
        try {
            nasService.deletePath(id);
            return ResponseEntity.ok(Map.of("message", "Ruta eliminada"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Explorador de archivos (ADMIN y NAS_USER) ─────────────────────────────

    @Operation(summary = "Listar archivos en una ruta NAS")
    @GetMapping("/browse/{pathId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> browse(@PathVariable Long pathId,
                                    @RequestParam(required = false) String subPath) {
        try {
            List<NasFileDto> files = nasService.listFiles(pathId, subPath);
            return ResponseEntity.ok(files);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Crear carpeta en NAS")
    @PostMapping("/browse/{pathId}/mkdir")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> mkdir(@PathVariable Long pathId,
                                   @RequestParam(required = false) String subPath,
                                   @RequestParam String folderName) {
        try {
            nasService.createFolder(pathId, subPath, folderName);
            return ResponseEntity.ok(Map.of("message", "Carpeta creada: " + folderName));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Eliminar archivo o carpeta del NAS")
    @DeleteMapping("/browse/{pathId}/delete")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> delete(@PathVariable Long pathId,
                                    @RequestParam String relativePath) {
        try {
            nasService.deleteFileOrFolder(pathId, relativePath);
            return ResponseEntity.ok(Map.of("message", "Eliminado correctamente"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Renombrar archivo o carpeta")
    @PutMapping("/browse/{pathId}/rename")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> rename(@PathVariable Long pathId,
                                    @RequestParam String relativePath,
                                    @RequestParam String newName) {
        try {
            String newPath = nasService.renameFileOrFolder(pathId, relativePath, newName);
            return ResponseEntity.ok(Map.of("message", "Renombrado correctamente", "newPath", newPath));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Mover archivo o carpeta")
    @PutMapping("/browse/{pathId}/move")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> move(@PathVariable Long pathId,
                                  @RequestParam String sourcePath,
                                  @RequestParam(required = false) String targetFolderPath) {
        try {
            nasService.moveFileOrFolder(pathId, sourcePath, targetFolderPath);
            return ResponseEntity.ok(Map.of("message", "Movido correctamente"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Cambiar imagen de portada de carpeta")
    @PostMapping("/browse/{pathId}/cover")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> uploadFolderCover(@PathVariable Long pathId,
                                               @RequestParam(required = false) String folderPath,
                                               @RequestPart("image") MultipartFile image) {
        try {
            nasService.saveFolderCover(pathId, folderPath, image.getBytes(), image.getContentType());
            return ResponseEntity.ok(Map.of("message", "Portada actualizada"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Subir archivos de música al NAS (soporta subida de carpetas con estructura)")
    @PostMapping("/browse/{pathId}/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> uploadFiles(@PathVariable Long pathId,
                                         @RequestParam(required = false) String subPath,
                                         @RequestPart("files") List<MultipartFile> files,
                                         @RequestParam(required = false) List<String> paths) {
        try {
            List<Map<String, Object>> results = nasService.uploadMusicFiles(pathId, subPath, files, paths);
            return ResponseEntity.ok(results);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Descargar archivo de música")
    @GetMapping("/browse/{pathId}/download")
    @PreAuthorize("isAuthenticated()")
    public void downloadFile(@PathVariable Long pathId,
                             @RequestParam String relativePath,
                             HttpServletResponse response) throws IOException {
        try {
            nasService.downloadFileToResponse(pathId, relativePath, response);
        } catch (SecurityException e) {
            response.sendError(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            response.sendError(400, e.getMessage());
        }
    }

    @Operation(summary = "Descargar carpeta como ZIP")
    @GetMapping("/browse/{pathId}/download-zip")
    @PreAuthorize("isAuthenticated()")
    public void downloadFolderZip(@PathVariable Long pathId,
                                  @RequestParam String relativePath,
                                  HttpServletResponse response) throws IOException {
        try {
            nasService.downloadFolderZipToResponse(pathId, relativePath, response);
        } catch (SecurityException e) {
            response.sendError(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            response.sendError(400, e.getMessage());
        }
    }

    @Operation(summary = "Copiar archivo o carpeta a otra carpeta NAS")
    @PostMapping("/copy")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> copyFile(
            @RequestParam Long sourcePathId,
            @RequestParam String sourcePath,
            @RequestParam Long destPathId,
            @RequestParam String destPath) {
        try {
            nasService.copyFileTo(sourcePathId, sourcePath, destPathId, destPath);
            return ResponseEntity.ok(Map.of("message", "Archivo copiado correctamente"));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al copiar el archivo"));
        }
    }
}
