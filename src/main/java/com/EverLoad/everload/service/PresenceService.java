package com.EverLoad.everload.service;

import com.EverLoad.everload.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final UserRepository userRepository;

    /** Username → time of last heartbeat. */
    private final ConcurrentHashMap<String, Instant> onlineMap = new ConcurrentHashMap<>();

    /** A user is considered online if their last heartbeat was within this many seconds. */
    private static final long ONLINE_THRESHOLD_SECONDS = 90;

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Called on each frontend heartbeat (every ~30 s). */
    public void heartbeat(String username) {
        onlineMap.put(username, Instant.now());
    }

    /** Called on explicit logout or tab-close beacon. Persists lastSeen to DB. */
    public void setOffline(String username) {
        onlineMap.remove(username);
        persistLastSeenToDb(username, LocalDateTime.now());
    }

    public boolean isOnline(String username) {
        Instant last = onlineMap.get(username);
        if (last == null) return false;
        return Duration.between(last, Instant.now()).getSeconds() < ONLINE_THRESHOLD_SECONDS;
    }

    // ── Scheduled tasks ────────────────────────────────────────────────────────

    /**
     * Every 2 minutes: persist lastSeen for online users and clean up stale entries
     * (users whose heartbeat stopped — likely closed the tab without a logout).
     */
    @Scheduled(fixedDelay = 120_000)
    public void flushAndCleanup() {
        Instant now = Instant.now();
        Instant staleThreshold = now.minusSeconds(ONLINE_THRESHOLD_SECONDS * 2);

        onlineMap.forEach((username, lastHeartbeat) -> {
            if (lastHeartbeat.isBefore(staleThreshold)) {
                // User went stale — remove and persist
                onlineMap.remove(username, lastHeartbeat);
                persistLastSeenToDb(username, LocalDateTime.now());
            } else {
                // Still online — refresh lastSeen in DB so it stays current
                persistLastSeenToDb(username, LocalDateTime.now());
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void persistLastSeenToDb(String username, LocalDateTime time) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastSeen(time);
            userRepository.save(user);
        });
    }
}