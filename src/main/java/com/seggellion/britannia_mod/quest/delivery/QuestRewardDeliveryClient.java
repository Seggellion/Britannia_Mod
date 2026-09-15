package com.seggellion.britannia_mod.quest.delivery;

import com.google.gson.JsonParseException;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.server.MinecraftServer;

import java.net.URI;
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
 * Signed, bounded HTTP transport for the two v2 reward-delivery endpoints (protocol sections 1.6
 * and 1.7), on the same tier and with the same seams as
 * {@link com.seggellion.britannia_mod.resource.preview.ResourceDepositPreviewClient}: shard
 * headers, a mandatory body signature, and {@code Minecraft-Server-Key} from the configured
 * credentials.
 *
 * <p>A 404 is read two ways, deliberately. The result endpoint answers
 * {@code 404 delivery_not_found} for a delivery Rails does not hold for this shard and player --
 * a terminal answer for that delivery. A 404 with no such code is the route itself missing: an
 * older Rails without the v2 endpoints ({@link EndpointUnsupported}), which the caller logs once
 * per boot and stops asking.
 */
public final class QuestRewardDeliveryClient {
    public sealed interface AcknowledgeResult permits Acknowledged, TerminalRejection, EndpointUnsupported, Failure {}
    public sealed interface PendingResult permits PendingFetched, EndpointUnsupported, Failure {}

    public record Acknowledged(QuestRewardDeliveryProtocol.AcknowledgementResponse response) implements AcknowledgeResult {
        public Acknowledged {
            Objects.requireNonNull(response, "response");
        }
    }

    /** {@code 409 conflicting_delivery_result}, {@code 409 player_mismatch}, {@code 404 delivery_not_found}. */
    public record TerminalRejection(String code) implements AcknowledgeResult {
        public TerminalRejection {
            code = code == null || code.isBlank() ? "terminal_rejection" : code;
        }
    }

    public record EndpointUnsupported() implements AcknowledgeResult, PendingResult {}

    public record Failure(String safeCode) implements AcknowledgeResult, PendingResult {
        public Failure {
            safeCode = safeCode == null || safeCode.isBlank() ? "transport_error" : safeCode;
        }
    }

    public record PendingFetched(QuestRewardDeliveryParser.PendingListing listing) implements PendingResult {
        public PendingFetched {
            Objects.requireNonNull(listing, "listing");
        }
    }

    private final CredentialsProvider credentialsProvider;
    private final AcknowledgeTransportSubmitter acknowledgeTransport;
    private final PendingTransportSubmitter pendingTransport;
    private final RequestFactory requestFactory;

    public QuestRewardDeliveryClient() {
        this(ServerAuthRegistry::credentials,
            (server, task) -> ServerHttpExecutor.submit(server, task),
            (server, task) -> ServerHttpExecutor.submit(server, task),
            CancellableHttpRequest::new);
    }

    /** Test seams, mirroring the repository's established server HTTP clients. */
    public QuestRewardDeliveryClient(CredentialsProvider credentialsProvider,
                                     AcknowledgeTransportSubmitter acknowledgeTransport,
                                     PendingTransportSubmitter pendingTransport,
                                     RequestFactory requestFactory) {
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
        this.acknowledgeTransport = Objects.requireNonNull(acknowledgeTransport, "acknowledgeTransport");
        this.pendingTransport = Objects.requireNonNull(pendingTransport, "pendingTransport");
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
    }

    public CompletableFuture<AcknowledgeResult> acknowledge(MinecraftServer server,
                                                            QuestRewardDeliveryProtocol.AcknowledgementRequest request) {
        Optional<ServerCredentials> configured = credentialsProvider.credentials(server);
        if (configured.isEmpty()) return CompletableFuture.completedFuture(new Failure("credentials_unavailable"));
        ServerCredentials credentials = configured.get();
        Optional<UUID> serverKey = credentials.minecraftServerKey();
        if (serverKey.isEmpty()) {
            return CompletableFuture.completedFuture(new Failure(credentials.minecraftServerKeyUnavailableCode()));
        }

        byte[] body = QuestRewardDeliveryProtocol.encodeResult(request);
        URI uri = credentials.apiUrls().resolvePath(
            RailsApiUrlResolver.Endpoint.QUEST_REWARD_DELIVERY_RESULT,
            Map.of("delivery_uuid", request.deliveryUuid().toString()));
        CancellableHttpRequest active = requestFactory.create(uri, QuestRewardDeliveryProtocol.MAX_RESULT_RESPONSE_BYTES);
        try {
            return acknowledgeTransport.submit(server, () -> executeAcknowledge(active, credentials, serverKey.get(), body))
                .handle((value, failure) -> failure == null ? value : transportFailure(active, failure));
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(new Failure("executor_saturated"));
        }
    }

    public CompletableFuture<PendingResult> fetchPending(MinecraftServer server, UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Optional<ServerCredentials> configured = credentialsProvider.credentials(server);
        if (configured.isEmpty()) return CompletableFuture.completedFuture(new Failure("credentials_unavailable"));
        ServerCredentials credentials = configured.get();
        Optional<UUID> serverKey = credentials.minecraftServerKey();
        if (serverKey.isEmpty()) {
            return CompletableFuture.completedFuture(new Failure(credentials.minecraftServerKeyUnavailableCode()));
        }

        URI uri = credentials.apiUrls().resolveQuery(
            RailsApiUrlResolver.Endpoint.QUEST_REWARD_DELIVERIES_PENDING,
            Map.of("player_uuid", playerUuid.toString()));
        CancellableHttpRequest active = requestFactory.create(uri, QuestRewardDeliveryProtocol.MAX_PENDING_RESPONSE_BYTES);
        try {
            return pendingTransport.submit(server, () -> executePending(active, credentials, serverKey.get()))
                .handle((value, failure) -> failure == null ? value : transportFailure(active, failure));
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(new Failure("executor_saturated"));
        }
    }

    private static AcknowledgeResult executeAcknowledge(CancellableHttpRequest active, ServerCredentials credentials,
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
            return classifyAcknowledge(response.status(), response.body());
        } catch (CancellableHttpRequest.RequestException requestFailure) {
            return new Failure(requestFailure.code().safeCode());
        } catch (QuestRewardDeliveryProtocol.MalformedProtocolException | JsonParseException | IllegalStateException malformed) {
            return new Failure("malformed_response");
        }
    }

    private static PendingResult executePending(CancellableHttpRequest active, ServerCredentials credentials,
                                                UUID serverKey) {
        try {
            CancellableHttpRequest.Response response = active.execute(connection -> {
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Minecraft-Server-Key", serverKey.toString());
                if (!RailsRequestAuthenticator.apply(connection, credentials, new byte[0])) {
                    throw new IllegalStateException("credentials_unavailable");
                }
            });
            return classifyPending(response.status(), response.body());
        } catch (CancellableHttpRequest.RequestException requestFailure) {
            return new Failure(requestFailure.code().safeCode());
        } catch (QuestRewardDeliveryParser.MalformedDeliveryException
                 | QuestRewardDeliveryProtocol.MalformedProtocolException
                 | JsonParseException | IllegalStateException malformed) {
            return new Failure("malformed_response");
        }
    }

    /** Package-visible so the status mapping can be tested without a connection. */
    static AcknowledgeResult classifyAcknowledge(int status, byte[] body) {
        if (status == 200) return new Acknowledged(QuestRewardDeliveryProtocol.parseResult(body));
        String code = QuestRewardDeliveryProtocol.errorCode(body);
        return switch (status) {
            case 404 -> QuestRewardDeliveryProtocol.ERROR_DELIVERY_NOT_FOUND.equals(code)
                ? new TerminalRejection(code) : new EndpointUnsupported();
            case 409 -> new TerminalRejection(code.isEmpty() ? "conflict" : code);
            default -> new Failure(statusCode(status));
        };
    }

    static PendingResult classifyPending(int status, byte[] body) {
        if (status == 200) {
            return new PendingFetched(QuestRewardDeliveryParser.parsePendingListing(QuestRewardDeliveryProtocol.object(body)));
        }
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
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof TimeoutException) return new Failure("overall_timeout");
        if (cause instanceof RejectedExecutionException) return new Failure("executor_saturated");
        return new Failure("transport_error");
    }

    @FunctionalInterface
    public interface CredentialsProvider {
        Optional<ServerCredentials> credentials(MinecraftServer server);
    }

    @FunctionalInterface
    public interface AcknowledgeTransportSubmitter {
        CompletableFuture<AcknowledgeResult> submit(MinecraftServer server, Supplier<AcknowledgeResult> task);
    }

    @FunctionalInterface
    public interface PendingTransportSubmitter {
        CompletableFuture<PendingResult> submit(MinecraftServer server, Supplier<PendingResult> task);
    }

    @FunctionalInterface
    public interface RequestFactory {
        CancellableHttpRequest create(URI endpoint, int maxResponseBytes);
    }
}
