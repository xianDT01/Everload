package com.everload.everload.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaTextCleanerTest {

    @Test
    void cleansTermsUsedForLyricsLookup() {
        assertEquals("Song", MediaTextCleaner.cleanLyricsTerm("Song (Official Music Video)"));
        assertEquals("Song", MediaTextCleaner.cleanLyricsTerm("Song - Official Audio 2026"));
        assertEquals("Song", MediaTextCleaner.cleanLyricsTerm("Song [ft. Guest]"));
        assertEquals("Song Name", MediaTextCleaner.cleanLyricsTerm("  Song   Name  "));
    }

    @Test
    void cleansYoutubeTitlesWithoutRemovingWordsThatContainMarkers() {
        assertEquals("Song", MediaTextCleaner.cleanYoutubeTitle("Song (Official Music Video)"));
        assertEquals("Song", MediaTextCleaner.cleanYoutubeTitle("Song [HD]"));
        assertEquals("Shadow", MediaTextCleaner.cleanYoutubeTitle("Shadow"));
        assertEquals("Song", MediaTextCleaner.cleanYoutubeTitle("Song [Live at Home]"));
    }

    @Test
    void cleansYoutubeChannelSuffixes() {
        assertEquals("Artist", MediaTextCleaner.cleanYoutubeArtist("Artist - Topic"));
        assertEquals("Artist", MediaTextCleaner.cleanYoutubeArtist("Artist Official YouTube Channel"));
        assertEquals("Musicology", MediaTextCleaner.cleanYoutubeArtist("Musicology"));
    }
}
