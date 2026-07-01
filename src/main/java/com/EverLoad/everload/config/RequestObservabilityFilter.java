package com.EverLoad.everload.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Adds a request id and records API calls that are slow enough to investigate. */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestObservabilityFilter extends OncePerRequestFilter {

    @Value("${app.observability.slow-request-ms:1500}")
    private long slowRequestMs;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 12);
        long startedAt = System.nanoTime();
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            response.setHeader("X-Response-Time-Ms", String.valueOf(elapsedMs));
            if (elapsedMs >= slowRequestMs || response.getStatus() >= 500) {
                log.warn("api_request method={} path={} status={} durationMs={} requestId={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs, requestId);
            } else {
                log.debug("api_request method={} path={} status={} durationMs={} requestId={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs, requestId);
            }
            MDC.remove("requestId");
        }
    }
}
