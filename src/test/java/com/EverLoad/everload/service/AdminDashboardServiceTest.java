package com.everload.everload.service;

import com.everload.everload.dto.SystemInfoDto;
import com.everload.everload.repository.DownloadRepository;
import com.everload.everload.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminDashboardServiceTest {

    @TempDir
    Path tempDir;

    private DownloadRepository downloadRepository;
    private UserRepository userRepository;
    private SystemInfoService systemInfoService;
    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        downloadRepository = mock(DownloadRepository.class);
        userRepository = mock(UserRepository.class);
        systemInfoService = mock(SystemInfoService.class);
        when(systemInfoService.getInfo()).thenReturn(SystemInfoDto.builder()
                .dbPath(tempDir.resolve("everload-db.mv.db").toString())
                .dbSizeBytes(4096)
                .build());
        service = new AdminDashboardService(downloadRepository, userRepository, systemInfoService);
    }

    @Test
    void summaryProvidesSevenDaysAndAggregatedCounts() {
        when(downloadRepository.count()).thenReturn(12L);
        when(userRepository.count()).thenReturn(5L);
        when(downloadRepository.countByCreatedAtGreaterThanEqual(org.mockito.ArgumentMatchers.any()))
                .thenReturn(2L);

        var summary = service.getSummary();

        assertEquals(12, summary.totalDownloads());
        assertEquals(5, summary.totalUsers());
        assertEquals(2, summary.downloadsToday());
        assertEquals(7, summary.downloadsByDay().size());
        assertEquals(4096, summary.databaseSizeBytes());
        assertNotNull(summary.jvmHeapMaxBytes());
    }
}
