package com.EverLoad.everload.controller;

import com.EverLoad.everload.config.AdminConfigService;
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

        List<String> expected = List.of(AdminConfigService.DEFAULT_AUTH_HERO_IMAGES.split("\\R"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody().get("images"));
    }
}
