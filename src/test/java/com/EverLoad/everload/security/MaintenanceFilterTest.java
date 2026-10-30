package com.everload.everload.security;

import com.everload.everload.service.MaintenanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaintenanceFilterTest {
    private final MaintenanceService maintenance = mock(MaintenanceService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final MaintenanceFilter filter = new MaintenanceFilter(maintenance, mapper);

    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test
    void serializesMultilineMessagesAndNeverCachesMaintenanceResponses() throws Exception {
        when(maintenance.isActive()).thenReturn(true);
        String message = "Line one\n\"Quoted\"\t\\path";
        when(maintenance.getMessage()).thenReturn(message);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request("/api/library/history"), response, chain);
        assertEquals(503, response.getStatus());
        assertEquals(message, mapper.readTree(response.getContentAsString()).path("message").asText());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("30", response.getHeader("Retry-After"));
        verifyNoInteractions(chain);
    }

    @Test
    void allowsStatusLoginAndAdminsButNotRegistrationOrStatusPrefixLookalikes() throws Exception {
        when(maintenance.isActive()).thenReturn(true);
        when(maintenance.getMessage()).thenReturn("Maintenance");
        for (String path : List.of("/api/maintenance/status", "/api/auth/login", "/api/auth/session/refresh", "/actuator/health")) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletRequest request = request(path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        }
        for (String path : List.of("/api/auth/register", "/api/maintenance/status/fake")) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request(path), response, mock(FilterChain.class));
            assertEquals(503, response.getStatus());
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "unused", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        MockHttpServletRequest request = request("/api/admin/maintenance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }
}
