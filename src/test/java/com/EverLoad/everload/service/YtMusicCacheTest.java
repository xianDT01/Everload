package com.EverLoad.everload.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YtMusicCacheTest {

    @Test
    void getOrComputeReusesValueUntilInvalidated() {
        YtMusicCache<String, String> cache = new YtMusicCache<>(60_000, 10);
        AtomicInteger loads = new AtomicInteger();

        String first = cache.getOrCompute("artist", () -> "value-" + loads.incrementAndGet());
        String second = cache.getOrCompute("artist", () -> "value-" + loads.incrementAndGet());

        assertEquals("value-1", first);
        assertEquals("value-1", second);
        assertEquals(1, loads.get());

        cache.invalidate("artist");

        assertEquals("value-2", cache.getOrCompute("artist", () -> "value-" + loads.incrementAndGet()));
        assertEquals(2, loads.get());
    }

    @Test
    void getOrComputeReloadsExpiredEntries() {
        AtomicLong now = new AtomicLong(1_000);
        YtMusicCache<String, String> cache = new YtMusicCache<>(1, 10, now::get);
        AtomicInteger loads = new AtomicInteger();

        assertEquals("value-1", cache.getOrCompute("track", () -> "value-" + loads.incrementAndGet()));
        now.incrementAndGet();

        assertEquals("value-2", cache.getOrCompute("track", () -> "value-" + loads.incrementAndGet()));
        assertEquals(2, loads.get());
    }

    @Test
    void clearDropsAllCachedValues() {
        YtMusicCache<String, String> cache = new YtMusicCache<>(60_000, 10);
        AtomicInteger loads = new AtomicInteger();

        cache.getOrCompute("album", () -> "value-" + loads.incrementAndGet());
        cache.clear();

        assertEquals("value-2", cache.getOrCompute("album", () -> "value-" + loads.incrementAndGet()));
        assertEquals(2, loads.get());
    }
}
