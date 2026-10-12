package com.everload.everload.dto;

import java.util.List;

public record AdminDashboardDto(
        long totalUsers,
        long activeUsers,
        long pendingUsers,
        long totalDownloads,
        long downloadsToday,
        long downloadsLast7Days,
        long databaseSizeBytes,
        long volumeTotalBytes,
        long volumeUsableBytes,
        Double processCpuPercent,
        long jvmHeapUsedBytes,
        long jvmHeapMaxBytes,
        double jvmHeapPercent,
        List<DailyDownloadDto> downloadsByDay,
        List<RecentDownloadDto> recentDownloads
) {
    public record DailyDownloadDto(String day, long count) {}
    public record RecentDownloadDto(String title, String platform, String username, String createdAt) {}
}
