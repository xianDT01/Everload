package com.everload.everload.controller;

import com.everload.everload.model.SpotifyResult;
import com.everload.everload.service.DownloadHistoryService;
import com.everload.everload.service.DownloadService;
import com.everload.everload.service.SpotifyService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpotifyControllerTest {

    @Test
    void playlistDownloadClassifiesSuccessfulMissingAndFailedTracks() {
        SpotifyService spotifyService = mock(SpotifyService.class);
        DownloadService downloadService = mock(DownloadService.class);
        DownloadHistoryService historyService = mock(DownloadHistoryService.class);
        SpotifyController controller = new SpotifyController(spotifyService, downloadService, historyService);
        List<SpotifyResult> tracks = List.of(
                new SpotifyResult("Downloaded", "https://youtube.test/watch?v=video-1"),
                new SpotifyResult("Missing", null),
                new SpotifyResult("Failed", "invalid-url"));
        when(spotifyService.extractPlaylistId("playlist-url")).thenReturn("playlist-id");
        when(spotifyService.getPlaylistTracks("playlist-id")).thenReturn(tracks);
        when(downloadService.downloadMusic("video-1", "mp3"))
                .thenReturn(ResponseEntity.ok(mock(FileSystemResource.class)));

        ResponseEntity<Map<String, Object>> response = controller.downloadSpotifyPlaylist(
                Map.of("url", "playlist-url"));

        assertEquals(List.of("Downloaded"), response.getBody().get("descargadas"));
        assertEquals(List.of("Missing"), response.getBody().get("noEncontradas"));
        assertEquals(List.of("Failed"), response.getBody().get("errores"));
        verify(historyService).recordDownload(any());
    }
}
