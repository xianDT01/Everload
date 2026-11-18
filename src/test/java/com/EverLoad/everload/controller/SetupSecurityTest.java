package com.everload.everload.controller;

import com.everload.everload.security.*;
import com.everload.everload.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {SetupController.class, SetupConfigurationController.class},
        properties = "app.rate-limit.requests-per-minute=10000")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class, MaintenanceFilter.class})
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class SetupSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean SetupService setup;
    @MockBean AuthService auth;
    @MockBean NasService nas;
    @MockBean SetupIntegrationService integrations;
    @MockBean JwtUtil jwt;
    @MockBean UserDetailsServiceImpl details;
    @MockBean TokenRevocationService revocations;
    @MockBean MaintenanceService maintenance;

    @BeforeEach void tokens() {
        for (String role : new String[]{"ADMIN", "BASIC_USER"}) {
            var principal = User.withUsername(role).password("irrelevant").roles(role).build();
            when(jwt.extractUsername(role)).thenReturn(role);
            when(details.loadUserByUsername(role)).thenReturn(principal);
            when(jwt.isTokenValid(role, principal)).thenReturn(true);
        }
    }
    @Test void statusIsPublicAndNeverReturnsCode() throws Exception {
        when(setup.required()).thenReturn(true);
        mvc.perform(get("/api/setup/status")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"required\":true}"));
    }
    @Test void configurationRoutesRequireAdminNotJustLogin() throws Exception {
        for (String path : new String[]{"/storage", "/configuration"}) {
            mvc.perform(get("/api/setup" + path)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/setup" + path).header("Authorization", "Bearer BASIC_USER"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(nas, integrations);
    }
    @Test void adminMayInspectTheMountedVolume() throws Exception {
        when(nas.inspectStorage("")).thenReturn(new NasService.StorageAccess("/app/nas_storage", "", false, false, java.util.List.of()));
        mvc.perform(get("/api/setup/storage").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.readable").value(false));
    }
    @Test void anonymousUserCannotSaveLibraryOrSecrets() throws Exception {
        for (String path : new String[]{"/library", "/integrations/spotify", "/integrations/youtube/test"}) {
            mvc.perform(post("/api/setup" + path).contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }
    @Test void integrationKeysAreRedactedEvenInDebugLogs(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        mvc.perform(post("/api/setup/integrations/youtube").header("Authorization", "Bearer ADMIN")
                .contentType("application/json").content("{\"apiKey\":\"API-KEY-DO-NOT-LOG\"}"))
                .andExpect(status().isOk()).andExpect(content().json("{\"status\":\"saved\"}"));
        org.junit.jupiter.api.Assertions.assertFalse(output.getAll().contains("API-KEY-DO-NOT-LOG"));
    }
    @Test void invalidRegistrationNeverEchoesRejectedSecrets(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        mvc.perform(post("/api/setup/admin").contentType("application/json")
                .content("{\"code\":\"DO-NOT-LOG-CODE\",\"username\":\"!\",\"password\":\"no-leak\",\"confirmation\":\"no-leak\"}"))
                .andExpect(status().isBadRequest()).andExpect(content().json("{\"error\":\"SETUP_FIELDS\"}"));
        verifyNoInteractions(setup, auth);
        org.junit.jupiter.api.Assertions.assertFalse(output.getAll().contains("no-leak"));
        org.junit.jupiter.api.Assertions.assertFalse(output.getAll().contains("DO-NOT-LOG-CODE"));
    }
    @Test void emailIsRequiredAndMustBeValid() throws Exception {
        for (String email : new String[]{"", "invalid-address"}) {
            mvc.perform(post("/api/setup/admin").contentType("application/json")
                    .content("{\"code\":\"code\",\"username\":\"owner\",\"email\":\"" + email
                            + "\",\"password\":\"strong-password-123\",\"confirmation\":\"strong-password-123\"}"))
                    .andExpect(status().isBadRequest()).andExpect(content().json("{\"error\":\"SETUP_FIELDS\"}"));
        }
        verifyNoInteractions(setup, auth);
    }
}
