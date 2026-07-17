package com.EverLoad.everload.service;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YouTubeSearchServiceTest {

    @Test
    void searchMapsYtDlpOutputToCompatibleThumbnailResponse() throws Exception {
        YouTubeSearchService service = new YouTubeSearchService();
        ReflectionTestUtils.setField(service, "ytDlpPath", "yt-dlp-test");
        ScheduledExecutorService watchdog = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(watchdog).schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));
        ReflectionTestUtils.setField(service, "watchdog", watchdog);
        Process process = mock(Process.class);
        String output = "video123\tTrack title\tChannel name\t180\thttps://image.test/thumb.jpg\n";
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes()));
        when(process.waitFor()).thenReturn(0);
        when(process.isAlive()).thenReturn(false);

        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                ProcessBuilder.class,
                (builder, context) -> {
                    when(builder.redirectErrorStream(true)).thenReturn(builder);
                    when(builder.start()).thenReturn(process);
                })) {
            List<Map<String, Object>> results = service.search("track", 1);

            assertEquals(1, results.size());
            Map<?, ?> snippet = (Map<?, ?>) results.get(0).get("snippet");
            Map<?, ?> thumbnails = (Map<?, ?>) snippet.get("thumbnails");
            assertTrue(String.valueOf(((Map<?, ?>) thumbnails.get("default")).get("url")).contains("video123"));
            assertTrue(String.valueOf(((Map<?, ?>) thumbnails.get("medium")).get("url")).contains("mqdefault"));
            assertTrue(String.valueOf(((Map<?, ?>) thumbnails.get("high")).get("url")).contains("hqdefault"));
        }

        verify(future).cancel(false);
    }
}
