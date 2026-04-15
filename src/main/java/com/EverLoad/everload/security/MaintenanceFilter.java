package com.EverLoad.everload.security;

import com.EverLoad.everload.service.MaintenanceService;
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

/**
 * Intercepts all /api/** requests while maintenance mode is active.
 *
 * <p>Exemptions (always pass-through):
 * <ul>
 *   <li>/api/auth/** — login/register remains accessible</li>
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
                || path.startsWith("/api/auth/")
                || path.startsWith("/api/maintenance/status")) {
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
        String escapedMessage = maintenanceService.getMessage()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        response.getWriter().write(
                "{\"maintenance\":true,\"message\":\"" + escapedMessage + "\"}");
    }
}