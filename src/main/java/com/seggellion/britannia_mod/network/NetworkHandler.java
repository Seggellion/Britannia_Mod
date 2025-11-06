package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.ui.ManaOverlayScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import com.seggellion.britannia_mod.client.gui.HouseManagementScreen;
import com.seggellion.britannia_mod.network.ManaSyncPayload;
import com.seggellion.britannia_mod.block.entity.TraderSpawnBlockEntity;

import com.seggellion.britannia_mod.network.RenameStorePayload;
import com.seggellion.britannia_mod.network.StoreSignScreenPayload;
import com.seggellion.britannia_mod.network.payload.TogglePrivacyPayload;
import com.seggellion.britannia_mod.network.payload.BuyItemsC2SPayload;
import com.seggellion.britannia_mod.network.payload.TransactionSuccessS2CPayload;
import com.seggellion.britannia_mod.network.payload.TransactionFailedS2CPayload;
import com.seggellion.britannia_mod.network.payload.CloseScreenS2CPayload;
import com.seggellion.britannia_mod.network.HousePlacementPayload;
import com.seggellion.britannia_mod.network.HouseManagementScreenPayload;
import com.seggellion.britannia_mod.network.SkillSyncPayload;
import com.seggellion.britannia_mod.network.payload.MonsterSpawnConfigC2SPayload;
import com.seggellion.britannia_mod.network.payload.MonsterSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.network.payload.TraderSpawnConfigC2SPayload;
import com.seggellion.britannia_mod.network.payload.TraderSpawnResyncC2SPayload;
import com.seggellion.britannia_mod.network.payload.TraderSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.network.HousePlacementHandler;
import com.seggellion.britannia_mod.structure.HousePrivacyHandler;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.city.City;

import net.minecraft.world.level.block.entity.BlockEntity;

import com.seggellion.britannia_mod.structure.HouseActionHandler;
import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload;
import com.seggellion.britannia_mod.network.ClientboundSyncCityTokenPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.fml.loading.FMLLoader;

import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class NetworkHandler {
private static final java.util.Set<String> PROCESSED_RECEIPTS = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static final Logger LOGGER = LogUtils.getLogger();

 @SubscribeEvent
public static void register(final RegisterPayloadHandlersEvent event) {
    final PayloadRegistrar registrar = event.registrar("1");

    /* ---------- packets that exist on BOTH sides or are SERVER-bound ---------- */

    registrar.playToServer(
        BuyItemsC2SPayload.TYPE, BuyItemsC2SPayload.STREAM_CODEC,
        (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                BuyItemsC2SPayload.handle(payload, p);
            }
        }));

    registrar.playToServer(
        HousePlacementPayload.TYPE, HousePlacementPayload.STREAM_CODEC,
        (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                HousePlacementHandler.handle(payload, p);
            }
        }));

    registrar.playToServer(
        SpellCastPayload.TYPE, SpellCastPayload.STREAM_CODEC,
        (data, ctx) -> handleSpellCastOnServer(data, ctx));

    registrar.playToServer(
        RenameStorePayload.TYPE, RenameStorePayload.STREAM_CODEC,
        (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                RenameStorePayload.handle(payload, p);
            }
        }));

    registrar.playToServer(
        RenameHousePayload.TYPE, RenameHousePayload.STREAM_CODEC,
        (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                RenameHouseHandler.handle(payload, p);
            }
        }));

    registrar.playToServer(
        UpdateSignStylePayload.TYPE, UpdateSignStylePayload.STREAM_CODEC,
        (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                UpdateSignStyleHandler.handle(payload, p);
            }
        }));

    registrar.playToServer(
        HouseManagementActionPayload.TYPE, HouseManagementActionPayload.STREAM_CODEC,
        (data, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p
                    && data.action() == HouseManagementActionPayload.Action.REDEED) {
                HouseActionHandler.handleRedeed(p);
            }
        }));

registrar.playToServer(
    com.seggellion.britannia_mod.network.payload.GrantCoinsC2SPayload.TYPE,
    com.seggellion.britannia_mod.network.payload.GrantCoinsC2SPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        if (!payload.receipt().isEmpty() && !PROCESSED_RECEIPTS.add(payload.receipt())) {
            return; // prevent duplicate transaction
        }

        // ✅ Grant payout
        giveCoins(player, payload.gold(), payload.silver(), payload.copper());
        removeSoldItems(player, payload.soldItems());

        // ✅ Update city treasury
        ServerLevel level = player.serverLevel();
        CityManager manager = CityManager.get(level);
        City city = manager.getCity(payload.city());

        if (city != null) {
            CityInventory inv = city.getInventory();

            // --- Adjust treasury ---
            int gold   = inv.getCurrencyAmount("gold")   + payload.gold();
            int silver = inv.getCurrencyAmount("silver") + payload.silver();
            int copper = inv.getCurrencyAmount("copper") + payload.copper();
            inv.updateTreasury(gold, silver, copper);

            manager.setDirty();

        // --- Trigger trader re-evaluation within nearby chunks ---
        int viewDistance = level.getServer().getPlayerList().getViewDistance();
        ChunkPos playerChunk = player.chunkPosition();
        int totalCopper = copper + (silver * 100) + (gold * 10000);

        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                int cx = playerChunk.x + dx;
                int cz = playerChunk.z + dz;

                if (!level.hasChunk(cx, cz)) continue;
                var chunk = level.getChunk(cx, cz);

                chunk.getBlockEntities().values().forEach(be -> {
                    if (be instanceof TraderSpawnBlockEntity spawn &&
                        spawn.getCityName().equalsIgnoreCase(payload.city())) {
                        spawn.applyCityUpdate((int)inv.getFoodSupply(), totalCopper);
                    }
                });
            }
        }


            LOGGER.info("Updated treasury for {} after transaction: {}g {}s {}c",
                payload.city(), gold, silver, copper);
        } else {
            LOGGER.warn("Transaction completed, but city {} not found in CityManager!", payload.city());
        }

        // ✅ Display payout message
        Style style = Style.EMPTY
            .withFont(ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic"))
            .withColor(TextColor.fromRgb(0x848484));

        Component payoutMsg = Component.literal(
            String.format("Received %dg %ds %dc from %s",
                payload.gold(), payload.silver(), payload.copper(), payload.city())
        ).withStyle(style);

        player.sendSystemMessage(payoutMsg);

        // ✅ Notify client of success
        com.seggellion.britannia_mod.network.payload.TransactionSuccessS2CPayload.send(player);
    })
);

registrar.playToServer(
    MonsterSpawnConfigC2SPayload.TYPE,
    MonsterSpawnConfigC2SPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        if (level == null) return;

        var be = level.getBlockEntity(payload.pos());
        if (be instanceof com.seggellion.britannia_mod.block.entity.MonsterSpawnBlockEntity spawner) {
            spawner.applyConfig(
                payload.entityId(),
                payload.radius(),
                payload.minTicks(),
                payload.maxTicks(),
                payload.nightOnly(),
                payload.maxEntities()
            );
        }
    })
);


registrar.playToServer(
    TraderSpawnConfigC2SPayload.TYPE,
    TraderSpawnConfigC2SPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        if (level == null) return;

        var be = level.getBlockEntity(payload.pos());
        if (be instanceof com.seggellion.britannia_mod.block.entity.TraderSpawnBlockEntity spawner) {
            spawner.setTraderType(payload.traderType());
            spawner.setCityName(payload.cityName());
            spawner.setTownPersonAmount(payload.townPersonAmount());
            spawner.setChanged();
        }
    })
);


registrar.playToServer(
    TraderSpawnResyncC2SPayload.TYPE,
    TraderSpawnResyncC2SPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        if (level == null) return;

        var be = level.getBlockEntity(payload.pos());
        if (be instanceof com.seggellion.britannia_mod.block.entity.TraderSpawnBlockEntity spawner) {
            spawner.forceResync();
        }
    })
);


registrar.playToServer(
    TogglePrivacyPayload.TYPE,
    TogglePrivacyPayload.STREAM_CODEC,
    (pkt, ctx) -> ctx.enqueueWork(() -> {

        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();

        BlockEntity be = level.getBlockEntity(pkt.pos());
        if (!(be instanceof HouseLotBlockEntity lot)) return;

        HousePrivacyHandler.handle(player, lot, pkt.makePrivate());
    }));




        // Mana sync
       /* ---------- client-bound packets ---------- */
// Mana sync
registrar.playToClient(
    ManaSyncPayload.TYPE,
    ManaSyncPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? ClientNetworkHandler::handleManaSyncOnClient
        : (p, c) -> {});

// Architect screen
registrar.playToClient(
    ClientboundOpenNpcScreenPayload.TYPE,
    ClientboundOpenNpcScreenPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? ClientNetworkHandler::handleOpenNpcScreen
        : (p, c) -> {}
);

registrar.playToClient(
    ClientboundSyncCityTokenPayload.TYPE,
    ClientboundSyncCityTokenPayload.STREAM_CODEC,
    (payload, context) -> {
        com.seggellion.britannia_mod.util.CityAPITokenData.setClientToken(payload.token());
    }
);


// Close current screen
registrar.playToClient(
    CloseScreenS2CPayload.TYPE,
    CloseScreenS2CPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? CloseScreenS2CPayload::handle
        : (p, c) -> {});

// Store-sign screen
registrar.playToClient(
    StoreSignScreenPayload.TYPE,
    StoreSignScreenPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? ClientNetworkHandler::handleStoreSignScreenOnClient
        : (p, c) -> {});

// Transaction success / failure
registrar.playToClient(
    TransactionSuccessS2CPayload.TYPE,
    TransactionSuccessS2CPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? TransactionSuccessS2CPayload::handle
        : (p, c) -> {});

registrar.playToClient(
    TransactionFailedS2CPayload.TYPE,
    TransactionFailedS2CPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? TransactionFailedS2CPayload::handle
        : (p, c) -> {});

// House-management screen
registrar.playToClient(
    HouseManagementScreenPayload.TYPE,
    HouseManagementScreenPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? ClientNetworkHandler::handleHouseScreenOnClient
        : (p, c) -> {});

    // skill system registration
    registrar.playToClient(
        SkillSyncPayload.TYPE,
        SkillSyncPayload.STREAM_CODEC,
        FMLLoader.getDist().isClient()
            ? (payload, ctx) -> ctx.enqueueWork(() -> SkillSyncPayload.handle(payload))
            : (p, c) -> {});

registrar.playToClient(
    MonsterSpawnScreenS2CPayload.TYPE,
    MonsterSpawnScreenS2CPayload.STREAM_CODEC,
    net.neoforged.fml.loading.FMLLoader.getDist().isClient()
        ? com.seggellion.britannia_mod.network.ClientNetworkHandler::handleMonsterSpawnScreen
        : (p, c) -> {}
);

registrar.playToClient(
    TraderSpawnScreenS2CPayload.TYPE,
    TraderSpawnScreenS2CPayload.STREAM_CODEC,
    net.neoforged.fml.loading.FMLLoader.getDist().isClient()
        ? com.seggellion.britannia_mod.network.ClientNetworkHandler::handleTraderSpawnScreen
        : (p, c) -> {}
);


    
}



private static String normalizeId(String raw) {
    if (raw == null) return "";
    String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
    // strip descriptionId prefixes
    if (s.startsWith("block.") || s.startsWith("item.")) {
        int idx = s.indexOf('.');
        if (idx >= 0 && idx + 1 < s.length()) s = s.substring(idx + 1); // e.g. "britannia_mod.cape_cod"
    }
    // turn dotted "britannia_mod.cape_cod" into "britannia_mod:cape_cod"
    s = s.replace('.', ':');
    return s;
}

private static boolean idMatches(ItemStack stack, String soldIdNormalized) {
    String stackId = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(stack.getItem()).toString().toLowerCase(java.util.Locale.ROOT);
    if (stackId.equals(soldIdNormalized)) return true;
    // lenient fallback: match by path if namespaces differ
    String stackPath = stackId.contains(":") ? stackId.substring(stackId.indexOf(':') + 1) : stackId;
    String soldPath  = soldIdNormalized.contains(":") ? soldIdNormalized.substring(soldIdNormalized.indexOf(':') + 1) : soldIdNormalized;
    return stackPath.equals(soldPath);
}

// --- main removal ---------------------------------------------------

private static void removeSoldItems(
        net.minecraft.server.level.ServerPlayer player,
        java.util.List<com.seggellion.britannia_mod.network.payload.GrantCoinsC2SPayload.SoldItem> soldItems
) {
    for (var sold : soldItems) {
        String targetId = normalizeId(sold.itemId()); // handles "block.britannia_mod.cape_cod" -> "britannia_mod:cape_cod"
        int remaining = sold.quantity();
        double targetWeight = sold.weight(); // 0 for non-weighted

        // pass 1: exact id (and weight if applicable)
        for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().items.get(i);
            if (stack.isEmpty()) continue;
            if (!idMatches(stack, targetId)) continue;

            if (stack.getItem() instanceof com.seggellion.britannia_mod.item.WeightedFishItem fishItem) {
                // weighted items are typically unstackable; remove per-item matching weight (±0.01)
                double w = fishItem.getWeight(stack);
                if (Math.abs(w - targetWeight) > 0.01) continue;
                stack.shrink(1);
                remaining -= 1;
                if (stack.isEmpty()) player.getInventory().items.set(i, net.minecraft.world.item.ItemStack.EMPTY);
            } else {
                int toRemove = Math.min(stack.getCount(), remaining);
                stack.shrink(toRemove);
                remaining -= toRemove;
                if (stack.isEmpty()) player.getInventory().items.set(i, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }

        // (optional) pass 2: if still remaining and item is not weighted, allow looser match (same id, ignore weight)
        if (remaining > 0 && targetWeight == 0.0) {
            for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
                net.minecraft.world.item.ItemStack stack = player.getInventory().items.get(i);
                if (stack.isEmpty()) continue;
                if (!idMatches(stack, targetId)) continue;
                int toRemove = Math.min(stack.getCount(), remaining);
                stack.shrink(toRemove);
                remaining -= toRemove;
                if (stack.isEmpty()) player.getInventory().items.set(i, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }

        // You can log if anything was left unmatched
        // if (remaining > 0) LOGGER.warn("Could not remove {}x of {}", remaining, targetId);
    }

    player.inventoryMenu.broadcastChanges();
}



private static void giveCoins(ServerPlayer player, int gold, int silver, int copper) {
    giveCoin(player, com.seggellion.britannia_mod.registry.ItemRegistry.GOLD_COIN.get(), gold);
    giveCoin(player, com.seggellion.britannia_mod.registry.ItemRegistry.SILVER_COIN.get(), silver);
    giveCoin(player, com.seggellion.britannia_mod.registry.ItemRegistry.COPPER_COIN.get(), copper);
}

private static void giveCoin(ServerPlayer player, net.minecraft.world.item.Item coinItem, int amount) {
    if (amount <= 0) return;
    int stackLimit = new ItemStack(coinItem).getMaxStackSize(); // usually 99

    while (amount > 0) {
        int give = Math.min(amount, stackLimit);
        ItemStack stack = new ItemStack(coinItem, give);

        // Try to insert into inventory
        boolean added = player.getInventory().add(stack);

        // If not all items were inserted, or inventory is full, drop remainder
        if (!added || !stack.isEmpty()) {
            player.drop(stack, false); // false = no random scatter
        }

        amount -= give;
    }

    player.inventoryMenu.broadcastChanges();
}



public static void sendToServer(SpellCastPayload payload) {
    if (FMLLoader.getDist().isClient()) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }
}

   public static void sendToServer(HousePlacementPayload payload) {
        if (FMLLoader.getDist().isClient()) {
            Minecraft.getInstance()
                     .getConnection()
                     .send(new ServerboundCustomPayloadPacket(payload));
        }
    }
    

public static void sendToServer(CustomPacketPayload payload) {
    if (FMLLoader.getDist().isClient()) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }
}

    // Method to send packets from server → client
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }
        // ✅ Handle spell casting on server
    public static void handleSpellCastOnServer(SpellCastPayload data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player playerEntity = context.player();
            if (playerEntity instanceof ServerPlayer player) {
                data.handleOnServer(player);
            }
        });
    }
}