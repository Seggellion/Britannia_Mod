package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload;
import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload.Notice;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.shop.Product;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The player-sells-to-NPC quote for economic Traders.
 *
 * <p>Economic projections used to be routed to the retail {@code economic_catalog} on nothing
 * more than "does this entity carry a type key", which is a question about provenance, not about
 * direction of trade. Every Trader therefore asked for a NPC-sells-to-player catalog, never
 * posted the player's inventory, and got back the honest empty {@code rows} array that retail
 * holds for a Trader key — surfacing as "I am not interested in anything you have" for every
 * Trader on the shard. Routing is now by the registry's {@code kind}, and Traders quote here.
 *
 * <p>Items are serialized by {@link ServerEconomyService#describeSaleItem} — the settlement
 * serializer — because the endpoint takes the same per-item shape the sale posts. A quote and the
 * sale that follows it therefore describe the goods identically, which is precisely what the
 * legacy {@code trader_catalog} could not promise: it priced on a different formula from the one
 * that paid out, so a player could be quoted one number and paid another.
 *
 * <p>The quote is a display snapshot. {@code EconomicTraderSale} re-validates under lock and
 * stays authoritative — treasury and stock can move between opening the screen and confirming.
 */
public final class EconomicBuybackCatalogService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final int MAX_QUOTE_ITEMS = 64;

    private EconomicBuybackCatalogService() {
    }

    /** A priced buyback offer plus everything the trader could not take, and why. */
    public record Quote(List<Product> products, List<Notice> notices) {
        public Quote {
            products = List.copyOf(products);
            notices = List.copyOf(notices);
        }

        static Quote noticeOnly(String code, String subject) {
            return new Quote(List.of(), List.of(new Notice(code, subject)));
        }
    }

    /**
     * A quote request built on the server thread, ready to be posted off it.
     *
     * <p>The item list is read from the live player inventory, so it is assembled before the
     * work is handed to {@code ServerHttpExecutor} and the stacks it keeps are copies — an
     * off-thread read of the inventory would race every hopper, pickup and container swap.
     */
    record Prepared(UUID worldNpcId, String body, List<ItemStack> sources) {
        boolean isEmpty() {
            return sources.isEmpty();
        }
    }

    /** Builds the quote request. Must run on the server thread. */
    static Prepared prepare(ServerPlayer player, CitizenEntity trader, String role) {
        UUID worldNpcId = trader.getWorldNpcPublicId();
        if (worldNpcId == null) {
            // The reconciler stamps the World NPC id alongside the type key, so this is a
            // half-materialized projection rather than a Rails-side problem. Fail closed
            // rather than invent an identity the sale would then reject.
            LOGGER.warn("Economic Trader {} has no world_npc_public_id; cannot quote a buyback",
                    trader.getEconomicNpcTypeKey());
            return new Prepared(null, "", List.of());
        }

        Set<String> categories =
                TraderBuybackCategories.forTrader(trader.getEconomicNpcTypeKey(), role);
        List<ItemStack> sources = new ArrayList<>();
        JsonArray items = new JsonArray();
        for (ItemStack stack : player.getInventory().items) {
            if (items.size() >= MAX_QUOTE_ITEMS) break;
            if (stack.isEmpty()) continue;
            JsonObject described = ServerEconomyService.describeSaleItem(stack);
            // A category is the whole test. describeSaleItem leaves it off anything it cannot
            // classify, and no stricter check belongs here: salvage and ingots are identified by
            // material rather than commodity_key, so demanding a commodity_key would hand the
            // Salvage Trader an empty offer -- the very bug being fixed, in a new place.
            String category = described.has("category") ? described.get("category").getAsString() : null;
            if (!TraderBuybackCategories.accepts(categories, category)) continue;

            // describeSaleItem stops short of quantity because the sale supplies it from the
            // reserved stack; here the whole stack is on offer.
            described.addProperty("quantity", stack.getCount());
            items.add(described);
            ItemStack template = stack.copy();
            template.setCount(1);
            sources.add(template);
        }
        if (items.isEmpty()) return new Prepared(worldNpcId, "", List.of());

        JsonObject payload = new JsonObject();
        payload.addProperty("world_npc_public_id", worldNpcId.toString());
        payload.addProperty("player_uuid", player.getStringUUID());
        payload.add("items", items);
        return new Prepared(worldNpcId, payload.toString(), List.copyOf(sources));
    }

    /** Posts a {@link #prepare}d quote. Must run off the server thread. */
    static Quote fetch(ServerPlayer player, Prepared prepared) throws Exception {
        UUID worldNpcId = prepared.worldNpcId();
        if (worldNpcId == null) return Quote.noticeOnly(Notice.TRADER_NOT_ASSIGNED, "");
        if (prepared.isEmpty()) return new Quote(List.of(), List.of());

        Response response = post(player, prepared.body());
        if (response.status() == 404 || response.status() == 422) {
            String code = errorCode(response.body());
            LOGGER.warn("Buyback quote rejected for world_npc={} status={} error={}",
                    worldNpcId, response.status(), code);
            return Quote.noticeOnly(
                    Notice.TRADER_NOT_ASSIGNED.equals(code) || Notice.TRADER_NOT_FOUND.equals(code)
                            ? code : Notice.TRADER_NOT_ASSIGNED,
                    "");
        }
        if (response.status() < 200 || response.status() >= 300) {
            throw new IllegalStateException("Buyback catalog endpoint returned HTTP " + response.status());
        }
        return parse(response.body(), prepared.sources());
    }

    static Quote parse(String body, List<ItemStack> sources) {
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) return new Quote(List.of(), List.of());
        JsonObject root = parsed.getAsJsonObject();

        List<Product> products = new ArrayList<>();
        List<Notice> notices = new ArrayList<>();
        if (root.has("rows") && root.get("rows").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("rows")) {
                if (!element.isJsonObject()) continue;
                readRow(element.getAsJsonObject(), sources, products, notices);
            }
        }

        if (root.has("payout") && root.get("payout").isJsonObject()) {
            JsonObject payout = root.getAsJsonObject("payout");
            boolean available = payout.has("available") && payout.get("available").getAsBoolean();
            if (!available) {
                String denomination = string(payout, "denomination");
                for (String reason : reasons(payout)) {
                    notices.add(new Notice(reason, denomination));
                }
            }
        }
        return new Quote(products, notices);
    }

    private static void readRow(JsonObject row, List<ItemStack> sources,
                                List<Product> products, List<Notice> notices) {
        int index = row.has("index") && !row.get("index").isJsonNull() ? row.get("index").getAsInt() : -1;
        if (index < 0 || index >= sources.size()) return;
        ItemStack source = sources.get(index);

        String commodityKey = string(row, "commodity_key");
        String displayName = row.has("display_name") && !row.get("display_name").isJsonNull()
                ? row.get("display_name").getAsString() : commodityKey;

        if (!(row.has("available") && row.get("available").getAsBoolean())) {
            for (String reason : reasons(row)) {
                // "This city does not trade that commodity" is the ordinary case for anything the
                // trader was never going to want; saying so per item would bury the reasons that
                // are actually actionable. The blanket line already covers a wholly empty offer.
                if (Notice.COMMODITY_NOT_FOUND.equals(reason)) continue;
                notices.add(new Notice(reason, displayName));
            }
            return;
        }
        if (products.size() >= ClientboundOpenNpcScreenPayload.MAX_PRODUCTS) return;

        int quantity = row.has("quantity") && !row.get("quantity").isJsonNull()
                ? row.get("quantity").getAsInt() : source.getCount();
        double lineTotal = row.has("line_total") && !row.get("line_total").isJsonNull()
                ? row.get("line_total").getAsDouble() : 0.0D;
        if (quantity <= 0 || !Double.isFinite(lineTotal) || lineTotal < 0) return;

        String denomination = string(row, "denomination");
        if (!denomination.matches("(?i)gold|silver|copper")) denomination = "copper";
        denomination = denomination.toLowerCase(Locale.ROOT);

        // line_total is NOT coins. Rails values every row in its canonical unit -- copper -- and
        // converts to coins once per basket, as round(sum(line_total) / base). Reading it as
        // coins would price a silver-tier row (metal/salvage, alcohol/wine) a hundred times too
        // high. unit_price is no better: for weight-canonical goods it is per STONE, not per item.
        long lineValueCopper = Math.round(lineTotal * 100.0D);
        if (lineValueCopper < 0) return;

        // Display only, and deliberately derived from the same value the cart totals from: the
        // row list has one integer field, so a row worth 2.5 silver has to show as 3s there. The
        // cart total does not round per item -- see NpcCatalogScreen#calculateCartTotalDisplay.
        long base = 100L * denominationBaseCopper(denomination);
        long perItemCoins = Math.round((double) lineValueCopper / quantity / base);
        if (perItemCoins < 0 || perItemCoins > Integer.MAX_VALUE) return;

        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(source.getItem()).toString();
        String name = displayName.isBlank() ? itemId : displayName;
        products.add(new Product(itemId, name, (int) perItemCoins, denomination,
                source.copy(), null, lineValueCopper, quantity));
    }

    /**
     * Copper per coin of a denomination -- the mod's half of Rails'
     * {@code Currency::CANONICAL_BASE_VALUES}. Both sides treat copper as the canonical unit;
     * {@code EconomicBuybackPricingContractTest} pins the two against each other.
     */
    /**
     * Coins of {@code denomination} for a value in hundredths of a copper, rounded exactly once.
     *
     * <p>Mirrors Rails' {@code amount = (total_value / base).round} — the conversion is applied
     * to the summed basket, never per row, because rounding each row first is what makes a
     * displayed total disagree with the payout.
     */
    public static int coinsFor(long valueCopperCentis, String denomination) {
        long base = 100L * denominationBaseCopper(denomination);
        return (int) Math.round((double) valueCopperCentis / base);
    }

    public static int denominationBaseCopper(String denomination) {
        return switch (denomination) {
            case "gold" -> com.seggellion.britannia_mod.economy.CoinConversion.COPPER_PER_GOLD;
            case "silver" -> com.seggellion.britannia_mod.economy.CoinConversion.COPPER_PER_SILVER;
            default -> 1;
        };
    }

    private static List<String> reasons(JsonObject holder) {
        if (!holder.has("reasons") || !holder.get("reasons").isJsonArray()) return List.of();
        List<String> codes = new ArrayList<>();
        for (JsonElement reason : holder.getAsJsonArray("reasons")) {
            if (reason == null || reason.isJsonNull() || !reason.isJsonPrimitive()) continue;
            String code = reason.getAsString().trim();
            if (!code.isEmpty() && code.length() <= 64 && !codes.contains(code)) codes.add(code);
        }
        return codes;
    }

    private static String string(JsonObject holder, String key) {
        if (!holder.has(key) || holder.get(key).isJsonNull()) return "";
        String value = holder.get(key).getAsString().trim();
        return value.length() > 128 ? value.substring(0, 128) : value;
    }

    private static String errorCode(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) return "";
            return string(parsed.getAsJsonObject(), "error");
        } catch (RuntimeException malformed) {
            return "";
        }
    }

    private record Response(int status, String body) {}

    private static Response post(ServerPlayer player, String body) throws Exception {
        var requestUri = ServerAuthRegistry.credentials(player.server).orElseThrow()
                .apiUrls().resolve(Endpoint.ECONOMIC_BUYBACK_CATALOG);
        HttpURLConnection connection = (HttpURLConnection) requestUri.toURL().openConnection();
        BoundedHttp.configure(connection);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Accept", "application/json");
        byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > 262_144) throw new IllegalArgumentException("Buyback quote request too large");
        if (!RailsRequestAuthenticator.apply(connection, player.server, encoded)) {
            throw new IllegalStateException("Server authentication unavailable");
        }
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(encoded);
        }
        int status = connection.getResponseCode();
        var stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = stream == null ? "" : BoundedHttp.readUtf8(stream, MAX_RESPONSE_BYTES);
        return new Response(status, responseBody);
    }
}
