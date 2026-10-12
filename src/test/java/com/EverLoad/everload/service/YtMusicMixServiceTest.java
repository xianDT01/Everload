package com.everload.everload.service;

import com.everload.everload.dto.YtTrackDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YtMusicMixServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private YtMusicInnertubeClient client;
    private YtMusicMixService service;

    @BeforeEach
    void setUp() {
        client = mock(YtMusicInnertubeClient.class);
        service = new YtMusicMixService(client);
    }

    @Test
    void startMixBuildsExpectedRequestAndParsesDeduplicatedTracks() throws Exception {
        JsonNode response = mapper.readTree("""
                {"contents":{"singleColumnMusicWatchNextResultsRenderer":{"tabbedRenderer":{
                  "watchNextTabbedResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
                    "musicQueueRenderer":{"content":{"playlistPanelRenderer":{"contents":[
                      %s,
                      %s,
                      {"playlistPanelVideoRenderer":{"title":{"runs":[{"text":"Missing id"}]}}},
                      {"otherRenderer":{}},
                      %s
                    ]}}
                  }}}}]}
                }}}}
                """.formatted(row("seed", "Seed song", "Artist", "Album", "3:15", "https://img.test/cover=w120-h120"),
                row("next", "Next song", "Next artist", "Next album", "1:02:03", "https://img.test/next"),
                row("seed", "Duplicate", "Artist", "Album", "3:15", null)));
        Map<String, Object> baseBody = Map.of("context", Map.of("client", "test"));
        when(client.baseBodyFor(YtMusicClient.MAIN_CLIENT)).thenReturn(baseBody);
        when(client.next(anyMap())).thenReturn(response);

        List<YtTrackDto> tracks = service.startMix("seed");

        assertEquals(List.of("seed", "next"), tracks.stream().map(YtTrackDto::getVideoId).toList());
        assertEquals("Seed song", tracks.get(0).getTitle());
        assertEquals("Artist", tracks.get(0).getArtist());
        assertEquals(List.of("Artist"), tracks.get(0).getArtists());
        assertEquals("Album", tracks.get(0).getAlbum());
        assertEquals(195, tracks.get(0).getDurationSeconds());
        assertEquals("https://img.test/cover=w544-h544-l90-rj", tracks.get(0).getThumbnailUrl());
        assertEquals("Next album", tracks.get(1).getAlbum());
        assertEquals(3723, tracks.get(1).getDurationSeconds());

        var request = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(client).next(request.capture());
        assertEquals("seed", request.getValue().get("videoId"));
        assertEquals("RDAMVMseed", request.getValue().get("playlistId"));
        assertEquals(true, request.getValue().get("isAudioOnly"));
        assertEquals("test", ((Map<?, ?>) request.getValue().get("context")).get("client"));
    }

    @Test
    void acceptsNavigationEndpointVideoIdsAndRowsWithoutAlbumOrDuration() throws Exception {
        JsonNode response = mapper.readTree("""
                {"contents":{"singleColumnMusicWatchNextResultsRenderer":{"tabbedRenderer":{
                  "watchNextTabbedResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
                    "musicQueueRenderer":{"content":{"playlistPanelRenderer":{"contents":[
                      {"playlistPanelVideoRenderer":{
                        "navigationEndpoint":{"watchEndpoint":{"videoId":"endpoint-id"}},
                        "title":{"runs":[{"text":"No byline"}]},
                        "longBylineText":{"runs":[{"text":"Artist only"}]}
                      }}
                    ]}}
                  }}}}]}
                }}}}
                """);
        when(client.baseBodyFor(YtMusicClient.MAIN_CLIENT)).thenReturn(Map.of());
        when(client.next(anyMap())).thenReturn(response);

        List<YtTrackDto> tracks = service.startMix("seed");

        assertEquals(1, tracks.size());
        assertEquals("endpoint-id", tracks.get(0).getVideoId());
        assertEquals("", tracks.get(0).getAlbum());
        assertEquals("Artist only", tracks.get(0).getArtist());
        assertEquals(0, tracks.get(0).getDurationSeconds());
        assertNull(tracks.get(0).getThumbnailUrl());
    }

    @Test
    void returnsEmptyListWhenResponseHasNoQueueOrIsNotAnArray() throws Exception {
        when(client.baseBodyFor(YtMusicClient.MAIN_CLIENT)).thenReturn(Map.of());
        when(client.next(anyMap())).thenReturn(mapper.readTree("{}"));

        assertEquals(List.of(), service.startMix("seed"));
    }

    private String row(String videoId, String title, String artist, String album,
                       String duration, String thumbnailUrl) {
        String thumbnail = thumbnailUrl == null ? "" : "\"thumbnail\":{\"thumbnails\":["
                + "{\"url\":\"" + thumbnailUrl + "\",\"width\":120}]},";
        return """
                {"playlistPanelVideoRenderer":{
                  "videoId":"%s",
                  "title":{"runs":[{"text":"%s"}]},
                  %s
                  "longBylineText":{"runs":[
                    {"text":"%s"},{"text":" • "},{"text":"%s"},{"text":" • "},{"text":"%s"}
                  ]}
                }}
                """.formatted(videoId, title, thumbnail, artist, album, duration);
    }
}
