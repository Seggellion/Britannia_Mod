package com.seggellion.britannia_mod.util;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.HttpURLConnection;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The curated vein rows Rails holds for this shard.
 *
 * <h2>Off the server thread, from milestone 8</h2>
 * This used to be a blocking HTTP GET called straight from {@code /populateores}, which runs on the
 * server thread — so an operator importing veins froze the whole server for as long as Rails took to
 * answer, and froze it again for the placement that followed. The playbook's requirement is exact:
 * no synchronous giant mutation after a slow network response.
 *
 * <p>The fetch now goes through {@link ServerHttpExecutor}, the bounded pool the rest of the mod's
 * Rails calls already use, and the caller hops back to the server thread before touching the world.
 * The world mutation is unchanged and still happens where it must; only the waiting moved.
 *
 * <h2>Failure is typed rather than empty</h2>
 * The old version logged whatever went wrong and returned an empty list, so "Rails is unreachable",
 * "the credentials are missing", "the payload is malformed" and "this shard genuinely has no veins"
 * were the same answer to the operator: nothing happened. Each is now its own outcome with its own
 * sentence.
 */
public class OreVeinFetcher {
    private static final Logger LOGGER = LogManager.getLogger();

    /** Refuse a payload larger than this rather than reading an unbounded body into memory. */
    private static final int MAX_PAYLOAD_BYTES = 1_048_576;

    public static class OreVein {
        public final String oreType;
        public final BlockPos position;
        public final int radius;
        public final String rotation;
        public final String region;

        public OreVein(String oreType, BlockPos position, int radius, String rotation, String region) {
            this.oreType = oreType;
            this.position = position;
            this.radius = radius;
            this.rotation = rotation;
            this.region = region;
        }

        public BlockPos getPosition() {
            return position;
        }
    }

    /** What the fetch actually did, so the caller can say something true about it. */
    public record FetchResult(Status status, List<OreVein> veins, String detail) {

        public enum Status {
            /** Rails answered, and the payload parsed. The list may still be empty. */
            OK,
            /** No server credentials, so the request was never made. */
            AUTH_UNAVAILABLE,
            /** Rails answered with something other than 200. */
            HTTP_ERROR,
            /** Rails answered 200 with a body this cannot read. */
            MALFORMED,
            /** The request never completed: DNS, connection, timeout, TLS. */
            TRANSPORT_ERROR
        }

        public boolean ok() {
            return status == Status.OK;
        }

        /** One sentence an operator can act on. */
        public String describe() {
            return switch (status) {
                case OK -> veins.isEmpty()
                        ? "Rails returned no curated veins for this shard."
                        : "Rails returned " + veins.size() + " curated vein row(s).";
                case AUTH_UNAVAILABLE -> "Server authentication is unavailable, so no request was"
                        + " made to Rails. Nothing was placed.";
                case HTTP_ERROR -> "Rails refused the request (" + detail + "). Nothing was placed.";
                case MALFORMED -> "Rails answered, but the vein payload could not be read ("
                        + detail + "). Nothing was placed.";
                case TRANSPORT_ERROR -> "Could not reach Rails (" + detail + ")."
                        + " Nothing was placed.";
            };
        }

        static FetchResult failure(Status status, String detail) {
            return new FetchResult(status, List.of(), detail);
        }
    }

    /**
     * Fetch the shard's curated veins without blocking the server thread.
     *
     * <p>The returned future completes on an HTTP pool thread. Whatever the caller does with the
     * rows afterwards must be moved back to the server thread by the caller — this class
     * deliberately does not do it, because it does not know what the caller intends to touch.
     */
    public static CompletableFuture<FetchResult> fetchOreVeinsAsync(
            MinecraftServer server, String shardName) {
        return ServerHttpExecutor.submit(server, () -> fetchBlocking(server, shardName));
    }

    /**
     * The blocking fetch itself.
     *
     * <p>Package-visible rather than public: everything outside should go through
     * {@link #fetchOreVeinsAsync}, so that reintroducing the freeze takes a deliberate act rather
     * than an autocomplete.
     */
    static FetchResult fetchBlocking(MinecraftServer server, String shardName) {
        HttpURLConnection conn = null;
        try {
            var credentials = ServerAuthRegistry.credentials(server).orElse(null);
            if (credentials == null) {
                return FetchResult.failure(FetchResult.Status.AUTH_UNAVAILABLE, "no credentials");
            }
            var requestUri = credentials.apiUrls().resolveQuery(
                    Endpoint.ORE_VEINS, Map.of("shard", credentials.shardName()));
            conn = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(conn);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            if (!RailsRequestAuthenticator.apply(conn, server, new byte[0])) {
                return FetchResult.failure(FetchResult.Status.AUTH_UNAVAILABLE,
                        "request could not be signed");
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                LOGGER.error("Failed to fetch ore veins. HTTP error code: {}", responseCode);
                return FetchResult.failure(FetchResult.Status.HTTP_ERROR, "HTTP " + responseCode);
            }

            String body = BoundedHttp.readUtf8(conn.getInputStream(), MAX_PAYLOAD_BYTES);
            return parse(body);
        } catch (Exception exception) {
            LOGGER.error("Exception while fetching ore veins: ", exception);
            return FetchResult.failure(FetchResult.Status.TRANSPORT_ERROR,
                    exception.getClass().getSimpleName()
                            + (exception.getMessage() == null ? "" : ": " + exception.getMessage()));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Read the payload, refusing a row rather than the whole import when one row is unreadable.
     *
     * <p>Visible for testing: the parsing rules are worth driving directly, and doing so needs no
     * server and no network.
     */
    public static FetchResult parse(String body) {
        List<OreVein> veins = new ArrayList<>();
        int malformedRows = 0;
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) {
                return FetchResult.failure(FetchResult.Status.MALFORMED,
                        "expected a JSON array of vein rows");
            }
            for (JsonElement element : root.getAsJsonArray()) {
                try {
                    JsonObject obj = element.getAsJsonObject();
                    veins.add(new OreVein(
                            obj.get("ore_type").getAsString(),
                            new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(),
                                    obj.get("z").getAsInt()),
                            obj.get("radius").getAsInt(),
                            obj.get("rotation").getAsString(),
                            obj.get("region").getAsString()));
                } catch (RuntimeException badRow) {
                    // One unusable row must not lose the other four hundred. It is counted and
                    // reported rather than dropped in silence.
                    malformedRows++;
                    LOGGER.warn("Skipping unreadable ore vein row: {}", element);
                }
            }
        } catch (RuntimeException exception) {
            return FetchResult.failure(FetchResult.Status.MALFORMED,
                    exception.getClass().getSimpleName());
        }
        return new FetchResult(FetchResult.Status.OK, veins,
                malformedRows == 0 ? "" : malformedRows + " unreadable row(s) skipped");
    }
}
