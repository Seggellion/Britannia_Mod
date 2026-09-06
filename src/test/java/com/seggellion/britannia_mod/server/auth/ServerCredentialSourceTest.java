package com.seggellion.britannia_mod.server.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServerCredentialSourceTest {
    @TempDir
    Path gameDirectory;

    /**
     * Environment mode, asserting <b>every</b> resolved value including the origin.
     *
     * <p>The origin assertion is the point. This test previously checked the source enum, the
     * shard name and the fingerprint but never where the origin ended up — which is exactly how
     * NF-002 survived review: environment mode substituted a compiled-in loopback constant, and
     * nothing looked. A test that omits the one value a defect corrupts will pass forever.
     */
    @Test
    void completeEnvironmentHasPrecedenceWithoutReadingTheFile() throws Exception {
        writeServerFile("shard_name=FileShard\nshard_secret=file-secret\napi_base_url=http://localhost:3000/api\n");
        Map<String, String> environment = Map.of(
            ServerCredentialSource.SHARD_NAME_ENV, "EnvShard",
            ServerCredentialSource.SHARD_SECRET_ENV, "environment-secret",
            ServerCredentialSource.API_BASE_URL_ENV, "http://127.0.0.1:4000"
        );

        ServerCredentials credentials = ServerCredentialSource.load(gameDirectory, environment).orElseThrow();

        assertEquals(ServerCredentials.Source.ENVIRONMENT, credentials.source());
        assertEquals("EnvShard", credentials.shardName());
        assertEquals("http://127.0.0.1:4000", credentials.serviceOrigin().toString(),
            "the origin must come from the environment, not from the file and not from a constant");
        assertEquals(8, credentials.fingerprint().length());
        assertFalse(credentials.toString().contains("environment-secret"));
        assertFalse(credentials.toString().contains(credentials.shardSecret()));
    }

    /**
     * NF-002, asserted as behaviour. Environment credentials without an origin must fail and name
     * the variable the operator has to set. They must never fall back to a compiled default: the
     * old fallback was {@code http://127.0.0.1:3000/api/}, a loopback address no production Rails
     * answers on and no operator could change.
     */
    @Test
    void environmentCredentialsWithoutBaseUrlFailInsteadOfUsingACompiledDefault() throws Exception {
        writeServerFile("shard_name=FileShard\nshard_secret=file-secret\napi_base_url=https://file.example.test\n");
        Map<String, String> environment = Map.of(
            ServerCredentialSource.SHARD_NAME_ENV, "EnvShard",
            ServerCredentialSource.SHARD_SECRET_ENV, "environment-secret"
        );

        CredentialConfigurationException failure = assertThrows(CredentialConfigurationException.class,
            () -> ServerCredentialSource.load(gameDirectory, environment));
        assertTrue(failure.getMessage().contains(ServerCredentialSource.API_BASE_URL_ENV),
            "the failure must name the missing variable, got: " + failure.getMessage());
    }

    /**
     * In file mode the origin variable overrides only itself, mirroring how the server key already
     * behaves. Everything else still comes from the file.
     */
    @Test
    void environmentBaseUrlOverridesOnlyThatValueInFileMode() throws Exception {
        writeServerFile("shard_name=FileShard\nshard_secret=file-secret\n"
            + "api_base_url=http://localhost:3000/api\nminecraft_server_key="
            + "00000000-0000-0000-0000-0000000000ff\n");
        Map<String, String> environment = Map.of(
            ServerCredentialSource.API_BASE_URL_ENV, "https://rails.example.test");

        ServerCredentials credentials = ServerCredentialSource.load(gameDirectory, environment).orElseThrow();

        assertEquals(ServerCredentials.Source.SERVER_FILE, credentials.source());
        assertEquals("FileShard", credentials.shardName());
        assertEquals("https://rails.example.test", credentials.serviceOrigin().toString());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-0000000000ff"),
            credentials.minecraftServerKey().orElseThrow());
    }

    @Test
    void incompleteEnvironmentFailsInsteadOfMixingWithTheFile() throws Exception {
        writeServerFile("shard_name=FileShard\nshard_secret=file-secret\napi_base_url=http://localhost:3000/api\n");
        Map<String, String> environment = Map.of(ServerCredentialSource.SHARD_NAME_ENV, "EnvShard");

        assertThrows(CredentialConfigurationException.class,
            () -> ServerCredentialSource.load(gameDirectory, environment));
    }

    @Test
    void completeServerFileLoadsLocalDevelopmentOptions() throws Exception {
        writeServerFile("""
            shard_name=Britannia
            shard_secret=local-development-secret
            api_base_url=http://127.0.0.1:3000/api/
            allow_integrated_server=true
            rails_update_listener_enabled=false
            """);

        ServerCredentials credentials = ServerCredentialSource.load(gameDirectory, new HashMap<>()).orElseThrow();

        assertEquals(ServerCredentials.Source.SERVER_FILE, credentials.source());
        assertEquals("Britannia", credentials.shardName());
        assertEquals("http://127.0.0.1:3000", credentials.serviceOrigin().toString());
        assertEquals(
            "http://127.0.0.1:3000/api/world_bootstrap/Britannia",
            credentials.apiUrls().resolvePath(
                com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint.WORLD_BOOTSTRAP,
                Map.of("shard", "Britannia")
            ).toString()
        );
        assertTrue(credentials.integratedServerAllowed());
        assertFalse(credentials.railsUpdateListenerEnabled());
    }

    @Test
    void serverKeyLoadsFromFileAndEnvironmentOverridesOnlyThatValue() throws Exception {
        UUID fileKey = UUID.randomUUID();
        UUID environmentKey = UUID.randomUUID();
        writeServerFile("""
            shard_name=Britannia
            shard_secret=local-development-secret
            api_base_url=http://127.0.0.1:3000
            minecraft_server_key=%s
            """.formatted(fileKey));

        ServerCredentials fromFile = ServerCredentialSource.load(gameDirectory, Map.of()).orElseThrow();
        ServerCredentials overridden = ServerCredentialSource.load(gameDirectory, Map.of(
            ServerCredentialSource.MINECRAFT_SERVER_KEY_ENV, environmentKey.toString()
        )).orElseThrow();

        assertEquals(fileKey, fromFile.minecraftServerKey().orElseThrow());
        assertEquals(environmentKey, overridden.minecraftServerKey().orElseThrow());
        assertFalse(fromFile.toString().contains(fileKey.toString()));
        assertFalse(overridden.toString().contains(environmentKey.toString()));
    }

    @Test
    void missingOrMalformedServerKeyDoesNotDisableExistingCredentials() throws Exception {
        writeServerFile("""
            shard_name=Britannia
            shard_secret=local-development-secret
            api_base_url=http://127.0.0.1:3000
            """);
        ServerCredentials missing = ServerCredentialSource.load(gameDirectory, Map.of()).orElseThrow();
        assertTrue(missing.minecraftServerKey().isEmpty());
        assertEquals(ServerCredentials.MinecraftServerKeyStatus.MISSING, missing.minecraftServerKeyStatus());
        assertEquals("minecraft_server_key_missing", missing.minecraftServerKeyUnavailableCode());
        assertEquals("Britannia", missing.shardName());

        writeServerFile("""
            shard_name=Britannia
            shard_secret=local-development-secret
            api_base_url=http://127.0.0.1:3000
            minecraft_server_key=not-a-uuid
            """);
        ServerCredentials malformed = ServerCredentialSource.load(gameDirectory, Map.of()).orElseThrow();
        assertTrue(malformed.minecraftServerKey().isEmpty());
        assertEquals(ServerCredentials.MinecraftServerKeyStatus.INVALID, malformed.minecraftServerKeyStatus());
        assertEquals("minecraft_server_key_invalid", malformed.minecraftServerKeyUnavailableCode());
    }

    @Test
    void duplicateKeysAndNonLoopbackHttpAreRejected() throws Exception {
        writeServerFile("shard_name=A\nshard_name=B\nshard_secret=value\napi_base_url=https://example.test/api\n");
        assertThrows(CredentialConfigurationException.class,
            () -> ServerCredentialSource.load(gameDirectory, Map.of()));

        writeServerFile("shard_name=A\nshard_secret=value\napi_base_url=http://example.test/api\n");
        assertThrows(CredentialConfigurationException.class,
            () -> ServerCredentialSource.load(gameDirectory, Map.of()));
    }

    @Test
    void missingSourcesReturnUnavailableWithoutCreatingRuntimeFiles() throws Exception {
        assertTrue(ServerCredentialSource.load(gameDirectory, Map.of()).isEmpty());
        assertFalse(Files.exists(gameDirectory.resolve(ServerCredentialSource.RELATIVE_SERVER_FILE)));
    }

    private void writeServerFile(String contents) throws Exception {
        Path file = gameDirectory.resolve(ServerCredentialSource.RELATIVE_SERVER_FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }
}
