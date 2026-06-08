package com.EverLoad.everload.service;

/**
 * Mirrors the {@code playabilityStatus.status} field YouTube returns from
 * {@code /player}. Geo-blocking and most "restricted" cases don't get a
 * dedicated status — they arrive as UNPLAYABLE/LOGIN_REQUIRED with a
 * human-readable {@code reason} string, which callers should surface as-is.
 */
public enum YtPlayabilityStatus {
    OK,
    UNKNOWN,
    LOGIN_REQUIRED,
    UNPLAYABLE,
    ERROR,
    AGE_CHECK_REQUIRED,
    OTHER;

    /** Whether it's worth asking this client for stream formats at all. */
    public boolean isAttemptable() {
        return this == OK || this == UNKNOWN;
    }

    public static YtPlayabilityStatus fromRaw(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        return switch (raw) {
            case "OK" -> OK;
            case "LOGIN_REQUIRED" -> LOGIN_REQUIRED;
            case "UNPLAYABLE" -> UNPLAYABLE;
            case "ERROR" -> ERROR;
            case "AGE_CHECK_REQUIRED", "CONTENT_CHECK_REQUIRED" -> AGE_CHECK_REQUIRED;
            default -> OTHER;
        };
    }
}
