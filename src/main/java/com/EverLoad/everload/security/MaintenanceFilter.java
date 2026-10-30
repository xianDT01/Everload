package com.everload.everload.security;

import com.everload.everload.service.MaintenanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Intercepts all /api/** requests while maintenance mode is active.
 *
 * <p>Exemptions (always pass-through):
 * <ul>
 *   <li>Login, logout and session refresh remain accessible; registration is blocked</li>
 *   <li>/api/maintenance/status — public status check for the Angular app</li>
 *   <li>ADMIN-role requests — administrators can work normally</li>
 *   <li>Non-API paths — Angular static assets are never blocked</li>
 * </ul>
 *
 * <p>This filter runs AFTER {@link JwtAuthenticationFilter}, so the
 * SecurityContext is populated and role checks work correctly.
 */
@Component
@RequiredArgsConstructor
public class MaintenanceFilter extends OncePerRequestFilter {

    private final MaintenanceService maintenanceService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!maintenanceService.isActive()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getServletPath();

        // Always allow: auth endpoints, public status check, non-API paths
        if (!path.startsWith("/api/")
                || path.equals("/api/auth/login") || path.equals("/api/auth/logout")
                || path.equals("/api/auth/refresh") || path.equals("/api/auth/session/refresh")
                || path.equals("/api/maintenance/status")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Always allow admins (JWT already validated by JwtAuthenticationFilter)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream()
                       .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            filterChain.doFilter(request, response);
            return;
        }

        // Return 503 Service Unavailable with JSON payload
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", "30");
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "maintenance", true, "message", maintenanceService.getMessage()));
    }
}
