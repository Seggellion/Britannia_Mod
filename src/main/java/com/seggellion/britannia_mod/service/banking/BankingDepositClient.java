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
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * HTTP client for {@code POST /api/banking/deposit/prepare}, {@code POST /api/banking/confirm},
 * and {@code POST /api/banking/cancel}, structurally identical to {@link BankingOpenClient}:
 * the same three injectable seams (credentials, transport submission, connection factory) so
 * tests can substitute a fake transport without touching production wiring, and the same
 * production collaborators ({@link ServerAuthRegistry}, {@link ServerHttpExecutor}, {@link
 * CancellableHttpRequest}) so the async/off-server-tick-thread behavior is identical to every
 * other Rails call this mod makes, not merely similar.
 */
public final class BankingDepositClient implements BankingDepositClientPort {
    public static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final BankingOpenClient.CredentialsProvider credentialsProvider;
    private final TransportSubmitter transportSubmitter;
    private final RequestFactory requestFactory;

    public BankingDepositClient() {
        this(ServerAuthRegistry::credentials, ServerHttpExecutor::submit, CancellableHttpRequest::new);
    }

    /** Test-substitution constructor, public for the same reason {@link BankingOpenClient}'s own is. */
    public BankingDepositClient(
            BankingOpenClient.CredentialsProvider credentialsProvider, TransportSubmitter transportSubmitter, RequestFactory requestFactory
    ) {
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
        this.transportSubmitter = Objects.requireNonNull(transportSubmitter, "transportSubmitter");
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
    }

    public CompletableFuture<BankingDepositPrepareResult> prepare(MinecraftServer server, BankingDepositPrepareRequest request) {
        Objects.requireNonNull(request, "request");
        return dispatch(
                server, RailsApiUrlResolver.Endpoint.BANKING_DEPOSIT_PREPARE, serializePrepare(request),
                BankingTransferResponseParser::parsePrepare,
                BankingDepositPrepareResult.LocalFailure::new, BankingDepositPrepareResult.TransportFailure::new
        );
    }

    public CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request) {
        Objects.requireNonNull(request, "request");
        return dispatch(
                server, RailsApiUrlResolver.Endpoint.BANKING_CONFIRM, serializeOperation(request),
                BankingTransferResponseParser::parseConfirm,
                BankingConfirmResult.LocalFailure::new, BankingConfirmResult.TransportFailure::new
        );
    }

    public CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request) {
        Objects.requireNonNull(request, "request");
        return dispatch(
                server, RailsApiUrlResolver.Endpoint.BANKING_CANCEL, serializeOperation(request),
                BankingTransferResponseParser::parseCancel,
                BankingCancelResult.LocalFailure::new, BankingCancelResult.TransportFailure::new
        );
    }

    private <R> CompletableFuture<R> dispatch(
            MinecraftServer server, RailsApiUrlResolver.Endpoint endpoint, byte[] body,
            ResponseParser<R> parser, java.util.function.Function<String, R> localFailure,
            java.util.function.Function<String, R> transportFailure
    ) {
        Objects.requireNonNull(server, "server");
        Optional<ServerCredentials> configured = credentialsProvider.credentials(server);
        if (configured.isEmpty()) {
            return CompletableFuture.completedFuture(localFailure.apply("credentials_unavailable"));
        }
        ServerCredentials credentials = configured.get();
        Optional<UUID> serverKey = credentials.minecraftServerKey();
        if (serverKey.isEmpty()) {
            return CompletableFuture.completedFuture(localFailure.apply(credentials.minecraftServerKeyUnavailableCode()));
        }

        URI uri = credentials.apiUrls().resolve(endpoint);
        CancellableHttpRequest active = requestFactory.create(uri, MAX_RESPONSE_BYTES);

        final CompletableFuture<R> transport;
        try {
            transport = transportSubmitter.submit(
                    server, () -> execute(active, credentials, serverKey.get(), body, parser, transportFailure)
            );
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(transportFailure.apply("executor_saturated"));
        }
        return transport.handle((value, failure) -> {
            if (failure == null) return value;
            Throwable cause = unwrap(failure);
            active.cancel();
            if (cause instanceof TimeoutException) return transportFailure.apply("overall_timeout");
            if (cause instanceof RejectedExecutionException) return transportFailure.apply("executor_saturated");
            return transportFailure.apply("transport_error");
        });
    }

    private static <R> R execute(
            CancellableHttpRequest active, ServerCredentials credentials, UUID serverKey, byte[] body,
            ResponseParser<R> parser, java.util.function.Function<String, R> transportFailure
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
            return parser.parse(response.status(), response.body());
        } catch (CancellableHttpRequest.RequestException failure) {
            return transportFailure.apply(failure.code().safeCode());
        }
    }

    private static byte[] serializePrepare(BankingDepositPrepareRequest request) {
        JsonObject item = new JsonObject();
        item.addProperty("schema_version", request.schemaVersion());
        item.addProperty("payload", Base64.getEncoder().encodeToString(request.payload()));
        item.addProperty("fingerprint", request.fingerprint());
        item.addProperty("weight", request.weight());

        JsonObject payload = new JsonObject();
        payload.addProperty("player_uuid", request.playerUuid().toString());
        payload.addProperty("world_npc_public_id", request.worldNpcPublicId().toString());
        payload.addProperty("idempotency_key", request.idempotencyKey());
        payload.add("item", item);
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] serializeOperation(BankingOperationRequest request) {
        JsonObject payload = new JsonObject();
        payload.addProperty("player_uuid", request.playerUuid().toString());
        payload.addProperty("operation_public_id", request.operationPublicId().toString());
        if (request.reason() != null) payload.addProperty("reason", request.reason());
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    @FunctionalInterface
    private interface ResponseParser<R> {
        R parse(int status, byte[] body);
    }

    @FunctionalInterface
    public interface TransportSubmitter {
        <R> CompletableFuture<R> submit(MinecraftServer server, Supplier<R> task);
    }

    @FunctionalInterface
    public interface RequestFactory {
        CancellableHttpRequest create(URI endpoint, int maxResponseBytes);
    }
}
