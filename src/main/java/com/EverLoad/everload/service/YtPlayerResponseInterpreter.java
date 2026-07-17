package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.YtStreamInfoDto;
import com.fasterxml.jackson.databind.JsonNode;

import static com.EverLoad.everload.service.YtMusicJsonUtils.*;

/**
 * Turns a raw {@code /player} response into a {@link YtStreamResolution} —
 * shared by every {@link YtStreamResolver} that talks to InnerTube directly,
 * so "restricted / geo-blocked / deleted / age-gated" all collapse to the
 * same {@link YtPlayabilityStatus} + reason reading, and "no unsigned audio
 * format" is reported the same explicit way regardless of which client asked.
 */
final class YtPlayerResponseInterpreter {

    private YtPlayerResponseInterpreter() {}

    static YtStreamResolution interpret(JsonNode playerResponse, YtMusicClient usedClient, String resolverName) {
        YtPlayabilityStatus status = playabilityStatus(playerResponse);
        if (!status.isAttemptable()) {
            return YtStreamResolution.failure(status, playabilityReason(playerResponse));
        }

        JsonNode format = pickPlainAudioFormat(playerResponse);
        if (format == null) {
            // YouTube reported the video playable but every adaptiveFormat we got
            // back is signature-ciphered (no plain `url`) — this client identity
            // simply isn't trusted enough to skip cipher decoding; not the same
            // as "video unavailable", so it gets its own status.
            return YtStreamResolution.failure(YtPlayabilityStatus.OTHER,
                    "ningún formato de audio sin firmar disponible para " + usedClient.clientName());
        }

        String mimeType = textAt(format, "mimeType");
        String formatName = (mimeType != null && mimeType.contains("webm")) ? "webm" : "m4a";
        Long durationSeconds = longAt(playerResponse, "videoDetails", "lengthSeconds");

        YtStreamInfoDto info = YtStreamInfoDto.builder()
                .url(textAt(format, "url"))
                .format(formatName)
                .userAgent(usedClient.userAgent())
                .contentLength(longAt(format, "contentLength"))
                .durationSeconds(durationSeconds)
                .resolvedBy(resolverName)
                .build();
        return YtStreamResolution.success(info);
    }

    static YtPlayabilityStatus playabilityStatus(JsonNode playerResponse) {
        return YtPlayabilityStatus.fromRaw(textAt(playerResponse, "playabilityStatus", "status"));
    }

    /**
     * Human-readable reason YouTube gave for a non-OK status. Geo-blocking has
     * no dedicated status code — it always arrives here, as free text on an
     * UNPLAYABLE/LOGIN_REQUIRED result (e.g. "This video is not available in
     * your country" / "Sign in to confirm your age").
     */
    static String playabilityReason(JsonNode playerResponse) {
        String reason = textAt(playerResponse, "playabilityStatus", "reason");
        if (reason != null && !reason.isBlank()) {
            return reason;
        }
        String subReason = runsText(playerResponse, "playabilityStatus", "errorScreen",
                "playerErrorMessageRenderer", "subreason", "runs");
        if (subReason != null && !subReason.isBlank()) {
            return subReason;
        }
        String simpleReason = textAt(playerResponse, "playabilityStatus", "errorScreen",
                "playerErrorMessageRenderer", "subreason", "simpleText");
        return simpleReason != null ? simpleReason : "";
    }

    /**
     * First playable audio-only adaptive format that carries a plain
     * (non-signature-ciphered) {@code url} — preferring webm/opus over m4a/aac
     * for quality, exactly like {@code pick_plain_format} did. Formats whose
     * URL is locked behind {@code signatureCipher}/{@code cipher} are skipped
     * outright: decoding those requires running YouTube's obfuscated player
     * JS, which anonymous-mode deliberately does not do.
     */
    private static JsonNode pickPlainAudioFormat(JsonNode playerResponse) {
        JsonNode formats = at(playerResponse, "streamingData", "adaptiveFormats");
        if (!formats.isArray()) {
            return null;
        }
        JsonNode webm = null;
        JsonNode m4a = null;
        for (JsonNode format : formats) {
            String mimeType = textAt(format, "mimeType");
            String url = textAt(format, "url");
            if (mimeType == null || url == null || !mimeType.startsWith("audio/")) {
                continue;
            }
            if (mimeType.contains("webm") && webm == null) {
                webm = format;
            } else if (mimeType.contains("mp4") && m4a == null) {
                m4a = format;
            }
        }
        return webm != null ? webm : m4a;
    }
}
