package com.seggellion.britannia_mod.worldstate;

import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.auth.ServerCredentialsTestFactory;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Real HTTP round trip against a loopback server, mirroring {@code BankingOpenClientTest}/
 * {@code ServiceNpcSpawnRegistrationClientTest} exactly: proves the actual headers/query this
 * client sends, that a well-formed response parses through intact, and -- the one proof those
 * precedents don't need because their callers apply results synchronously on the tick thread --
 * that the HTTP work genuinely runs on a different thread than the caller, using a real,
 * deterministically-named substitute pool rather than just checking "it didn't hang."
 */
class WorldStateChangesClientTest {
    private HttpServer server;
    private ExecutorService executor;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void sendsOneAuthenticatedGetWithExactQueryAndParsesTheResponse() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> shardName = new AtomicReference<>();
        AtomicReference<String> shardSecret = new AtomicReference<>();
        AtomicReference<String> serverKeyHeader = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        UUID serverKey = UUID.randomUUID();

        start(exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getQuery());
            shardName.set(exchange.getRequestHeaders().getFirst("Shard-Name"));
            shardSecret.set(exchange.getRequestHeaders().getFirst("Shard-Secret"));
            serverKeyHeader.set(exchange.getRequestHeaders().getFirst("Minecraft-Server-Key"));
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            byte[] response = wellFormedBody().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        WorldStateChangesClient.Result result = synchronousClient()
                .fetchChangesSinceForTesting(7L, credentials(serverKey))
                .get(5, TimeUnit.SECONDS);

        assertEquals("GET", method.get());
        assertEquals("/api/world_state_changes/Britannia", path.get());
        assertEquals("from_version=7", query.get());
        assertEquals("Britannia", shardName.get());
        assertEquals("test-only-secret-sentinel", shardSecret.get());
        assertEquals(serverKey.toString(), serverKeyHeader.get());
        assertEquals("application/json", accept.get());

        WorldStateChangesClient.Success success = assertInstanceOf(WorldStateChangesClient.Success.class, result);
        assertEquals(1, success.response().schemaVersion());
        assertEquals(2, success.response().changes().size());
    }

    @Test
    void missingServerKeyBlocksBeforeOpeningAnyConnection() {
        WorldStateChangesClient.Result result = synchronousClient()
                .fetchChangesSinceForTesting(0L, ServerCredentialsTestFactory.create(URI.create("http://127.0.0.1"), null))
                .join();
        WorldStateChangesClient.Failure failure = assertInstanceOf(WorldStateChangesClient.Failure.class, result);
        assertEquals("minecraft_server_key_missing", failure.safeCode());
    }

    @Test
    void nonSuccessHttpStatusesMapToDistinctSafeCodes() throws Exception {
        start(exchange -> {
            exchange.sendResponseHeaders(422, -1);
            exchange.close();
        });

        WorldStateChangesClient.Result result = synchronousClient()
                .fetchChangesSinceForTesting(0L, credentials(UUID.randomUUID()))
                .get(5, TimeUnit.SECONDS);

        WorldStateChangesClient.Failure failure = assertInstanceOf(WorldStateChangesClient.Failure.class, result);
        assertEquals("http_status_failure", failure.safeCode());
    }

    @Test
    void aTrulyMalformedResponseBodyIsRejectedAsMalformedNotACrash() throws Exception {
        start(exchange -> {
            byte[] response = "{\"schema_version\":1,\"changes\":[".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        WorldStateChangesClient.Result result = synchronousClient()
                .fetchChangesSinceForTesting(0L, credentials(UUID.randomUUID()))
                .get(5, TimeUnit.SECONDS);

        WorldStateChangesClient.Failure failure = assertInstanceOf(WorldStateChangesClient.Failure.class, result);
        assertEquals("malformed_response", failure.safeCode());
    }

    @Test
    void aWellFormedButIncompleteResponseBodyIsRejectedAsMalformedNotACrash() throws Exception {
        start(exchange -> {
            byte[] response = "{\"schema_version\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        WorldStateChangesClient.Result result = synchronousClient()
                .fetchChangesSinceForTesting(0L, credentials(UUID.randomUUID()))
                .get(5, TimeUnit.SECONDS);

        WorldStateChangesClient.Failure failure = assertInstanceOf(WorldStateChangesClient.Failure.class, result);
        assertEquals("malformed_response", failure.safeCode());
    }

    @Test
    void overallTimeoutAndExecutorSaturationUseSafeCodes() {
        ServerCredentials looseCredentials =
                ServerCredentialsTestFactory.create(URI.create("http://127.0.0.1"), UUID.randomUUID());

        WorldStateChangesClient timeout = new WorldStateChangesClient(
                ignored -> Optional.empty(),
                (ignored, task) -> CompletableFuture.failedFuture(new TimeoutException()),
                CancellableHttpRequest::new
        );
        WorldStateChangesClient.Failure timedOut = assertInstanceOf(
                WorldStateChangesClient.Failure.class,
                timeout.fetchChangesSinceForTesting(0L, looseCredentials).join()
        );
        assertEquals("overall_timeout", timedOut.safeCode());

        WorldStateChangesClient saturated = new WorldStateChangesClient(
                ignored -> Optional.empty(),
                (ignored, task) -> { throw new RejectedExecutionException(); },
                CancellableHttpRequest::new
        );
        WorldStateChangesClient.Failure rejected = assertInstanceOf(
                WorldStateChangesClient.Failure.class,
                saturated.fetchChangesSinceForTesting(0L, looseCredentials).join()
        );
        assertEquals("executor_saturated", rejected.safeCode());
    }

    @Test
    void theHttpCallGenuinelyRunsOnADifferentThreadThanTheCaller() throws Exception {
        // The loopback HttpServer services every accepted connection on its own internal
        // dispatch thread regardless of which thread the client used to open it, so that
        // thread cannot prove anything about the CLIENT's threading. The proof has to capture
        // the thread the client's own blocking work (the Supplier handed to TransportSubmitter
        // -- connecting, reading the response, parsing JSON) actually executes on.
        String callingThreadName = Thread.currentThread().getName();
        AtomicReference<String> executionThreadName = new AtomicReference<>();
        start(exchange -> {
            byte[] response = wellFormedBody().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        ThreadFactory namedFactory = runnable -> {
            Thread thread = new Thread(runnable, "world-state-changes-test-pool");
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newSingleThreadExecutor(namedFactory);
        WorldStateChangesClient offThreadClient = new WorldStateChangesClient(
                ignored -> Optional.empty(),
                (ignored, task) -> CompletableFuture.supplyAsync(() -> {
                    executionThreadName.set(Thread.currentThread().getName());
                    return task.get();
                }, executor),
                CancellableHttpRequest::new
        );

        WorldStateChangesClient.Result result = offThreadClient
                .fetchChangesSinceForTesting(0L, credentials(UUID.randomUUID()))
                .get(5, TimeUnit.SECONDS);

        assertInstanceOf(WorldStateChangesClient.Success.class, result);
        assertEquals("world-state-changes-test-pool", executionThreadName.get(),
                "the client's blocking HTTP work did not run on the expected off-thread pool");
        assertNotEquals(callingThreadName, executionThreadName.get(),
                "the client's blocking HTTP work ran on the calling thread instead of off-thread");
    }

    private WorldStateChangesClient synchronousClient() {
        return new WorldStateChangesClient(
                ignored -> Optional.empty(),
                (ignored, task) -> CompletableFuture.completedFuture(task.get()),
                CancellableHttpRequest::new
        );
    }

    private ServerCredentials credentials(UUID key) {
        return ServerCredentialsTestFactory.create(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), key
        );
    }

    private void start(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/world_state_changes/Britannia", handler);
        server.start();
    }

    private static String wellFormedBody() {
        return """
                {"schema_version":1,"shard_public_id":"11111111-1111-4111-8111-111111111111",
                 "from_version":7,"to_version":9,"current_version":9,"full_bootstrap_required":false,
                 "changes":[
                   {"version":8,"change_type":"created","resource_type":"npc_spawn_assignment",
                    "resource_id":"aaaaaaaa-1111-4111-8111-111111111111","resource_revision":1,
                    "payload":{"status":"active"},"created_at":"2026-07-16T12:00:00.000000Z"},
                   {"version":9,"change_type":"closed","resource_type":"npc_spawn_assignment",
                    "resource_id":"aaaaaaaa-1111-4111-8111-111111111111","resource_revision":2,
                    "payload":{"status":"closed"},"created_at":"2026-07-16T12:01:00.000000Z"}
                 ]}
                """;
    }
}
