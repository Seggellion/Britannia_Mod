package com.seggellion.britannia_mod.server.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A structural guard on where the server learns its own backend identity.
 *
 * <h2>Why this exists</h2>
 * {@code ModConfig} holds compile-time constants that look like configuration but are not:
 *
 * <pre>
 * public static final String API_BASE_URL = "http://127.0.0.1:3000/api/";
 * public static final String SHARD_NAME   = "Britannia";
 * </pre>
 *
 * Both are {@code final} and assigned at their declaration; {@code loadConfig()} reads only
 * {@code exampleValue}, so <b>no file, environment variable or command can change either</b>.
 * Reading one of them where a configured value was meant produces a value that is right by
 * coincidence and wrong the moment an operator configures anything else.
 *
 * <p>That is exactly what NF-002 was: environment mode substituted the origin constant, so a host
 * configured entirely by environment variables talked to its own loopback and could not be pointed
 * anywhere else. It is fixed, and this test is what stops it coming back — the defect was
 * invisible to review and had no test for the value it corrupted.
 *
 * <p>Source text is inspected because a <em>field read</em> of a constant leaves no trace in the
 * class file that reflection can find. Comments and string literals are stripped first, so the
 * guard cannot be silenced by deleting the javadoc that explains it.
 */
class ServerIdentityComesFromCredentialsTest {

    /**
     * The project's {@code src/main/java}, found by walking up from wherever the test runner
     * happens to start. NeoGradle runs tests from {@code build/test-run/}, not the project
     * directory, so a relative path alone resolves to nothing — and a guard that cannot find the
     * source it is meant to scan would pass vacuously, which is worse than failing.
     */
    private static final Path MAIN_SOURCE = locateMainSource();

    private static Path locateMainSource() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            Path source = candidate.resolve("src/main/java");
            if (Files.isDirectory(source)) return source;
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "could not locate src/main/java above " + Path.of("").toAbsolutePath());
    }

    /** The class that declares the constants may of course mention them. */
    private static final String DECLARING_CLASS =
            "config" + java.io.File.separator + "ModConfig.java";

    private record SourceFile(Path path, String text) {
        String name() {
            return path.getFileName().toString();
        }
    }

    /** Strips comments and string literals so prose about code is not mistaken for code. */
    private static String codeOnly(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inBlock = false, inLine = false, inString = false, inChar = false, escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLine) {
                if (c == '\n') { inLine = false; out.append(c); }
                continue;
            }
            if (inBlock) {
                if (c == '*' && next == '/') { inBlock = false; i++; }
                continue;
            }
            if (inString || inChar) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (inString && c == '"') inString = false;
                else if (inChar && c == '\'') inChar = false;
                continue;
            }
            if (c == '/' && next == '*') { inBlock = true; i++; continue; }
            if (c == '/' && next == '/') { inLine = true; i++; continue; }
            if (c == '"') { inString = true; continue; }
            if (c == '\'') { inChar = true; continue; }
            out.append(c);
        }
        return out.toString();
    }

    private static List<SourceFile> mainSources() {
        assertTrue(Files.isDirectory(MAIN_SOURCE),
                "expected to run from the project directory; " + MAIN_SOURCE.toAbsolutePath()
                        + " does not exist");
        try (Stream<Path> paths = Files.walk(MAIN_SOURCE)) {
            List<SourceFile> files = new ArrayList<>();
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    files.add(new SourceFile(path,
                            codeOnly(Files.readString(path, StandardCharsets.UTF_8))));
                } catch (IOException unreadable) {
                    throw new UncheckedIOException(unreadable);
                }
            });
            assertFalse(files.isEmpty(), "no main sources were found to scan");
            return files;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static boolean isDeclaringClass(SourceFile file) {
        return file.path().toString().endsWith(DECLARING_CLASS);
    }

    /**
     * NF-002's permanent guard. Nothing may take a Rails origin from the compiled constant — the
     * repair removed the only reader, so this list is empty and must stay empty.
     */
    @Test
    void nothingBuildsARailsOriginFromTheCompiledConstant() {
        List<String> offenders = new ArrayList<>();
        for (SourceFile file : mainSources()) {
            if (isDeclaringClass(file)) continue;
            if (file.text().contains("ModConfig.API_BASE_URL")) offenders.add(file.name());
        }
        assertTrue(offenders.isEmpty(),
                "ModConfig.API_BASE_URL is a compile-time constant pointing at loopback that no "
                        + "operator can change; resolve the origin through "
                        + "ServerCredentials.apiUrls() instead: " + offenders);
    }

    /**
     * The stricter half: a class that authenticates a request must take its origin from the same
     * credentials that supply the secret, or the two can disagree and the secret goes to the
     * wrong host.
     */
    @Test
    void nothingAuthenticatesARequestItAddressedFromTheCompiledConstant() {
        List<String> offenders = new ArrayList<>();
        for (SourceFile file : mainSources()) {
            if (isDeclaringClass(file)) continue;
            String text = file.text();
            if (text.contains("ModConfig.API_BASE_URL")
                    && text.contains("RailsRequestAuthenticator")) {
                offenders.add(file.name());
            }
        }
        assertTrue(offenders.isEmpty(),
                "these classes attach a credential to a request addressed from a compiled "
                        + "constant, so the secret can leave for an unconfigured host: " + offenders);
    }

    /**
     * Environment access is centralised in the credential loader, so a new feature cannot invent
     * its own variable with its own name, precedence and validation drifting free of the
     * documented contract.
     */
    @Test
    void onlyTheCredentialLoaderReadsTheEnvironment() {
        List<String> offenders = new ArrayList<>();
        for (SourceFile file : mainSources()) {
            if (file.name().equals("ServerCredentialSource.java")) continue;
            if (file.text().contains("System.getenv")) offenders.add(file.name());
        }
        assertTrue(offenders.isEmpty(),
                "these classes read the environment directly instead of going through "
                        + "ServerCredentialSource, which is how a second undocumented "
                        + "configuration contract starts: " + offenders);
    }

    /**
     * NF-003's permanent guard. <b>Nothing</b> may read the compiled shard name — there is no
     * allowlist any more, because the repair took the count to zero.
     *
     * <p>The reason this is absolute rather than "avoid it where it matters": the shard identity
     * reaches durable state. A spawn operation is persisted, replayed after a restart and retried
     * by a delivery processor that rejects any record whose shard does not match the credentials.
     * A single compiled value written into that record is not one wrong field — it is a row on
     * disk that can never be delivered and will be retried forever.
     */
    @Test
    void nothingReadsTheCompiledShardName() {
        List<String> readers = new ArrayList<>();
        for (SourceFile file : mainSources()) {
            if (isDeclaringClass(file)) continue;
            if (file.text().contains("ModConfig.SHARD_NAME")) readers.add(file.name());
        }
        assertTrue(readers.isEmpty(),
                "ModConfig.SHARD_NAME is a compile-time constant, not the configured shard; read "
                        + "ServerAuthRegistry.shardName(server) instead: " + readers);
    }

    /**
     * The premise everything above rests on: those two fields are still compile-time constants.
     * If either ever becomes genuinely configurable, these rules should be revisited deliberately
     * rather than quietly becoming stale.
     */
    @Test
    void theModConfigIdentityFieldsAreStillCompileTimeConstants() throws Exception {
        String declaring = codeOnly(Files.readString(
                MAIN_SOURCE.resolve("com/seggellion/britannia_mod/config/ModConfig.java"),
                StandardCharsets.UTF_8));

        assertTrue(declaring.contains("static final String API_BASE_URL"),
                "API_BASE_URL is no longer a constant — re-examine whether reading it is still unsafe");
        assertTrue(declaring.contains("static final String SHARD_NAME"),
                "SHARD_NAME is no longer a constant — re-examine NF-003 and this test's premise");
    }
}
