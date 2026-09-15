package com.seggellion.britannia_mod.economy;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.server.auth.*;
import com.seggellion.britannia_mod.server.http.*;
import java.net.HttpURLConnection;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import org.slf4j.Logger;

/**
 * Resolves uncertain sales by their original Rails receipt, then delivers to the current player.
 * Inventory and a delivery marker share one player save; the marker outlives receipt resolution.
 * Full inventories and unavailable/ambiguous backend answers remain pending, never world drops.
 */
public final class TraderSaleSettlementService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MARKERS = "BritanniaTraderSaleMarkers";
    private static final Map<MinecraftServer, Set<String>> IN_FLIGHT = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<String, Long>> LAST_ATTEMPT = new WeakHashMap<>();

    private TraderSaleSettlementService() {}

    public static void markRemoved(ServerPlayer player, String key) { marker(player, key, "removed"); }
    public static boolean removed(ServerPlayer player, String key) { return !marker(player, key).isBlank(); }
    public static void clearMarker(ServerPlayer player, String key) {
        var tags = player.getPersistentData().getCompound(MARKERS);
        tags.remove(key);
        if (tags.isEmpty()) player.getPersistentData().remove(MARKERS);
        else player.getPersistentData().put(MARKERS, tags);
    }
    private static String marker(ServerPlayer player, String key) {
        return player.getPersistentData().getCompound(MARKERS).getString(key);
    }
    private static void marker(ServerPlayer player, String key, String value) {
        CompoundTag tags = player.getPersistentData().getCompound(MARKERS);
        tags.putString(key, value); player.getPersistentData().put(MARKERS, tags);
    }

    /** Starts only after exact goods and their removed marker were durably saved. */
    public static void dispatch(ServerLevel level, String key) {
        var store = TraderSaleReservationStore.get(level);
        var receipt = store.find(key);
        if (receipt == null || !receipt.replayable()) return;
        send(level, receipt, false);
    }

    /** Login and bounded server-tick retry use the same current-player resolver. */
    public static void recover(ServerLevel level, ServerPlayer player) {
        int budget = 4;
        for (var receipt : TraderSaleReservationStore.get(level).forPlayer(player.getUUID())) {
            if ((!receipt.replayable() && receipt.status() != TraderSaleReservationReceipt.Status.REFUND_PENDING) || budget-- <= 0) continue;
            recoverOne(level, player, receipt, true);
        }
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) return;
        var level = server.overworld();
        var attempts = LAST_ATTEMPT.computeIfAbsent(server, ignored -> new HashMap<>());
        long now = level.getGameTime();
        int budget = 4;
        for (var receipt : TraderSaleReservationStore.get(level).snapshot()) {
            if (!receipt.replayable() && receipt.status() != TraderSaleReservationReceipt.Status.REFUND_PENDING) continue;
            var player = server.getPlayerList().getPlayer(receipt.playerUuid());
            if (player == null || now - attempts.getOrDefault(receipt.idempotencyKey(), Long.MIN_VALUE / 2) < 600) continue;
            attempts.put(receipt.idempotencyKey(), now);
            recoverOne(level, player, receipt, false);
            if (--budget == 0) break;
        }
    }

    public static void stopped(MinecraftServer server) {
        IN_FLIGHT.remove(server); LAST_ATTEMPT.remove(server);
    }

    private static void recoverOne(ServerLevel level, ServerPlayer player, TraderSaleReservationReceipt receipt, boolean notify) {
        String key = receipt.idempotencyKey();
        var store = TraderSaleReservationStore.get(level);
        if ("delivered".equals(marker(player, key))) {
            resolveDelivered(level, player, receipt);
            return;
        }
        switch (receipt.status()) {
            case RESERVED -> {
                if (!removed(player, key)) {
                    // No removal marker in the same saved player data: no goods left this player.
                    store.resolve(key);
                    if (!store.flush(level)) store.record(receipt);
                } else {
                    store.advance(key, TraderSaleReservationReceipt.Status.ITEMS_REMOVED);
                    if (store.flush(level)) dispatch(level, key);
                }
            }
            case ITEMS_REMOVED -> dispatch(level, key);
            case DISPATCHED, RECONCILING -> send(level, receipt, true);
            case PAYOUT_PENDING, REFUND_PENDING -> deliver(level, player, receipt, notify);
        }
    }

    private static void send(ServerLevel level, TraderSaleReservationReceipt receipt, boolean reconcile) {
        var server = level.getServer();
        var inFlight = IN_FLIGHT.computeIfAbsent(server, ignored -> new HashSet<>());
        String key = receipt.idempotencyKey();
        if (!inFlight.add(key)) return;
        JsonObject request;
        try {
            request = JsonParser.parseString(receipt.requestJson()).getAsJsonObject();
            var credentials = ServerAuthRegistry.credentials(server).orElseThrow();
            if (!credentials.serviceOrigin().toString().equals(receipt.serviceOrigin())
                    || !credentials.shardName().equals(request.get("shard").getAsString())
                    || !key.equals(request.get("idempotency_key").getAsString())
                    || !receipt.playerUuid().toString().equals(request.get("player_uuid").getAsString()))
                throw new IllegalStateException("receipt does not match this server/owner");
        } catch (Exception invalid) {
            inFlight.remove(key);
            LOGGER.warn("Trader sale {} remains pending: {}", key, invalid.getMessage());
            return;
        }
        var store = TraderSaleReservationStore.get(level);
        var next = reconcile ? TraderSaleReservationReceipt.Status.RECONCILING : TraderSaleReservationReceipt.Status.DISPATCHED;
        store.advance(key, next);
        if (!store.flush(level)) {
            store.advance(key, receipt.status());
            inFlight.remove(key);
            return;
        }
        LAST_ATTEMPT.computeIfAbsent(server, ignored -> new HashMap<>()).put(key, level.getGameTime());
        ServerHttpExecutor.submit(server, () -> {
            if (reconcile) {
                var lookup = lookup(level, receipt);
                if (!lookup.available()) return ServerEconomyService.SaleResult.failure(0, "", "Receipt lookup unavailable");
                if (lookup.response() != null) return ServerEconomyService.resultFromReceipt(lookup.response());
            }
            return ServerEconomyService.postSale(level, request, key);
        }).whenComplete((result, error) -> server.execute(() -> {
            inFlight.remove(key);
            var live = store.find(key);
            if (live == null) return;
            if (error == null && result != null && confirmed(result) && matchesReceipt(result.responseJson(), receipt)) {
                store.settlement(key, TraderSaleReservationReceipt.Status.PAYOUT_PENDING, result.responseJson().toString());
            } else if (!reconcile && error == null && result != null
                    && result.statusCode() >= 400 && result.statusCode() < 500 && result.statusCode() != 408) {
                // A definitive reply to the only dispatch permits refund. A rejection AFTER
                // an uncertain dispatch cannot prove the earlier request did not commit.
                store.settlement(key, TraderSaleReservationReceipt.Status.REFUND_PENDING, "{}");
            } else {
                store.advance(key, TraderSaleReservationReceipt.Status.RECONCILING);
                store.flush(level);
                LOGGER.warn("Trader sale {} awaits authoritative receipt resolution; no refund or payout guessed", key);
                return;
            }
            if (!store.flush(level)) return;
            var currentPlayer = server.getPlayerList().getPlayer(receipt.playerUuid());
            if (currentPlayer != null) deliver(level, currentPlayer, store.find(key), true);
        }));
    }

    private record Lookup(boolean available, JsonObject response) {}
    private static Lookup lookup(ServerLevel level, TraderSaleReservationReceipt receipt) {
        HttpURLConnection connection = null;
        try {
            var credentials = ServerAuthRegistry.credentials(level.getServer()).orElseThrow();
            var uri = credentials.apiUrls().resolveQuery(RailsApiUrlResolver.Endpoint.TRADER_SALE_RECEIPT,
                    Map.of("idempotency_key", receipt.idempotencyKey(), "player_uuid", receipt.playerUuid().toString()));
            connection = (HttpURLConnection) uri.toURL().openConnection();
            BoundedHttp.configure(connection);
            connection.setRequestMethod("GET");
            if (!RailsRequestAuthenticator.apply(connection, level.getServer(), new byte[0])) return new Lookup(false, null);
            int status = connection.getResponseCode();
            if (status != 200) return new Lookup(false, null);
            var body = JsonParser.parseString(BoundedHttp.readUtf8(connection.getInputStream(), 1_048_576)).getAsJsonObject();
            if (!body.has("found")) return new Lookup(false, null);
            if (!body.get("found").getAsBoolean()) return new Lookup(true, null);
            return new Lookup(true, body.getAsJsonObject("receipt"));
        } catch (Exception failure) {
            return new Lookup(false, null);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean confirmed(ServerEconomyService.SaleResult result) {
        return result.success() && !result.receiptId().isBlank()
                && result.gold() >= 0 && result.silver() >= 0 && result.copper() >= 0
                && (long) result.gold() + result.silver() + result.copper() > 0;
    }

    private static boolean matchesReceipt(JsonObject response, TraderSaleReservationReceipt receipt) {
        var contract = TraderSaleReceiptContract.parse(response);
        if (!contract.accepted()) {
            LOGGER.warn("Trader sale receipt rejected key={} reason={}",
                    receipt.idempotencyKey(), contract.rejection());
            return false;
        }
        String mismatch = contract.receipt().mismatch(receipt.idempotencyKey(), receipt.playerUuid());
        if (!mismatch.isBlank()) {
            LOGGER.warn("Trader sale receipt rejected key={} reason={}", receipt.idempotencyKey(), mismatch);
            return false;
        }
        return true;
    }

    /** No partial delivery and no world-entity crash window when an inventory is full. */
    public static boolean deliver(ServerLevel level, ServerPlayer player, TraderSaleReservationReceipt receipt, boolean notify) {
        if (receipt == null || !receipt.playerUuid().equals(player.getUUID())
                || level.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return false;
        String key = receipt.idempotencyKey();
        var live = TraderSaleReservationStore.get(level).find(key);
        if (live == null) return false;
        receipt = live;
        if ("delivered".equals(marker(player, key))) return resolveDelivered(level, player, receipt);
        var outputs = new ArrayList<ItemStack>();
        JsonObject response = null;
        if (receipt.status() == TraderSaleReservationReceipt.Status.REFUND_PENDING) {
            if (receipt.hasUnreadableItems(level.registryAccess())) return false;
            outputs.addAll(receipt.decodeItems(level.registryAccess()));
        } else if (receipt.status() == TraderSaleReservationReceipt.Status.PAYOUT_PENDING) {
            try {
                response = JsonParser.parseString(receipt.settlementJson()).getAsJsonObject();
                var result = ServerEconomyService.resultFromReceipt(response);
                if (!confirmed(result) || !matchesReceipt(response, receipt)) return false;
                if (!coins(outputs, ItemRegistry.GOLD_COIN.get(), result.gold())
                        || !coins(outputs, ItemRegistry.SILVER_COIN.get(), result.silver())
                        || !coins(outputs, ItemRegistry.COPPER_COIN.get(), result.copper())) return false;
            } catch (RuntimeException invalid) { return false; }
        } else return false;
        var original = player.getInventory().items.stream().map(ItemStack::copy).toList();
        var planned = insertion(original, outputs);
        if (planned == null) {
            if (notify) player.sendSystemMessage(Component.literal("Your trader settlement is pending. Clear inventory space to collect it."));
            return false;
        }
        for (int i = 0; i < planned.size(); i++) player.getInventory().items.set(i, planned.get(i));
        String priorMarker = marker(player, key);
        marker(player, key, "delivered");
        if (!TraderSalePlayerDurability.save(player)) {
            for (int i = 0; i < original.size(); i++) player.getInventory().items.set(i, original.get(i));
            if (priorMarker.isBlank()) clearMarker(player, key); else marker(player, key, priorMarker);
            return false;
        }
        if (response != null) {
            try { ServerEconomyService.applyRecoveredSale(level, receipt, response); }
            catch (RuntimeException invalid) { LOGGER.warn("Sale delivered; commodity cache refresh failed for {}", key, invalid); }
        }
        player.inventoryMenu.broadcastChanges(); player.inventoryMenu.broadcastFullState();
        boolean resolved = resolveDelivered(level, player, receipt);
        if (receipt.status() == TraderSaleReservationReceipt.Status.PAYOUT_PENDING
                && player.connection.hasChannel(com.seggellion.britannia_mod.network.payload.TransactionSuccessS2CPayload.TYPE))
            com.seggellion.britannia_mod.network.payload.TransactionSuccessS2CPayload.send(player);
        else if (receipt.status() == TraderSaleReservationReceipt.Status.REFUND_PENDING
                && player.connection.hasChannel(com.seggellion.britannia_mod.network.payload.TransactionFailedS2CPayload.TYPE))
            com.seggellion.britannia_mod.network.payload.TransactionFailedS2CPayload.send(player, "Sale refused. Items returned.");
        if (notify) player.sendSystemMessage(Component.literal(receipt.status() == TraderSaleReservationReceipt.Status.PAYOUT_PENDING
                ? "Sale complete. Payment delivered." : "Sale refused. Your items have been returned."));
        return resolved;
    }

    private static boolean resolveDelivered(ServerLevel level, ServerPlayer player, TraderSaleReservationReceipt receipt) {
        var store = TraderSaleReservationStore.get(level);
        store.resolve(receipt.idempotencyKey());
        if (!store.flush(level)) {
            store.record(receipt); // Keep the player marker: retry resolves without another insertion.
            return false;
        }
        clearMarker(player, receipt.idempotencyKey());
        TraderSalePlayerDurability.save(player);
        var attempts = LAST_ATTEMPT.get(level.getServer());
        if (attempts != null) attempts.remove(receipt.idempotencyKey());
        return true;
    }

    private static boolean coins(List<ItemStack> outputs, Item item, int count) {
        // A payout larger than the entire inventory will remain pending, without allocating
        // millions of stack objects from an unexpected backend value.
        int limit = new ItemStack(item).getMaxStackSize();
        if (count > 36L * limit) return false;
        while (count > 0) {
            int n = Math.min(count, limit); outputs.add(new ItemStack(item, n)); count -= n;
        }
        return true;
    }

    private static List<ItemStack> insertion(List<ItemStack> original, List<ItemStack> outputs) {
        var result = new ArrayList<ItemStack>();
        original.forEach(stack -> result.add(stack.copy()));
        for (var output : outputs) {
            int remaining = output.getCount();
            for (var destination : result) {
                if (!destination.isEmpty() && ItemStack.isSameItemSameComponents(destination, output)) {
                    int move = Math.min(remaining, Math.max(0, destination.getMaxStackSize() - destination.getCount()));
                    destination.grow(move); remaining -= move;
                }
            }
            for (int i = 0; i < result.size() && remaining > 0; i++) if (result.get(i).isEmpty()) {
                int move = Math.min(remaining, output.getMaxStackSize());
                result.set(i, output.copyWithCount(move)); remaining -= move;
            }
            if (remaining > 0) return null;
        }
        return result;
    }
}

