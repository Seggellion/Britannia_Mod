package com.seggellion.britannia_mod.server.http;

import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailsApiUrlResolverTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1:3000",
        "http://127.0.0.1:3000/",
        "http://127.0.0.1:3000/api",
        "http://127.0.0.1:3000/api/"
    })
    void equivalentConfiguredRootsProduceOneApiNamespace(String configuredBase) {
        RailsApiUrlResolver resolver = RailsApiUrlResolver.fromConfiguredBase(configuredBase);

        assertEquals("http://127.0.0.1:3000", resolver.serviceOrigin().toString());
        assertEquals(
            "http://127.0.0.1:3000/api/world_bootstrap/Britannia",
            resolver.resolvePath(Endpoint.WORLD_BOOTSTRAP, Map.of("shard", "Britannia")).toString()
        );
        URI spawnOperations = resolver.resolve(Endpoint.SERVICE_NPC_SPAWN_OPERATIONS);
        assertEquals("http://127.0.0.1:3000/api/service_npc_spawn_operations", spawnOperations.toString());
        assertFalse(spawnOperations.toString().contains("/api/api/"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveQuery(
            Endpoint.SERVICE_NPC_SPAWN_OPERATIONS, Map.of("next", "https://elsewhere.test")
        ));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolvePath(
            Endpoint.SERVICE_NPC_SPAWN_OPERATIONS, Map.of("path", "injected")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1:3000/api/world_bootstrap",
        "http://127.0.0.1:3000/service",
        "http://127.0.0.1:3000//api",
        "http://127.0.0.1:3000?mode=development"
    })
    void unsupportedBasePathsAndQueriesAreRejected(String configuredBase) {
        assertThrows(IllegalArgumentException.class,
            () -> RailsApiUrlResolver.fromConfiguredBase(configuredBase));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "127.0.0.1:3000",
        "ftp://example.test",
        "https://user@example.test",
        "https://example.test/#fragment",
        "http://example.test",
        "https://example.test:65536"
    })
    void malformedOrUnsafeOriginsAreRejected(String configuredBase) {
        assertThrows(IllegalArgumentException.class,
            () -> RailsApiUrlResolver.fromConfiguredBase(configuredBase));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1:3000",
        "http://localhost:3000",
        "http://[::1]:3000"
    })
    void explicitLoopbackHttpIsAcceptedForDevelopment(String configuredBase) {
        assertEquals("http", RailsApiUrlResolver.fromConfiguredBase(configuredBase)
            .serviceOrigin().getScheme());
    }

    @Test
    void nonLoopbackHttpsIsAccepted() {
        RailsApiUrlResolver resolver = RailsApiUrlResolver.fromConfiguredBase("https://rails.example.test/");

        assertEquals("https://rails.example.test", resolver.serviceOrigin().toString());
    }

    @Test
    void shardPathSegmentIsEncodedAsOneRawValue() {
        RailsApiUrlResolver resolver = localResolver();

        URI uri = resolver.resolvePath(
            Endpoint.WORLD_BOOTSTRAP, Map.of("shard", "Britannia / West"));

        assertEquals("/api/world_bootstrap/Britannia%20%2F%20West", uri.getRawPath());
    }

    @Test
    void queryParametersAreEncodedExactlyOnce() {
        RailsApiUrlResolver resolver = localResolver();

        URI uri = resolver.resolve(
            Endpoint.WORLD_BOOTSTRAP,
            Map.of("shard", "Britannia"),
            Map.of("minecraft_username", "Dev +%2F?")
        );

        assertEquals("minecraft_username=Dev%20%2B%252F%3F", uri.getRawQuery());
    }

    @Test
    void worldBootstrapCompactProfileIsEncodedExactlyOnceWithPlayerParameters() {
        RailsApiUrlResolver resolver = localResolver();

        URI uri = resolver.resolve(
            Endpoint.WORLD_BOOTSTRAP,
            Map.of("shard", "Britannia"),
            Map.of(
                "player_uuid", "uuid/one",
                "minecraft_uuid", "uuid/one",
                "minecraft_username", "Dev +",
                "profile", "minecraft_server"
            )
        );

        assertEquals("/api/world_bootstrap/Britannia", uri.getRawPath());
        assertEquals(
            "minecraft_username=Dev%20%2B&minecraft_uuid=uuid%2Fone&player_uuid=uuid%2Fone&profile=minecraft_server",
            uri.getRawQuery()
        );
        assertEquals(1, occurrences(uri.getRawQuery(), "profile="));
        assertFalse(uri.toString().contains("Shard-Secret"));
    }

    @Test
    void credentialsCannotBeAddedToAnEndpointUrl() {
        RailsApiUrlResolver resolver = localResolver();
        String sentinel = "test-only-secret-sentinel";

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
            Endpoint.WORLD_BOOTSTRAP,
            Map.of("shard", "Britannia"),
            Map.of("shard_secret", sentinel)
        ));
        URI valid = resolver.resolvePath(Endpoint.WORLD_BOOTSTRAP, Map.of("shard", "Britannia"));
        assertFalse(valid.toString().contains(sentinel));
    }

    @Test
    void symbolicLoggingContainsNoPathOrQueryValues() {
        assertEquals("world_bootstrap", Endpoint.WORLD_BOOTSTRAP.symbolicName());
        assertFalse(Endpoint.WORLD_BOOTSTRAP.symbolicName().contains("?"));
        assertEquals("/api/world_bootstrap/:shard", Endpoint.WORLD_BOOTSTRAP.symbolicPath());
    }

    @Test
    void representativeActiveRoutesUseExactlyOneApiNamespace() {
        RailsApiUrlResolver resolver = RailsApiUrlResolver.fromConfiguredBase("https://rails.example.test/api/");

        URI quest = resolver.resolvePath(Endpoint.QUEST_TRANSITION, Map.of("quest_id", "42"));
        URI verification = resolver.resolve(Endpoint.MINECRAFT_VERIFY);
        URI catalog = resolver.resolveQuery(Endpoint.CATALOG, Map.of("city", "New Magincia"));
        URI npc = resolver.resolvePath(Endpoint.NPC_HEARTBEAT, Map.of("npc_id", "npc/one"));

        assertEquals("/api/quests/42/choose", quest.getRawPath());
        assertEquals("/api/minecraft_verifications/verify", verification.getRawPath());
        assertEquals("/api/catalog", catalog.getRawPath());
        assertEquals("city=New%20Magincia", catalog.getRawQuery());
        assertEquals("/api/npcs/npc%2Fone/heartbeat", npc.getRawPath());
        for (URI uri : new URI[] { quest, verification, catalog, npc }) {
            assertEquals(1, occurrences(uri.getRawPath(), "/api/"));
            assertFalse(uri.getRawPath().contains("/api/api/"));
        }
    }

    @Test
    void endpointRejectsMissingPathAndUnknownQueryParameters() {
        RailsApiUrlResolver resolver = localResolver();

        assertThrows(IllegalArgumentException.class,
            () -> resolver.resolve(Endpoint.QUEST_START));
        assertThrows(IllegalArgumentException.class,
            () -> resolver.resolveQuery(Endpoint.MINECRAFT_VERIFY, Map.of("next", "elsewhere")));
    }

    @Test
    void everyClosedEndpointDeclaresTheApiNamespaceSymbolically() {
        for (Endpoint endpoint : Endpoint.values()) {
            assertTrue(endpoint.symbolicPath().startsWith("/api/"));
            assertFalse(endpoint.symbolicPath().contains("/api/api/"));
            assertFalse(endpoint.symbolicPath().contains("?"));
        }
    }

    private static RailsApiUrlResolver localResolver() {
        return RailsApiUrlResolver.fromConfiguredBase("http://127.0.0.1:3000");
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int index = value.indexOf(needle); index >= 0;
             index = value.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }
}
