package com.EverLoad.everload.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifica la protección anti-spoofing del rate limiter: X-Forwarded-For solo
 * se respeta cuando la conexión viene del proxy (loopback/red privada). Un
 * cliente directo no puede fabricar IPs para saltarse el límite del login.
 */
class RateLimitFilterTest {

    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "authRpm", 2);
        chain = mock(FilterChain.class);
    }

    private MockHttpServletRequest authRequest(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServletPath("/api/auth/login");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    @Test
    void clienteDirectoNoPuedeSaltarseElLimiteFalseandoXForwardedFor() throws Exception {
        // remoteAddr público (no es el proxy) + XFF distinto en cada petición
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(authRequest("203.0.113.7", "1.2.3." + i), response, chain);
            assertEquals(200, response.getStatus(), "las primeras 2 pasan");
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(authRequest("203.0.113.7", "9.9.9.9"), blocked, chain);

        assertEquals(429, blocked.getStatus(), "la 3ª debe bloquearse aunque cambie el XFF");
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void detrasDelProxyCadaClienteRealTieneSuPropioBucket() throws Exception {
        // remoteAddr loopback (Caddy) → se confía en XFF: dos clientes, dos buckets
        for (String clientIp : new String[]{"80.10.10.1", "80.10.10.2"}) {
            for (int i = 0; i < 2; i++) {
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilter(authRequest("127.0.0.1", clientIp), response, chain);
                assertEquals(200, response.getStatus());
            }
        }
        verify(chain, times(4)).doFilter(any(), any());

        // pero el mismo cliente sí agota su bucket
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(authRequest("127.0.0.1", "80.10.10.1"), blocked, chain);
        assertEquals(429, blocked.getStatus());
    }

    @Test
    void redPrivadaDelDockerComposeEsProxyDeConfianza() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(authRequest("172.18.0.2", "80.10.10.5"), response, chain);
        assertEquals(200, response.getStatus());

        // Mismo cliente real vía proxy: comparte bucket aunque cambie la IP del contenedor
        filter.doFilter(authRequest("172.18.0.3", "80.10.10.5"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(authRequest("172.18.0.2", "80.10.10.5"), blocked, chain);
        assertEquals(429, blocked.getStatus());
    }

    @Test
    void lasImagenesYPortadasNoSeFiltran() {
        MockHttpServletRequest cover = new MockHttpServletRequest("GET", "/api/music/cover");
        cover.setServletPath("/api/music/cover");
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(filter, "shouldNotFilter", cover));

        MockHttpServletRequest upload = new MockHttpServletRequest("POST", "/api/music/cover");
        upload.setServletPath("/api/music/cover");
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(filter, "shouldNotFilter", upload),
                "un POST al mismo path sí se limita");

        MockHttpServletRequest spa = new MockHttpServletRequest("GET", "/home");
        spa.setServletPath("/home");
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(filter, "shouldNotFilter", spa),
                "las rutas del SPA no pasan por el limiter");
    }
}
