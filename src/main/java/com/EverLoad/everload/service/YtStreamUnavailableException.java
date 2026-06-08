package com.EverLoad.everload.service;

import java.util.List;

/**
 * Thrown when every registered {@link YtStreamResolver} failed to produce a
 * playable URL for a video — e.g. it's region-locked, Premium-only, age
 * gated, or has been removed. Carries the per-resolver failure trail so the
 * controller can return a useful message instead of a bare 500.
 */
public class YtStreamUnavailableException extends RuntimeException {

    private final String videoId;
    private final List<String> resolverFailures;

    public YtStreamUnavailableException(String videoId, List<String> resolverFailures) {
        super("No se pudo resolver un stream reproducible para " + videoId
                + " (" + String.join("; ", resolverFailures) + ")");
        this.videoId = videoId;
        this.resolverFailures = List.copyOf(resolverFailures);
    }

    public String videoId() {
        return videoId;
    }

    public List<String> resolverFailures() {
        return resolverFailures;
    }
}
