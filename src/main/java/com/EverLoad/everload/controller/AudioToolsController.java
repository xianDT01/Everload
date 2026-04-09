package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.AudioInfoDto;
import com.EverLoad.everload.service.AudioToolsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/audio")
@RequiredArgsConstructor
public class AudioToolsController {

    private final AudioToolsService audioToolsService;

    /** Returns metadata (duration, format, bitrate, etc.) for an uploaded audio file. */
    @PostMapping("/info")
    public ResponseEntity<AudioInfoDto> getInfo(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(audioToolsService.getAudioInfo(file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Converts / compresses an audio file to the requested format.
     * @param format  target format: mp3 | m4a | wav | ogg | aac | flac
     * @param bitrate optional bitrate: 64k | 96k | 128k | 192k | 256k | 320k
     */
    @PostMapping("/convert")
    public ResponseEntity<FileSystemResource> convert(
            @RequestParam("file") MultipartFile file,
            @RequestParam("format") String format,
            @RequestParam(value = "bitrate", required = false, defaultValue = "192k") String bitrate,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            return audioToolsService.convertAudio(file, format, bitrate);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Trims an audio file between start and end (seconds).
     * Output keeps the same format/codec as the input.
     */
    @PostMapping("/trim")
    public ResponseEntity<FileSystemResource> trim(
            @RequestParam("file") MultipartFile file,
            @RequestParam("start") double start,
            @RequestParam("end") double end,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            return audioToolsService.trimAudio(file, start, end);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
