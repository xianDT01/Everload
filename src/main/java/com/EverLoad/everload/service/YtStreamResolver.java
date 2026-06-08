package com.EverLoad.everload.service;

/**
 * One strategy for turning a YouTube video id into a playable stream URL.
 * {@link YtMusicStreamService} runs the registered resolvers in order
 * (lowest {@code @Order} first) and stops at the first success — letting
 * new resolution methods be added later as plain {@code @Service} beans
 * without touching the orchestrator.
 */
public interface YtStreamResolver {

    /** Short identifier used in logs and in the failure trail (e.g. "botguard", "yt-dlp"). */
    String name();

    /** Never throws for "this video isn't playable here" — that's a {@link YtStreamResolution#failure}. */
    YtStreamResolution resolve(String videoId);
}
