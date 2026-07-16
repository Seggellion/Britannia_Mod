package com.seggellion.britannia_mod.server.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServerCredentialSourceTest {
    @TempDir
    Path gameDirectory;

    @Test
    void completeEnvironmentHasPrecedenceWithoutReadingTheFile() throws Exception {
        writeServerFile("shard_name=FileShard\nshard_secret=file-secret\napi_base_url=http://localhost:3000/api\n");
        Map<String, String> environment = Map.of(
            ServerCredentialSource.SHARD_NAME_ENV, "EnvShard",
            ServerCredentialSource.SHARD_SECRET_ENV, "environment-secret"
        );

        ServerCredentials credentials = ServerCredentialSource.load(gameDirectory, environment).orElseThrow();

        assertEquals(ServerCredentials.Source.ENVIRONMENT, credentials.source());
        assertEquals("EnvShard", credentials.shardName());
        assertEquals(8, credentials.fingerprint().length());
        assertFalse(credentials.toString().contains("environment-secret"));
        assertFalse(credentials.toString().contains(credentials.shardSecret()));
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
