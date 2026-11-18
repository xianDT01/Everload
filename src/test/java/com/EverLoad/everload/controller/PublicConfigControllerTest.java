package com.everload.everload.controller;

import com.everload.everload.config.AdminConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicConfigControllerTest {

    @Test
    void unreadableConfiguration_returnsDefaultImages() throws IOException {
        AdminConfigService configService = mock(AdminConfigService.class);
        when(configService.getConfig()).thenThrow(new IOException("unreadable"));
        PublicConfigController controller = new PublicConfigController(configService);

        ResponseEntity<Map<String, List<String>>> response = controller.getAuthHeroImages();

        List<String> expected = List.of();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody().get("images"));
    }
    @Test
    void emptyConfigurationReturnsNoImages() throws IOException {
        AdminConfigService config = mock(AdminConfigService.class);
        when(config.getConfig()).thenReturn(Map.of("authHeroImages", ""));
        assertEquals(List.of(), new PublicConfigController(config).getAuthHeroImages().getBody().get("images"));
    }
    @Test
    void partialConfigurationKeepsBlankSlotsWithoutFillingThem() throws IOException {
        AdminConfigService config = mock(AdminConfigService.class);
        when(config.getConfig()).thenReturn(Map.of("authHeroImages", "\n\n/own-image.jpg\n"));
        assertEquals(List.of("", "", "/own-image.jpg", ""),
                new PublicConfigController(config).getAuthHeroImages().getBody().get("images"));
    }
}
