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
 * HTTP client for {@code POST /api/banking/cheque/redeem_stored} -- the same plumbing {@link
 * BankingChequeRedemptionClient} uses (credentials lookup, server-key header, timeout/transport
 * mapping), pointed at the stored-redemption endpoint with a different body.
 *
 * <p>The response is parsed by {@link BankingTransferResponseParser#parseChequeRedeem} unchanged,
 * because the two endpoints answer in the same envelope: {@code CHEQUE_REDEEMED} with a {@code
 * bank_cheque.public_id}, or a rejection carrying an outcome that already exists in {@link
 * BankingTransferOutcome}. Rails' own doc states this endpoint introduces no new outcome string.
 * The extra {@code account} and {@code bank_item} objects it also returns are simply not read --
 * this client's standing policy is to parse only what a caller needs, and the authoritative
 * balance arrives through the {@code bank.open} refresh the proxy triggers anyway.
 */
public final class BankingStoredChequeRedemptionClient implements BankingStoredChequeRedemptionClientPort {
    public static final int MAX_RESPONSE_BYTES = BankingDepositClient.MAX_RESPONSE_BYTES;

    private final BankingOpenClient.CredentialsProvider credentialsProvider;
    private final BankingDepositClient.TransportSubmitter transportSubmitter;
    private final BankingDepositClient.RequestFactory requestFactory;

    public BankingStoredChequeRedemptionClient() {
        this(ServerAuthRegistry::credentials, ServerHttpExecutor::submit, CancellableHttpRequest::new);
    }

    /** Test-substitution constructor, public for the same reason every sibling client's own is. */
    public BankingStoredChequeRedemptionClient(
            BankingOpenClient.CredentialsProvider credentialsProvider,
            BankingDepositClient.TransportSubmitter transportSubmitter,
            BankingDepositClient.RequestFactory requestFactory
    ) {
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
        this.transportSubmitter = Objects.requireNonNull(transportSubmitter, "transportSubmitter");
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
    }

    @Override
    public CompletableFuture<BankingChequeRedemptionResult> redeemStored(
            MinecraftServer server, BankingStoredChequeRedemptionRequest request
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(request, "request");

        Optional<ServerCredentials> configured = credentialsProvider.credentials(server);
        if (configured.isEmpty()) {
            return CompletableFuture.completedFuture(new BankingChequeRedemptionResult.LocalFailure("credentials_unavailable"));
        }
        ServerCredentials credentials = configured.get();
        Optional<UUID> serverKey = credentials.minecraftServerKey();
        if (serverKey.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new BankingChequeRedemptionResult.LocalFailure(credentials.minecraftServerKeyUnavailableCode()));
        }

        URI uri = credentials.apiUrls().resolve(RailsApiUrlResolver.Endpoint.BANKING_CHEQUE_REDEEM_STORED);
        CancellableHttpRequest active = requestFactory.create(uri, MAX_RESPONSE_BYTES);
        byte[] body = serialize(request);

        final CompletableFuture<BankingChequeRedemptionResult> transport;
        try {
            transport = transportSubmitter.submit(server, () -> execute(active, credentials, serverKey.get(), body));
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(new BankingChequeRedemptionResult.TransportFailure("executor_saturated"));
        }
        return transport.handle((value, failure) -> {
            if (failure == null) return value;
            Throwable cause = unwrap(failure);
            active.cancel();
            if (cause instanceof TimeoutException) return new BankingChequeRedemptionResult.TransportFailure("overall_timeout");
            if (cause instanceof RejectedExecutionException) return new BankingChequeRedemptionResult.TransportFailure("executor_saturated");
            return new BankingChequeRedemptionResult.TransportFailure("transport_error");
        });
    }

    private static BankingChequeRedemptionResult execute(
            CancellableHttpRequest active, ServerCredentials credentials, UUID serverKey, byte[] body
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
            return BankingTransferResponseParser.parseChequeRedeem(response.status(), response.body());
        } catch (CancellableHttpRequest.RequestException failure) {
            return new BankingChequeRedemptionResult.TransportFailure(failure.code().safeCode());
        }
    }

    /** Exposes the exact bytes on the wire, for the same reason the deposit client does. */
    public static byte[] serializeForTesting(BankingStoredChequeRedemptionRequest request) {
        return serialize(request);
    }

    private static byte[] serialize(BankingStoredChequeRedemptionRequest request) {
        JsonObject payload = new JsonObject();
        payload.addProperty("player_uuid", request.playerUuid().toString());
        payload.addProperty("world_npc_public_id", request.worldNpcPublicId().toString());
        payload.addProperty("bank_item_public_id", request.bankItemPublicId().toString());
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }
}
