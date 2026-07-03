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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Multi-tier IP-based rate limiter for all API endpoints.
 *
 * Tiers (per unique client IP):
 *  - AUTH     /api/auth/**        - 10  req/min  (brute-force protection)
 *  - DOWNLOAD download/search     - 8   req/min  (heavy resource use)
 *  - UPLOAD   upload/write jobs   - 15  req/min  (file writes)
 *  - GLOBAL   all other /api/**   - 300 req/min  (flood protection)
 *
 * Non-API paths (static assets, Angular) are not filtered.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /** Tope del mapa de buckets; al superarlo se purgan los inactivos. */
    private static final int MAX_BUCKETS = 10_000;
    private static final long BUCKET_IDLE_EVICT_MS = TimeUnit.MINUTES.toMillis(10);

    @Value("${app.rate-limit.requests-per-minute:10}")
    private int authRpm;

    // key: "TIER:IP"
    private final ConcurrentHashMap<String, TimestampedBucket> buckets = new ConcurrentHashMap<>();

    private static final class TimestampedBucket {
        final Bucket bucket;
        volatile long lastAccess;

        TimestampedBucket(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccess = System.currentTimeMillis();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (!path.startsWith("/api/")) return true;
        // Servir imágenes/portadas (GET) no se limita: son lecturas baratas con su
        // propia caché y el Home/Álbumes piden muchas de golpe. Antes caían en el tier
        // UPLOAD (acababan en "/cover") y devolvían 429 → portadas en negro.
        return ("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod()))
                && isMediaServe(path);
    }

    /** Endpoints de solo lectura que sirven imágenes/portadas/avatares. */
    private boolean isMediaServe(String path) {
        return path.startsWith("/api/music/cover")
                || path.startsWith("/api/music/artist-image")
                || path.startsWith("/api/music/artist-auto-image")
                || path.startsWith("/api/music/album-auto-cover")
                || path.startsWith("/api/artists/image/")
                || path.startsWith("/api/user/avatar/img");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = resolveClientIp(request);
        String path = request.getServletPath();
        Tier tier = classifyTier(path);

        pruneBucketsIfNeeded();
        TimestampedBucket entry = buckets.computeIfAbsent(tier.name() + ":" + ip,
                k -> new TimestampedBucket(newBucket(tier)));
        entry.lastAccess = System.currentTimeMillis();

        if (entry.bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", "60");
        response.getWriter().write(
                "{\"error\":\"Demasiadas peticiones. Espera un momento e intentalo de nuevo.\"}");
    }

    /** Evita que el mapa crezca sin límite (una entrada por IP vista, para siempre). */
    private void pruneBucketsIfNeeded() {
        if (buckets.size() <= MAX_BUCKETS) return;
        long cutoff = System.currentTimeMillis() - BUCKET_IDLE_EVICT_MS;
        buckets.values().removeIf(b -> b.lastAccess < cutoff);
        // Si tras purgar sigue desbordado (flood distribuido), se vacía entero:
        // los buckets se recrean al vuelo y solo se pierde el conteo del minuto en curso.
        if (buckets.size() > MAX_BUCKETS) buckets.clear();
    }

    private enum Tier { AUTH, DOWNLOAD, UPLOAD, GLOBAL }

    private Tier classifyTier(String path) {
        if (path.startsWith("/api/auth/")) return Tier.AUTH;
        if (path.startsWith("/api/download")
                || path.startsWith("/api/playlistVideos")
                || path.startsWith("/api/youtube/search")
                || path.startsWith("/api/spotify/playlist")) {
            return Tier.DOWNLOAD;
        }
        if (path.endsWith("/upload")
                || path.endsWith("/mkdir")
                || path.endsWith("/cover")
                || path.startsWith("/api/saveMusicToNas")
                || path.startsWith("/api/nas/ytdlp/queue")) {
            return Tier.UPLOAD;
        }
        return Tier.GLOBAL;
    }

    private Bucket newBucket(Tier tier) {
        int rpm = switch (tier) {
            case AUTH -> authRpm;
            case DOWNLOAD -> 8;
            case UPLOAD -> 15;
            case GLOBAL -> 300;
        };
        return Bucket.builder()
                .addLimit(Bandwidth.classic(rpm, Refill.greedy(rpm, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * IP real del cliente. Solo se confía en X-Forwarded-For / X-Real-IP cuando la
     * conexión llega desde el proxy (loopback o red privada del docker-compose);
     * si no, un cliente conectado directamente podría falsear la cabecera y
     * saltarse el límite del login con cada petición.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String addr) {
        if (addr == null) return false;
        try {
            InetAddress ip = InetAddress.getByName(addr);
            return ip.isLoopbackAddress()
                    || ip.isSiteLocalAddress()      // 10/8, 172.16/12, 192.168/16 (red docker)
                    || ip.isLinkLocalAddress()
                    || isUniqueLocalIpv6(ip);
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** IPv6 ULA fc00::/7 — equivalente privado que isSiteLocalAddress no cubre. */
    private boolean isUniqueLocalIpv6(InetAddress ip) {
        byte[] bytes = ip.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == (byte) 0xFC;
    }
}
