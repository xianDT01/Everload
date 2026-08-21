package com.everload.everload.service;

import com.everload.everload.dto.YtAlbumDto;
import com.everload.everload.dto.YtArtistDto;
import com.everload.everload.dto.YtDiscoverHomeDto;
import com.everload.everload.dto.YtDiscoverItemDto;
import com.everload.everload.dto.YtTrackDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YtMusicDiscoverServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private YtMusicDiscoverService service;
    private YtMusicInnertubeClient client;

    @BeforeEach
    void setUp() {
        client = mock(YtMusicInnertubeClient.class);
        service = new YtMusicDiscoverService(client);
        ReflectionTestUtils.setField(service, "cacheTtlSeconds", 600L);
        ReflectionTestUtils.setField(service, "cacheMaxEntries", 100);
        service.init();
    }

    @Test
    void homeReleaseChartAndContinuationResponsesAreCached() {
        JsonNode empty = mapper.createObjectNode();
        when(client.browse("FEmusic_home")).thenReturn(empty);
        when(client.browse("FEmusic_new_releases")).thenReturn(empty);
        when(client.browse("FEmusic_charts")).thenReturn(empty);
        when(client.browseContinuation("next-page")).thenReturn(empty);

        assertSame(service.fetchHome(), service.fetchHome());
        assertSame(service.fetchNewReleases(), service.fetchNewReleases());
        assertSame(service.fetchCharts(), service.fetchCharts());
        assertSame(service.fetchContinuation("next-page"), service.fetchContinuation("next-page"));
        verify(client, times(1)).browse("FEmusic_home");
        verify(client, times(1)).browse("FEmusic_new_releases");
        verify(client, times(1)).browse("FEmusic_charts");
        verify(client, times(1)).browseContinuation("next-page");
    }

    @Test
    void moodsAndCategoryParseButtonsAndPlaylistTiles() throws Exception {
        when(client.browse("FEmusic_moods_and_genres")).thenReturn(json("""
                {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
                  {"sectionListRenderer":{"contents":[{"gridRenderer":{
                    "header":{"gridHeaderRenderer":{"title":{"runs":[{"text":"Focus"}]}}},
                    "items":[
                      {"musicNavigationButtonRenderer":{"buttonText":{"runs":[{"text":"Coding"}]},
                        "clickCommand":{"browseEndpoint":{"browseId":"FEmusic_focus","params":"p1"}}}},
                      {"ignoredRenderer":{}}
                    ]}}]}}}}]}}}
                """));
        when(client.browse("FEmusic_moods_and_genres_category", null)).thenReturn(json("""
                {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
                  {"sectionListRenderer":{"contents":[{"gridRenderer":{"items":[
                    {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Daily Mix"}]},
                      "navigationEndpoint":{"watchPlaylistEndpoint":{"playlistId":"PL123"}}}}
                  ]}}]}}}}]}}}
                """));

        YtDiscoverHomeDto moods = service.fetchMoods();
        YtDiscoverHomeDto category = service.fetchMoodCategory(" ", null);

        assertEquals("Focus", moods.getShelves().get(0).getTitle());
        assertEquals("Coding", moods.getShelves().get(0).getItems().get(0).getTitle());
        assertEquals("p1", moods.getShelves().get(0).getItems().get(0).getMoodParams());
        assertEquals(YtDiscoverItemDto.Type.PLAYLIST,
                category.getShelves().get(0).getItems().get(0).getType());
        assertEquals("PL123", category.getShelves().get(0).getItems().get(0).getPlaylistId());
    }

    @Test
    void tileParserRecognizesEverySupportedDestination() throws Exception {
        assertEquals(YtDiscoverItemDto.Type.SONG, tileWithEndpoint(
                "\"watchEndpoint\":{\"videoId\":\"vid-1\"}").getType());
        assertEquals(YtDiscoverItemDto.Type.PLAYLIST, tileWithEndpoint(
                "\"watchPlaylistEndpoint\":{\"playlistId\":\"PL1\"}").getType());
        assertEquals("PL2", tileWithEndpoint(
                "\"browseEndpoint\":{\"browseId\":\"VLPL2\"}").getPlaylistId());
        assertEquals(YtDiscoverItemDto.Type.ALBUM, tileWithEndpoint(
                "\"browseEndpoint\":{\"browseId\":\"MPREalbum\"}").getType());
        assertEquals(YtDiscoverItemDto.Type.ARTIST, tileWithEndpoint(
                "\"browseEndpoint\":{\"browseId\":\"UCartist\"}").getType());
        assertEquals(YtDiscoverItemDto.Type.MOOD, tileWithEndpoint(
                "\"browseEndpoint\":{\"browseId\":\"FEmusic_energy\"}").getType());
        assertNull(ReflectionTestUtils.invokeMethod(service, "parseTile", mapper.createObjectNode()));
    }

    @Test
    void albumPageParsesHeaderTracksAndCachesTheResult() throws Exception {
        JsonNode response = json("""
                {"header":{"musicDetailHeaderRenderer":{
                  "title":{"runs":[{"text":"Album One"}]},
                  "subtitle":{"runs":[{"text":"Album"},{"text":"Artist One"},{"text":"2024"}]},
                  "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"//cover"}]}}}
                }},"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
                  {"sectionListRenderer":{"contents":[{"musicShelfRenderer":{"contents":[
                    {"musicResponsiveListItemRenderer":{
                      "flexColumns":[
                        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                          {"text":"Song One","navigationEndpoint":{"watchEndpoint":{"videoId":"v1"}}}]}}},
                        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                          {"text":"Artist One","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist"}}}]}}},
                        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"3:05"}]}}}
                      ],"fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"3:06"}]}}}]
                    }}]}}]}}}}]}}}
                """
        );
        when(client.browse("MPREalbum")).thenReturn(response);

        YtAlbumDto album = service.fetchAlbum("MPREalbum");

        assertSame(album, service.fetchAlbum("MPREalbum"));
        assertSame(album.getTracks(), service.fetchAlbumTracks("MPREalbum"));
        assertEquals("Album One", album.getTitle());
        assertEquals("Artist One", album.getArtist());
        assertEquals("2024", album.getYear());
        assertEquals("//cover", album.getThumbnailUrl());
        assertEquals(186, album.getTracks().get(0).getDurationSeconds());
        verify(client, times(1)).browse("MPREalbum");
    }

    @Test
    void artistPageSeparatesAlbumsSinglesAndTopSongs() throws Exception {
        when(client.browse("UCartist")).thenReturn(json("""
                {"header":{"musicImmersiveHeaderRenderer":{
                  "title":{"runs":[{"text":"Artist One"}]},
                  "description":{"runs":[{"text":"Biography"}]},
                  "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"//artist"}]}}}
                }},"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
                  {"sectionListRenderer":{"contents":[
                    {"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":
                      {"title":{"runs":[{"text":"Albums"}]}}},"contents":[
                        {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Album A"}]},
                          "subtitle":{"runs":[{"text":"2024"}]},
                          "navigationEndpoint":{"browseEndpoint":{"browseId":"MPREA"}}}}
                      ]}},
                    {"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":
                      {"title":{"runs":[{"text":"Singles"}]}}},"contents":[
                        {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Single A"}]},
                          "navigationEndpoint":{"browseEndpoint":{"browseId":"MPRES"}}}}
                      ]}},
                    {"musicShelfRenderer":{"title":{"runs":[{"text":"Top songs"}]},"contents":[
                      {"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"top-1"},
                        "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                          {"text":"Top Track","navigationEndpoint":{"watchEndpoint":{"videoId":"top-1"}}}]}}}],
                        "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[
                          {"text":"4:10"}]}}}]}}
                    ]}}
                  ]}}}}]}}}
                """));

        YtArtistDto artist = service.fetchArtist("UCartist");

        assertEquals("Artist One", artist.getName());
        assertEquals("Biography", artist.getDescription());
        assertEquals("//artist", artist.getThumbnailUrl());
        assertEquals("Album A", artist.getAlbums().get(0).getTitle());
        assertEquals("Single A", artist.getSingles().get(0).getTitle());
        assertEquals("top-1", artist.getTopSongs().get(0).getVideoId());
        assertEquals(250, artist.getTopSongs().get(0).getDurationSeconds());
        assertSame(artist, service.fetchArtist("UCartist"));
        verify(client, times(1)).browse("UCartist");
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

    private YtDiscoverItemDto tileWithEndpoint(String endpoint) throws Exception {
        JsonNode tile = json("""
                {"musicTwoRowItemRenderer":{
                  "title":{"runs":[{"text":"Item"}]},
                  "subtitle":{"runs":[{"text":"Artist metadata"}]},
                  "navigationEndpoint":{%s}
                }}
                """.formatted(endpoint));
        YtDiscoverItemDto item = ReflectionTestUtils.invokeMethod(service, "parseTile", tile);
        assertNotNull(item);
        return item;
    }

    private JsonNode json(String value) throws Exception {
        return mapper.readTree(value);
    }
}
