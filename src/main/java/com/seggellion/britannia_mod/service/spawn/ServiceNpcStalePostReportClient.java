package com.seggellion.britannia_mod.service.spawn;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

/**
 * Milestone 14: reports a post this server has confirmed physically missing to Rails, so the
 * observation survives beyond this server's own durable store and a human can actually see it.
 *
 * <p>Deliberately a plain reporting call, not an operation. Nothing here asks Rails to remove,
 * tombstone or unassign anything -- Rails records the report as evidence and returns. That is
 * the entire contract; see Rails' own ServiceNpcStalePostReport for why acting on this signal
 * automatically would be wrong.
 *
 * <p>Structurally a narrowed {@link ServiceNpcSpawnRegistrationClient}: same credential lookup
 * ({@link ServerAuthRegistry}), same server-key header, same
 * {@link RailsRequestAuthenticator#apply} attachment and HMAC signing, same
 * {@link ServerHttpExecutor} off-thread submission and same {@link CancellableHttpRequest}
 * bounded read. It is narrower only because a report has no operation semantics to model: there
 * is no revision to conflict, nothing to retry against a state machine, and no result a caller
 * needs to branch on. A failed report is logged and dropped, because the reconciler re-confirms
 * a still-missing post on its own observation interval and will simply report it again -- which
 * is also what makes this safe if Rails is briefly unreachable.
 */
public final class ServiceNpcStalePostReportClient {
    public static final String SERVER_KEY_HEADER = "Minecraft-Server-Key";
    public static final int MAX_RESPONSE_BYTES = 16 * 1024;

    private static final Logger LOGGER = LogUtils.getLogger();

    private ServiceNpcStalePostReportClient() {
    }

    /**
     * Submits one report off the server thread. Never throws into the caller: the reconciler
     * that calls this runs on the tick thread and must not be able to fail because a report
     * could not be sent.
     */
    public static void report(MinecraftServer server, ServiceNpcSpawnMissingPostReport report) {
        if (server == null || report == null) return;

        Optional<ServerCredentials> configured = ServerAuthRegistry.credentials(server);
        if (configured.isEmpty()) {
            LOGGER.debug("Skipping stale post report for {}: credentials are not configured", report.spawnPointId());
            return;
        }
        ServerCredentials credentials = configured.get();
        Optional<UUID> serverKey = credentials.minecraftServerKey();
        if (serverKey.isEmpty()) {
            LOGGER.debug(
                "Skipping stale post report for {}: {}", report.spawnPointId(),
                credentials.minecraftServerKeyUnavailableCode()
            );
            return;
        }

        byte[] body = serialize(report);
        URI endpoint = credentials.apiUrls().resolve(RailsApiUrlResolver.Endpoint.SERVICE_NPC_STALE_POST_REPORTS);
        CancellableHttpRequest active = new CancellableHttpRequest(endpoint, MAX_RESPONSE_BYTES);

        final CompletableFuture<Integer> transport;
        try {
            transport = ServerHttpExecutor.submit(server, () -> execute(active, credentials, serverKey.get(), body));
        } catch (RejectedExecutionException rejected) {
            LOGGER.debug("Stale post report for {} not submitted: executor saturated", report.spawnPointId());
            return;
        }

        transport.whenComplete((status, failure) -> {
            if (failure != null) {
                active.cancel();
                LOGGER.info(
                    "Stale post report for {} did not reach Rails; it will be re-reported on the next confirmation",
                    report.spawnPointId()
                );
                return;
            }
            if (status != null && status >= 200 && status < 300) {
                LOGGER.info("Stale post report accepted by Rails for {}", report.spawnPointId());
            } else {
                LOGGER.info(
                    "Stale post report for {} was rejected by Rails status={}; it will be re-reported later",
                    report.spawnPointId(), status
                );
            }
        });
    }

    private static int execute(
        CancellableHttpRequest active, ServerCredentials credentials, UUID serverKey, byte[] body
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
            return response.status();
        } catch (CancellableHttpRequest.RequestException failure) {
            return -1;
        }
    }

    static byte[] serialize(ServiceNpcSpawnMissingPostReport report) {
        Objects.requireNonNull(report, "report");
        ServiceNpcSpawnLocation location = report.location();

        JsonObject locationJson = new JsonObject();
        locationJson.addProperty("world_name", location.worldName());
        locationJson.addProperty("dimension", location.dimension().toString());
        locationJson.addProperty("x", location.pos().getX());
        locationJson.addProperty("y", location.pos().getY());
        locationJson.addProperty("z", location.pos().getZ());

        JsonObject payload = new JsonObject();
        payload.addProperty("spawn_uuid", report.spawnPointId().toString());
        payload.addProperty(
            "observed_missing_since",
            Instant.ofEpochMilli(report.detectedAtEpochMillis()).toString()
        );
        payload.add("location", locationJson);

        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }
}
