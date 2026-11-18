package com.everload.everload.service;

import com.everload.everload.dto.SetupAdminRequest;
import com.everload.everload.model.*;
import com.everload.everload.repository.*;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Import({SetupService.class, SetupServiceTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SetupServiceTest {
    static final Path code = Path.of("target/setup-tests-" + UUID.randomUUID(), "setup-code");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.setup.code-path", () -> code.toString());
    }
    @TestConfiguration static class Config {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
    }
    @Autowired SetupService setup;
    @Autowired UserRepository users;
    @Autowired InstallationStateRepository states;
    @Autowired PasswordEncoder encoder;

    @BeforeEach void reset() throws Exception {
        users.deleteAll(); states.deleteAll(); Files.deleteIfExists(code); setup.initialize();
    }
    SetupAdminRequest request(String name) throws Exception {
        return new SetupAdminRequest(Files.readString(code).trim(), name, name + "@example.com", "strong-password-123", "strong-password-123");
    }
    @Test void emptyInstallationRequiresSetupAndHasNoDefaultAccount() throws Exception {
        assertTrue(setup.required()); assertEquals(0, users.count());
        assertTrue(Files.readString(code).trim().length() >= 40);
        assertNotEquals(Files.readString(code).trim(), states.findById(1L).orElseThrow().getCodeHash());
    }
    @Test void wrongCodeDoesNotConsumeTheInstallation() {
        var error = assertThrows(ResponseStatusException.class, () -> setup.createAdmin(
                new SetupAdminRequest("incorrect", "owner", "owner@example.com", "strong-password-123", "strong-password-123")));
        assertEquals(403, error.getStatusCode().value()); assertTrue(setup.required()); assertEquals(0, users.count());
    }
    @Test void administratorIsActivePasswordIsHashedAndCodeIsInvalidated() throws Exception {
        var request = request("owner"); setup.createAdmin(request);
        var owner = users.findByUsername("owner").orElseThrow();
        assertEquals(Role.ADMIN, owner.getRole()); assertEquals(UserStatus.ACTIVE, owner.getStatus());
        assertTrue(encoder.matches(request.password(), owner.getPassword()));
        assertEquals(request.email(), owner.getEmail());
        assertFalse(Files.exists(code)); assertFalse(setup.required());
        assertNull(states.findById(1L).orElseThrow().getCodeHash());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> setup.createAdmin(request)).getStatusCode().value());
    }
    @Test void closedInstallationDoesNotReopenWhenAdminIsRemovedOrServiceRestarts() throws Exception {
        setup.createAdmin(request("owner")); users.deleteAll(); setup.initialize();
        assertFalse(setup.required()); assertFalse(Files.exists(code));
    }
    @Test void existingAdminWithAnyNameClosesSetupWithoutChangingTheirPassword() throws Exception {
        users.save(User.builder().username("existing-owner").email("owner@example.invalid")
                .password("original-hash").role(Role.ADMIN).status(UserStatus.ACTIVE).build());
        setup.initialize(); assertFalse(setup.required()); assertFalse(Files.exists(code));
        assertEquals("original-hash", users.findByUsername("existing-owner").orElseThrow().getPassword());
    }
    @Test void restartPreservesAnUnusedCode() throws Exception {
        String before = Files.readString(code); setup.initialize(); assertEquals(before, Files.readString(code));
    }
    @Test void passwordMismatchDoesNotCreateAnAccount() throws Exception {
        var valid = request("owner");
        assertThrows(ResponseStatusException.class, () -> setup.createAdmin(new SetupAdminRequest(
                valid.code(), valid.username(), valid.email(), valid.password(), "different-password")));
        assertTrue(setup.required()); assertEquals(0, users.count());
    }
    @Test void concurrentClaimsCreateExactlyOneAdministrator() throws Exception {
        var first = request("first-owner"); var second = request("second-owner");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var tasks = List.of(first, second).stream().map(request -> executor.submit(() -> {
                start.await();
                try { setup.createAdmin(request); return 200; }
                catch (ResponseStatusException e) { return e.getStatusCode().value(); }
            })).toList();
            start.countDown();
            var outcomes = List.of(tasks.get(0).get(15, TimeUnit.SECONDS), tasks.get(1).get(15, TimeUnit.SECONDS));
            assertTrue(outcomes.contains(200)); assertTrue(outcomes.contains(409)); assertEquals(1, users.count());
        } finally { executor.shutdownNow(); }
    }
    @Test void duplicateEmailDoesNotConsumeCodeOrCreateAdmin() throws Exception {
        users.save(User.builder().username("existing").email("OWNER@example.com")
                .password("hash").role(Role.BASIC_USER).status(UserStatus.ACTIVE).build());
        var error = assertThrows(ResponseStatusException.class, () -> setup.createAdmin(request("owner")));
        assertEquals("SETUP_EMAIL", error.getReason());
        assertTrue(setup.required()); assertTrue(Files.exists(code));
        assertFalse(users.existsByRole(Role.ADMIN));
    }
}
