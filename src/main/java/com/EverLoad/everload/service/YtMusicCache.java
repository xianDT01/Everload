package com.EverLoad.everload.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Small TTL + max-size cache for the read-only catalogue calls (search,
 * discover shelves, album/artist lookups, public playlist pages). These
 * results are identical for every visitor — caching them cuts InnerTube
 * round-trips dramatically without touching anything user-specific (there
 * is no per-user state in anonymous mode to begin with).
 *
 * Deliberately a plain {@link ConcurrentHashMap}-backed cache (matching
 * {@code MusicService}'s existing caching style) rather than the Spring
 * cache abstraction — one less thing to configure for a single module.
 */
public final class YtMusicCache<K, V> {

    private record Entry<V>(V value, long expiresAtMillis) {
        boolean isExpired(long now) {
            return now >= expiresAtMillis;
        }
    }

    private final ConcurrentHashMap<K, Entry<V>> store = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final int maxEntries;
    private final LongSupplier currentTimeMillis;

    public YtMusicCache(long ttlMillis, int maxEntries) {
        this(ttlMillis, maxEntries, System::currentTimeMillis);
    }

    YtMusicCache(long ttlMillis, int maxEntries, LongSupplier currentTimeMillis) {
        this.ttlMillis = ttlMillis;
        this.maxEntries = maxEntries;
        this.currentTimeMillis = currentTimeMillis;
    }

    public V getOrCompute(K key, Supplier<V> loader) {
        long now = currentTimeMillis.getAsLong();
        Entry<V> existing = store.get(key);
        if (existing != null && !existing.isExpired(now)) {
            return existing.value();
        }
        V computed = loader.get();
        if (store.size() >= maxEntries && !store.containsKey(key)) {
            // Cheap unbounded-growth guard: drop one arbitrary expired-or-oldest
            // entry rather than maintaining an LRU order for a cache this small.
            store.keySet().stream().findAny().ifPresent(store::remove);
        }
        store.put(key, new Entry<>(computed, now + ttlMillis));
        return computed;
    }

    public void invalidate(K key) {
        store.remove(key);
    }

    public void clear() {
        store.clear();
    }
}
