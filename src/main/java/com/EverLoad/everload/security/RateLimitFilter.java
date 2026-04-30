package com.EverLoad.everload.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-tier IP-based rate limiter for all API endpoints.
 *
 * Tiers (per unique client IP):
 *  - AUTH     /api/auth/**         - 10  req/min  (brute-force protection)
 *  - DOWNLOAD /api/download/**     - 8   req/min  (heavy resource use)
 *  - UPLOAD   paths ending /upload - 15  req/min  (file writes)
 *  - GLOBAL   all other /api/**    - 300 req/min  (flood protection)
 *
 * Non-API paths (static assets, Angular) are not filtered.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${app.rate-limit.requests-per-minute:10}")
    private int authRpm;

    // key: "TIER:IP"
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Only rate-limit API paths
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip   = resolveClientIp(request);
        String path = request.getServletPath();
        Tier   tier = classifyTier(path);

        Bucket bucket = buckets.computeIfAbsent(tier.name() + ":" + ip,
                k -> newBucket(tier));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Demasiadas peticiones. Espera un momento e inténtalo de nuevo.\"}");
        }
    }

    private enum Tier { AUTH, DOWNLOAD, UPLOAD, GLOBAL }

    private Tier classifyTier(String path) {
        if (path.startsWith("/api/auth/")) return Tier.AUTH;
        if (path.startsWith("/api/download/")) return Tier.DOWNLOAD;
        if (path.endsWith("/upload") || path.endsWith("/mkdir")) return Tier.UPLOAD;
        return Tier.GLOBAL;
    }

    private Bucket newBucket(Tier tier) {
        int rpm = switch (tier) {
            case AUTH     -> authRpm;          // 10
            case DOWNLOAD -> 8;
            case UPLOAD   -> 15;
            case GLOBAL   -> 300;
        };
        return Bucket.builder()
                .addLimit(Bandwidth.classic(rpm, Refill.greedy(rpm, Duration.ofMinutes(1))))
                .build();
    }

    /** Reads real client IP respecting X-Forwarded-For from Caddy/Nginx. */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank())
            return forwarded.split(",")[0].trim();
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank())
            return realIp.trim();
        return request.getRemoteAddr();
    }
}
