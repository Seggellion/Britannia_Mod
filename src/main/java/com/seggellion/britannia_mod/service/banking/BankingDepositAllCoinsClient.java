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

/**
 * HTTP client for {@code POST /api/banking/currency/deposit/all/prepare}, built exactly like
 * {@link BankingCurrencyDepositClient}: the same three injectable seams for its one prepare
 * action, and {@code confirm}/{@code cancel} delegated to a composed {@link
 * BankingDepositClientPort} rather than duplicated. Rails' own contract is explicit that a bulk
 * deposit confirms and cancels through the unchanged, operation-type-agnostic endpoints
 * (docs/banking_bulk_currency_deposit.md, "Endpoint"), so there is nothing type-specific to write.
 */
public final class BankingDepositAllCoinsClient implements BankingDepositAllCoinsClientPort {
    public static final int MAX_RESPONSE_BYTES = BankingDepositClient.MAX_RESPONSE_BYTES;

    private final BankingOpenClient.CredentialsProvider credentialsProvider;
    private final BankingDepositClient.TransportSubmitter transportSubmitter;
    private final BankingDepositClient.RequestFactory requestFactory;
    private final BankingDepositClientPort confirmCancelDelegate;

    public BankingDepositAllCoinsClient() {
        this(ServerAuthRegistry::credentials, ServerHttpExecutor::submit, CancellableHttpRequest::new, new BankingDepositClient());
    }

    /** Test-substitution constructor, public for the same reason {@link BankingDepositClient}'s own is. */
    public BankingDepositAllCoinsClient(
            BankingOpenClient.CredentialsProvider credentialsProvider,
            BankingDepositClient.TransportSubmitter transportSubmitter,
            BankingDepositClient.RequestFactory requestFactory,
            BankingDepositClientPort confirmCancelDelegate
    ) {
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
        this.transportSubmitter = Objects.requireNonNull(transportSubmitter, "transportSubmitter");
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
        this.confirmCancelDelegate = Objects.requireNonNull(confirmCancelDelegate, "confirmCancelDelegate");
    }

    @Override
    public CompletableFuture<BankingDepositAllCoinsPrepareResult> prepareDepositAllCoins(
            MinecraftServer server, BankingDepositAllCoinsPrepareRequest request
    ) {
        Objects.requireNonNull(request, "request");
        return dispatch(
                server, RailsApiUrlResolver.Endpoint.BANKING_CURRENCY_DEPOSIT_ALL_PREPARE, serializePrepare(request),
                BankingTransferResponseParser::parseDepositAllCoinsPrepare,
                BankingDepositAllCoinsPrepareResult.LocalFailure::new,
                BankingDepositAllCoinsPrepareResult.TransportFailure::new
        );
    }

    @Override
    public CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request) {
        return confirmCancelDelegate.confirm(server, request);
    }

    @Override
    public CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request) {
        return confirmCancelDelegate.cancel(server, request);
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
                if (!RailsRequestAuthenticator.apply(connection, credentials, body)) {
                    throw new IllegalStateException("credentials_unavailable");
                }
            }, body);
            return parser.parse(response.status(), response.body());
        } catch (CancellableHttpRequest.RequestException failure) {
            return transportFailure.apply(failure.code().safeCode());
        }
    }

    /**
     * All three denominations, always, including zeros -- Rails validates {@code coins} as a
     * strict three-key envelope and rejects a missing key as hard as an unknown one. See
     * {@link BankingDepositAllCoinsPrepareRequest} for why absence is not allowed to mean zero.
     */
    private static byte[] serializePrepare(BankingDepositAllCoinsPrepareRequest request) {
        JsonObject coins = new JsonObject();
        coins.addProperty("gold", request.gold());
        coins.addProperty("silver", request.silver());
        coins.addProperty("copper", request.copper());

        JsonObject payload = new JsonObject();
        payload.addProperty("player_uuid", request.playerUuid().toString());
        payload.addProperty("world_npc_public_id", request.worldNpcPublicId().toString());
        payload.addProperty("idempotency_key", request.idempotencyKey());
        payload.add("coins", coins);
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    @FunctionalInterface
    private interface ResponseParser<R> {
        R parse(int status, byte[] body);
    }
}
