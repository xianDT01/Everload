package com.everload.everload.controller;

import com.everload.everload.dto.AdminDashboardDto;
import com.everload.everload.service.AdminDashboardService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminDashboardControllerTest {

    @Test
    void summaryReturnsServiceDashboard() {
        AdminDashboardService service = mock(AdminDashboardService.class);
        AdminDashboardDto expected = new AdminDashboardDto(
                3, 2, 1, 10, 2, 8, 1024, 4096, 2048, 12d,
                512, 2048, 25d, List.of(), List.of());
        when(service.getSummary()).thenReturn(expected);

        assertSame(expected, new AdminDashboardController(service).summary());
    }
}
