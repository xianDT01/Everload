package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.YtTrackDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class YtMusicDiscoverServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private YtMusicDiscoverService service;

    @BeforeEach
    void setUp() {
        service = new YtMusicDiscoverService(mock(YtMusicInnertubeClient.class));
    }

    @Test
    void albumRowUsesEmptyArtistWhenNeitherRowNorAlbumProvidesOne() throws Exception {
        JsonNode row = rowWithTitleAndUnknownColumn();

        YtTrackDto track = ReflectionTestUtils.invokeMethod(
                service, "parseAlbumRow", row, "Album", null, "cover.jpg");

        assertEquals("video-1", track.getVideoId());
        assertEquals("Track", track.getTitle());
        assertEquals("", track.getArtist());
        assertEquals(0, track.getArtists().size());
    }

    @Test
    void artistRowIgnoresUnknownColumn() throws Exception {
        YtTrackDto track = ReflectionTestUtils.invokeMethod(
                service, "parseArtistSongRow", rowWithTitleAndUnknownColumn());

        assertEquals("video-1", track.getVideoId());
        assertEquals("Track", track.getTitle());
    }

    @Test
    void albumRowPrefersArtistFromRow() throws Exception {
        JsonNode row = mapper.readTree("""
                {
                  "flexColumns": [
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                      {"text":"Track","navigationEndpoint":{"watchEndpoint":{"videoId":"video-1"}}}
                    ]}}},
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                      {"text":"Row Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UC123"}}}
                    ]}}}
                  ]
                }
                """);

        YtTrackDto track = ReflectionTestUtils.invokeMethod(
                service, "parseAlbumRow", row, "Album", "Album Artist", "cover.jpg");

        assertEquals("Row Artist", track.getArtist());
    }

    private JsonNode rowWithTitleAndUnknownColumn() throws Exception {
        return mapper.readTree("""
                {
                  "flexColumns": [
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                      {"text":"Track","navigationEndpoint":{"watchEndpoint":{"videoId":"video-1"}}}
                    ]}}},
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                      {"text":"miscellaneous"}
                    ]}}}
                  ]
                }
                """);
    }
}
