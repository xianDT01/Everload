package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.AndroidReleaseDto;
import com.EverLoad.everload.service.AndroidReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app-release/android")
public class AndroidReleaseController {

    private final AndroidReleaseService androidReleaseService;

    @GetMapping
    public AndroidReleaseDto getRelease() {
        return androidReleaseService.getRelease();
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download() throws Exception {
        Resource apk = androidReleaseService.getApkResource();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + androidReleaseService.getDownloadFileName() + "\"")
                .body(apk);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public AndroidReleaseDto upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String versionName,
            @RequestParam(required = false) String versionCode,
            @RequestParam(required = false) String minAndroidVersion,
            @RequestParam(required = false) String releaseNotes) throws Exception {
        return androidReleaseService.saveRelease(file, versionName, versionCode, minAndroidVersion, releaseNotes);
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete() throws Exception {
        androidReleaseService.deleteRelease();
        return ResponseEntity.ok().build();
    }
}
