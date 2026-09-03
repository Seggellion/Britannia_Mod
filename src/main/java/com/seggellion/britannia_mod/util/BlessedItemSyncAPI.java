package com.seggellion.britannia_mod.sync;

import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class BlessedItemSyncAPI {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    /**
     * One row of the Rails blessed-item catalogue.
     *
     * <p>Starfarer M4 added {@code instance_uuid} and {@code state} beside the three legacy
     * fields. Both are nullable here on purpose: a Rails build older than M4 simply does not
     * send them, and a row for an entitlement Rails declined to materialize on this shard
     * (already consumed, or scoped to a different shard) carries them as JSON null.
     *
     * <p>A null {@code instanceUuid} is never a reason to invent one. Delivery without a
     * materialization identity is precisely the duplication bug this milestone removes, so the
     * sync fails closed on it -- see {@code BlessedItemInventorySync}.
     */
    public record BlessedRow(String itemName, String deedId, boolean used,
                             String instanceUuid, String state) {

        /** Pre-M4 shape, kept so existing callers and tests read unchanged. */
        public BlessedRow(String itemName, String deedId, boolean used) {
            this(itemName, deedId, used, null, null);
        }

        public boolean hasLifecycle() {
            return instanceUuid != null && !instanceUuid.isBlank()
                && state != null && !state.isBlank();
        }
    }

    public static List<BlessedRow> fetch(ServerPlayer player) {
        HttpURLConnection conn = null;
        try {
            var requestUri = ServerAuthRegistry.credentials(player.server).orElseThrow().apiUrls()
                    .resolveQuery(Endpoint.BLESSED_ITEMS,
                            Map.of("minecraft_uuid", player.getStringUUID()));
            conn = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(conn);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            if (!RailsRequestAuthenticator.apply(conn, player.server, new byte[0])) throw new IllegalStateException("Server authentication unavailable");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                LOGGER.warn("Blessed-item sync failed for {}  HTTP {}", player.getName().getString(), code);
                return List.of();
            }

            return parse(BoundedHttp.readUtf8(conn.getInputStream(), 1_048_576));
        } catch (Exception e) {
            LOGGER.error("Unable to fetch blessed items for {}", player.getName().getString(), e);
            return List.of();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * The wire contract, as a pure function of the response body.
     *
     * <p>Starfarer M10 lifted this out of {@link #fetch} so the contract is provable without a
     * socket. It was previously inline inside the network call, which meant the shape Rails
     * promises -- {@code item}, {@code deed_id}, {@code used}, {@code instance_uuid},
     * {@code state} -- was pinned by a Rails test on one side of the wire and by nothing at all
     * on this side. A renamed key would have failed here at runtime, on an event day, as a
     * player silently receiving nothing.
     *
     * <p>Deliberately tolerant in exactly one direction: the two lifecycle fields may be absent
     * or null (see {@link BlessedRow}), while the three legacy fields are required and a row
     * missing one is a genuine protocol violation rather than something to guess at.
     */
    static List<BlessedRow> parse(String json) {
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        List<BlessedRow> rows = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            rows.add(new BlessedRow(
                    object.get("item").getAsString(),
                    object.get("deed_id").getAsString(),
                    object.get("used").getAsBoolean(),
                    optionalString(object, "instance_uuid"),
                    optionalString(object, "state")));
        }
        return rows;
    }

    /**
     * Reads a field that may be absent (a pre-M4 Rails build) or explicitly null (an
     * entitlement Rails declined to materialize here). Both arrive as null rather than as an
     * exception or an empty string pretending to be an identity.
     */
    private static String optionalString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) return null;
        String text = value.getAsString();
        return text.isBlank() ? null : text;
    }
}
