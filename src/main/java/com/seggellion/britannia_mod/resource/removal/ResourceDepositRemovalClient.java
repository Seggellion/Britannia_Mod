package com.seggellion.britannia_mod.resource.removal;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/** Signed, bounded transport for M6 destructive previews and provenance-aware removals. */
public final class ResourceDepositRemovalClient {
    public static final int MAX_PENDING_RESPONSE_BYTES = 512 * 1024;
    public static final int MAX_RESULT_RESPONSE_BYTES = 64 * 1024;

    public sealed interface FetchResult permits PendingPreviews, PendingRemovals, Failure {}
    public record PendingPreviews(List<ResourceDepositRemovalProtocol.PreviewRequest> requests,
                                  UUID configuredServerKey) implements FetchResult {
        public PendingPreviews { requests = List.copyOf(requests); }
    }
    public record PendingRemovals(List<ResourceDepositRemovalProtocol.RemovalOperation> operations,
                                  UUID configuredServerKey) implements FetchResult {
        public PendingRemovals { operations = List.copyOf(operations); }
    }
    public sealed interface SubmitResult permits Accepted, Failure {}
    public record Accepted(boolean duplicate) implements SubmitResult {}
    public record Failure(String safeCode) implements FetchResult, SubmitResult {}

    public CompletableFuture<FetchResult> fetchPendingPreviews(MinecraftServer server) {
        return fetch(server, RailsApiUrlResolver.Endpoint.RESOURCE_DEPOSIT_REMOVAL_PREVIEWS_PENDING,
                body -> new PendingPreviews(ResourceDepositRemovalProtocol.parsePendingPreviews(body),
                        configuredKey(server)));
    }

    public CompletableFuture<FetchResult> fetchPendingRemovals(MinecraftServer server) {
        return fetch(server, RailsApiUrlResolver.Endpoint.RESOURCE_DEPOSIT_REMOVALS_PENDING,
                body -> new PendingRemovals(ResourceDepositRemovalProtocol.parsePendingRemovals(body),
                        configuredKey(server)));
    }

    public CompletableFuture<SubmitResult> submitPreviewResult(MinecraftServer server,
            ResourceDepositRemovalProtocol.PreviewRequest request,
            ResourceDepositRemovalProtocol.PreviewOutcome outcome) {
        return submit(server, RailsApiUrlResolver.Endpoint.RESOURCE_DEPOSIT_REMOVAL_PREVIEW_RESULT,
                "preview_uuid", request.previewUuid().toString(),
                ResourceDepositRemovalProtocol.encodePreviewResult(request, outcome));
    }

    public CompletableFuture<SubmitResult> submitRemovalResult(MinecraftServer server,
            ResourceDepositRemovalProtocol.RemovalOperation operation,
            ResourceDepositRemovalProtocol.RemovalOutcome outcome) {
        return submit(server, RailsApiUrlResolver.Endpoint.RESOURCE_DEPOSIT_REMOVAL_RESULT,
                "operation_uuid", operation.operationUuid().toString(),
                ResourceDepositRemovalProtocol.encodeRemovalResult(operation, outcome));
    }

    private CompletableFuture<FetchResult> fetch(MinecraftServer server,
            RailsApiUrlResolver.Endpoint endpoint, Function<byte[], FetchResult> parser) {
        Optional<ServerCredentials> configured = ServerAuthRegistry.credentials(server);
        if (configured.isEmpty()) return CompletableFuture.completedFuture(new Failure("credentials_unavailable"));
        ServerCredentials credentials = configured.get();
        if (credentials.minecraftServerKey().isEmpty()) {
            return CompletableFuture.completedFuture(new Failure(credentials.minecraftServerKeyUnavailableCode()));
        }
        CancellableHttpRequest request = new CancellableHttpRequest(
                credentials.apiUrls().resolve(endpoint), MAX_PENDING_RESPONSE_BYTES);
        try {
            return ServerHttpExecutor.submit(server, () -> executeFetch(request, credentials, parser))
                    .handle((value, failure) -> failure == null ? value : transportFailure(request, failure));
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(new Failure("executor_saturated"));
        }
    }

    private CompletableFuture<SubmitResult> submit(MinecraftServer server,
            RailsApiUrlResolver.Endpoint endpoint, String parameter, String value, byte[] body) {
        Optional<ServerCredentials> configured = ServerAuthRegistry.credentials(server);
        if (configured.isEmpty()) return CompletableFuture.completedFuture(new Failure("credentials_unavailable"));
        ServerCredentials credentials = configured.get();
        if (credentials.minecraftServerKey().isEmpty()) {
            return CompletableFuture.completedFuture(new Failure(credentials.minecraftServerKeyUnavailableCode()));
        }
        CancellableHttpRequest request = new CancellableHttpRequest(
                credentials.apiUrls().resolvePath(endpoint, Map.of(parameter, value)),
                MAX_RESULT_RESPONSE_BYTES);
        try {
            return ServerHttpExecutor.submit(server, () -> executeSubmit(request, credentials, body))
                    .handle((result, failure) -> failure == null ? result : transportFailure(request, failure));
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(new Failure("executor_saturated"));
        }
    }

    private static FetchResult executeFetch(CancellableHttpRequest request,
            ServerCredentials credentials, Function<byte[], FetchResult> parser) {
        try {
            CancellableHttpRequest.Response response = request.execute(connection -> {
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Minecraft-Server-Key",
                        credentials.minecraftServerKey().orElseThrow().toString());
                if (!RailsRequestAuthenticator.apply(connection, credentials, new byte[0])) {
                    throw new IllegalStateException("credentials_unavailable");
                }
            });
            if (response.status() != 200) return new Failure(statusCode(response.status()));
            return parser.apply(response.body());
        } catch (CancellableHttpRequest.RequestException failure) {
            return new Failure(failure.code().safeCode());
        } catch (ResourceDepositRemovalProtocol.MalformedProtocolException
                 | JsonParseException | IllegalStateException malformed) {
            return new Failure("malformed_response");
        }
    }

    private static SubmitResult executeSubmit(CancellableHttpRequest request,
            ServerCredentials credentials, byte[] body) {
        try {
            CancellableHttpRequest.Response response = request.execute(connection -> {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Minecraft-Server-Key",
                        credentials.minecraftServerKey().orElseThrow().toString());
                if (!RailsRequestAuthenticator.apply(connection, credentials, body)) {
                    throw new IllegalStateException("credentials_unavailable");
                }
            }, body);
            if (response.status() != 200) return new Failure(statusCode(response.status()));
            JsonObject root = JsonParser.parseString(
                    new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
            return new Accepted(root.has("duplicate") && root.get("duplicate").getAsBoolean());
        } catch (CancellableHttpRequest.RequestException failure) {
            return new Failure(failure.code().safeCode());
        } catch (JsonParseException | IllegalStateException malformed) {
            return new Failure("malformed_response");
        }
    }

    private static UUID configuredKey(MinecraftServer server) {
        return ServerAuthRegistry.credentials(server).flatMap(ServerCredentials::minecraftServerKey)
                .orElseThrow();
    }

    private static String statusCode(int status) {
        return switch (status) {
            case 400 -> "invalid_request";
            case 401, 403 -> "authentication_rejected";
            case 404 -> "removal_request_not_found";
            case 409 -> "removal_stale_or_conflicting";
            case 429 -> "rate_limited";
            case 503 -> "service_unavailable";
            default -> "http_status_failure";
        };
    }

    private static Failure transportFailure(CancellableHttpRequest request, Throwable failure) {
        request.cancel();
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        if (cause instanceof TimeoutException) return new Failure("overall_timeout");
        if (cause instanceof RejectedExecutionException) return new Failure("executor_saturated");
        return new Failure("transport_error");
    }
}
