package com.seggellion.britannia_mod.service.spawn;

import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.server.MinecraftServer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class ServiceNpcSpawnRegistrationClient {
    public static final String SERVER_KEY_HEADER = "Minecraft-Server-Key";
    public static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final CredentialsProvider credentialsProvider;
    private final TransportSubmitter transportSubmitter;
    private final RequestFactory requestFactory;

    public ServiceNpcSpawnRegistrationClient() {
        this(ServerAuthRegistry::credentials, ServerHttpExecutor::submit, CancellableHttpRequest::new);
    }

    ServiceNpcSpawnRegistrationClient(
        CredentialsProvider credentialsProvider, TransportSubmitter transportSubmitter, RequestFactory requestFactory
    ) {
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
        this.transportSubmitter = Objects.requireNonNull(transportSubmitter, "transportSubmitter");
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
    }

    public ServiceNpcSpawnRequestHandle submit(
        MinecraftServer server, ServiceNpcSpawnOperationRequest request
    ) {
        Objects.requireNonNull(server, "server");
        if (request == null) return completed(new ServiceNpcSpawnClientResult.LocalFailure(
            "request_missing", ServiceNpcSpawnClientResult.Disposition.PERMANENT
        ));
        Optional<ServerCredentials> configured = credentialsProvider.credentials(server);
        if (configured.isEmpty()) return completed(new ServiceNpcSpawnClientResult.LocalFailure("credentials_unavailable"));
        return submitConfigured(server, request, configured.get());
    }

    ServiceNpcSpawnRequestHandle submitForTesting(
        ServiceNpcSpawnOperationRequest request, ServerCredentials credentials
    ) {
        return submitConfigured(null, request, credentials);
    }

    private ServiceNpcSpawnRequestHandle submitConfigured(
        MinecraftServer server, ServiceNpcSpawnOperationRequest request, ServerCredentials credentials
    ) {
        Optional<UUID> serverKey = credentials.minecraftServerKey();
        if (serverKey.isEmpty()) {
            return completed(new ServiceNpcSpawnClientResult.LocalFailure(
                credentials.minecraftServerKeyUnavailableCode()
            ));
        }

        final byte[] body;
        try {
            body = ServiceNpcSpawnRequestSerializer.serialize(request);
        } catch (IllegalArgumentException invalid) {
            return completed(new ServiceNpcSpawnClientResult.LocalFailure(
                invalid.getMessage(), ServiceNpcSpawnClientResult.Disposition.PERMANENT
            ));
        }
        URI endpoint = credentials.apiUrls().resolve(RailsApiUrlResolver.Endpoint.SERVICE_NPC_SPAWN_OPERATIONS);
        CancellableHttpRequest active = requestFactory.create(endpoint, MAX_RESPONSE_BYTES);
        CompletableFuture<ServiceNpcSpawnClientResult> result = new CompletableFuture<>();

        final CompletableFuture<ServiceNpcSpawnClientResult> transport;
        try {
            transport = transportSubmitter.submit(server, () -> execute(
                active, credentials, serverKey.get(), request, body
            ));
        } catch (RejectedExecutionException rejected) {
            return completed(new ServiceNpcSpawnClientResult.TransportFailure("executor_saturated"));
        }
        transport.whenComplete((value, failure) -> {
            if (failure == null) {
                result.complete(value);
                return;
            }
            Throwable cause = unwrap(failure);
            if (cause instanceof TimeoutException) {
                active.cancel();
                result.complete(new ServiceNpcSpawnClientResult.TransportFailure("overall_timeout"));
            } else if (cause instanceof RejectedExecutionException) {
                result.complete(new ServiceNpcSpawnClientResult.TransportFailure("executor_saturated"));
            } else if (cause instanceof CancellationException) {
                active.cancel();
                result.complete(new ServiceNpcSpawnClientResult.Cancelled());
            } else {
                active.cancel();
                result.complete(new ServiceNpcSpawnClientResult.TransportFailure("transport_error"));
            }
        });
        return new ServiceNpcSpawnRequestHandle(result, () -> {
            active.cancel();
            transport.cancel(true);
            result.complete(new ServiceNpcSpawnClientResult.Cancelled());
        });
    }

    private static ServiceNpcSpawnClientResult execute(
        CancellableHttpRequest active, ServerCredentials credentials, UUID serverKey,
        ServiceNpcSpawnOperationRequest submitted, byte[] body
    ) {
        try {
            CancellableHttpRequest.Response response = active.execute(connection -> {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty(SERVER_KEY_HEADER, serverKey.toString());
                if (!RailsRequestAuthenticator.apply(connection, credentials, body)) {
                    throw new IllegalStateException("credentials_unavailable");
                }
            }, body);
            return ServiceNpcSpawnResponseParser.parse(
                response.status(), response.body(), response.headers(), submitted
            );
        } catch (CancellableHttpRequest.RequestException failure) {
            if (failure.code() == CancellableHttpRequest.FailureCode.CANCELLED) {
                return new ServiceNpcSpawnClientResult.Cancelled();
            }
            return new ServiceNpcSpawnClientResult.TransportFailure(failure.code().safeCode());
        }
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    private static ServiceNpcSpawnRequestHandle completed(ServiceNpcSpawnClientResult result) {
        return new ServiceNpcSpawnRequestHandle(CompletableFuture.completedFuture(result), () -> {});
    }

    @FunctionalInterface interface CredentialsProvider {
        Optional<ServerCredentials> credentials(MinecraftServer server);
    }
    @FunctionalInterface interface TransportSubmitter {
        CompletableFuture<ServiceNpcSpawnClientResult> submit(
            MinecraftServer server, Supplier<ServiceNpcSpawnClientResult> task
        );
    }
    @FunctionalInterface interface RequestFactory {
        CancellableHttpRequest create(URI endpoint, int maxResponseBytes);
    }
}
