package com.seggellion.britannia_mod.server.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The operator-facing configuration contract, pinned as literal strings.
 *
 * <h2>Why literals</h2>
 * {@link ServerCredentialSourceTest} exercises the loader through the {@code *_ENV} constants, so
 * it would keep passing if someone renamed one — test and code would move together and stay
 * consistent with each other while every operator's environment file silently stopped working.
 * These names are not an internal detail; they are the published interface between this mod and
 * the host it runs on. A rename must be a build failure.
 *
 * <h2>Why this file exists in both repositories</h2>
 * The same four variables, the same file name and the same precedence are implemented by the
 * Atrevion Fabric server, so one host template drives either loader and only the values change.
 * This is the NeoForge half of that contract; Fabric has an identical suite. See
 * {@code docs/porting/SERVER_CONFIGURATION_PARITY.md} in the Fabric repository.
 *
 * <p>{@link ServerCredentialSource#API_BASE_URL_ENV} is the variable NF-002 was about. Environment
 * mode used to substitute {@code ModConfig.API_BASE_URL} — a {@code static final} constant reading
 * {@code http://127.0.0.1:3000/api/} that no file, variable or command could change — so a host
 * configured entirely by environment variables talked to its own loopback. It is now required.
 */
class CanonicalHostEnvironmentContractTest {

    @TempDir
    Path gameDirectory;

    private static final String TEMPLATE_SHARD_NAME = "Britannia";
    private static final String TEMPLATE_SHARD_SECRET = "test-secret";
    private static final String TEMPLATE_SERVER_KEY = "00000000-0000-0000-0000-000000000001";
    private static final String TEMPLATE_API_BASE_URL = "https://rails.example.test";

    private static Map<String, String> canonicalTemplate() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("ULTIMACRAFT_SHARD_NAME", TEMPLATE_SHARD_NAME);
        environment.put("ULTIMACRAFT_SHARD_SECRET", TEMPLATE_SHARD_SECRET);
        environment.put("ULTIMACRAFT_MINECRAFT_SERVER_KEY", TEMPLATE_SERVER_KEY);
        environment.put("ULTIMACRAFT_API_BASE_URL", TEMPLATE_API_BASE_URL);
        return environment;
    }

    /**
     * The names. Written as literals on purpose — referencing the constants would make this
     * tautological, which is precisely the hole it exists to close.
     */
    @Test
    void theCanonicalVariableNamesAreExactlyThese() {
        assertEquals("ULTIMACRAFT_SHARD_NAME", ServerCredentialSource.SHARD_NAME_ENV);
        assertEquals("ULTIMACRAFT_SHARD_SECRET", ServerCredentialSource.SHARD_SECRET_ENV);
        assertEquals("ULTIMACRAFT_MINECRAFT_SERVER_KEY", ServerCredentialSource.MINECRAFT_SERVER_KEY_ENV);
        assertEquals("ULTIMACRAFT_API_BASE_URL", ServerCredentialSource.API_BASE_URL_ENV);
    }

    /** The file path, which must also match Fabric's byte for byte. */
    @Test
    void theCanonicalServerFilePathIsExactlyThis() {
        assertEquals(Path.of("config", "britannia_mod-server.properties"),
                ServerCredentialSource.RELATIVE_SERVER_FILE);
    }

    /**
     * The whole template, resolved, with no file on disk. This is the acceptance criterion in
     * executable form — and the case NF-002 broke.
     */
    @Test
    void theCanonicalTemplateResolvesWithNoFileOnDisk() throws Exception {
        ServerCredentials credentials =
                ServerCredentialSource.load(gameDirectory, canonicalTemplate()).orElseThrow();

        assertEquals(ServerCredentials.Source.ENVIRONMENT, credentials.source());
        assertEquals(TEMPLATE_SHARD_NAME, credentials.shardName());
        assertEquals(TEMPLATE_API_BASE_URL, credentials.serviceOrigin().toString());
        assertEquals(UUID.fromString(TEMPLATE_SERVER_KEY),
                credentials.minecraftServerKey().orElseThrow());

        assertFalse(Files.exists(gameDirectory.resolve(ServerCredentialSource.RELATIVE_SERVER_FILE)),
                "loading must not create a file as a side effect");
    }

    /** The same four values from the file instead, resolving identically. */
    @Test
    void theSameFourValuesResolveIdenticallyFromTheFile() throws Exception {
        writeServerFile("shard_name=" + TEMPLATE_SHARD_NAME + "\n"
                + "shard_secret=" + TEMPLATE_SHARD_SECRET + "\n"
                + "minecraft_server_key=" + TEMPLATE_SERVER_KEY + "\n"
                + "api_base_url=" + TEMPLATE_API_BASE_URL + "\n");

        ServerCredentials credentials =
                ServerCredentialSource.load(gameDirectory, Map.of()).orElseThrow();

        assertEquals(ServerCredentials.Source.SERVER_FILE, credentials.source());
        assertEquals(TEMPLATE_SHARD_NAME, credentials.shardName());
        assertEquals(TEMPLATE_API_BASE_URL, credentials.serviceOrigin().toString());
        assertEquals(UUID.fromString(TEMPLATE_SERVER_KEY),
                credentials.minecraftServerKey().orElseThrow());
    }

    /** Environment beats file for every one of the four, with both present. */
    @Test
    void environmentWinsOverTheFileForEveryCanonicalValue() throws Exception {
        writeServerFile("shard_name=FileShard\nshard_secret=file-secret\n"
                + "minecraft_server_key=00000000-0000-0000-0000-0000000000ff\n"
                + "api_base_url=http://127.0.0.1:9999\n");

        ServerCredentials credentials =
                ServerCredentialSource.load(gameDirectory, canonicalTemplate()).orElseThrow();

        assertEquals(ServerCredentials.Source.ENVIRONMENT, credentials.source());
        assertEquals(TEMPLATE_SHARD_NAME, credentials.shardName());
        assertEquals(TEMPLATE_API_BASE_URL, credentials.serviceOrigin().toString());
        assertEquals(UUID.fromString(TEMPLATE_SERVER_KEY),
                credentials.minecraftServerKey().orElseThrow());
    }

    /** Dropping the origin from the environment must fail loudly and name the variable. */
    @Test
    void anEnvironmentWithoutTheOriginFailsAndNamesTheMissingVariable() {
        Map<String, String> environment = new HashMap<>(canonicalTemplate());
        environment.remove("ULTIMACRAFT_API_BASE_URL");

        CredentialConfigurationException failure = assertThrows(
                CredentialConfigurationException.class,
                () -> ServerCredentialSource.load(gameDirectory, environment));
        assertTrue(failure.getMessage().contains("ULTIMACRAFT_API_BASE_URL"),
                "the failure must name the variable the operator has to set, got: "
                        + failure.getMessage());
    }

    /** Every key the file accepts, and proof nothing else is. */
    @Test
    void theFileAcceptsExactlyTheSixDocumentedKeys() throws Exception {
        writeServerFile("shard_name=Britannia\nshard_secret=test-secret\n"
                + "api_base_url=http://127.0.0.1:3000\n"
                + "minecraft_server_key=00000000-0000-0000-0000-000000000001\n"
                + "allow_integrated_server=true\nrails_update_listener_enabled=true\n");
        ServerCredentials credentials =
                ServerCredentialSource.load(gameDirectory, Map.of()).orElseThrow();
        assertTrue(credentials.integratedServerAllowed());
        assertTrue(credentials.railsUpdateListenerEnabled());

        writeServerFile("shard_name=Britannia\nshard_secret=test-secret\n"
                + "api_base_url=http://127.0.0.1:3000\napi_token=not-a-real-key\n");
        CredentialConfigurationException failure = assertThrows(
                CredentialConfigurationException.class,
                () -> ServerCredentialSource.load(gameDirectory, Map.of()));
        assertTrue(failure.getMessage().contains("api_token"),
                "an unknown key must be named so a typo is obvious, got: " + failure.getMessage());
    }

    /** Both optional flags default to the safe value. */
    @Test
    void theOptionalFlagsDefaultOff() throws Exception {
        writeServerFile("shard_name=Britannia\nshard_secret=test-secret\n"
                + "api_base_url=http://127.0.0.1:3000\n");
        ServerCredentials credentials =
                ServerCredentialSource.load(gameDirectory, Map.of()).orElseThrow();

        assertFalse(credentials.integratedServerAllowed(),
                "an integrated server must not be trusted unless the operator says so");
        assertFalse(credentials.railsUpdateListenerEnabled());
        assertTrue(credentials.minecraftServerKey().isEmpty(),
                "an absent server key is absent, not a default");
    }

    /** Surrounding whitespace is operator noise, not part of any value. */
    @Test
    void whitespaceAroundValuesIsIgnoredInBothMechanisms() throws Exception {
        Map<String, String> padded = new LinkedHashMap<>();
        padded.put("ULTIMACRAFT_SHARD_NAME", "  " + TEMPLATE_SHARD_NAME + "  ");
        padded.put("ULTIMACRAFT_SHARD_SECRET", "  " + TEMPLATE_SHARD_SECRET + "  ");
        padded.put("ULTIMACRAFT_MINECRAFT_SERVER_KEY", "  " + TEMPLATE_SERVER_KEY + "  ");
        padded.put("ULTIMACRAFT_API_BASE_URL", "  " + TEMPLATE_API_BASE_URL + "  ");

        ServerCredentials fromEnvironment =
                ServerCredentialSource.load(gameDirectory, padded).orElseThrow();
        assertEquals(TEMPLATE_SHARD_NAME, fromEnvironment.shardName());
        assertEquals(TEMPLATE_API_BASE_URL, fromEnvironment.serviceOrigin().toString());
        assertEquals(UUID.fromString(TEMPLATE_SERVER_KEY),
                fromEnvironment.minecraftServerKey().orElseThrow());

        writeServerFile("shard_name =   " + TEMPLATE_SHARD_NAME + " \n"
                + "shard_secret =   " + TEMPLATE_SHARD_SECRET + " \n"
                + "api_base_url =   " + TEMPLATE_API_BASE_URL + " \n");
        ServerCredentials fromFile =
                ServerCredentialSource.load(gameDirectory, Map.of()).orElseThrow();
        assertEquals(TEMPLATE_SHARD_NAME, fromFile.shardName());
        assertEquals(TEMPLATE_API_BASE_URL, fromFile.serviceOrigin().toString());
    }

    /**
     * A blank environment value is absent, not an empty credential. This is what makes a template
     * with an unfilled placeholder fall through to the file rather than authenticating as the
     * empty shard.
     */
    @Test
    void blankEnvironmentValuesAreAbsentRatherThanEmpty() throws Exception {
        writeServerFile("shard_name=FileShard\nshard_secret=file-secret\n"
                + "api_base_url=http://127.0.0.1:3000\n");
        Map<String, String> blank = new LinkedHashMap<>();
        blank.put("ULTIMACRAFT_SHARD_NAME", "   ");
        blank.put("ULTIMACRAFT_SHARD_SECRET", "");
        blank.put("ULTIMACRAFT_API_BASE_URL", "  ");

        ServerCredentials credentials =
                ServerCredentialSource.load(gameDirectory, blank).orElseThrow();
        assertEquals(ServerCredentials.Source.SERVER_FILE, credentials.source(),
                "blank environment values must fall through to the file");
        assertEquals("FileShard", credentials.shardName());
    }

    /** The origin rules are part of the host contract, and are identical to Fabric's. */
    @Test
    void originValidationMatchesTheDocumentedRules() throws Exception {
        assertEquals("http://127.0.0.1:3000",
                loadWithOrigin("http://127.0.0.1:3000").serviceOrigin().toString());
        assertEquals("http://localhost:3000",
                loadWithOrigin("http://localhost:3000").serviceOrigin().toString());

        // A trailing /api or /api/ is tolerated and stripped, so an older value still works.
        assertEquals("http://127.0.0.1:3000",
                loadWithOrigin("http://127.0.0.1:3000/api/").serviceOrigin().toString());

        assertThrows(CredentialConfigurationException.class,
                () -> loadWithOrigin("http://rails.example.com"));
        assertThrows(CredentialConfigurationException.class,
                () -> loadWithOrigin("https://rails.example.com/api/quests"));
        assertEquals("https://rails.example.com",
                loadWithOrigin("https://rails.example.com").serviceOrigin().toString());
    }

    private ServerCredentials loadWithOrigin(String origin) throws Exception {
        Map<String, String> environment = new LinkedHashMap<>(canonicalTemplate());
        environment.put("ULTIMACRAFT_API_BASE_URL", origin);
        return ServerCredentialSource.load(gameDirectory, environment).orElseThrow();
    }

    private void writeServerFile(String contents) throws Exception {
        Path file = gameDirectory.resolve(ServerCredentialSource.RELATIVE_SERVER_FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }
}
