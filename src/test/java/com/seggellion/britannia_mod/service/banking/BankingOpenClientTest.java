package com.seggellion.britannia_mod.service.banking;

import com.seggellion.britannia_mod.server.auth.RequestSignature;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.auth.ServerCredentialsTestFactory;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real HTTP round trip against a loopback server, mirroring
 * {@code ServiceNpcSpawnRegistrationClientTest} exactly: proves the actual headers/body
 * this client sends and that a well-formed response parses through to the caller intact.
 */
class BankingOpenClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsOneAuthenticatedPostWithExactBodyAndParsesTheResponse() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> shardName = new AtomicReference<>();
        AtomicReference<String> shardSecret = new AtomicReference<>();
        AtomicReference<String> serverKeyHeader = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        UUID playerUuid = UUID.randomUUID();
        UUID worldNpcPublicId = UUID.randomUUID();
        UUID accountPublicId = UUID.randomUUID();
        UUID serverKey = UUID.randomUUID();

        start(exchange -> {
            method.set(exchange.getRequestMethod());
            shardName.set(exchange.getRequestHeaders().getFirst("Shard-Name"));
            shardSecret.set(exchange.getRequestHeaders().getFirst("Shard-Secret"));
            serverKeyHeader.set(exchange.getRequestHeaders().getFirst("Minecraft-Server-Key"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("""
                    {"protocol_version":1,"success":true,"outcome":"OPENED","retryable":false,
                     "account":{"public_id":"%s","banking_mode":"global","city_public_id":null,
                     "weight_limit":250,"current_weight":0.0,"gold_balance":0,"silver_balance":0,
                     "copper_balance":0,"revision":1},
                     "bank_items":{"items":[],"next_cursor":null}}
                    """.formatted(accountPublicId)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        BankingOpenClientResult result = synchronousClient().submitForTesting(
                new BankingOpenRequest(playerUuid, worldNpcPublicId), credentials(serverKey)
        ).get(5, TimeUnit.SECONDS);

        assertEquals("POST", method.get());
        assertEquals("Britannia", shardName.get());
        assertEquals("test-only-secret-sentinel", shardSecret.get());
        assertEquals(serverKey.toString(), serverKeyHeader.get());
        assertEquals("application/json", contentType.get());
        assertEquals("application/json", accept.get());
        assertTrue(body.get().contains(playerUuid.toString()));
        assertTrue(body.get().contains(worldNpcPublicId.toString()));
        assertFalse(body.get().contains("test-only-secret-sentinel"));
        assertFalse(body.get().contains(serverKey.toString()));

        BankingOpenClientResult.Success success = assertInstanceOf(BankingOpenClientResult.Success.class, result);
        assertEquals(accountPublicId, success.account().publicId());
        assertEquals("global", success.account().bankingMode());
    }

    /**
     * Milestone 14 Security Slice 3: proves the signing rollout onto this value-changing
     * (banking) call site the same way {@code WorldStateChangesClientTest} proved it for the
     * first, read-only call site -- but with a real non-empty POST body this time, so the
     * canonical string's body-hash component is genuinely exercised, not just the empty-body
     * GET case. The expected signature is recomputed here independently of {@link
     * RequestSignature} (raw {@code Mac}/{@code MessageDigest}) so this test cannot trivially
     * agree with its own production code.
     */
    @Test
    void sendsAValidHmacSignatureWhoseCanonicalStringMatchesTheRealPostBodyActuallySent() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<byte[]> requestBody = new AtomicReference<>();
        AtomicReference<String> timestampHeader = new AtomicReference<>();
        AtomicReference<String> nonceHeader = new AtomicReference<>();
        AtomicReference<String> signatureHeader = new AtomicReference<>();
        long beforeRequest = System.currentTimeMillis() / 1000;

        start(exchange -> {
            path.set(exchange.getRequestURI().getPath());
            requestBody.set(exchange.getRequestBody().readAllBytes());
            timestampHeader.set(exchange.getRequestHeaders().getFirst(RequestSignature.TIMESTAMP_HEADER));
            nonceHeader.set(exchange.getRequestHeaders().getFirst(RequestSignature.NONCE_HEADER));
            signatureHeader.set(exchange.getRequestHeaders().getFirst(RequestSignature.SIGNATURE_HEADER));
            byte[] response = """
                    {"protocol_version":1,"success":true,"outcome":"OPENED","retryable":false,
                     "account":{"public_id":"11111111-1111-4111-8111-111111111111","banking_mode":"global",
                     "city_public_id":null,"weight_limit":250,"current_weight":0.0,"gold_balance":0,
                     "silver_balance":0,"copper_balance":0,"revision":1},
                     "bank_items":{"items":[],"next_cursor":null}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        BankingOpenClientResult result = synchronousClient().submitForTesting(
                request(), credentials(UUID.randomUUID())
        ).get(5, TimeUnit.SECONDS);
        long afterRequest = System.currentTimeMillis() / 1000;

        assertInstanceOf(BankingOpenClientResult.Success.class, result);
        assertTrue(timestampHeader.get() != null && !timestampHeader.get().isBlank(), "expected an X-Signature-Timestamp header");
        assertTrue(nonceHeader.get() != null && nonceHeader.get().matches("[0-9a-f-]{36}"),
                "expected a UUID-shaped X-Signature-Nonce header, got: " + nonceHeader.get());
        assertTrue(signatureHeader.get() != null && signatureHeader.get().matches("[0-9a-f]{64}"),
                "expected a 64-character lowercase-hex X-Signature header, got: " + signatureHeader.get());

        long timestamp = Long.parseLong(timestampHeader.get());
        assertTrue(timestamp >= beforeRequest && timestamp <= afterRequest,
                "expected the signed timestamp to be the real time this request was actually sent");

        String canonical = String.join("\n", "POST", path.get(), timestampHeader.get(), nonceHeader.get(),
                sha256Hex(requestBody.get()));
        String expectedSignature = hmacSha256Hex("test-only-secret-sentinel", canonical);

        assertEquals(expectedSignature, signatureHeader.get(),
                "the real signature sent over the wire must match Rails' own documented canonical-string algorithm applied to the real POST body that was actually sent");
    }

    private static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static String hmacSha256Hex(String key, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void missingServerKeyBlocksBeforeOpeningAnyConnection() {
        BankingOpenClientResult result = synchronousClient().submitForTesting(
                request(), ServerCredentialsTestFactory.create(URI.create("http://127.0.0.1"), null)
        ).join();
        BankingOpenClientResult.LocalFailure failure = assertInstanceOf(BankingOpenClientResult.LocalFailure.class, result);
        assertEquals("minecraft_server_key_missing", failure.safeCode());
        assertFalse(failure.retryable());
    }

    @Test
    void rejectsWithATellerWrongServerOutcome() throws Exception {
        start(exchange -> {
            byte[] response = """
                    {"protocol_version":1,"success":false,"outcome":"TELLER_WRONG_SERVER","retryable":false}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(422, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        BankingOpenClientResult result = synchronousClient().submitForTesting(
                request(), credentials(UUID.randomUUID())
        ).get(5, TimeUnit.SECONDS);

        BankingOpenClientResult.Rejected rejected = assertInstanceOf(BankingOpenClientResult.Rejected.class, result);
        assertEquals(BankingOpenOutcome.TELLER_WRONG_SERVER, rejected.outcome());
        assertFalse(rejected.retryable());
    }

    @Test
    void overallTimeoutAndExecutorSaturationUseSafeCodes() {
        ServerCredentials looseCredentials = ServerCredentialsTestFactory.create(
                URI.create("http://127.0.0.1"), UUID.randomUUID()
        );
        BankingOpenClient timeout = new BankingOpenClient(
                ignored -> Optional.empty(),
                (ignored, task) -> CompletableFuture.failedFuture(new TimeoutException()),
                CancellableHttpRequest::new
        );
        BankingOpenClientResult.TransportFailure timedOut = assertInstanceOf(
                BankingOpenClientResult.TransportFailure.class,
                timeout.submitForTesting(request(), looseCredentials).join()
        );
        assertEquals("overall_timeout", timedOut.safeCode());

        BankingOpenClient saturated = new BankingOpenClient(
                ignored -> Optional.empty(),
                (ignored, task) -> { throw new RejectedExecutionException(); },
                CancellableHttpRequest::new
        );
        BankingOpenClientResult.TransportFailure rejected = assertInstanceOf(
                BankingOpenClientResult.TransportFailure.class,
                saturated.submitForTesting(request(), looseCredentials).join()
        );
        assertEquals("executor_saturated", rejected.safeCode());
    }

    private BankingOpenClient synchronousClient() {
        return new BankingOpenClient(
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
        server.createContext("/api/banking/open", handler);
        server.start();
    }

    private static BankingOpenRequest request() {
        return new BankingOpenRequest(UUID.randomUUID(), UUID.randomUUID());
    }
}
