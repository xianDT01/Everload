package com.EverLoad.everload.config;

import com.EverLoad.everload.service.LogService;
import com.EverLoad.everload.service.MusicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogScheduler {

    private final LogService logService;
    private final MusicService musicService;

    // Every 7 days
    @Scheduled(fixedRate = 604_800_000)
    public void autoClearLog() {
        try {
            boolean cleared = logService.clearLog();
            if (cleared) log.info("🧹 Log automatically cleared by scheduler.");
        } catch (Exception e) {
            log.error("❌ Scheduled log clear failed: {}", e.getMessage());
        }
    }

    // Every day — delete transcode cache files older than 7 days
    @Scheduled(fixedRate = 86_400_000)
    public void cleanTranscodeCache() {
        try {
            musicService.cleanTranscodeCache();
        } catch (Exception e) {
            log.error("❌ Transcode cache cleanup failed: {}", e.getMessage());
        }
    }
}