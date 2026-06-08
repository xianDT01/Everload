package com.EverLoad.everload.service;

/**
 * One InnerTube client identity tuple — the (clientName, clientVersion,
 * userAgent, ...) combination YouTube's own apps send. The stream-resolution
 * fallback chain iterates a short list of these looking for one whose
 * {@code /player} response carries plain (non-signature-cipher) URLs.
 *
 * These are the same factual identifiers documented by public
 * reverse-engineering references (NewPipe, yt-dlp): nothing here is secret,
 * and none of them carry or require any user credential.
 */
public record YtMusicClient(
        String clientName,
        String clientVersion,
        /** Numeric id sent as the {@code X-YouTube-Client-Name} header. */
        String clientId,
        String userAgent,
        String osName,
        String osVersion,
        String deviceMake,
        String deviceModel,
        Integer androidSdkVersion,
        boolean loginSupported,
        boolean isEmbedded
) {

    public static final String ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com";

    private static final String USER_AGENT_WEB =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

    public static final YtMusicClient WEB_REMIX = new YtMusicClient(
            "WEB_REMIX", "1.20260213.01.00", "67", USER_AGENT_WEB,
            "", "", "", "", null, true, false
    );

    public static final YtMusicClient ANDROID_VR_1_43_32 = new YtMusicClient(
            "ANDROID_VR", "1.43.32", "28",
            "com.google.android.apps.youtube.vr.oculus/1.43.32 "
                    + "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)",
            "Android", "12", "Oculus", "Quest 3", 32, false, false
    );

    public static final YtMusicClient ANDROID_VR_1_61_48 = new YtMusicClient(
            "ANDROID_VR", "1.61.48", "28",
            "com.google.android.apps.youtube.vr.oculus/1.61.48 "
                    + "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            "Android", "12", "Oculus", "Quest 3", 32, false, false
    );

    /** Used for browse/search and as the first attempt in the player fallback chain. */
    public static final YtMusicClient MAIN_CLIENT = WEB_REMIX;

    /**
     * Player fallback chain, tried after the primary ANDROID_VR + content-PO-token
     * path. Kept narrow on purpose — iOS/iPadOS variants return URLs that are
     * rate-limited to the first ~1 MiB, so chunked Range fetches 403 mid-playback
     * even when {@code /player} reports OK. Worse than failing fast.
     */
    public static final YtMusicClient[] STREAM_FALLBACK_CLIENTS = {
            ANDROID_VR_1_43_32,
            ANDROID_VR_1_61_48,
    };
}
