package com.everload.everload.service;

import com.everload.everload.dto.YtTrackDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YtMusicSearchServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private YtMusicInnertubeClient client;
    private YtMusicSearchService service;

    @BeforeEach
    void setUp() {
        client = mock(YtMusicInnertubeClient.class);
        service = new YtMusicSearchService(client);
        ReflectionTestUtils.setField(service, "cacheTtlSeconds", 600L);
        ReflectionTestUtils.setField(service, "cacheMaxEntries", 200);
        service.init();
    }

    @Test
    void suggestionsRejectBlankInputWithoutCallingClient() {
        assertEquals(List.of(), service.suggestions("  "));
        assertEquals(List.of(), service.suggestions(null));
        verify(client, never()).searchSuggestions(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void suggestionsFlattenRunsUseQueryFallbackDeduplicateAndCache() throws Exception {
        JsonNode response = mapper.readTree("""
                {
                  "contents": [
                    {"searchSuggestionRenderer":{"suggestion":{"runs":[
                      {"text":"  Daft  "},{"text":"Punk  "}
                    ]}}},
                    {"nested":{"searchSuggestionRenderer":{"navigationEndpoint":{"searchEndpoint":{
                      "query":"daft punk"
                    }}}}},
                    {"searchSuggestionRenderer":{"navigationEndpoint":{"searchEndpoint":{
                      "query":"Discovery"
                    }}}}
                  ]
                }
                """);
        when(client.searchSuggestions("Daft")).thenReturn(response);

        assertEquals(List.of("Daft Punk", "Discovery"), service.suggestions(" Daft "));
        assertEquals(List.of("Daft Punk", "Discovery"), service.suggestions("DAFT"));
        verify(client).searchSuggestions("Daft");
    }

    @Test
    void searchMergesTopSongsAndVideosInOrderWithoutDuplicatesAndCaches() throws Exception {
        when(client.search(" Mix ", null)).thenReturn(searchRows("top-a", "top-b"));
        when(client.search(" Mix ", YtMusicInnertubeClient.SONGS_FILTER))
                .thenReturn(searchRows("top-b", "song-c"));
        when(client.search(" Mix ", YtMusicInnertubeClient.VIDEOS_FILTER))
                .thenReturn(searchRows("video-d", "top-a"));

        List<YtTrackDto> tracks = service.searchTracks(" Mix ");

        assertEquals(List.of("top-a", "top-b", "video-d", "song-c"),
                tracks.stream().map(YtTrackDto::getVideoId).toList());
        assertEquals("Artist top-a", tracks.get(0).getArtist());
        assertEquals("Album top-a", tracks.get(0).getAlbum());
        assertEquals(185, tracks.get(0).getDurationSeconds());

        assertEquals(tracks, service.searchTracks("MIX"));
        verify(client).search(" Mix ", null);
        verify(client).search(" Mix ", YtMusicInnertubeClient.SONGS_FILTER);
        verify(client).search(" Mix ", YtMusicInnertubeClient.VIDEOS_FILTER);
    }

    @Test
    void walkTracksParsesCardAndPlaylistRowsAndDropsUnsupportedEntries() throws Exception {
        List<YtTrackDto> tracks = service.walkTracks(mapper.readTree("""
                {
                  "contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
                    "sectionListRenderer":{"contents":[
                      {"musicCardShelfRenderer":{
                        "onTap":{"watchEndpoint":{"videoId":"card-1",
                          "watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{
                            "musicVideoType":"MUSIC_VIDEO_TYPE_ATV"
                          }}}},
                        "title":{"runs":[{"text":"Card title"}]},
                        "subtitle":{"runs":[
                          {"text":"Song"},{"text":", "},{"text":"Card artist"},
                          {"text":", "},{"text":"Card album"}
                        ]}
                      }},
                      {"musicShelfRenderer":{"contents":[
                        {"musicResponsiveListItemRenderer":{
                          "musicVideoType":"MUSIC_VIDEO_TYPE_ATV",
                          "playlistItemData":{"videoId":"playlist-1"},
                          "flexColumns":[
                            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Playlist title"}]}}},
                            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Playlist artist"}]}}},
                            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{
                              "text":"Playlist album","navigationEndpoint":{"browseEndpoint":{"browseId":"MPRE123"}}
                            }]}}}
                          ],
                          "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{
                            "text":{"runs":[{"text":"4:05"}]}
                          }}]
                        }},
                        {"musicResponsiveListItemRenderer":{
                          "musicVideoType":"MUSIC_VIDEO_TYPE_PODCAST",
                          "playlistItemData":{"videoId":"ignored"}
                        }}
                      ]}}
                    ]}
                  }}}]}}}
                }
                """));

        assertEquals(List.of("card-1", "playlist-1"),
                tracks.stream().map(YtTrackDto::getVideoId).toList());
        assertEquals("Card artist", tracks.get(0).getArtist());
        assertEquals("Card album", tracks.get(0).getAlbum());
        assertEquals("ytmusic:album:MPRE123", tracks.get(1).getAlbumId());
        assertEquals(245, tracks.get(1).getDurationSeconds());
    }

    @Test
    void resolveArtistFindsFirstChannelIdAndHandlesMissingInput() throws Exception {
        JsonNode response = mapper.readTree("""
                {"items":[
                  {"browseEndpoint":{"browseId":"MPRE-album"}},
                  {"nested":{"browseEndpoint":{"browseId":"UC-channel"}}},
                  {"browseEndpoint":{"browseId":"UC-later"}}
                ]}
                """);
        when(client.search("Artist", YtMusicInnertubeClient.ARTISTS_FILTER)).thenReturn(response);

        assertEquals("UC-channel", service.resolveArtistChannelId("Artist"));
        assertNull(service.resolveArtistChannelId(" "));
        assertNull(service.resolveArtistChannelId(null));
    }

    private JsonNode searchRows(String... videoIds) throws Exception {
        StringBuilder items = new StringBuilder();
        for (String videoId : videoIds) {
            if (!items.isEmpty()) items.append(',');
            items.append("""
                    {"musicResponsiveListItemRenderer":{
                      "musicVideoType":"MUSIC_VIDEO_TYPE_ATV",
                      "playlistItemData":{"videoId":"%1$s"},
                      "flexColumns":[
                        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Title %1$s"}]}}},
                        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                          {"text":"Artist %1$s"},{"text":", "},{"text":"Album %1$s"},
                          {"text":", "},{"text":"3:05"}
                        ]}}}
                      ]
                    }}
                    """.formatted(videoId));
        }
        return mapper.readTree("""
                {"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
                  "sectionListRenderer":{"contents":[{"musicShelfRenderer":{"contents":[%s]}}]}
                }}}]}}}
                """.formatted(items));
    }
}
