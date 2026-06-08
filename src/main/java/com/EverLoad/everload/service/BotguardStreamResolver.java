package com.EverLoad.everload.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Primary stream resolver: mints a content-bound proof-of-origin ("PO")
 * token via the external {@code rustypipe-botguard} helper binary and pairs
 * it with the ANDROID_VR client — the combination that unlocks plain,
 * non-signature-ciphered audio URLs straight from {@code /player} without
 * running YouTube's player JS.
 *
 * <p>The PO token mint and the {@code visitorData} fetch are independent
 * round-trips (one shells out locally, the other hits InnerTube) and run
 * concurrently. Anything that goes wrong here — binary missing, mint
 * timeout, malformed output — yields a clean {@link YtStreamResolution#failure}
 * so {@link YtMusicStreamService} falls through to the next resolver; it
 * never bubbles up as an exception that would abort the whole chain.
 */
@Component
@Order(10)
public class BotguardStreamResolver implements YtStreamResolver {

    private static final Logger log = LoggerFactory.getLogger(BotguardStreamResolver.class);
    private static final String BINARY_NAME =
            System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "rustypipe-botguard.exe" : "rustypipe-botguard";

    private final YtMusicInnertubeClient client;

    @Value("${ytmusic.botguard.enabled:true}")
    private boolean enabled;

    /** Explicit override; when blank, {@link #resolveBinaryPath()} searches common locations and falls back to PATH. */
    @Value("${ytmusic.botguard.binary-path:}")
    private String configuredBinaryPath;

    /**
     * Argument template for minting a content-bound PO token; {@code {videoId}}
     * is substituted at call time. Exposed as a property because
     * {@code rustypipe-botguard}'s CLI surface can change between releases —
     * adjust it to match `<binary> --help` for the installed version without
     * a rebuild.
     */
    @Value("${ytmusic.botguard.mint-args:generate-po-token --content-binding {videoId}}")
    private String mintArgsTemplate;

    @Value("${ytmusic.botguard.timeout-seconds:20}")
    private int timeoutSeconds;

    private volatile String resolvedBinaryPath;
    private volatile boolean pathResolved;

    /** Cached for the process lifetime — InnerTube hands out the same visitor id repeatedly; mirrors upstream behaviour. */
    private volatile String cachedVisitorData;

    public BotguardStreamResolver(YtMusicInnertubeClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "botguard";
    }

    @Override
    public YtStreamResolution resolve(String videoId) {
        if (!enabled) {
            return YtStreamResolution.failure(YtPlayabilityStatus.UNKNOWN, "resolver Botguard deshabilitado por configuración");
        }
        String binary = resolveBinaryPath();
        if (binary == null) {
            return YtStreamResolution.failure(YtPlayabilityStatus.UNKNOWN,
                    "binario " + BINARY_NAME + " no encontrado (configura ytmusic.botguard.binary-path)");
        }

        CompletableFuture<String> potFuture = CompletableFuture.supplyAsync(() -> mintContentPot(binary, videoId));
        CompletableFuture<String> visitorFuture = CompletableFuture.supplyAsync(this::getOrFetchVisitorData);

        String contentPot;
        String visitorData;
        try {
            contentPot = potFuture.join();
            visitorData = visitorFuture.join();
        } catch (Exception e) {
            return YtStreamResolution.failure(YtPlayabilityStatus.UNKNOWN,
                    "fallo obteniendo PO token / visitorData: " + rootMessage(e));
        }
        if (contentPot == null || contentPot.isBlank()) {
            return YtStreamResolution.failure(YtPlayabilityStatus.UNKNOWN, "Botguard no devolvió un PO token utilizable");
        }

        JsonNode playerResponse;
        try {
            playerResponse = client.player(YtMusicClient.ANDROID_VR_1_61_48, videoId, contentPot, visitorData);
        } catch (YtMusicTransportException e) {
            return YtStreamResolution.failure(YtPlayabilityStatus.UNKNOWN, e.getMessage());
        }
        return YtPlayerResponseInterpreter.interpret(playerResponse, YtMusicClient.ANDROID_VR_1_61_48, name());
    }

    /** True when the binary resolves and a trivial invocation succeeds — surfaced for diagnostics, not on the hot path. */
    public boolean isAvailable() {
        return enabled && resolveBinaryPath() != null;
    }

    // ── PO token minting ──────────────────────────────────────────────

    private String mintContentPot(String binaryPath, String videoId) {
        String[] args = mintArgsTemplate.replace("{videoId}", videoId).trim().split("\\s+");
        String[] cmd = new String[args.length + 1];
        cmd[0] = binaryPath;
        System.arraycopy(args, 0, cmd, 1, args.length);
        try {
            String output = runProcess(cmd, timeoutSeconds);
            String token = extractToken(output);
            if (token == null) {
                log.warn("Botguard no devolvió un PO token reconocible para {}: {}", videoId, truncate(output));
            }
            return token;
        } catch (Exception e) {
            log.warn("Fallo minteando PO token de Botguard para {}: {}", videoId, rootMessage(e));
            return null;
        }
    }

    /** Accepts either a bare token on stdout or a small JSON object containing a {@code poToken}/{@code token} field. */
    private String extractToken(String output) {
        if (output == null) {
            return null;
        }
        String trimmed = output.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("{")) {
            for (String key : new String[]{"\"poToken\"", "\"token\"", "\"contentPoToken\""}) {
                int idx = trimmed.indexOf(key);
                if (idx >= 0) {
                    int colon = trimmed.indexOf(':', idx);
                    int firstQuote = trimmed.indexOf('"', colon + 1);
                    int secondQuote = trimmed.indexOf('"', firstQuote + 1);
                    if (firstQuote >= 0 && secondQuote > firstQuote) {
                        return trimmed.substring(firstQuote + 1, secondQuote);
                    }
                }
            }
            return null;
        }
        // Bare-token output is a single line; ignore any banner/log noise on other lines.
        String[] lines = trimmed.split("\\r?\\n");
        String last = lines[lines.length - 1].trim();
        return last.isEmpty() ? null : last;
    }

    // ── Visitor data (cached, fetched via InnerTube — independent of Botguard) ──

    private String getOrFetchVisitorData() {
        String cached = cachedVisitorData;
        if (cached != null) {
            return cached;
        }
        try {
            String fetched = client.fetchVisitorData();
            cachedVisitorData = fetched;
            return fetched;
        } catch (YtMusicTransportException e) {
            log.warn("No se pudo obtener visitorData: {}", e.getMessage());
            return null;
        }
    }

    // ── Binary path resolution ────────────────────────────────────────

    /**
     * Resolution order: explicit config → next to the running jar → walking
     * up from the working directory → a short list of common install
     * locations → bare name (left for the OS's PATH lookup at spawn time).
     * Cached after the first lookup since the filesystem layout doesn't
     * change while the process is running.
     */
    private String resolveBinaryPath() {
        if (pathResolved) {
            return resolvedBinaryPath;
        }
        synchronized (this) {
            if (pathResolved) {
                return resolvedBinaryPath;
            }
            resolvedBinaryPath = doResolveBinaryPath();
            pathResolved = true;
            return resolvedBinaryPath;
        }
    }

    private String doResolveBinaryPath() {
        if (configuredBinaryPath != null && !configuredBinaryPath.isBlank()) {
            Path configured = Path.of(configuredBinaryPath);
            return Files.exists(configured) ? configured.toString() : configuredBinaryPath;
        }

        Path jarDir = jarDirectory();
        if (jarDir != null) {
            Path candidate = jarDir.resolve(BINARY_NAME);
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }

        Path dir = Path.of("").toAbsolutePath();
        for (int depth = 0; dir != null && depth < 6; depth++) {
            Path candidate = dir.resolve(BINARY_NAME);
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
            dir = dir.getParent();
        }

        for (String common : commonInstallDirs()) {
            Path candidate = Path.of(common, BINARY_NAME);
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }

        return isOnPath(BINARY_NAME) ? BINARY_NAME : null;
    }

    private Path jarDirectory() {
        try {
            Path location = Path.of(BotguardStreamResolver.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return Files.isDirectory(location) ? location : location.getParent();
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> commonInstallDirs() {
        String home = System.getProperty("user.home", "");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            return List.of(
                    home + "\\AppData\\Local\\Programs\\rustypipe-botguard",
                    "C:\\Program Files\\rustypipe-botguard",
                    "C:\\tools\\rustypipe-botguard"
            );
        }
        return List.of(
                "/usr/local/bin",
                "/usr/bin",
                home + "/.local/bin"
        );
    }

    private boolean isOnPath(String exeName) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (dir.isBlank()) continue;
            if (Files.isRegularFile(Path.of(dir, exeName))) {
                return true;
            }
        }
        return false;
    }

    // ── Process execution ─────────────────────────────────────────────

    private String runProcess(String[] cmd, int timeoutSecs) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }
        boolean finished = process.waitFor(timeoutSecs, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new YtMusicTransportException(BINARY_NAME + " agotó el tiempo de espera (" + timeoutSecs + "s)");
        }
        if (process.exitValue() != 0) {
            throw new YtMusicTransportException(BINARY_NAME + " salió con código " + process.exitValue()
                    + ": " + truncate(output));
        }
        return output;
    }

    private String truncate(String s) {
        if (s == null) return "";
        String oneLine = s.replace("\n", " ").trim();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "…" : oneLine;
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg;
    }
}
