package com.everload.everload.util;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

public final class MediaTextCleaner {

    private static final Set<String> VIDEO_DECORATIONS = Set.of(
            "official music video", "official video", "official audio", "lyric video",
            "visualizer", "remaster", "remastered", "audio", "video", "explicit");
    private static final List<String> YOUTUBE_TITLE_MARKERS = List.of(
            "official music video", "video oficial", "audio oficial", "official audio",
            "lyric video", "visualizer", "official video", "4k", "hd", "hq");
    private static final List<String> CHANNEL_SUFFIXES = List.of(
            "official youtube channel", "youtube channel", "official channel", "channel");
    private static final List<String> ARTIST_SUFFIXES = List.of("topic", "official", "vevo", "music");

    private MediaTextCleaner() {
    }

    public static String cleanLyricsTerm(String value) {
        if (value == null) return "";
        String cleaned = removeDelimitedSegments(value, MediaTextCleaner::isVideoDecoration);
        cleaned = removeDashDecoration(cleaned);
        cleaned = removeDelimitedSegments(cleaned, MediaTextCleaner::isFeaturing);
        return collapseWhitespace(cleaned);
    }

    public static String cleanYoutubeTitle(String value) {
        if (value == null) return "";
        String cleaned = removeDelimitedSegments(value, MediaTextCleaner::isYoutubeTitleMarker);
        cleaned = removeKnownPhrases(cleaned, YOUTUBE_TITLE_MARKERS);
        cleaned = removeTrailingDelimitedSegment(cleaned);
        return collapseWhitespace(cleaned);
    }

    public static String cleanYoutubeArtist(String value) {
        if (value == null) return "";
        String cleaned = collapseWhitespace(value);
        cleaned = removeSuffix(cleaned, CHANNEL_SUFFIXES);
        cleaned = removeSuffix(cleaned, ARTIST_SUFFIXES);
        cleaned = cleaned.stripTrailing();
        if (cleaned.endsWith("-")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        return collapseWhitespace(cleaned);
    }

    private static String removeDashDecoration(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != '-') continue;
            String suffix = collapseWhitespace(value.substring(index + 1)).toLowerCase(Locale.ROOT);
            if (startsWithMarker(suffix, VIDEO_DECORATIONS)) return value.substring(0, index);
        }
        return value;
    }

    private static String removeKnownPhrases(String value, List<String> markers) {
        String cleaned = collapseWhitespace(value);
        for (String marker : markers) {
            int fromIndex = 0;
            while (fromIndex < cleaned.length()) {
                String lower = cleaned.toLowerCase(Locale.ROOT);
                int index = lower.indexOf(marker, fromIndex);
                if (index < 0) break;
                int end = index + marker.length();
                if (isWordBoundary(cleaned, index - 1) && isWordBoundary(cleaned, end)) {
                    cleaned = cleaned.substring(0, index) + cleaned.substring(end);
                    fromIndex = Math.max(0, index - 1);
                } else {
                    fromIndex = end;
                }
            }
        }
        return cleaned;
    }

    private static String removeSuffix(String value, List<String> suffixes) {
        String lower = value.toLowerCase(Locale.ROOT);
        for (String suffix : suffixes) {
            if (!lower.endsWith(suffix)) continue;
            int start = value.length() - suffix.length();
            if (start == 0 || isWordBoundary(value, start - 1)) return value.substring(0, start);
        }
        return value;
    }

    private static String removeDelimitedSegments(String value, Predicate<String> shouldRemove) {
        StringBuilder cleaned = new StringBuilder(value.length());
        int index = 0;
        while (index < value.length()) {
            char opening = value.charAt(index);
            char closing = closingDelimiter(opening);
            if (closing != 0) {
                int end = value.indexOf(closing, index + 1);
                if (end >= 0 && shouldRemove.test(value.substring(index + 1, end))) {
                    index = end + 1;
                    continue;
                }
            }
            cleaned.append(opening);
            index++;
        }
        return cleaned.toString();
    }

    private static String removeTrailingDelimitedSegment(String value) {
        String cleaned = value.stripTrailing();
        if (cleaned.isEmpty()) return cleaned;
        char closing = cleaned.charAt(cleaned.length() - 1);
        char opening = openingDelimiter(closing);
        if (opening == 0) return cleaned;
        int start = cleaned.lastIndexOf(opening);
        return start < 0 ? cleaned : cleaned.substring(0, start);
    }

    private static boolean isVideoDecoration(String value) {
        return VIDEO_DECORATIONS.contains(collapseWhitespace(value).toLowerCase(Locale.ROOT));
    }

    private static boolean isYoutubeTitleMarker(String value) {
        String normalized = collapseWhitespace(value).toLowerCase(Locale.ROOT);
        return YOUTUBE_TITLE_MARKERS.contains(normalized);
    }

    private static boolean isFeaturing(String value) {
        String normalized = collapseWhitespace(value).toLowerCase(Locale.ROOT);
        return normalized.startsWith("feat ") || normalized.startsWith("feat. ")
                || normalized.startsWith("ft ") || normalized.startsWith("ft. ");
    }

    private static boolean startsWithMarker(String value, Set<String> markers) {
        return markers.stream().anyMatch(marker -> value.equals(marker) || value.startsWith(marker + " "));
    }

    private static boolean isWordBoundary(String value, int index) {
        return index < 0 || index >= value.length() || !Character.isLetterOrDigit(value.charAt(index));
    }

    private static String collapseWhitespace(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current)) {
                pendingSpace = !normalized.isEmpty();
            } else {
                if (pendingSpace) normalized.append(' ');
                normalized.append(current);
                pendingSpace = false;
            }
        }
        return normalized.toString();
    }

    private static char closingDelimiter(char opening) {
        return switch (opening) {
            case '(' -> ')';
            case '[' -> ']';
            case '{' -> '}';
            default -> 0;
        };
    }

    private static char openingDelimiter(char closing) {
        return switch (closing) {
            case ')' -> '(';
            case ']' -> '[';
            case '}' -> '{';
            default -> 0;
        };
    }
}
