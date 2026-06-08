package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.YtStreamInfoDto;

/**
 * Outcome of a single {@link YtStreamResolver} attempt. Modeled explicitly
 * (rather than throwing on failure) so the orchestrator can collect a
 * per-resolver failure trail — restricted/geo-blocked/deleted videos all
 * surface here with their {@link YtPlayabilityStatus} and YouTube's own
 * reason text instead of a generic exception message.
 */
public final class YtStreamResolution {

    private final YtStreamInfoDto streamInfo;
    private final YtPlayabilityStatus status;
    private final String reason;

    private YtStreamResolution(YtStreamInfoDto streamInfo, YtPlayabilityStatus status, String reason) {
        this.streamInfo = streamInfo;
        this.status = status;
        this.reason = reason;
    }

    public static YtStreamResolution success(YtStreamInfoDto info) {
        return new YtStreamResolution(info, YtPlayabilityStatus.OK, null);
    }

    public static YtStreamResolution failure(YtPlayabilityStatus status, String reason) {
        return new YtStreamResolution(null, status, reason);
    }

    public boolean isSuccess() {
        return streamInfo != null;
    }

    public YtStreamInfoDto streamInfo() {
        return streamInfo;
    }

    public YtPlayabilityStatus status() {
        return status;
    }

    public String reason() {
        return reason == null ? "" : reason;
    }

    /** One-line summary for the failure trail surfaced to the caller on total exhaustion. */
    public String describe(String resolverName) {
        if (isSuccess()) {
            return resolverName + ": ok";
        }
        return reason() == null || reason().isBlank()
                ? resolverName + ": " + status
                : resolverName + ": " + status + " (" + reason() + ")";
    }
}
