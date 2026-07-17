package com.EverLoad.everload.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NasYtDlpServiceTest {

    @TempDir
    Path tempDir;

    private ExecutorService executor;
    private NasYtDlpService service;

    @BeforeEach
    void setUp() {
        service = new NasYtDlpService(
                mock(NasService.class),
                mock(DownloadHistoryService.class),
                mock(MusicService.class));
        executor = mock(ExecutorService.class);
        ReflectionTestUtils.setField(service, "executor", executor);
    }

    @Test
    void queueNormalizesDefaultsWithoutExecutingDownload() {
        String jobId = service.queue("video-id", "", 3L, null, "EXE");

        NasYtDlpService.YtDlpJob job = service.getJob(jobId);
        assertEquals("video-id", job.title);
        assertEquals("mp3", job.format);
        assertEquals("", job.subPath);
        assertEquals(NasYtDlpService.YtDlpJob.Status.QUEUED, job.status);
        verify(executor).submit(any(Runnable.class));
    }

    @Test
    void queueUrlAndActiveJobsExposeQueuedWork() {
        String jobId = service.queueUrl("https://example.test/video", null, 4L, "clips");

        NasYtDlpService.YtDlpJob job = service.getJob(jobId);
        List<NasYtDlpService.YtDlpJob> active = service.getActiveJobs();
        assertEquals("video", job.title);
        assertEquals("video", job.format);
        assertEquals("clips", job.subPath);
        assertEquals(jobId, active.get(0).jobId);
        assertNull(service.getJob("missing"));
        verify(executor, times(1)).submit(any(Runnable.class));
    }

    @Test
    void updateProgressIgnoresNoiseAndCapsPercentage() {
        NasYtDlpService.YtDlpJob job = new NasYtDlpService.YtDlpJob(
                "job", "video", "title", 1L, "", "mp3");

        ReflectionTestUtils.invokeMethod(service, "updateProgress", job, "download 55.5%");
        assertEquals(55, job.progress);
        ReflectionTestUtils.invokeMethod(service, "updateProgress", job, "download 99%");
        assertEquals(94, job.progress);
        ReflectionTestUtils.invokeMethod(service, "updateProgress", job, "no percentage");
        assertEquals(94, job.progress);
    }

    @Test
    void cleanupRemovesNestedTemporaryDirectory() throws Exception {
        Path nested = Files.createDirectories(tempDir.resolve("a/b"));
        Files.writeString(nested.resolve("track.tmp"), "data");

        ReflectionTestUtils.invokeMethod(service, "cleanup", tempDir.toString());

        assertFalse(Files.exists(tempDir));
    }
}
