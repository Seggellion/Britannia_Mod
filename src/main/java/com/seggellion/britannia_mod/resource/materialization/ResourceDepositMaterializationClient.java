package com.seggellion.britannia_mod.resource.materialization;

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

/** Signed, bounded transport for M5 materialization polling and acknowledgments. */
public final class ResourceDepositMaterializationClient {
    public static final int MAX_PENDING_RESPONSE_BYTES = 512 * 1024;
    public static final int MAX_RESULT_RESPONSE_BYTES = 64 * 1024;

    public sealed interface FetchResult permits Pending, Failure {}
    public record Pending(List<ResourceDepositMaterializationProtocol.Operation> operations,
                          UUID configuredServerKey) implements FetchResult {
        public Pending { operations = List.copyOf(operations); }
    }
    public sealed interface SubmitResult permits Accepted, Failure {}
    public record Accepted(boolean duplicate) implements SubmitResult {}
    public record Failure(String safeCode) implements FetchResult, SubmitResult {}

    public CompletableFuture<FetchResult> fetchPending(MinecraftServer server) {
        Optional<ServerCredentials> configured = ServerAuthRegistry.credentials(server);
        if (configured.isEmpty()) return CompletableFuture.completedFuture(new Failure("credentials_unavailable"));
        ServerCredentials credentials = configured.get();
        Optional<UUID> serverKey = credentials.minecraftServerKey();
        if (serverKey.isEmpty()) {
            return CompletableFuture.completedFuture(new Failure(credentials.minecraftServerKeyUnavailableCode()));
        }
        URI uri = credentials.apiUrls().resolve(
                RailsApiUrlResolver.Endpoint.RESOURCE_DEPOSIT_MATERIALIZATIONS_PENDING);
        CancellableHttpRequest request = new CancellableHttpRequest(uri, MAX_PENDING_RESPONSE_BYTES);
        try {
            return ServerHttpExecutor.submit(server, () -> executeFetch(request, credentials, serverKey.get()))
                    .handle((value, failure) -> failure == null ? value : transportFailure(request, failure));
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(new Failure("executor_saturated"));
        }
    }

    public CompletableFuture<SubmitResult> submitResult(MinecraftServer server,
            ResourceDepositMaterializationProtocol.Operation operation,
            ResourceDepositMaterializationProtocol.Outcome outcome) {
        Optional<ServerCredentials> configured = ServerAuthRegistry.credentials(server);
        if (configured.isEmpty()) return CompletableFuture.completedFuture(new Failure("credentials_unavailable"));
        ServerCredentials credentials = configured.get();
        Optional<UUID> serverKey = credentials.minecraftServerKey();
        if (serverKey.isEmpty()) {
            return CompletableFuture.completedFuture(new Failure(credentials.minecraftServerKeyUnavailableCode()));
        }
        byte[] body = ResourceDepositMaterializationProtocol.encodeResult(operation, outcome);
        URI uri = credentials.apiUrls().resolvePath(
                RailsApiUrlResolver.Endpoint.RESOURCE_DEPOSIT_MATERIALIZATION_RESULT,
                Map.of("operation_uuid", operation.operationUuid().toString()));
        CancellableHttpRequest request = new CancellableHttpRequest(uri, MAX_RESULT_RESPONSE_BYTES);
        try {
            return ServerHttpExecutor.submit(server,
                    () -> executeSubmit(request, credentials, serverKey.get(), body))
                    .handle((value, failure) -> failure == null ? value : transportFailure(request, failure));
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(new Failure("executor_saturated"));
        }
    }

    private static FetchResult executeFetch(CancellableHttpRequest request,
            ServerCredentials credentials, UUID serverKey) {
        try {
            CancellableHttpRequest.Response response = request.execute(connection -> {
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Minecraft-Server-Key", serverKey.toString());
                if (!RailsRequestAuthenticator.apply(connection, credentials, new byte[0])) {
                    throw new IllegalStateException("credentials_unavailable");
                }
            });
            if (response.status() != 200) return new Failure(statusCode(response.status()));
            return new Pending(ResourceDepositMaterializationProtocol.parsePending(response.body()), serverKey);
        } catch (CancellableHttpRequest.RequestException failure) {
            return new Failure(failure.code().safeCode());
        } catch (ResourceDepositMaterializationProtocol.MalformedProtocolException
                 | JsonParseException | IllegalStateException malformed) {
            return new Failure("malformed_response");
        }
    }

    private static SubmitResult executeSubmit(CancellableHttpRequest request,
            ServerCredentials credentials, UUID serverKey, byte[] body) {
        try {
            CancellableHttpRequest.Response response = request.execute(connection -> {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Minecraft-Server-Key", serverKey.toString());
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

    private static String statusCode(int status) {
        return switch (status) {
            case 400 -> "invalid_request";
            case 401, 403 -> "authentication_rejected";
            case 404 -> "materialization_not_found";
            case 409 -> "materialization_stale_or_conflicting";
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
