package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.bank.item.BankItemCodec;
import com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.item.MaterialQualityJewelryItem;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import com.seggellion.britannia_mod.item.WeightedCommodityItem;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import com.seggellion.britannia_mod.network.CityDataSync;
import com.seggellion.britannia_mod.network.payload.SellItemsC2SPayload;
import com.seggellion.britannia_mod.network.payload.TransactionFailedS2CPayload;
import com.seggellion.britannia_mod.network.payload.TransactionSuccessS2CPayload;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.trader.ITrader;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerEconomyService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double MAX_SALE_DISTANCE_SQ = 12.0D * 12.0D;
    private static final String TRADER_SOURCE_TAG_PREFIX = "trader_source_";
    private static final Set<String> SYNCED_NPCS = ConcurrentHashMap.newKeySet();

    private ServerEconomyService() {}

    public static void sellAllWood(ServerPlayer player, Entity trader, String cityName, String role) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (cityName == null || cityName.isBlank()) {
            fail(player, "This trader is not associated with a city.");
            return;
        }

        List<SaleStack> selected = collectWood(player);
        if (selected.isEmpty()) {
            fail(player, "You don't have any wood to sell.");
            return;
        }

        submitReservedSale(player, level, cityName, role, trader, selected);
    }

    public static void sellRequestedItems(ServerPlayer player, SellItemsC2SPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) return;

        Entity trader = level.getEntity(payload.entityId());
        if (trader == null || !trader.isAlive()) {
            fail(player, "Trader not found.");
            return;
        }
        if (player.distanceToSqr(trader) > MAX_SALE_DISTANCE_SQ) {
            fail(player, "You are too far away from the trader.");
            return;
        }

        String cityName = payload.city();
        String role = payload.role();
        if (trader instanceof ITrader traderEntity) {
            if (traderEntity.getTraderCityName() != null && !traderEntity.getTraderCityName().isBlank()) {
                cityName = traderEntity.getTraderCityName();
            }
            role = traderEntity.getEconomyRole();
        }

        if (cityName == null || cityName.isBlank()) {
            fail(player, "This trader is not associated with a city.");
            return;
        }

        List<SaleStack> selected = collectRequestedItems(player, payload.items());
        if (selected.isEmpty()) {
            fail(player, "No matching items were found to sell.");
            return;
        }

        submitReservedSale(player, level, cityName, role, trader, selected);
    }

    private static void submitReservedSale(ServerPlayer player, ServerLevel level, String cityName, String role,
                                           Entity trader, List<SaleStack> selected) {
        MinecraftServer server = level.getServer();
        try {
            ServerHttpExecutor.submit(server, () -> ensureTraderNpcSynced(level, trader, cityName, role))
                .whenComplete((synced, failure) -> server.execute(() -> {
                    if (server.getPlayerList().getPlayer(player.getUUID()) != player
                        || !trader.isAlive() || player.distanceToSqr(trader) > MAX_SALE_DISTANCE_SQ) return;
                    if (failure != null || !Boolean.TRUE.equals(synced)) {
                        LOGGER.warn("Trader sale preflight NPC sync failed player={} trader={} city={} role={}",
                            player.getStringUUID(), trader.getUUID(), cityName, role);
                        fail(player, "Sale rejected: trader NPC could not sync.");
                        return;
                    }
                    reserveAndSubmitSale(player, level, cityName, role, trader, selected);
                }));
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            fail(player, "Sale rejected: economy queue is full.");
        }
    }

    private static void reserveAndSubmitSale(ServerPlayer player, ServerLevel level, String cityName, String role,
                                             Entity trader, List<SaleStack> selected) {

        // Vendor/Trader Milestone 19.5: the sale's identity is computed BEFORE
        // anything leaves the inventory, because the durable reservation receipt
        // is keyed by it -- see reserveItemsDurably.
        UUID transactionUuid = UUID.randomUUID();
        Reservation planned = planReservation(player, selected);
        if (planned.isEmpty()) {
            fail(player, "No matching items were found to sell.");
            return;
        }
        String idempotencyKey = "sale:" + player.getUUID() + ":" + trader.getUUID() + ":" +
                level.getGameTime() + ":" + planned.itemSignature() + ":" + transactionUuid;

        Reservation reservation = reserveItemsDurably(level, player, planned, idempotencyKey);
        if (reservation.isEmpty()) {
            fail(player, "No matching items were found to sell.");
            return;
        }

        JsonObject payload = buildSalePayload(player, level, cityName, role, trader, reservation, idempotencyKey, transactionUuid);
        MinecraftServer server = level.getServer();

        LOGGER.info("Wood/trader sale start key={} player={} city={} role={} trader={} items={}",
                idempotencyKey, player.getStringUUID(), cityName, role, trader.getUUID(), reservation.items().size());

        // The outcome of the call below is unknown to this process until it
        // answers; the receipt records that so a crash mid-flight is recoverable.
        TraderSaleReservationStore.get(level)
                .advance(idempotencyKey, TraderSaleReservationReceipt.Status.DISPATCHED);

        ServerHttpExecutor
                .submit(server, () -> postSale(level, payload, idempotencyKey))
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null) {
                        LOGGER.warn("Wood sale failure key={} error={}", idempotencyKey, error.toString());
                        reservation.refund(player);
                        resolveReservation(level, idempotencyKey);
                        fail(player, "Sale failed: economy server unavailable.");
                        return;
                    }

                    if (!result.success()) {
                        LOGGER.warn("Wood sale failure key={} status={} body={}",
                                idempotencyKey, result.statusCode(), result.body());
                        reservation.refund(player);
                        resolveReservation(level, idempotencyKey);
                        fail(player, result.message());
                        return;
                    }

                    if (result.idempotentReplay()) {
                        LOGGER.warn("Rails idempotent replay detected key={} receipt={}",
                                idempotencyKey, result.receiptId());
                        reservation.refund(player);
                        resolveReservation(level, idempotencyKey);
                        fail(player, "That sale was already processed.");
                        return;
                    }

                    applyRailsCommodityResponse(level, cityName, result.responseJson(), reservation);
                    grantCurrency(player, result.gold(), result.silver(), result.copper());

                    // The items are now genuinely sold: the reservation's risk is over.
                    resolveReservation(level, idempotencyKey);
                    EconomySyncData.get(level).markApplied(idempotencyKey);
                    if (!result.receiptId().isBlank()) {
                        EconomySyncData.get(level).markApplied("rails_receipt:" + result.receiptId());
                    }

                    LOGGER.info("Wood sale success key={} receipt={} payout={}g {}s {}c",
                            idempotencyKey, result.receiptId(), result.gold(), result.silver(), result.copper());
                    player.sendSystemMessage(Component.literal("Sale complete: " +
                            formatPayout(result.gold(), result.silver(), result.copper()) + "."));
                    TransactionSuccessS2CPayload.send(player);
                }));
    }

    private static boolean ensureTraderNpcSynced(ServerLevel level, Entity trader, String cityName, String role) {
        String syncKey = ModConfig.SHARD_NAME + ":" + trader.getUUID();
        if (SYNCED_NPCS.contains(syncKey)) {
            return true;
        }

        String npcType = normalizeNpcType(role);
        String sourceId = traderSourceId(trader);
        boolean synced = CityDataSync.upsertLiveNpc(
                level,
                trader,
                npcType,
                cityName,
                sourceId,
                trader.blockPosition().toShortString(),
                "active"
        );
        if (synced) {
            SYNCED_NPCS.add(syncKey);
            LOGGER.info("Wood sale preflight NPC sync success trader={} city={} type={} source={}",
                    trader.getUUID(), cityName, npcType, sourceId);
        }
        return synced;
    }

    private static String traderSourceId(Entity trader) {
        for (String tag : trader.getTags()) {
            if (tag.startsWith(TRADER_SOURCE_TAG_PREFIX) && tag.length() > TRADER_SOURCE_TAG_PREFIX.length()) {
                return tag.substring(TRADER_SOURCE_TAG_PREFIX.length());
            }
        }
        return trader.getUUID().toString();
    }

    private static String normalizeNpcType(String role) {
        if (role == null || role.isBlank()) return "wood_trader";
        String normalized = role.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return normalized.endsWith("_trader") ? normalized : normalized + "_trader";
    }

    private static List<SaleStack> collectWood(ServerPlayer player) {
        List<SaleStack> out = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof WeightedWoodItem woodItem)) continue;
            double weight = woodItem.getWeight(stack);
            if (weight <= 0.0D) continue;
            out.add(new SaleStack(slot, stack.copy(), stack.getCount()));
        }
        return out;
    }

    private static List<SaleStack> collectRequestedItems(ServerPlayer player, List<SellItemsC2SPayload.ItemRequest> requests) {
        List<SaleStack> out = new ArrayList<>();
        boolean[] consumedSlots = new boolean[player.getInventory().getContainerSize()];

        for (SellItemsC2SPayload.ItemRequest request : requests) {
            int remaining = Math.max(0, request.quantity());
            if (remaining == 0) continue;

            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                if (consumedSlots[slot]) continue;
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.isEmpty() || !matchesRequest(player, stack, request)) continue;

                int take = Math.min(stack.getCount(), remaining);
                out.add(new SaleStack(slot, stack.copyWithCount(take), take));
                remaining -= take;
                if (take >= stack.getCount()) consumedSlots[slot] = true;
            }
        }

        out.sort(Comparator.comparingInt(SaleStack::slot));
        return out;
    }

    private static boolean matchesRequest(ServerPlayer player, ItemStack stack, SellItemsC2SPayload.ItemRequest request) {
        String stackId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String requestId = normalizeItemId(request.itemId());

        if (!stackId.equals(requestId) && !stackId.endsWith(":" + pathOnly(requestId))) {
            return false;
        }

        String requestedName = request.itemName() == null ? "" : request.itemName().trim();
        if (stack.getItem() instanceof WeightedWoodItem woodItem && !requestedName.isBlank()) {
            String woodType = woodItem.getWoodType(stack);
            if (!requestedName.equalsIgnoreCase(woodType) && !requestedName.equalsIgnoreCase(pathOnly(stackId))) {
                return false;
            }
        }
        if (stack.getItem() instanceof WeightedFishItem fishItem && !requestedName.isBlank()) {
            String fishType = fishItem.getFishType(stack);
            String fishKey = CommodityMappings.fishCommodityKey(fishType);
            if (!requestedName.equalsIgnoreCase(fishType)
                    && !requestedName.equalsIgnoreCase(fishKey)
                    && !requestedName.equalsIgnoreCase(pathOnly(stackId))) {
                return false;
            }
        }
        if (stack.getItem() instanceof PurityOreItem oreItem) {
            // Every mined ore shares one item id, so without this the metal was never checked and a
            // request for Silver could reserve a Valorite stack sitting in the same inventory.
            String oreKey = CommodityMappings.oreCommodityKey(oreItem.getOreType(stack)).orElse("");
            if (oreKey.isBlank()) return false;
            if (requestedName.isBlank()) return true;

            String requestedKey = CommodityMappings.oreCommodityKey(requestedName)
                    .orElse(CityCommodity.normalize(requestedName));
            if (!oreKey.equals(requestedKey)) {
                return false;
            }
        }
        if (stack.getItem() instanceof GradeStoneItem stoneItem) {
            String stoneKey = CommodityMappings.stoneCommodityKey(stoneItem.getStoneType(stack)).orElse("");
            if (stoneKey.isBlank()) return false;
            if (requestedName.isBlank()) return true;

            String requestedKey = CommodityMappings.stoneCommodityKey(requestedName).orElse(CityCommodity.normalize(requestedName));
            if (!stoneKey.equals(requestedKey)) {
                return false;
            }
        }

        if (request.matchNbt() != null && stack.has(DataComponentRegistry.WINE_DATA)) {
            CompoundTag saved = (CompoundTag) stack.save(player.registryAccess());
            if (saved.contains("components") && request.matchNbt().contains("components")) {
                return saved.getCompound("components").equals(request.matchNbt().getCompound("components"));
            }
        }

        return true;
    }

    /**
     * Milestone 19.5: decides WHAT would be reserved without touching the
     * inventory, so the sale's idempotency key (and therefore its durable
     * receipt) can exist before any item moves.
     */
    private static Reservation planReservation(ServerPlayer player, List<SaleStack> selected) {
        List<SaleStack> planned = new ArrayList<>();
        for (SaleStack item : selected) {
            ItemStack live = player.getInventory().getItem(item.slot());
            if (live.isEmpty() || live.getCount() < item.quantity()) continue;
            planned.add(new SaleStack(item.slot(), live.copyWithCount(item.quantity()), item.quantity()));
        }
        return new Reservation(planned);
    }

    /**
     * Milestone 19.5: removes the planned items behind a durable receipt, so a
     * crash can no longer destroy them.
     *
     * <p>Ordering is the whole guarantee, and it is deliberate:
     * <ol>
     *   <li>write the receipt as {@code RESERVED} and flush it to disk — a
     *       later crash can never leave items missing with no record;</li>
     *   <li>remove the items and force-save the player, so their inventory
     *       without those items is itself on disk;</li>
     *   <li>advance the receipt to {@code ITEMS_REMOVED} and flush again —
     *       only now is the removal provably durable, and only receipts in
     *       that state (or {@code DISPATCHED}) are ever refunded on recovery.</li>
     * </ol>
     * A crash before step 3 leaves a {@code RESERVED} receipt, which recovery
     * resolves WITHOUT refunding: the player's saved inventory still holds the
     * items, so refunding would duplicate them. See
     * {@link TraderSaleReservationRecovery} for why that asymmetry is the right
     * way to be wrong.
     */
    private static Reservation reserveItemsDurably(ServerLevel level, ServerPlayer player,
                                                   Reservation planned, String idempotencyKey) {
        List<byte[]> payloads = new ArrayList<>();
        for (SaleStack item : planned.items()) {
            payloads.add(BankItemCodec.serialize(item.stack(), level.registryAccess()));
        }

        TraderSaleReservationStore store = TraderSaleReservationStore.get(level);
        boolean recorded = store.record(new TraderSaleReservationReceipt(
                idempotencyKey, player.getUUID(), payloads,
                TraderSaleReservationReceipt.Status.RESERVED, System.currentTimeMillis()));
        if (!recorded) {
            LOGGER.warn("Refusing a trader sale whose reservation key is already tracked key={}", idempotencyKey);
            return new Reservation(List.of());
        }
        store.flush(level);

        List<SaleStack> reserved = new ArrayList<>();
        for (SaleStack item : planned.items()) {
            ItemStack live = player.getInventory().getItem(item.slot());
            if (live.isEmpty() || live.getCount() < item.quantity()) continue;
            ItemStack removed = live.copyWithCount(item.quantity());
            live.shrink(item.quantity());
            reserved.add(new SaleStack(item.slot(), removed, item.quantity()));
        }
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();

        if (reserved.isEmpty()) {
            // Nothing actually moved (the inventory changed under us): drop the
            // receipt rather than leaving a phantom reservation behind.
            store.resolve(idempotencyKey);
            store.flush(level);
            return new Reservation(reserved);
        }

        BankTransferPlayerDurability.forceSave(player);
        store.advance(idempotencyKey, TraderSaleReservationReceipt.Status.ITEMS_REMOVED);
        store.flush(level);
        return new Reservation(reserved);
    }

    private static void resolveReservation(ServerLevel level, String idempotencyKey) {
        TraderSaleReservationStore store = TraderSaleReservationStore.get(level);
        store.resolve(idempotencyKey);
        store.flush(level);
    }

    private static JsonObject buildSalePayload(ServerPlayer player, ServerLevel level, String cityName, String role,
                                               Entity trader, Reservation reservation, String idempotencyKey,
                                               UUID transactionUuid) {
        JsonObject payload = new JsonObject();
        payload.addProperty("transaction_type", "sell");
        payload.addProperty("idempotency_key", idempotencyKey);
        payload.addProperty("local_transaction_uuid", transactionUuid.toString());
        payload.addProperty("player_uuid", player.getStringUUID());
        payload.addProperty("player_name", player.getGameProfile().getName());
        payload.addProperty("city", cityName);
        payload.addProperty("city_name", cityName);
        payload.addProperty("role", role);
        payload.addProperty("npc_id", trader.getUUID().toString());
        // Vendor/Trader Milestone 10: economic projections identify themselves by
        // their persistent World NPC id so Rails resolves city/identity from the
        // assignment (never the client payload) and routes to the denomination-
        // aware, treasury-debiting EconomicTraderSale.
        if (trader instanceof com.seggellion.britannia_mod.entity.CitizenEntity citizen
                && citizen.getWorldNpcPublicId() != null
                && citizen.getEconomicNpcTypeKey() != null) {
            payload.addProperty("world_npc_public_id", citizen.getWorldNpcPublicId().toString());
        }
        payload.addProperty("trader_uuid", trader.getUUID().toString());
        payload.addProperty("entity_id", trader.getId());
        payload.addProperty("shard", ModConfig.SHARD_NAME);
        payload.addProperty("server_game_time", level.getGameTime());

        JsonArray items = new JsonArray();
        for (SaleStack saleStack : reservation.items()) {
            JsonObject item = describeSaleItem(saleStack.stack());
            item.addProperty("quantity", saleStack.quantity());
            items.add(item);
        }
        payload.add("items", items);
        payload.add("transaction_items", items.deepCopy());
        return payload;
    }

    private static JsonObject describeSaleItem(ItemStack stack) {
        JsonObject item = new JsonObject();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        item.addProperty("item_id", itemId);
        item.addProperty("item_name", pathOnly(itemId));

        Item itemObj = stack.getItem();
        if (itemObj instanceof WeightedWoodItem woodItem) {
            String woodType = woodItem.getWoodType(stack);
            double weight = woodItem.getWeight(stack) * stack.getCount();
            item.addProperty("item_name", woodType);
            item.addProperty("commodity_key", woodType);
            item.addProperty("category", "wood");
            item.addProperty("subcategory", "logs");
            item.addProperty("weight", weight);
        } else if (itemObj instanceof WeightedFishItem fishItem) {
            String fishType = CommodityMappings.fishCommodityKey(fishItem.getFishType(stack));
            double weight = fishItem.getWeight(stack) * stack.getCount();
            item.addProperty("item_name", fishType);
            item.addProperty("commodity_key", fishType);
            item.addProperty("category", "fish");
            item.addProperty("subcategory", "raw");
            item.addProperty("weight", weight);
        } else if (itemObj instanceof PurityOreItem oreItem) {
            // Mining milestone 5: post the seeded ore/raw identity ("silver"), not the item's
            // display name ("Silver ore"). Because category+subcategory are sent, Rails performs an
            // exact lookup with no legacy fallback, so the display name could never resolve.
            String rawOreType = oreItem.getOreType(stack);
            String oreKey = CommodityMappings.oreCommodityKey(rawOreType)
                    .orElse(CityCommodity.normalize(rawOreType));
            item.addProperty("item_name", oreKey);
            item.addProperty("commodity_key", oreKey);
            item.addProperty("purity", oreItem.getPurity(stack));
            item.addProperty("category", "ore");
            item.addProperty("subcategory", "raw");
        } else if (itemObj instanceof GradeStoneItem stoneItem) {
            String rawStoneType = stoneItem.getStoneType(stack);
            String stoneType = CommodityMappings.stoneCommodityKey(rawStoneType).orElse(CityCommodity.normalize(rawStoneType));
            double weight = stack.getCount();
            item.addProperty("item_name", stoneType);
            item.addProperty("commodity_key", CommodityMappings.stoneCommodityIdentityKey(stoneType));
            item.addProperty("category", "stone");
            // Milestone 8: the seeded family (rubble/common/igneous/volcanic/sedimentary/mineral),
            // not the literal "blocks" no commodity row has ever carried.
            item.addProperty("subcategory",
                    CommodityMappings.stoneCommoditySubcategory(stoneType).orElse(""));
            item.addProperty("weight", weight);
            LOGGER.info("StoneTrader sale GradeStoneItem item_id={} rawStoneType={} commodity_key={} weight={}",
                    itemId, rawStoneType, CommodityMappings.stoneCommodityIdentityKey(stoneType), weight);
        } else {
            classifyMappedCommodity(item, stack);
        }

        if (stack.has(DataComponentRegistry.WINE_DATA)) {
            var wine = stack.get(DataComponentRegistry.WINE_DATA);
            item.addProperty("winery_name", wine.wineryName());
            item.addProperty("grape_type", wine.grapeType());
            item.addProperty("year", wine.year());
            item.addProperty("region", wine.region());
            item.addProperty("quality", wine.quality());
            item.addProperty("label_color", wine.labelColor());
            item.addProperty("category", "alcohol");
            item.addProperty("subcategory", "wine");
        }

        if (itemObj instanceof MaterialQualityJewelryItem) {
            MaterialQualityJewelryItem.UOMaterial material = MaterialQualityJewelryItem.getMaterial(stack);
            MaterialQualityJewelryItem.JewelryType jewelryType = MaterialQualityJewelryItem.getJewelryType(stack);
            if (material != null) item.addProperty("material", material.id());
            if (jewelryType != null) item.addProperty("jewelry_type", jewelryType.name().toLowerCase(Locale.ROOT));
            item.addProperty("quality", MaterialQualityJewelryItem.getQuality(stack));
            item.addProperty("category", "metal");
            item.addProperty("subcategory", "salvage");
        } else if (itemObj instanceof QualitySwordItem) {
            item.addProperty("material", QualitySwordItem.getMaterial(stack));
            item.addProperty("quality", QualitySwordItem.getQuality(stack));
            item.addProperty("category", "metal");
            item.addProperty("subcategory", "salvage");
        } else {
            String path = pathOnly(itemId);
            String ingotMaterial = materialFromIngotPath(path);
            if (ingotMaterial != null) {
                item.addProperty("material", ingotMaterial);
                item.addProperty("quality", 0);
                item.addProperty("category", "metal");
                item.addProperty("subcategory", "ingot");
            }
        }

        return item;
    }

    private static SaleResult postSale(ServerLevel level, JsonObject payload, String idempotencyKey) {
        SaleResult result = postSaleToEndpoint(level, Endpoint.TRADER_SALE, payload, idempotencyKey);
        if (result.statusCode() == HttpURLConnection.HTTP_NOT_FOUND ||
                result.statusCode() == HttpURLConnection.HTTP_BAD_METHOD) {
            LOGGER.warn("Rails trader_transactions endpoint unavailable, falling back to transactions for key={}", idempotencyKey);
            return postSaleToEndpoint(level, Endpoint.TRANSACTION, payload, idempotencyKey);
        }
        return result;
    }

    private static SaleResult postSaleToEndpoint(ServerLevel level, Endpoint endpoint,
                                                 JsonObject payload, String idempotencyKey) {
        try {
            var requestUri = ServerAuthRegistry.credentials(level.getServer()).orElseThrow().apiUrls().resolve(endpoint);
            HttpURLConnection conn = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(conn);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Idempotency-Key", idempotencyKey);
            byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            attachServerAuth(level, conn, bodyBytes);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int status = conn.getResponseCode();
            String body = BoundedHttp.readUtf8(
                    status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(), 1_048_576);
            if (status < 200 || status >= 300) {
                return SaleResult.failure(status, body, "Sale rejected by economy server.");
            }

            JsonObject response = body == null || body.isBlank()
                    ? new JsonObject()
                    : JsonParser.parseString(body).getAsJsonObject();
            Payout payout = parsePayout(response);
            String receipt = response.has("receipt_id") ? response.get("receipt_id").getAsString()
                    : response.has("transaction_id") ? response.get("transaction_id").getAsString()
                    : "";
            boolean idempotentReplay = booleanFrom(response, "idempotent_replay");

            LOGGER.info("Rails commodity response key={} endpoint={} body={}",
                    idempotencyKey, endpoint.symbolicName(), body);
            return SaleResult.success(status, body, response, receipt, idempotentReplay,
                    payout.gold(), payout.silver(), payout.copper());
        } catch (Exception e) {
            LOGGER.warn("Rails sale request failed key={} error={}", idempotencyKey, e.toString());
            return SaleResult.failure(0, e.toString(), "Sale failed: economy server unavailable.");
        }
    }

    private static void attachServerAuth(ServerLevel level, HttpURLConnection conn, byte[] body) {
        if (!RailsRequestAuthenticator.apply(conn, level.getServer(), body)) {
            throw new IllegalStateException("Server authentication unavailable");
        }
    }

    private static Payout parsePayout(JsonObject response) {
        JsonObject currencyGrant = currencyGrantFrom(response);
        if (currencyGrant != null) {
            Payout payout = payoutFrom(currencyGrant);
            LOGGER.info("Rails currency_grant parsed raw={} payout={}g {}s {}c",
                    currencyGrant, payout.gold(), payout.silver(), payout.copper());
            return payout;
        }

        if (response.has("payout") && response.get("payout").isJsonObject()) {
            JsonObject payout = response.getAsJsonObject("payout");
            return payoutFrom(payout);
        }
        if (response.has("currency") && response.get("currency").isJsonObject()) {
            JsonObject payout = response.getAsJsonObject("currency");
            return payoutFrom(payout);
        }
        if (response.has("total_gold")) {
            return new Payout(response.get("total_gold").getAsInt(), 0, 0);
        }
        if (response.has("total_copper")) {
            return new Payout(0, 0, response.get("total_copper").getAsInt());
        }
        return new Payout(0, 0, 0);
    }

    private static JsonObject currencyGrantFrom(JsonObject response) {
        JsonObject rootGrant = objectFrom(response, "currency_grant");
        if (rootGrant != null) return rootGrant;

        JsonObject transaction = objectFrom(response, "transaction");
        return transaction == null ? null : objectFrom(transaction, "currency_grant");
    }

    private static JsonObject objectFrom(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null;
    }

    private static Payout payoutFrom(JsonObject object) {
        return new Payout(
                intFrom(object, "gold"),
                intFrom(object, "silver"),
                intFrom(object, "copper")
        );
    }

    private static int intFrom(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return 0;

        JsonElement value = object.get(key);
        try {
            return value.getAsInt();
        } catch (NumberFormatException | IllegalStateException e) {
            LOGGER.warn("Invalid currency denomination value key={} value={}", key, value);
            return 0;
        }
    }

    private static boolean booleanFrom(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return false;

        JsonElement value = object.get(key);
        try {
            return value.getAsBoolean();
        } catch (ClassCastException | IllegalStateException e) {
            LOGGER.warn("Invalid boolean response value key={} value={}", key, value);
            return false;
        }
    }

    private static void applyRailsCommodityResponse(ServerLevel level, String cityName, JsonObject response, Reservation reservation) {
        CityManager manager = CityManager.get(level);
        City city = manager.getCity(cityName);
        if (city == null) {
            manager.addCity(cityName);
            city = manager.getCity(cityName);
        }
        if (city == null) return;

        CityInventory inv = city.getInventory();
        inv.setManager(manager);

        JsonObject source = response.has("city") && response.get("city").isJsonObject()
                ? response.getAsJsonObject("city")
                : response;

        if (hasAnySupply(source)) {
            inv.updateSupplies(
                    doubleFrom(source, "food_supply", inv.getFoodSupply()),
                    doubleFrom(source, "wood_supply", inv.getWoodSupply()),
                    doubleFrom(source, "metal_supply", inv.getMetalSupply()),
                    doubleFrom(source, "stone_supply", inv.getStoneSupply()),
                    doubleFrom(source, "textile_supply", inv.getTextileSupply()),
                    doubleFrom(source, "alcohol_supply", inv.getAlcoholSupply()),
                    doubleFrom(source, "technology_supply", inv.getTechnologySupply())
            );
            manager.setDirty();
            return;
        }

        for (SaleStack saleStack : reservation.items()) {
            ItemStack stack = saleStack.stack();
            if (stack.getItem() instanceof WeightedWoodItem woodItem) {
                inv.addCommodityWeight("wood", "logs", woodItem.getWoodType(stack), woodItem.getWeight(stack) * saleStack.quantity());
            } else if (stack.getItem() instanceof WeightedFishItem fishItem) {
                inv.addCommodityWeight("fish", "raw", CommodityMappings.fishCommodityKey(fishItem.getFishType(stack)), fishItem.getWeight(stack) * saleStack.quantity());
            } else {
                CommodityMappings.forStack(stack).ifPresent(mapping -> {
                    if (mapping.unit() == CommodityUnit.WEIGHT) {
                        double weight = stack.getItem() instanceof WeightedCommodityItem
                                ? WeightedCommodityItem.getWeight(stack) * saleStack.quantity()
                                : saleStack.quantity();
                        inv.addCommodityWeight(mapping.category(), mapping.subcategory(), mapping.itemName(), weight);
                    }
                });
            }
        }
        manager.setDirty();
    }

    private static boolean hasAnySupply(JsonObject object) {
        return object.has("food_supply") || object.has("wood_supply") || object.has("metal_supply") ||
                object.has("stone_supply") || object.has("textile_supply") || object.has("alcohol_supply") ||
                object.has("technology_supply");
    }

    private static double doubleFrom(JsonObject object, String key, double fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsDouble() : fallback;
    }

    private static void grantCurrency(ServerPlayer player, int gold, int silver, int copper) {
        LOGGER.info("Currency grant application player={} payout={}g {}s {}c",
                player.getStringUUID(), gold, silver, copper);
        giveCoin(player, ItemRegistry.GOLD_COIN.get(), gold);
        giveCoin(player, ItemRegistry.SILVER_COIN.get(), silver);
        giveCoin(player, ItemRegistry.COPPER_COIN.get(), copper);
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();
    }

    private static void giveCoin(ServerPlayer player, Item coinItem, int amount) {
        if (amount <= 0) return;
        int maxStack = new ItemStack(coinItem).getMaxStackSize();
        while (amount > 0) {
            int give = Math.min(amount, maxStack);
            ItemStack stack = new ItemStack(coinItem, give);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            amount -= give;
        }
    }

    private static void fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        TransactionFailedS2CPayload.send(player, message);
    }

    private static String formatPayout(int gold, int silver, int copper) {
        List<String> parts = new ArrayList<>();
        if (gold > 0) parts.add(gold + " gold");
        if (silver > 0) parts.add(silver + " silver");
        if (copper > 0) parts.add(copper + " copper");
        return parts.isEmpty() ? "0 copper" : String.join(", ", parts);
    }

    private static String normalizeItemId(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("item.")) value = value.substring("item.".length());
        if (value.startsWith("block.")) value = value.substring("block.".length());
        value = value.replace('.', ':');
        if (!value.contains(":")) value = "britannia_mod:" + value;
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed == null ? value : parsed.toString();
    }

    private static String pathOnly(String id) {
        if (id == null) return "";
        int idx = id.indexOf(':');
        return idx >= 0 ? id.substring(idx + 1) : id;
    }

    private static String materialFromIngotPath(String path) {
        if (path == null) return null;
        return switch (path) {
            case "copper_ingot", "copper_ingots" -> "copper";
            case "silver_ingot", "silver_ingots" -> "silver";
            case "gold_ingot", "gold_ingots" -> "gold";
            default -> null;
        };
    }

    private static void classifyMappedCommodity(JsonObject item, ItemStack stack) {
        CommodityMappings.forStack(stack).ifPresent(mapping -> {
            commodity(item, mapping.category(), mapping.subcategory(), mapping.itemName());
            if (mapping.unit() == CommodityUnit.WEIGHT) {
                double weight = stack.getItem() instanceof WeightedCommodityItem
                        ? WeightedCommodityItem.getWeight(stack) * stack.getCount()
                        : stack.getCount();
                item.addProperty("weight", weight);
            }
        });
    }

    private static void commodity(JsonObject item, String category, String subcategory, String key) {
        item.addProperty("item_name", key);
        item.addProperty("commodity_key", key);
        item.addProperty("category", category);
        item.addProperty("subcategory", subcategory);
    }

    private record SaleStack(int slot, ItemStack stack, int quantity) {}

    private record Reservation(List<SaleStack> items) {
        boolean isEmpty() {
            return items.isEmpty();
        }

        void refund(ServerPlayer player) {
            for (SaleStack item : items) {
                ItemStack refund = item.stack().copy();
                if (!player.getInventory().add(refund)) {
                    player.drop(refund, false);
                }
            }
            player.inventoryMenu.broadcastChanges();
            player.inventoryMenu.broadcastFullState();
        }

        String itemSignature() {
            List<String> parts = new ArrayList<>();
            for (SaleStack item : items) {
                ItemStack stack = item.stack();
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                parts.add(id + "x" + item.quantity());
            }
            parts.sort(String::compareTo);
            return Integer.toHexString(String.join("|", parts).hashCode());
        }
    }

    private record Payout(int gold, int silver, int copper) {}

    private record SaleResult(boolean success, int statusCode, String body, JsonObject responseJson,
                              String receiptId, boolean idempotentReplay, int gold, int silver, int copper,
                              String message) {
        static SaleResult success(int statusCode, String body, JsonObject responseJson, String receiptId,
                                  boolean idempotentReplay, int gold, int silver, int copper) {
            return new SaleResult(true, statusCode, body, responseJson, receiptId, idempotentReplay,
                    gold, silver, copper, "");
        }

        static SaleResult failure(int statusCode, String body, String message) {
            return new SaleResult(false, statusCode, body, new JsonObject(), "", false, 0, 0, 0, message);
        }
    }
}
