package com.seggellion.britannia_mod.quest.action;

import com.google.gson.JsonParseException;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.server.MinecraftServer;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Signed, bounded HTTP transport for {@code POST /api/v2/quest_action_events} (protocol section
 * 2.3), on the same v2 tier and with the same seams as {@code QuestRewardDeliveryClient}: shard
 * headers, a mandatory body signature, and {@code Minecraft-Server-Key} from the configured
 * credentials.
 *
 * <p>A 404 is read two ways, exactly as the delivery client reads it. {@code 404 player_not_found}
 * is an answer about this player that a later attempt can change, so it is retryable. A 404 with
 * no such code is the route itself missing -- an older Rails without the v2 action endpoint
 * ({@link EndpointUnsupported}) -- which section 4 says to log once per boot and stop asking.
 */
public final class QuestActionEventClient {
    public sealed interface SendResult permits Answered, TerminalRejection, EndpointUnsupported, Failure {}

    public record Answered(QuestActionEventProtocol.Response response) implements SendResult {
        public Answered {
            Objects.requireNonNull(response, "response");
        }
    }

    /** {@code 400 invalid_action_event}: an authoring or encoder defect, never retried (section 2.3). */
    public record TerminalRejection(String code) implements SendResult {
        public TerminalRejection {
            code = code == null || code.isBlank() ? "terminal_rejection" : code;
        }
    }

    public record EndpointUnsupported() implements SendResult {}

    public record Failure(String safeCode) implements SendResult {
        public Failure {
            safeCode = safeCode == null || safeCode.isBlank() ? "transport_error" : safeCode;
        }
    }

    private final CredentialsProvider credentialsProvider;
    private final TransportSubmitter transport;
    private final RequestFactory requestFactory;

    public QuestActionEventClient() {
        this(ServerAuthRegistry::credentials, ServerHttpExecutor::submit, CancellableHttpRequest::new);
    }

    /** Test seams, mirroring the repository's established server HTTP clients. */
    public QuestActionEventClient(CredentialsProvider credentialsProvider, TransportSubmitter transport,
                                  RequestFactory requestFactory) {
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
    }

    public CompletableFuture<SendResult> send(MinecraftServer server, byte[] body) {
        Objects.requireNonNull(body, "body");
        Optional<ServerCredentials> configured = credentialsProvider.credentials(server);
        if (configured.isEmpty()) return CompletableFuture.completedFuture(new Failure("credentials_unavailable"));
        ServerCredentials credentials = configured.get();
        Optional<UUID> serverKey = credentials.minecraftServerKey();
        if (serverKey.isEmpty()) {
            return CompletableFuture.completedFuture(new Failure(credentials.minecraftServerKeyUnavailableCode()));
        }

        URI uri = credentials.apiUrls().resolve(RailsApiUrlResolver.Endpoint.QUEST_ACTION_EVENTS);
        CancellableHttpRequest active = requestFactory.create(uri, QuestActionEventProtocol.MAX_RESPONSE_BYTES);
        try {
            return transport.submit(server, () -> execute(active, credentials, serverKey.get(), body))
                .handle((value, failure) -> failure == null ? value : transportFailure(active, failure));
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(new Failure("executor_saturated"));
        }
    }

    private static SendResult execute(CancellableHttpRequest active, ServerCredentials credentials,
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
            return classify(response.status(), response.body());
        } catch (CancellableHttpRequest.RequestException requestFailure) {
            return new Failure(requestFailure.code().safeCode());
        } catch (QuestActionEventProtocol.MalformedActionEventException | JsonParseException
                 | IllegalStateException malformed) {
            return new Failure("malformed_response");
        }
    }

    /** Package-visible so the status mapping is tested without a connection. */
    static SendResult classify(int status, byte[] body) {
        if (status == 200) return new Answered(QuestActionEventProtocol.parse(body));
        String code = QuestActionEventProtocol.errorCode(body);
        return switch (status) {
            case 400 -> new TerminalRejection(code.isEmpty()
                ? QuestActionEventProtocol.ERROR_INVALID_ACTION_EVENT : code);
            case 404 -> QuestActionEventProtocol.ERROR_PLAYER_NOT_FOUND.equals(code)
                ? new Failure(QuestActionEventProtocol.ERROR_PLAYER_NOT_FOUND) : new EndpointUnsupported();
            default -> new Failure(statusCode(status));
        };
    }

    private static String statusCode(int status) {
        return switch (status) {
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
    public interface TransportSubmitter {
        CompletableFuture<SendResult> submit(MinecraftServer server, Supplier<SendResult> task);
    }

    @FunctionalInterface
    public interface RequestFactory {
        CancellableHttpRequest create(URI endpoint, int maxResponseBytes);
    }
}
