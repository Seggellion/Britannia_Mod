package com.seggellion.britannia_mod.service.spawn;

import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.auth.ServerCredentialsTestFactory;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ServiceNpcSpawnRegistrationClientTest {
    private HttpServer server;
    private ExecutorService executor;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void sendsOneAuthenticatedPostWithExactBodyAndNoAutomaticRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> shardName = new AtomicReference<>();
        AtomicReference<String> shardSecret = new AtomicReference<>();
        AtomicReference<String> serverKeyHeader = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        ServiceNpcSpawnOperationRequest request = request();
        UUID serverKey = UUID.randomUUID();
        start(exchange -> {
            calls.incrementAndGet();
            method.set(exchange.getRequestMethod());
            shardName.set(exchange.getRequestHeaders().getFirst("Shard-Name"));
            shardSecret.set(exchange.getRequestHeaders().getFirst("Shard-Secret"));
            serverKeyHeader.set(exchange.getRequestHeaders().getFirst("Minecraft-Server-Key"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = success(request).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        ServiceNpcSpawnClientResult result = synchronousClient().submitForTesting(
            request, credentials(serverKey)
        ).future().get(5, TimeUnit.SECONDS);

        assertEquals(ServiceNpcSpawnClientResult.Disposition.SUCCESS, result.disposition());
        assertEquals(1, calls.get());
        assertEquals("POST", method.get());
        assertEquals("Britannia", shardName.get());
        assertEquals("test-only-secret-sentinel", shardSecret.get());
        assertEquals(serverKey.toString(), serverKeyHeader.get());
        assertEquals("application/json", contentType.get());
        assertEquals("application/json", accept.get());
        assertArrayEquals(ServiceNpcSpawnRequestSerializer.serialize(request),
            body.get().getBytes(StandardCharsets.UTF_8));
        assertFalse(body.get().contains("test-only-secret-sentinel"));
        assertFalse(body.get().contains(serverKey.toString()));
    }

    @Test
    void missingServerKeyBlocksOnlyThisClientBeforeOpeningConnection() {
        ServiceNpcSpawnClientResult.LocalFailure result =
            (ServiceNpcSpawnClientResult.LocalFailure) synchronousClient()
                .submitForTesting(request(), ServerCredentialsTestFactory.create(
                    java.net.URI.create("http://127.0.0.1"), null
                )).future().join();
        assertEquals("minecraft_server_key_missing", result.safeCode());
        assertEquals(ServiceNpcSpawnClientResult.Disposition.CONFIGURATION_BLOCKED, result.disposition());
    }

    @Test
    void overallTimeoutAndExecutorSaturationUseSafeCodes() {
        ServiceNpcSpawnRegistrationClient timeout = new ServiceNpcSpawnRegistrationClient(
            ignored -> Optional.empty(),
            (ignored, task) -> CompletableFuture.failedFuture(new TimeoutException()),
            CancellableHttpRequest::new
        );
        ServiceNpcSpawnClientResult.TransportFailure timedOut =
            (ServiceNpcSpawnClientResult.TransportFailure) timeout.submitForTesting(
                request(), ServerCredentialsTestFactory.create(java.net.URI.create("http://127.0.0.1"), UUID.randomUUID())
            ).future().join();
        assertEquals("overall_timeout", timedOut.safeCode());

        ServiceNpcSpawnRegistrationClient saturated = new ServiceNpcSpawnRegistrationClient(
            ignored -> Optional.empty(),
            (ignored, task) -> { throw new RejectedExecutionException(); },
            CancellableHttpRequest::new
        );
        ServiceNpcSpawnClientResult.TransportFailure rejected =
            (ServiceNpcSpawnClientResult.TransportFailure) saturated.submitForTesting(
                request(), ServerCredentialsTestFactory.create(java.net.URI.create("http://127.0.0.1"), UUID.randomUUID())
            ).future().join();
        assertEquals("executor_saturated", rejected.safeCode());
    }

    @Test
    void explicitCancellationCompletesSafelyAndDoesNotRetry() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        executor = Executors.newSingleThreadExecutor();
        ServiceNpcSpawnRegistrationClient client = new ServiceNpcSpawnRegistrationClient(
            ignored -> Optional.empty(),
            (ignored, task) -> CompletableFuture.supplyAsync(task, executor),
            CancellableHttpRequest::new
        );
        ServiceNpcSpawnRequestHandle handle = client.submitForTesting(request(), credentials(UUID.randomUUID()));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertTrue(handle.cancel());
        assertInstanceOf(ServiceNpcSpawnClientResult.Cancelled.class, handle.future().get(5, TimeUnit.SECONDS));
        release.countDown();
        assertEquals(1, calls.get());
    }

    private ServiceNpcSpawnRegistrationClient synchronousClient() {
        return new ServiceNpcSpawnRegistrationClient(
            ignored -> Optional.empty(),
            (ignored, task) -> CompletableFuture.completedFuture(task.get()),
            CancellableHttpRequest::new
        );
    }

    private ServerCredentials credentials(UUID key) {
        return ServerCredentialsTestFactory.create(
            java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()), key
        );
    }

    private void start(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/service_npc_spawn_operations", handler);
        server.start();
    }

    private static ServiceNpcSpawnOperationRequest request() {
        return new ServiceNpcSpawnOperationRequest(
            1, UUID.fromString("11111111-2222-4333-8444-555555555555"),
            ServiceNpcSpawnOperation.UPSERT,
            UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"), 3, "Britannia",
            ResourceLocation.parse("minecraft:overworld"), 1, 64, 2,
            UUID.fromString("12345678-1234-4234-8234-123456789abc"), "bank_teller", true
        );
    }

    private static String success(ServiceNpcSpawnOperationRequest request) {
        return """
            {"protocol_version":1,"success":true,"outcome":"APPLIED","retryable":false,
             "operation_id":"%s","spawn_uuid":"%s","submitted_revision":3,
             "acknowledged_revision":3,"registration_state":"LIVE",
             "acknowledged_at":"2026-07-16T12:00:00Z","created":true}
            """.formatted(request.operationId(), request.spawnUuid());
    }
}
