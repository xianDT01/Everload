package com.everload.everload.service;

import com.everload.everload.config.AdminConfigService;
import java.io.IOException;
import java.net.http.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SetupIntegrationServiceTest {
    AdminConfigService config;
    HttpClient http;
    SetupIntegrationService integrations;
    @BeforeEach void prepare() throws Exception {
        config = mock(AdminConfigService.class); http = mock(HttpClient.class);
        when(config.getClientId()).thenReturn("test-id"); when(config.getClientSecret()).thenReturn("test-secret");
        when(config.getApiKey()).thenReturn("test-key"); when(config.getAcoustidApiKey()).thenReturn("");
        integrations = new SetupIntegrationService(config, http);
    }
    @Test void summaryContainsOnlyConfigurationFlags() throws Exception {
        assertEquals(Map.of("spotify", true, "youtube", true, "acoustid", false), integrations.configured());
    }
    @Test void savesOnlyAllowedKeysAndRejectsPartialCredentials() throws Exception {
        integrations.save("youtube", Map.of("apiKey", "new-key"));
        verify(config).mergeIntegrationConfig(Map.of("apiKey", "new-key"));
        assertThrows(IllegalArgumentException.class, () -> integrations.save("spotify", Map.of("clientId", "only-id")));
        assertThrows(IllegalArgumentException.class, () -> integrations.save("youtube", Map.of("githubToken", "unrelated")));
        assertThrows(IllegalArgumentException.class, () -> integrations.save("youtube", Map.of("apiKey", "bad\r\nvalue")));
    }
    @Test void acoustIdDoesNotReportAFakeVerification() throws Exception {
        assertEquals("unverified", integrations.verify("acoustid")); verifyNoInteractions(http);
    }
    @Test void providerFailureDoesNotExposeSecretBearingExceptions() throws Exception {
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("secret URL test-key"));
        assertEquals("failed", integrations.verify("youtube"));
    }
    @Test void spotifyRequiresAnActualAccessTokenNotMerelyReachability() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class); when(response.statusCode()).thenReturn(200);
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.body()).thenReturn("{}"); assertEquals("failed", integrations.verify("spotify"));
        when(response.body()).thenReturn("{\"access_token\":\"test-token\"}"); assertEquals("verified", integrations.verify("spotify"));
    }
    @Test void youtubeChecksItsActualApiResponse() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class); when(response.statusCode()).thenReturn(200);
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.body()).thenReturn("{\"kind\":\"youtube#videoListResponse\",\"items\":[]}");
        assertEquals("verified", integrations.verify("youtube"));
    }
}
