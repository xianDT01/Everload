package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    /** Frontend sends this every ~30 s to signal the user is still active. */
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@AuthenticationPrincipal UserDetails userDetails) {
        presenceService.heartbeat(userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    /**
     * Frontend sends this on explicit logout or on tab-close (fetch keepalive).
     * Immediately marks the user offline and persists lastSeen.
     */
    @PostMapping("/offline")
    public ResponseEntity<Void> goOffline(@AuthenticationPrincipal UserDetails userDetails) {
        presenceService.setOffline(userDetails.getUsername());
        return ResponseEntity.ok().build();
    }
}