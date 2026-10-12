package com.everload.everload.service;

import com.everload.everload.dto.AdminDashboardDto;
import com.everload.everload.model.Download;
import com.everload.everload.model.UserStatus;
import com.everload.everload.repository.DownloadRepository;
import com.everload.everload.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final DownloadRepository downloadRepository;
    private final UserRepository userRepository;
    private final SystemInfoService systemInfoService;

    public AdminDashboardDto getSummary() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDateTime todayStart = today.atStartOfDay();
        long downloadsToday = downloadRepository.countByCreatedAtGreaterThanEqual(todayStart);

        List<AdminDashboardDto.DailyDownloadDto> daily = java.util.stream.IntStream.rangeClosed(0, 6)
                .mapToObj(daysAgo -> {
                    LocalDate day = today.minusDays(6L - daysAgo);
                    return new AdminDashboardDto.DailyDownloadDto(
                            day.toString(),
                            downloadRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                    day.atStartOfDay(), day.plusDays(1).atStartOfDay()));
                })
                .toList();

        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        long heapMax = runtime.maxMemory();
        double heapPercent = heapMax > 0 ? Math.min(100d, heapUsed * 100d / heapMax) : 0d;
        Double cpuPercent = processCpuPercent();

        var systemInfo = systemInfoService.getInfo();
        File database = new File(systemInfo.getDbPath());
        File volume = database.getParentFile();
        long volumeTotal = volume != null && volume.exists() ? volume.getTotalSpace() : 0;
        long volumeUsable = volume != null && volume.exists() ? volume.getUsableSpace() : 0;

        List<AdminDashboardDto.RecentDownloadDto> recent = downloadRepository
                .findTop6ByOrderByCreatedAtDesc().stream()
                .map(this::toRecentDownload)
                .toList();

        return new AdminDashboardDto(
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.PENDING),
                downloadRepository.count(),
                downloadsToday,
                daily.stream().mapToLong(AdminDashboardDto.DailyDownloadDto::count).sum(),
                systemInfo.getDbSizeBytes(),
                volumeTotal,
                volumeUsable,
                cpuPercent,
                heapUsed,
                heapMax,
                heapPercent,
                daily,
                recent);
    }

    private Double processCpuPercent() {
        var operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        if (operatingSystem instanceof com.sun.management.OperatingSystemMXBean bean) {
            double load = bean.getProcessCpuLoad();
            if (load >= 0d) return Math.min(100d, load * 100d);
        }
        return null;
    }

    private AdminDashboardDto.RecentDownloadDto toRecentDownload(Download download) {
        return new AdminDashboardDto.RecentDownloadDto(
                download.getTitle(), download.getPlatform(), download.getUsername(),
                download.getCreatedAt() == null ? null : download.getCreatedAt().toString());
    }
}
