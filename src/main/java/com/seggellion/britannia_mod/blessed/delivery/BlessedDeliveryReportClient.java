package com.seggellion.britannia_mod.blessed.delivery;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reports to Rails that a blessed item physically exists on this shard.
 *
 * <p>Speaks the Starfarer M4 protocol at
 * {@code POST /api/v2/blessed_item_materializations/:instance_uuid/result}, which is the
 * strongest tier this application has: the request is signed, carries the per-server
 * Minecraft-Server-Key on top of the shard secret, and is rate limited and body capped
 * server-side. All of that is already implemented by
 * {@link RailsRequestAuthenticator} and the credentials registry -- nothing is hand rolled here.
 *
 * <p>The report is idempotent by design on the Rails side, so replaying one is safe and is the
 * intended recovery path: a delivery whose acknowledgement was lost is re-reported later from
 * the durable local receipt rather than by delivering a second item.
 *
 * <p>Every outcome is a value, never an exception escaping to the caller. A failure to TELL
 * Rails about a delivery must never be confused with a failure to deliver -- the item is
 * already in the player's hands by the time this runs, and the receipt is what remembers that.
 */
public final class BlessedDeliveryReportClient {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Must match Rails' BlessedItemMaterialization::PROTOCOL_VERSION. */
    public static final int PROTOCOL_VERSION = 1;

    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private BlessedDeliveryReportClient() {
    }

    /** What Rails said, reduced to the distinctions the delivery path actually acts on. */
    public sealed interface Outcome {
        /** Rails recorded the delivery. {@code duplicate} means it already had. */
        record Accepted(boolean duplicate) implements Outcome {}

        /**
         * Rails refused because its own view contradicts ours -- 409. Reporting delivery of a
         * materialization Rails considers destroyed, or naming the wrong player. Never retried
         * blindly and never grounds for delivering another item.
         */
        record Conflict(String error) implements Outcome {}

        /**
         * 404. The instance is unknown to Rails, or belongs to another shard. Rails answers
         * both the same way on purpose, so a shard cannot probe for other shards' identities.
         */
        record NotFound() implements Outcome {}

        /** Transport, auth, or anything else worth retrying later. */
        record Retryable(String code) implements Outcome {}
    }

    /**
     * Blocking. Call from {@code ServerHttpExecutor}, never from the server thread.
     */
    public static Outcome reportDelivered(MinecraftServer server, UUID instanceUuid, UUID ownerUuid) {
        Optional<ServerCredentials> maybeCredentials = ServerAuthRegistry.credentials(server);
        if (maybeCredentials.isEmpty()) {
            return new Outcome.Retryable("credentials_unavailable");
        }
        ServerCredentials credentials = maybeCredentials.get();

        Optional<UUID> serverKey = credentials.minecraftServerKey();
        if (serverKey.isEmpty()) {
            return new Outcome.Retryable(credentials.minecraftServerKeyUnavailableCode());
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("protocol_version", PROTOCOL_VERSION);
        payload.addProperty("instance_uuid", instanceUuid.toString());
        payload.addProperty("minecraft_uuid", ownerUuid.toString());
        payload.addProperty("status", "delivered");
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection connection = null;
        try {
            URI uri = credentials.apiUrls().resolvePath(
                    Endpoint.BLESSED_ITEM_MATERIALIZATION_RESULT,
                    Map.of("instance_uuid", instanceUuid.toString()));

            connection = (HttpURLConnection) uri.toURL().openConnection();
            BoundedHttp.configure(connection);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Minecraft-Server-Key", serverKey.get().toString());

            if (!RailsRequestAuthenticator.apply(connection, server, body)) {
                return new Outcome.Retryable("authentication_unavailable");
            }

            connection.getOutputStream().write(body);
            connection.getOutputStream().flush();

            int status = connection.getResponseCode();
            return switch (status) {
                case HttpURLConnection.HTTP_OK -> accepted(connection, instanceUuid);
                case HttpURLConnection.HTTP_NOT_FOUND -> {
                    LOGGER.warn("Blessed delivery report rejected as unknown instance={} -- Rails "
                            + "does not recognise it, or it belongs to another shard", instanceUuid);
                    yield new Outcome.NotFound();
                }
                case HttpURLConnection.HTTP_CONFLICT -> {
                    String error = errorCode(connection);
                    LOGGER.warn("Blessed delivery report conflicted instance={} error={} -- Rails "
                            + "state disagrees with ours; not retrying and not re-delivering",
                            instanceUuid, error);
                    yield new Outcome.Conflict(error);
                }
                case HttpURLConnection.HTTP_BAD_REQUEST -> new Outcome.Retryable("invalid_request");
                case HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                        new Outcome.Retryable("authentication_rejected");
                case 429 -> new Outcome.Retryable("rate_limited");
                default -> new Outcome.Retryable("http_" + status);
            };
        } catch (JsonParseException | IllegalStateException malformed) {
            return new Outcome.Retryable("malformed_response");
        } catch (Exception failure) {
            // Deliberately broad and deliberately retryable: the item already exists in the
            // world, so an unreachable Rails is a reporting delay, never a delivery failure.
            LOGGER.warn("Blessed delivery report could not reach Rails instance={} -- will replay "
                    + "from the durable receipt", instanceUuid, failure);
            return new Outcome.Retryable("transport_error");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Outcome accepted(HttpURLConnection connection, UUID instanceUuid) throws Exception {
        JsonObject root = JsonParser.parseString(
                BoundedHttp.readUtf8(connection.getInputStream(), MAX_RESPONSE_BYTES)).getAsJsonObject();
        boolean duplicate = root.has("duplicate") && !root.get("duplicate").isJsonNull()
                && root.get("duplicate").getAsBoolean();
        return new Outcome.Accepted(duplicate);
    }

    private static String errorCode(HttpURLConnection connection) {
        try {
            JsonObject root = JsonParser.parseString(
                    BoundedHttp.readUtf8(connection.getErrorStream(), MAX_RESPONSE_BYTES)).getAsJsonObject();
            return root.has("error") ? root.get("error").getAsString() : "unknown";
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}
