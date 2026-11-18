package com.everload.everload.service;

import com.everload.everload.dto.SetupAdminRequest;
import com.everload.everload.model.*;
import com.everload.everload.repository.InstallationStateRepository;
import com.everload.everload.repository.UserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class SetupService {
    private final InstallationStateRepository states;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transactions;
    private final Path codeFile;

    public SetupService(InstallationStateRepository states, UserRepository users, PasswordEncoder encoder,
                        org.springframework.transaction.PlatformTransactionManager manager,
                        @Value("${app.setup.code-path:./data/setup-code}") String codePath) {
        this.states = states;
        this.users = users;
        this.encoder = encoder;
        this.transactions = new TransactionTemplate(manager);
        this.codeFile = Path.of(codePath).toAbsolutePath().normalize();
    }

    public void initialize() {
        try {
            transactions.executeWithoutResult(tx -> {
                if (!states.existsById(1L)) states.saveAndFlush(new InstallationState());
            });
        } catch (DataIntegrityViolationException concurrentStartup) {
            // Another instance may have inserted the singleton before us.
        }
        transactions.executeWithoutResult(tx -> {
            InstallationState state = states.lockInstallation();
            if (users.existsByRole(Role.ADMIN)) state.setCompleted(true);
            if (state.isCompleted()) { state.setCodeHash(null); return; }
            try {
                if (Files.isRegularFile(codeFile, LinkOption.NOFOLLOW_LINKS)
                        && hash(Files.readString(codeFile).trim()).equals(state.getCodeHash())) return;
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                String code = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
                Files.createDirectories(codeFile.getParent());
                Path temporary = Files.createTempFile(codeFile.getParent(), ".setup-", ".tmp");
                try {
                    if (Files.getFileStore(temporary).supportsFileAttributeView("posix")) {
                        Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
                    }
                    Files.writeString(temporary, code + System.lineSeparator());
                    Files.move(temporary, codeFile, StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(temporary); }
                state.setCodeHash(hash(code));
                log.info("First-run setup required. Installation code file: {} (never served over HTTP).", codeFile);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot securely prepare installation code file", e);
            }
        });
        if (!required()) removeCode();
    }

    public boolean required() {
        InstallationState state = states.findById(1L).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SETUP_STARTING"));
        return !state.isCompleted() && !users.existsByRole(Role.ADMIN);
    }

    public String createAdmin(SetupAdminRequest request) {
        if (!request.password().equals(request.confirmation())
                || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SETUP_PASSWORD");
        }
        String username = transactions.execute(tx -> {
            InstallationState state = states.lockInstallation();
            if (state == null || state.isCompleted() || users.existsByRole(Role.ADMIN)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SETUP_CLOSED");
            }
            if (state.getCodeHash() == null || !MessageDigest.isEqual(
                    state.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                    hash(request.code()).getBytes(StandardCharsets.US_ASCII))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SETUP_CODE");
            }
            if (users.existsByUsername(request.username())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SETUP_USERNAME");
            }
            String email = request.email().trim();
            if (users.existsByEmailIgnoreCase(email)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SETUP_EMAIL");
            }
            users.saveAndFlush(User.builder().username(request.username())
                    .email(email)
                    .password(encoder.encode(request.password())).role(Role.ADMIN).status(UserStatus.ACTIVE).build());
            state.setCompleted(true);
            state.setCodeHash(null);
            return request.username();
        });
        // The DB invalidation commits before the file is removed or an authenticated session is issued.
        removeCode();
        return username;
    }

    private void removeCode() {
        try { Files.deleteIfExists(codeFile); }
        catch (IOException e) { log.warn("Installation is closed; stale code file could not be removed."); }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
