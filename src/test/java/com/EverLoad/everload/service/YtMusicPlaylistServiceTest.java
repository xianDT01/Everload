package com.everload.everload.service;

import com.everload.everload.dto.YtPlaylistSummaryDto;
import com.everload.everload.dto.YtTrackDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YtMusicPlaylistServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private YtMusicInnertubeClient client;
    private YtMusicPlaylistService service;

    @BeforeEach
    void setUp() {
        client = mock(YtMusicInnertubeClient.class);
        service = new YtMusicPlaylistService(client);
        ReflectionTestUtils.setField(service, "cacheTtlSeconds", 600L);
        ReflectionTestUtils.setField(service, "cacheMaxEntries", 200);
        service.init();
    }

    @Test
    void summaryReadsDirectHeaderNormalizesThumbnailAndCaches() throws Exception {
        JsonNode response = mapper.readTree("""
                {"header":{"musicResponsiveHeaderRenderer":{
                  "title":{"runs":[{"text":"Road trip"}]},
                  "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
                    {"url":"//small.jpg","width":120},
                    {"url":"https://large.jpg","width":640}
                  ]}}}
                }}}
                """);
        when(client.browse("VLPL123")).thenReturn(response);

        YtPlaylistSummaryDto first = service.fetchPlaylistSummary("PL123");

        assertEquals("PL123", first.getPlaylistId());
        assertEquals("Road trip", first.getTitle());
        assertEquals("https://large.jpg", first.getThumbnailUrl());
        assertEquals(first, service.fetchPlaylistSummary("PL123"));
        verify(client).browse("VLPL123");
    }

    @Test
    void summaryFindsNestedSimpleHeaderAndKeepsExistingVlPrefix() throws Exception {
        JsonNode response = mapper.readTree("""
                {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
                  "sectionListRenderer":{"contents":[{"musicDetailHeaderRenderer":{
                    "title":{"simpleText":"Nested playlist"},
                    "thumbnail":{"thumbnails":[{"url":"//nested.jpg","width":320}]}
                  }}]}
                }}}]}}}
                """);
        when(client.browse("VLREADY")).thenReturn(response);

        YtPlaylistSummaryDto summary = service.fetchPlaylistSummary("VLREADY");

        assertEquals("Nested playlist", summary.getTitle());
        assertEquals("//nested.jpg", summary.getThumbnailUrl());
        verify(client).browse("VLREADY");
    }

    @Test
    void entriesWalkInitialShelfAndContinuationDeduplicatingTracks() throws Exception {
        JsonNode initial = mapper.readTree("""
                {"contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{
                  "sectionListRenderer":{"contents":[{"musicPlaylistShelfRenderer":{
                    "contents":[%s],
                    "continuations":[{"nextContinuationData":{"continuation":"next-1"}}]
                  }}]}
                }}}}
                """.formatted(row("track-1", true, "3:10", "Album one", "MPRE-one")));
        JsonNode continuation = mapper.readTree("""
                {"continuationContents":{"musicPlaylistShelfContinuation":{
                  "contents":[%s,%s]
                }}}
                """.formatted(
                row("track-1", true, "3:10", "Album one", "MPRE-one"),
                row("track-2", false, "4:02", "", null)));
        when(client.browse("VLMIX")).thenReturn(initial);
        when(client.browseContinuation("next-1")).thenReturn(continuation);

        List<YtTrackDto> tracks = service.getPlaylistEntries("MIX");

        assertEquals(List.of("track-1", "track-2"),
                tracks.stream().map(YtTrackDto::getVideoId).toList());
        assertEquals("ytmusic:album:MPRE-one", tracks.get(0).getAlbumId());
        assertEquals(190, tracks.get(0).getDurationSeconds());
        assertEquals("", tracks.get(1).getAlbum());
        assertEquals(242, tracks.get(1).getDurationSeconds());
        assertEquals(tracks, service.getPlaylistEntries("MIX"));
        verify(client).browse("VLMIX");
        verify(client).browseContinuation("next-1");
    }

    private String row(String videoId, boolean playlistItemId, String duration,
                       String album, String albumBrowseId) {
        String videoSource = playlistItemId
                ? "\"playlistItemData\":{\"videoId\":\"" + videoId + "\"},"
                : "";
        String titleEndpoint = playlistItemId ? "" : "\"navigationEndpoint\":{\"watchEndpoint\":{\"videoId\":\""
                + videoId + "\"}},";
        String albumEndpoint = albumBrowseId == null ? "" :
                ",\"navigationEndpoint\":{\"browseEndpoint\":{\"browseId\":\"" + albumBrowseId + "\"}}";
        return """
                {"musicResponsiveListItemRenderer":{
                  %s
                  "flexColumns":[
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{%s"text":"Title %s"}]}}},
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist %s"}]}}},
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"%s"%s}]}}}
                  ],
                  "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{
                    "text":{"runs":[{"text":"%s"}]}
                  }}]
                }}
                """.formatted(videoSource, titleEndpoint, videoId, videoId, album, albumEndpoint, duration);
    }
}
