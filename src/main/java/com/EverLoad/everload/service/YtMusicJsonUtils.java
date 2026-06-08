package com.EverLoad.everload.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared JSON tree-walking helpers for YouTube Music's polymorphic InnerTube
 * responses. Every lookup iterates and dispatches on the renderer key /
 * endpoint type rather than indexing positionally — YT reorders columns and
 * shelves across A/B tests, and a positional dive silently drops rows the
 * moment that happens.
 */
final class YtMusicJsonUtils {

    private YtMusicJsonUtils() {}

    private static final Set<String> SEPARATORS = Set.of(" • ", " & ", ", ");
    private static final Pattern SIZE_SUFFIX = Pattern.compile("=w\\d");

    /** Joins every {@code text} fragment under {@code runs} at the given path; empty/missing → null. */
    static String runsText(JsonNode node, String... path) {
        JsonNode runs = at(node, path);
        if (!runs.isArray() || runs.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode run : runs) {
            JsonNode text = run.get("text");
            if (text != null && text.isTextual()) {
                sb.append(text.asText());
            }
        }
        String joined = sb.toString();
        return joined.isEmpty() ? null : joined;
    }

    /** Navigate a chain of object/array keys, tolerating missing nodes at any step (returns MissingNode). */
    static JsonNode at(JsonNode node, String... path) {
        JsonNode cur = node;
        for (String key : path) {
            if (cur == null || cur.isMissingNode()) {
                return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
            }
            if (key.chars().allMatch(Character::isDigit)) {
                cur = cur.path(Integer.parseInt(key));
            } else {
                cur = cur.path(key);
            }
        }
        return cur == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : cur;
    }

    static String textAt(JsonNode node, String... path) {
        JsonNode v = at(node, path);
        return v.isTextual() ? v.asText() : null;
    }

    /** First direct-child value whose key ends with {@code suffix} (e.g. locating any {@code *HeaderRenderer}). */
    static JsonNode firstKeyEndingWith(JsonNode node, String suffix) {
        if (node == null || !node.isObject()) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (entry.getKey().endsWith(suffix)) {
                return entry.getValue();
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    /** Numeric value at path, accepting both JSON numbers and YouTube's stringified-number fields; absent → null. */
    static Long longAt(JsonNode node, String... path) {
        JsonNode v = at(node, path);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.asLong();
        }
        if (v.isTextual()) {
            try {
                return Long.parseLong(v.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static boolean isSeparator(String s) {
        return SEPARATORS.contains(s);
    }

    /** All non-separator run texts in a flexColumn at the given column index. */
    static List<String> pickAllRuns(JsonNode row, int col) {
        List<String> out = new ArrayList<>();
        JsonNode runs = at(row, "flexColumns", String.valueOf(col),
                "musicResponsiveListItemFlexColumnRenderer", "text", "runs");
        if (runs.isArray()) {
            for (JsonNode run : runs) {
                JsonNode text = run.get("text");
                if (text != null && text.isTextual() && !isSeparator(text.asText())) {
                    out.add(text.asText());
                }
            }
        }
        return out;
    }

    static String pickRun(JsonNode row, int col, int run) {
        String t = textAt(row, "flexColumns", String.valueOf(col),
                "musicResponsiveListItemFlexColumnRenderer", "text", "runs", String.valueOf(run), "text");
        return t == null ? "" : t;
    }

    /** Best (largest) thumbnail URL from a {@code thumbnails[]} array; null if absent. */
    static String bestThumbnailUrl(JsonNode thumbnailsArray) {
        if (!thumbnailsArray.isArray() || thumbnailsArray.isEmpty()) {
            return null;
        }
        JsonNode best = null;
        long bestWidth = -1;
        for (JsonNode t : thumbnailsArray) {
            long w = t.path("width").asLong(0);
            if (w > bestWidth) {
                bestWidth = w;
                best = t;
            }
        }
        if (best == null) {
            return null;
        }
        JsonNode url = best.get("url");
        return url != null && url.isTextual() ? url.asText() : null;
    }

    static String bestThumbnailAt(JsonNode node, String... path) {
        JsonNode arr = at(node, path);
        return arr.isArray() ? bestThumbnailUrl(arr) : null;
    }

    /**
     * Rewrites photo-CDN URLs ending in a {@code =wNNN[-hNNN-...]} size
     * suffix to a larger fixed size; leaves mixart/query-string/token-style
     * URLs untouched since appending the suffix there 404s.
     */
    static String normalizeThumbnail(String url) {
        if (url == null) {
            return null;
        }
        int idx = url.lastIndexOf("=w");
        if (idx >= 0 && idx + 2 < url.length() && Character.isDigit(url.charAt(idx + 2))) {
            return url.substring(0, idx) + "=w544-h544-l90-rj";
        }
        return url;
    }

    /** "mm:ss" / "h:mm:ss" → total seconds; null/unparseable → 0. */
    static int parseMmSs(String s) {
        if (s == null) {
            return 0;
        }
        String[] parts = s.trim().split(":");
        if (parts.length == 0 || parts.length > 3) {
            return 0;
        }
        try {
            int secs = Integer.parseInt(parts[parts.length - 1]);
            int mins = parts.length >= 2 ? Integer.parseInt(parts[parts.length - 2]) : 0;
            int hours = parts.length == 3 ? Integer.parseInt(parts[0]) : 0;
            return hours * 3600 + mins * 60 + secs;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static final HexFormat HEX = HexFormat.of();

    static String encodeUrlTag(String url) {
        return "urlhex_" + HEX.formatHex(url.getBytes(StandardCharsets.UTF_8));
    }

    /** Synthesizes a stable album id when YT doesn't hand us a real MPRE browse id. */
    static String synthesizeAlbumId(String album, String artist) {
        if (album == null || album.isBlank()) {
            return "ytmusic:album:singles";
        }
        String key = album.toLowerCase();
        if (artist != null && !artist.isBlank()) {
            key = key + "|" + artist.toLowerCase();
        }
        return "ytmusic:album:" + HEX.formatHex(key.getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-1 hex digest, used by {@code sapisidHash} equivalents elsewhere — kept here as the one shared impl. */
    static String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return HEX.formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 no disponible", e);
        }
    }

    /** First {@code nextContinuationData.continuation} token found anywhere under {@code continuations[]}. */
    static String firstContinuation(JsonNode continuationsArray) {
        if (!continuationsArray.isArray()) {
            return null;
        }
        for (JsonNode c : continuationsArray) {
            String token = textAt(c, "nextContinuationData", "continuation");
            if (token != null) {
                return token;
            }
        }
        return null;
    }

    /** musicVideoType anywhere in the subtree — depth-first, first hit wins. */
    static String findMusicVideoType(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            JsonNode mvt = node.get("musicVideoType");
            if (mvt != null && mvt.isTextual()) {
                return mvt.asText();
            }
            for (JsonNode child : node) {
                String found = findMusicVideoType(child);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findMusicVideoType(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** True when a {@code musicVideoType} implies the row carries an album field (vs. a view-count slot). */
    static boolean musicVideoTypeHasAlbum(String mvt) {
        return "MUSIC_VIDEO_TYPE_ATV".equals(mvt) || "MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC".equals(mvt);
    }

    static boolean isPlayableMusicVideoType(String mvt) {
        return "MUSIC_VIDEO_TYPE_ATV".equals(mvt) || "MUSIC_VIDEO_TYPE_OMV".equals(mvt)
                || "MUSIC_VIDEO_TYPE_UGC".equals(mvt) || "MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC".equals(mvt);
    }
}
