package com.everload.everload.config;

import com.everload.everload.service.ChatService;
import com.everload.everload.service.SetupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final SetupService setupService;
    private final ChatService chatService;

    @Override
    public void run(String... args) {
        setupService.initialize();
        try { chatService.ensureAnnouncementChannel(); }
        catch (Exception e) { log.warn("Could not initialize announcement channel: {}", e.getMessage()); }
    }
}
