package com.seggellion.britannia_mod.quest.handin;

import com.google.gson.JsonParseException;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.server.MinecraftServer;

import java.net.URI;
import java.util.List;
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
 * Signed, bounded HTTP transport for the two v2 hand-in endpoints (protocol section 1.5.3), on the
 * same tier and with the same seams as {@code QuestRewardDeliveryClient}: shard headers, a
 * mandatory body signature, and {@code Minecraft-Server-Key} from the configured credentials.
 *
 * <p>There is no prepare call here, and its absence is a security property rather than an omission.
 * A hand-in is minted by the player's own claim on the existing {@code choose} endpoint, so a shard
 * cannot mint transactions nobody asked for. This client only reports what was already taken, and
 * asks what happened to a report whose answer was lost.
 *
 * <p>A 404 is read two ways, as everywhere else on this tier. {@code 404 handin_not_found} is Rails
 * answering deliberately about a transaction it does not hold for this shard and player. A 404 with
 * no such code is the route itself missing -- an older Rails without the hand-in endpoints -- which
 * the caller logs once per boot and stops asking. Confusing the two would turn "deploy the mod
 * before the seed" into an item quietly stranded.
 */
public final class QuestItemHandinClient {

    public sealed interface ConfirmResult
            permits Answered, EndpointUnsupported, Failure {}

    public sealed interface ReconcileResult
            permits Reconciled, EndpointUnsupported, Failure {}

    /**
     * Rails answered about this transaction. Which answer it was lives in
     * {@link QuestItemHandinProtocol.ConfirmationResponse#result()} -- including the refusals, which
     * arrive as ordinary envelopes on a 409 and are not transport failures.
     */
    public record Answered(QuestItemHandinProtocol.ConfirmationResponse response) implements ConfirmResult {
        public Answered {
            Objects.requireNonNull(response, "response");
        }
    }

    public record Reconciled(QuestItemHandinProtocol.ReconcileResponse response) implements ReconcileResult {
        public Reconciled {
            Objects.requireNonNull(response, "response");
        }
    }

    /** The route does not exist on this Rails. Never confused with a refusal about a transaction. */
    public record EndpointUnsupported() implements ConfirmResult, ReconcileResult {}

    /** Nothing was learned. The caller retries; it must never take this as an outcome. */
    public record Failure(String safeCode) implements ConfirmResult, ReconcileResult {
        public Failure {
            safeCode = safeCode == null || safeCode.isBlank() ? "transport_error" : safeCode;
        }
    }

    private final CredentialsProvider credentialsProvider;
    private final ConfirmTransportSubmitter confirmTransport;
    private final ReconcileTransportSubmitter reconcileTransport;
    private final RequestFactory requestFactory;

    public QuestItemHandinClient() {
        this(ServerAuthRegistry::credentials,
                (server, task) -> ServerHttpExecutor.submit(server, task),
                (server, task) -> ServerHttpExecutor.submit(server, task),
                CancellableHttpRequest::new);
    }

    /** Test seams, mirroring the repository's established server HTTP clients. */
    public QuestItemHandinClient(CredentialsProvider credentialsProvider,
                                 ConfirmTransportSubmitter confirmTransport,
                                 ReconcileTransportSubmitter reconcileTransport,
                                 RequestFactory requestFactory) {
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
        this.confirmTransport = Objects.requireNonNull(confirmTransport, "confirmTransport");
        this.reconcileTransport = Objects.requireNonNull(reconcileTransport, "reconcileTransport");
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
    }

    /**
     * Reports what this server removed, or that it removed nothing.
     *
     * <p>The body is encoded from the caller's already-persisted evidence and nowhere else, so a
     * retry after a restart posts the same bytes the first attempt did.
     */
    public CompletableFuture<ConfirmResult> confirm(MinecraftServer server,
                                                    QuestItemHandinProtocol.ConfirmationRequest request) {
        Objects.requireNonNull(request, "request");
        Optional<ServerCredentials> configured = credentialsProvider.credentials(server);
        if (configured.isEmpty()) return CompletableFuture.completedFuture(new Failure("credentials_unavailable"));
        ServerCredentials credentials = configured.get();
        Optional<UUID> serverKey = credentials.minecraftServerKey();
        if (serverKey.isEmpty()) {
            return CompletableFuture.completedFuture(new Failure(credentials.minecraftServerKeyUnavailableCode()));
        }

        byte[] body = QuestItemHandinProtocol.encodeConfirmation(request);
        URI uri = credentials.apiUrls().resolvePath(
                RailsApiUrlResolver.Endpoint.QUEST_ITEM_HANDIN_RESULT,
                Map.of("handin_uuid", request.handinUuid().toString()));
        CancellableHttpRequest active = requestFactory.create(uri,
                QuestItemHandinProtocol.MAX_RESULT_RESPONSE_BYTES);
        try {
            return confirmTransport
                    .submit(server, () -> executeConfirm(active, credentials, serverKey.get(), body))
                    .handle((value, failure) -> failure == null ? value : transportFailure(active, failure));
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(new Failure("executor_saturated"));
        }
    }

    /** Asks what happened to transactions this server holds but has no final answer for. */
    public CompletableFuture<ReconcileResult> reconcile(MinecraftServer server, UUID playerUuid,
                                                        List<UUID> handinUuids) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        if (handinUuids.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new Reconciled(new QuestItemHandinProtocol.ReconcileResponse(List.of())));
        }
        Optional<ServerCredentials> configured = credentialsProvider.credentials(server);
        if (configured.isEmpty()) return CompletableFuture.completedFuture(new Failure("credentials_unavailable"));
        ServerCredentials credentials = configured.get();
        Optional<UUID> serverKey = credentials.minecraftServerKey();
        if (serverKey.isEmpty()) {
            return CompletableFuture.completedFuture(new Failure(credentials.minecraftServerKeyUnavailableCode()));
        }

        byte[] body = QuestItemHandinProtocol.encodeReconcile(playerUuid, handinUuids);
        URI uri = credentials.apiUrls().resolve(RailsApiUrlResolver.Endpoint.QUEST_ITEM_HANDIN_RECONCILE);
        CancellableHttpRequest active = requestFactory.create(uri,
                QuestItemHandinProtocol.MAX_RECONCILE_RESPONSE_BYTES);
        try {
            return reconcileTransport
                    .submit(server, () -> executeReconcile(active, credentials, serverKey.get(), body))
                    .handle((value, failure) -> failure == null ? value : transportFailure(active, failure));
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(new Failure("executor_saturated"));
        }
    }

    private static ConfirmResult executeConfirm(CancellableHttpRequest active, ServerCredentials credentials,
                                                UUID serverKey, byte[] body) {
        try {
            CancellableHttpRequest.Response response = active.execute(connection -> {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Minecraft-Server-Key", serverKey.toString());
                if (!RailsRequestAuthenticator.apply(connection, credentials, body)) {
                    throw new IllegalStateException("credentials_unavailable");
                }
            }, body);
            return classifyConfirm(response.status(), response.body());
        } catch (CancellableHttpRequest.RequestException requestFailure) {
            return new Failure(requestFailure.code().safeCode());
        } catch (QuestItemHandinProtocol.MalformedHandinException | JsonParseException
                 | IllegalStateException malformed) {
            return new Failure("malformed_response");
        }
    }

    private static ReconcileResult executeReconcile(CancellableHttpRequest active, ServerCredentials credentials,
                                                    UUID serverKey, byte[] body) {
        try {
            CancellableHttpRequest.Response response = active.execute(connection -> {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Minecraft-Server-Key", serverKey.toString());
                if (!RailsRequestAuthenticator.apply(connection, credentials, body)) {
                    throw new IllegalStateException("credentials_unavailable");
                }
            }, body);
            return classifyReconcile(response.status(), response.body());
        } catch (CancellableHttpRequest.RequestException requestFailure) {
            return new Failure(requestFailure.code().safeCode());
        } catch (QuestItemHandinProtocol.MalformedHandinException | JsonParseException
                 | IllegalStateException malformed) {
            return new Failure("malformed_response");
        }
    }

    /**
     * Package-visible so the status mapping can be tested without a connection.
     *
     * <p>200 and 409 are both real answers about the transaction and are parsed the same way: a 409
     * is {@code evidence_rejected}, which Rails is careful to keep out of the 404 precisely because
     * a caller reaching it has already proved it owns the transaction and can still send a true
     * report. A 400 is a body this build should never have produced, and retrying identical bytes
     * cannot change it -- but it is still only a {@link Failure}, and the caller deliberately keeps
     * retrying every failure rather than closing a row: the items are already gone, and an
     * unprovable outcome is never an ending. A wasteful retry is the cheaper mistake.
     */
    static ConfirmResult classifyConfirm(int status, byte[] body) {
        if (status == 200 || status == 409) {
            return new Answered(QuestItemHandinProtocol.parseConfirmation(body));
        }
        if (status == 404) {
            return QuestItemHandinProtocol.ERROR_HANDIN_NOT_FOUND.equals(QuestItemHandinProtocol.errorCode(body))
                    ? new Answered(QuestItemHandinProtocol.parseConfirmation(body))
                    : new EndpointUnsupported();
        }
        return new Failure(statusCode(status));
    }

    static ReconcileResult classifyReconcile(int status, byte[] body) {
        if (status == 200) return new Reconciled(QuestItemHandinProtocol.parseReconcile(body));
        if (status == 404) return new EndpointUnsupported();
        return new Failure(statusCode(status));
    }

    private static String statusCode(int status) {
        return switch (status) {
            case 400 -> "invalid_request";
            case 401, 403 -> "authentication_rejected";
            case 429 -> "rate_limited";
            case 503 -> "service_unavailable";
            default -> "http_status_failure";
        };
    }

    private static Failure transportFailure(CancellableHttpRequest active, Throwable failure) {
        active.cancel();
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        if (cause instanceof TimeoutException) return new Failure("overall_timeout");
        if (cause instanceof RejectedExecutionException) return new Failure("executor_saturated");
        return new Failure("transport_error");
    }

    @FunctionalInterface
    public interface CredentialsProvider {
        Optional<ServerCredentials> credentials(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ConfirmTransportSubmitter {
        CompletableFuture<ConfirmResult> submit(MinecraftServer server, Supplier<ConfirmResult> task);
    }

    @FunctionalInterface
    public interface ReconcileTransportSubmitter {
        CompletableFuture<ReconcileResult> submit(MinecraftServer server, Supplier<ReconcileResult> task);
    }

    @FunctionalInterface
    public interface RequestFactory {
        CancellableHttpRequest create(URI endpoint, int maxResponseBytes);
    }
}
