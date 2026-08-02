package com.seggellion.britannia_mod.worldstate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.server.MinecraftServer;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * HTTP client for {@code GET /api/world_state_changes/:shard}, structurally identical to
 * {@code BankingDepositClient}/{@code ServiceNpcSpawnRegistrationClient}: the same three
 * injectable seams (credentials, transport submission, connection factory) so tests can
 * substitute a fake transport without touching production wiring, and the same production
 * collaborators ({@link ServerAuthRegistry}, {@link ServerHttpExecutor}, {@link
 * CancellableHttpRequest}) so the async/off-server-tick-thread behavior is identical to every
 * other Rails call this mod makes, not merely similar.
 *
 * This client only fetches and parses -- it never applies a delta or triggers a full bootstrap.
 * Semantic acceptance/rejection of a successfully-parsed response is {@link
 * WorldStateSyncValidator}'s job, one layer up in {@link WorldStateSyncPoller}.
 */
public final class WorldStateChangesClient {
    public static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    public sealed interface Result permits Success, Failure {}

    public record Success(WorldStateChangesResponse response) implements Result {}

    /** safeCode never carries raw response text, URLs, or secrets -- same discipline as every other client here. */
    public record Failure(String safeCode) implements Result {}

    private final CredentialsProvider credentialsProvider;
    private final TransportSubmitter transportSubmitter;
    private final RequestFactory requestFactory;

    public WorldStateChangesClient() {
        this(ServerAuthRegistry::credentials, ServerHttpExecutor::submit, CancellableHttpRequest::new);
    }

    /** Test-substitution constructor, public for the same reason {@code BankingOpenClient}'s own is. */
    public WorldStateChangesClient(
            CredentialsProvider credentialsProvider, TransportSubmitter transportSubmitter, RequestFactory requestFactory
    ) {
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
        this.transportSubmitter = Objects.requireNonNull(transportSubmitter, "transportSubmitter");
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
    }

    public CompletableFuture<Result> fetchChangesSince(MinecraftServer server, long fromVersion) {
        Optional<ServerCredentials> configured = credentialsProvider.credentials(server);
        if (configured.isEmpty()) {
            return CompletableFuture.completedFuture(new Failure("credentials_unavailable"));
        }
        return dispatch(server, configured.get(), fromVersion);
    }

    /**
     * Test-only entry point bypassing the {@link CredentialsProvider} lookup (and therefore
     * needing no real {@link MinecraftServer}), mirroring {@code BankingOpenClient}/{@code
     * ServiceNpcSpawnRegistrationClient}'s own {@code submitForTesting} seam.
     */
    CompletableFuture<Result> fetchChangesSinceForTesting(long fromVersion, ServerCredentials credentials) {
        return dispatch(null, credentials, fromVersion);
    }

    private CompletableFuture<Result> dispatch(MinecraftServer server, ServerCredentials credentials, long fromVersion) {
        Optional<UUID> serverKey = credentials.minecraftServerKey();
        if (serverKey.isEmpty()) {
            return CompletableFuture.completedFuture(new Failure(credentials.minecraftServerKeyUnavailableCode()));
        }

        URI uri = credentials.apiUrls().resolve(
                RailsApiUrlResolver.Endpoint.WORLD_STATE_CHANGES,
                Map.of("shard", credentials.shardName()),
                Map.of("from_version", Long.toString(fromVersion))
        );
        CancellableHttpRequest active = requestFactory.create(uri, MAX_RESPONSE_BYTES);

        final CompletableFuture<Result> transport;
        try {
            transport = transportSubmitter.submit(server, () -> execute(active, credentials, serverKey.get()));
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(new Failure("executor_saturated"));
        }
        return transport.handle((value, failure) -> {
            if (failure == null) return value;
            Throwable cause = unwrap(failure);
            active.cancel();
            if (cause instanceof TimeoutException) return new Failure("overall_timeout");
            if (cause instanceof RejectedExecutionException) return new Failure("executor_saturated");
            return new Failure("transport_error");
        });
    }

    private static Result execute(CancellableHttpRequest active, ServerCredentials credentials, UUID serverKey) {
        try {
            CancellableHttpRequest.Response response = active.execute(connection -> {
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Minecraft-Server-Key", serverKey.toString());
                // Milestone 14 Security Slice 2: the one call site chosen to prove HMAC request
                // signing end to end first -- read-only, no request body, no real financial
                // consequence if this integration has a mistake. The empty byte array is the
                // real signed body for a bodyless GET, not a placeholder -- it must match
                // exactly what Rails' own body-hash check computes for an absent body.
                if (!RailsRequestAuthenticator.apply(connection, credentials, new byte[0])) {
                    throw new IllegalStateException("credentials_unavailable");
                }
            });
            if (response.status() != 200) {
                // 409/503 are the two new real outcomes this endpoint can now return once
                // signed: Rails' own Api::ShardServerAuthentication#render_signature_failure
                // maps "the same nonce was already reserved" to 409 (a real, valid signature
                // describing an already-processed request, not an auth failure) and "Redis was
                // unreachable when checking replay protection" to 503 (Rails' own fail-closed
                // decision -- Security Slice 1 -- rejects a signed request rather than silently
                // treating it as unsigned). Both are given their own distinct, non-crashing
                // safe code here rather than falling into the generic default bucket, matching
                // this client's own established one-safe-code-per-real-cause discipline; a
                // signing mistake on this client's own side would show up as
                // authentication_rejected (401), the existing bucket, not a new one -- that
                // case is not expected to be reachable if the canonical string is constructed
                // correctly, and this client does not try to guess otherwise.
                String code = switch (response.status()) {
                    case 400 -> "invalid_request";
                    case 401, 403 -> "authentication_rejected";
                    case 404 -> "route_failure";
                    case 409 -> "signature_replayed";
                    case 503 -> "signature_store_unavailable";
                    default -> "http_status_failure";
                };
                return new Failure(code);
            }

            JsonObject root = JsonParser.parseString(
                    new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
            return new Success(WorldStateChangesResponseParser.parse(root));
        } catch (CancellableHttpRequest.RequestException requestFailure) {
            return new Failure(requestFailure.code().safeCode());
        } catch (WorldStateChangesResponseParser.MalformedResponseException
                 | JsonParseException | IllegalStateException malformed) {
            return new Failure("malformed_response");
        }
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    @FunctionalInterface
    public interface CredentialsProvider {
        Optional<ServerCredentials> credentials(MinecraftServer server);
    }

    @FunctionalInterface
    public interface TransportSubmitter {
        CompletableFuture<Result> submit(MinecraftServer server, Supplier<Result> task);
    }

    @FunctionalInterface
    public interface RequestFactory {
        CancellableHttpRequest create(URI endpoint, int maxResponseBytes);
    }
}
