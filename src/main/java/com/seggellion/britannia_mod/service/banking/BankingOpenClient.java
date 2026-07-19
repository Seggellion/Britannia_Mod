package com.seggellion.britannia_mod.service.banking;

import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.server.MinecraftServer;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * HTTP client for {@code POST /api/banking/open}, structurally mirroring
 * {@link com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnRegistrationClient}:
 * the same three injectable seams (credentials, transport submission, connection factory)
 * so tests can substitute a real loopback {@code HttpServer} or a synchronous fake executor
 * without touching production wiring. The real constructor wires the same production
 * collaborators that client uses ({@link ServerAuthRegistry}, {@link ServerHttpExecutor},
 * {@link CancellableHttpRequest}), so the async/off-server-tick-thread behavior is
 * identical, not merely similar.
 */
public final class BankingOpenClient {
    public static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final CredentialsProvider credentialsProvider;
    private final TransportSubmitter transportSubmitter;
    private final RequestFactory requestFactory;

    public BankingOpenClient() {
        this(ServerAuthRegistry::credentials, ServerHttpExecutor::submit, CancellableHttpRequest::new);
    }

    /**
     * Test-substitution constructor. Public (unlike
     * {@code ServiceNpcSpawnRegistrationClient}'s own package-private equivalent) because
     * this client's GameTest-based interaction-level tests live in the {@code gametest}
     * package by this codebase's established convention, not alongside this class.
     */
    public BankingOpenClient(
            CredentialsProvider credentialsProvider, TransportSubmitter transportSubmitter, RequestFactory requestFactory
    ) {
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
        this.transportSubmitter = Objects.requireNonNull(transportSubmitter, "transportSubmitter");
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
    }

    public CompletableFuture<BankingOpenClientResult> submit(MinecraftServer server, BankingOpenRequest request) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(request, "request");
        Optional<ServerCredentials> configured = credentialsProvider.credentials(server);
        if (configured.isEmpty()) {
            return CompletableFuture.completedFuture(new BankingOpenClientResult.LocalFailure("credentials_unavailable"));
        }
        return submitConfigured(server, request, configured.get());
    }

    CompletableFuture<BankingOpenClientResult> submitForTesting(BankingOpenRequest request, ServerCredentials credentials) {
        return submitConfigured(null, request, credentials);
    }

    private CompletableFuture<BankingOpenClientResult> submitConfigured(
            MinecraftServer server, BankingOpenRequest request, ServerCredentials credentials
    ) {
        Optional<UUID> serverKey = credentials.minecraftServerKey();
        if (serverKey.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new BankingOpenClientResult.LocalFailure(credentials.minecraftServerKeyUnavailableCode())
            );
        }

        byte[] body = serialize(request);
        URI endpoint = credentials.apiUrls().resolve(RailsApiUrlResolver.Endpoint.BANKING_OPEN);
        CancellableHttpRequest active = requestFactory.create(endpoint, MAX_RESPONSE_BYTES);

        final CompletableFuture<BankingOpenClientResult> transport;
        try {
            transport = transportSubmitter.submit(server, () -> execute(active, credentials, serverKey.get(), body));
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(new BankingOpenClientResult.TransportFailure("executor_saturated"));
        }
        return transport.handle((value, failure) -> {
            if (failure == null) return value;
            Throwable cause = unwrap(failure);
            active.cancel();
            if (cause instanceof TimeoutException) return new BankingOpenClientResult.TransportFailure("overall_timeout");
            if (cause instanceof RejectedExecutionException) {
                return new BankingOpenClientResult.TransportFailure("executor_saturated");
            }
            return new BankingOpenClientResult.TransportFailure("transport_error");
        });
    }

    private static BankingOpenClientResult execute(
            CancellableHttpRequest active, ServerCredentials credentials, UUID serverKey, byte[] body
    ) {
        try {
            CancellableHttpRequest.Response response = active.execute(connection -> {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Minecraft-Server-Key", serverKey.toString());
                if (!RailsRequestAuthenticator.apply(connection, credentials)) {
                    throw new IllegalStateException("credentials_unavailable");
                }
            }, body);
            return BankingOpenResponseParser.parse(response.status(), response.body());
        } catch (CancellableHttpRequest.RequestException failure) {
            return new BankingOpenClientResult.TransportFailure(failure.code().safeCode());
        }
    }

    private static byte[] serialize(BankingOpenRequest request) {
        JsonObject payload = new JsonObject();
        payload.addProperty("player_uuid", request.playerUuid().toString());
        payload.addProperty("world_npc_public_id", request.worldNpcPublicId().toString());
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    // Public, unlike ServiceNpcSpawnRegistrationClient's package-private equivalents: this
    // client's own interaction-level tests are GameTests in the gametest package (this
    // codebase's established convention for anything exercising a live entity), not
    // co-located plain JUnit tests, so the substitution constructor above and these three
    // seams must be reachable across the package boundary.
    @FunctionalInterface
    public interface CredentialsProvider {
        Optional<ServerCredentials> credentials(MinecraftServer server);
    }

    @FunctionalInterface
    public interface TransportSubmitter {
        CompletableFuture<BankingOpenClientResult> submit(MinecraftServer server, Supplier<BankingOpenClientResult> task);
    }

    @FunctionalInterface
    public interface RequestFactory {
        CancellableHttpRequest create(URI endpoint, int maxResponseBytes);
    }
}
