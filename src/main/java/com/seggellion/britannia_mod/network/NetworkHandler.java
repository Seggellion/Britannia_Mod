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
import com.seggellion.britannia_mod.block.entity.MerchantSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity;
import com.seggellion.britannia_mod.network.RenameStorePayload;
import com.seggellion.britannia_mod.network.StoreSignScreenPayload;
import com.seggellion.britannia_mod.network.payload.TogglePrivacyPayload;
import com.seggellion.britannia_mod.network.payload.BuyItemsC2SPayload;
import com.seggellion.britannia_mod.network.payload.BuyMerchantItemsC2SPayload;
import com.seggellion.britannia_mod.network.payload.TransactionSuccessS2CPayload;
import com.seggellion.britannia_mod.network.payload.TransactionFailedS2CPayload;
import com.seggellion.britannia_mod.network.payload.CloseScreenS2CPayload;
import com.seggellion.britannia_mod.network.payload.OpenBlacksmithGuiS2CPayload;
import com.seggellion.britannia_mod.network.HousePlacementPayload;
import com.seggellion.britannia_mod.network.HouseManagementScreenPayload;
import com.seggellion.britannia_mod.network.SkillSyncPayload;
import com.seggellion.britannia_mod.network.payload.BritanniaSpawnConfigC2SPayload;
import com.seggellion.britannia_mod.network.payload.BritanniaSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.network.payload.TraderSpawnConfigC2SPayload;
import com.seggellion.britannia_mod.network.payload.TraderSpawnResyncC2SPayload;
import com.seggellion.britannia_mod.network.payload.TraderSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.network.payload.MerchantSpawnConfigC2SPayload;
import com.seggellion.britannia_mod.network.payload.MerchantSpawnResyncC2SPayload;
import com.seggellion.britannia_mod.network.payload.MerchantSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.network.payload.QuestGiverSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.network.payload.QuestGiverSpawnConfigC2SPayload;
import com.seggellion.britannia_mod.network.payload.QuestDestinationScreenS2CPayload;
import com.seggellion.britannia_mod.network.payload.QuestDestinationConfigC2SPayload;
import com.seggellion.britannia_mod.network.payload.CraftBlacksmithItemC2SPayload;
import com.seggellion.britannia_mod.network.payload.ChessBoardMoveC2SPayload;
import com.seggellion.britannia_mod.network.payload.ChessBoardScreenS2CPayload;
import com.seggellion.britannia_mod.network.payload.EscortArrivedS2CPayload;
import com.seggellion.britannia_mod.network.payload.OpenQuestScreenS2CPayload;
import com.seggellion.britannia_mod.network.payload.ItemBurnedS2CPayload;
import com.seggellion.britannia_mod.network.payload.ClientboundSyncQuestsPayload;
import com.seggellion.britannia_mod.network.payload.ServerboundQuestAcceptedPayload;
import com.seggellion.britannia_mod.network.payload.ServerboundQuitQuestPayload;
import com.seggellion.britannia_mod.skill.crafting.CraftableDef;
import com.seggellion.britannia_mod.skill.crafting.CraftableRegistry;
import com.seggellion.britannia_mod.skill.BlacksmithCrafting;
import com.seggellion.britannia_mod.network.payload.GrantCoinsC2SPayload;
import com.seggellion.britannia_mod.network.payload.SellItemsC2SPayload;
import com.seggellion.britannia_mod.network.HousePlacementHandler;
import com.seggellion.britannia_mod.structure.HousePrivacyHandler;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.economy.ServerEconomyService;
import com.seggellion.britannia_mod.economy.MerchantEconomyService;
import com.seggellion.britannia_mod.spawner.BritanniaSpawnableEntities;
import com.seggellion.britannia_mod.quest.ServerQuestService;
import com.seggellion.britannia_mod.quest.ServerQuestTable;

import net.minecraft.world.level.block.entity.BlockEntity;

import com.seggellion.britannia_mod.structure.HouseActionHandler;
import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload;
import com.seggellion.britannia_mod.network.ClientboundSyncCityTokenPayload;
import com.seggellion.britannia_mod.network.payload.SpawnEscortC2SPayload;
import com.seggellion.britannia_mod.network.QuestPayloadHandler;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.minecraft.nbt.CompoundTag;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import com.seggellion.britannia_mod.item.WeightedFishItem;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class NetworkHandler {
private static final java.util.Set<String> PROCESSED_RECEIPTS = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static final Logger LOGGER = LogUtils.getLogger();

 @SubscribeEvent
public static void register(final RegisterPayloadHandlersEvent event) {
    ClientModWhitelist.registerPayloads(event);

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
        SellItemsC2SPayload.TYPE, SellItemsC2SPayload.STREAM_CODEC,
        (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                ServerEconomyService.sellRequestedItems(p, payload);
            }
        }));

    registrar.playToServer(
        BuyMerchantItemsC2SPayload.TYPE, BuyMerchantItemsC2SPayload.STREAM_CODEC,
        (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                MerchantEconomyService.buyRequestedItems(p, payload);
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
        LOGGER.warn("Rejected client-authoritative GrantCoinsC2SPayload from {} receipt={}. Trader sales must use SellItemsC2SPayload.",
            player.getStringUUID(), payload.receipt());
        TransactionFailedS2CPayload.send(player, "Sale rejected: server must validate economy transactions.");
        if (!PROCESSED_RECEIPTS.remove("server-authorized:" + payload.receipt())) return;
        if (!payload.receipt().isEmpty() && !PROCESSED_RECEIPTS.add(payload.receipt())) {
            return; // prevent duplicate transaction
        }

        // ✅ Grant payout
        giveCoins(player, payload.gold(), payload.silver(), payload.copper());
        LOGGER.info("Remove items: {}",payload.soldItems());
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
    BritanniaSpawnConfigC2SPayload.TYPE,
    BritanniaSpawnConfigC2SPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        if (level == null) return;

        var be = level.getBlockEntity(payload.pos());
        if (be instanceof com.seggellion.britannia_mod.block.entity.BritanniaSpawnBlockEntity spawner) {
            if (!(player.isCreative() || player.hasPermissions(2))) return;
            if (!BritanniaSpawnableEntities.isAllowed(payload.entityId())) {
                player.sendSystemMessage(Component.literal("That entity is not allowed in a Britannia spawn block."));
                return;
            }
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

// Add this with your other registrar.playToServer blocks
// Add this with your other registrar.playToServer blocks
registrar.playToServer(
    com.seggellion.britannia_mod.network.payload.ClaimQuestRewardC2SPayload.TYPE,
    com.seggellion.britannia_mod.network.payload.ClaimQuestRewardC2SPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer player)) return;

        for (com.seggellion.britannia_mod.quest.network.QuestModels.ItemData itemData : payload.items()) {
            
            // 1. Resolve the namespace
            ResourceLocation itemId = itemData.id.contains(":") ? 
                ResourceLocation.parse(itemData.id) : 
                ResourceLocation.fromNamespaceAndPath("britannia_mod", itemData.id);
                
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);

            // 2. Give the item with quantity!
            if (item != net.minecraft.world.item.Items.AIR) {
                int remaining = itemData.count;
                int maxStack = new net.minecraft.world.item.ItemStack(item).getMaxStackSize();
                
                while (remaining > 0) {
                    int give = Math.min(remaining, maxStack);
                    net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, give);
                    stampQuestReward(stack, itemData, payload, player);
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                    remaining -= give;
                }
            } else if (itemData.id.equals("magic_ring")) {
                net.minecraft.world.item.ItemStack ring = new net.minecraft.world.item.ItemStack(ItemRegistry.ONE_RING.get(), itemData.count);
                ring.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("a magic gold ring").withStyle(net.minecraft.ChatFormatting.GOLD));
                stampQuestReward(ring, itemData, payload, player);

                if (!player.getInventory().add(ring)) player.drop(ring, false);
            } else {
                LOGGER.warn("Quest reward item could not be resolved player={} item_id={} count={} quest_id={}",
                    player.getStringUUID(), itemData.id, itemData.count, payload.questId());
            }
        }
        
        player.inventoryMenu.broadcastChanges();
    })
);


registrar.playToServer(
    TraderSpawnConfigC2SPayload.TYPE,
    TraderSpawnConfigC2SPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (!(ctx.player() instanceof ServerPlayer player)) return;

        LOGGER.info("Network TraderSpawnConfig received");

        ServerLevel level = player.serverLevel();
        if (level == null) return;

        var be = level.getBlockEntity(payload.pos());
        if (be instanceof TraderSpawnBlockEntity spawner) {
            spawner.applyAndResync(
                payload.traderType(),
                payload.cityName(),
                payload.townPersonAmount()
            );
        }
    })
);

registrar.playToServer(
    MerchantSpawnConfigC2SPayload.TYPE,
    MerchantSpawnConfigC2SPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (!(ctx.player() instanceof ServerPlayer player)) return;

        LOGGER.info("Network MerchantSpawnConfig received");

        ServerLevel level = player.serverLevel();
        if (level == null) return;

        var be = level.getBlockEntity(payload.pos());
        if (be instanceof MerchantSpawnBlockEntity spawner) {
            spawner.applyAndResync(
                payload.merchantType(),
                payload.cityName(),
                payload.townPersonAmount()
            );
        }
    })
);

registrar.playToServer(
    QuestGiverSpawnConfigC2SPayload.TYPE,
    QuestGiverSpawnConfigC2SPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        if (level == null) return;

        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(payload.pos());
        if (be instanceof com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity spawner) {
            spawner.applyConfig(payload.npcName(), payload.cityName(), payload.customApiId(), payload.gender(), payload.spawnRadius());
        }
    })
);

// Server bound (C2S) - When admin clicks Save
registrar.playToServer(
    QuestDestinationConfigC2SPayload.TYPE,
    QuestDestinationConfigC2SPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        if (level == null) return;

        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(payload.pos());
        if (be instanceof com.seggellion.britannia_mod.block.entity.QuestDestinationBlockEntity dest) {
            dest.applyConfig(payload.cityName());
        }
    })
);


registrar.playToServer(
        SpawnEscortC2SPayload.TYPE,
        SpawnEscortC2SPayload.STREAM_CODEC,
        QuestPayloadHandler::handleSpawnEscort
    );

registrar.playToServer(
    ServerboundQuestAcceptedPayload.TYPE,
    ServerboundQuestAcceptedPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ServerQuestTable.addFromRailsAcceptSuccess(player, payload.quest());
        ClientboundSyncQuestsPayload.send(player, ServerQuestTable.snapshot(player));
    })
);

registrar.playToServer(
    ServerboundQuitQuestPayload.TYPE,
    ServerboundQuitQuestPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ServerQuestService.quitQuest(player, payload.questStateId());
    })
);


    registrar.playToClient(
        ItemBurnedS2CPayload.TYPE,
        ItemBurnedS2CPayload.CODEC,
        QuestPayloadHandler::handleItemBurned
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
    MerchantSpawnResyncC2SPayload.TYPE,
    MerchantSpawnResyncC2SPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        if (level == null) return;

        var be = level.getBlockEntity(payload.pos());
        if (be instanceof MerchantSpawnBlockEntity spawner) {
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


// blacksmithing window

registrar.playToClient(
    com.seggellion.britannia_mod.network.payload.TriggerQuestS2CPayload.TYPE,
    com.seggellion.britannia_mod.network.payload.TriggerQuestS2CPayload.STREAM_CODEC,
    net.neoforged.fml.loading.FMLLoader.getDist().isClient()
        ? com.seggellion.britannia_mod.network.ClientNetworkHandler::handleTriggerQuest
        : (p, c) -> {}
);

registrar.playToClient(
    com.seggellion.britannia_mod.network.payload.QuestTriggerResultS2CPayload.TYPE,
    com.seggellion.britannia_mod.network.payload.QuestTriggerResultS2CPayload.STREAM_CODEC,
    net.neoforged.fml.loading.FMLLoader.getDist().isClient()
        ? com.seggellion.britannia_mod.network.ClientNetworkHandler::handleQuestTriggerResult
        : (p, c) -> {}
);

registrar.playToClient(
    OpenBlacksmithGuiS2CPayload.TYPE,
    OpenBlacksmithGuiS2CPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient() 
        ? ClientNetworkHandler::handleOpenBlacksmithGui 
        : (payload, context) -> {} // Do nothing on a dedicated server
);


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
        // Update both token and secret on the client
        com.seggellion.britannia_mod.util.CityAPITokenData.setClientToken(payload.token());
        com.seggellion.britannia_mod.util.CityAPITokenData.setClientShardSecret(payload.shardSecret());
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

// Winery Bottling Logic
    registrar.playToServer(
        com.seggellion.britannia_mod.network.payload.BottlingPayload.TYPE,
        com.seggellion.britannia_mod.network.payload.BottlingPayload.STREAM_CODEC,
        com.seggellion.britannia_mod.network.ServerPayloadHandler::handleBottling
    );

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
        ClientboundSyncQuestsPayload.TYPE,
        ClientboundSyncQuestsPayload.STREAM_CODEC,
        FMLLoader.getDist().isClient()
            ? (payload, ctx) -> ctx.enqueueWork(() -> ClientboundSyncQuestsPayload.handle(payload))
            : (p, c) -> {});

registrar.playToClient(
    BritanniaSpawnScreenS2CPayload.TYPE,
    BritanniaSpawnScreenS2CPayload.STREAM_CODEC,
    net.neoforged.fml.loading.FMLLoader.getDist().isClient()
        ? com.seggellion.britannia_mod.network.ClientNetworkHandler::handleBritanniaSpawnScreen
        : (p, c) -> {}
);

registrar.playToServer(
    CraftBlacksmithItemC2SPayload.TYPE,
    CraftBlacksmithItemC2SPayload.STREAM_CODEC,
    NetworkHandler::handleCraftBlacksmithItem
);

registrar.playToServer(
    ChessBoardMoveC2SPayload.TYPE,
    ChessBoardMoveC2SPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (ctx.player() instanceof ServerPlayer player) {
            ChessBoardMoveC2SPayload.handle(payload, player);
        }
    })
);

registrar.playToClient(
    ChessBoardScreenS2CPayload.TYPE,
    ChessBoardScreenS2CPayload.STREAM_CODEC,
    net.neoforged.fml.loading.FMLLoader.getDist().isClient()
        ? com.seggellion.britannia_mod.network.ClientNetworkHandler::handleChessBoardScreen
        : (p, c) -> {}
);

registrar.playToClient(
    TraderSpawnScreenS2CPayload.TYPE,
    TraderSpawnScreenS2CPayload.STREAM_CODEC,
    net.neoforged.fml.loading.FMLLoader.getDist().isClient()
        ? com.seggellion.britannia_mod.network.ClientNetworkHandler::handleTraderSpawnScreen
        : (p, c) -> {}
);

// ✅ Safe Registration
registrar.playToClient(
    MerchantSpawnScreenS2CPayload.TYPE,
    MerchantSpawnScreenS2CPayload.STREAM_CODEC,
    net.neoforged.fml.loading.FMLLoader.getDist().isClient()
        ? com.seggellion.britannia_mod.network.ClientNetworkHandler::handleMerchantSpawnScreen
        : (p, c) -> {}
);

registrar.playToClient(
    QuestDestinationScreenS2CPayload.TYPE,
    QuestDestinationScreenS2CPayload.STREAM_CODEC,
    net.neoforged.fml.loading.FMLLoader.getDist().isClient()
        ? com.seggellion.britannia_mod.network.ClientNetworkHandler::handleQuestDestinationScreen
        : (p, c) -> {}
);

registrar.playToClient(
    EscortArrivedS2CPayload.TYPE,
    EscortArrivedS2CPayload.STREAM_CODEC,
    net.neoforged.fml.loading.FMLLoader.getDist().isClient()
        ? com.seggellion.britannia_mod.network.ClientNetworkHandler::handleEscortArrived
        : (p, c) -> {}
);

registrar.playToClient(
    QuestGiverSpawnScreenS2CPayload.TYPE,
    QuestGiverSpawnScreenS2CPayload.STREAM_CODEC,
    net.neoforged.fml.loading.FMLLoader.getDist().isClient()
        ? com.seggellion.britannia_mod.network.ClientNetworkHandler::handleQuestGiverSpawnScreen
        : (p, c) -> {}
);

registrar.playToServer(
    com.seggellion.britannia_mod.network.payload.dye.C2SConfirmDyeApplicationPayload.TYPE,
    com.seggellion.britannia_mod.network.payload.dye.C2SConfirmDyeApplicationPayload.STREAM_CODEC,
    (payload, context) -> context.enqueueWork(() -> {
        if (context.player() instanceof ServerPlayer player) {
            com.seggellion.britannia_mod.dye.preview.DyePreviewRuntime.confirm(player, payload.sessionId());
        }
    })
);

registrar.playToServer(
    com.seggellion.britannia_mod.network.payload.dye.C2SCancelDyePreviewPayload.TYPE,
    com.seggellion.britannia_mod.network.payload.dye.C2SCancelDyePreviewPayload.STREAM_CODEC,
    (payload, context) -> context.enqueueWork(() -> {
        if (context.player() instanceof ServerPlayer player) {
            com.seggellion.britannia_mod.dye.preview.DyePreviewRuntime.cancel(player, payload.sessionId());
        }
    })
);

registrar.playToClient(
    com.seggellion.britannia_mod.network.payload.dye.S2COpenDyePreviewPayload.TYPE,
    com.seggellion.britannia_mod.network.payload.dye.S2COpenDyePreviewPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? ClientNetworkHandler::handleOpenDyePreview
        : (payload, context) -> {}
);

registrar.playToClient(
    com.seggellion.britannia_mod.network.payload.dye.S2CDyeApplicationResultPayload.TYPE,
    com.seggellion.britannia_mod.network.payload.dye.S2CDyeApplicationResultPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? ClientNetworkHandler::handleDyeApplicationResult
        : (payload, context) -> {}
);

registrar.playToClient(
    com.seggellion.britannia_mod.network.payload.banner.S2CBannerRenderDataPayload.TYPE,
    com.seggellion.britannia_mod.network.payload.banner.S2CBannerRenderDataPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? ClientNetworkHandler::handleBannerRenderData
        : (payload, context) -> {}
);


    
}

@SubscribeEvent
public static void registerConfigurationTasks(final RegisterConfigurationTasksEvent event) {
    ClientModWhitelist.registerConfigurationTasks(event);
}

private static void stampQuestReward(
        ItemStack stack,
        com.seggellion.britannia_mod.quest.network.QuestModels.ItemData itemData,
        com.seggellion.britannia_mod.network.payload.ClaimQuestRewardC2SPayload payload,
        ServerPlayer player
) {
    if (stack == null || stack.isEmpty() || itemData == null) return;

    net.minecraft.world.item.component.CustomData existingData =
            stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY);
    CompoundTag tag = existingData.copyTag();

    String rewardItemId = itemData.id == null ? "" : itemData.id;
    if (!rewardItemId.isBlank()) {
        tag.putString("quest_item", rewardItemId);
    }

    tag.putString("quest_owner_uuid", player.getStringUUID());
    tag.putString("quest_owner_name", player.getGameProfile().getName());
    if (payload.questId() > 0) {
        tag.putLong("quest_id", payload.questId());
    }
    if (payload.questStateId() != null && !payload.questStateId().isBlank()) {
        tag.putString("quest_state_id", payload.questStateId().trim());
    }

    if (payload.hasDestroyTriggerContext() && questRewardMatchesDestroyTarget(rewardItemId, payload.destroyItemTag())) {
        tag.putString("quest_trigger_key", payload.destroyTriggerKey());
        tag.putString("quest_item", payload.destroyItemTag());
        tag.putInt("quest_min_x", payload.minX());
        tag.putInt("quest_min_y", payload.minY());
        tag.putInt("quest_min_z", payload.minZ());
        tag.putInt("quest_max_x", payload.maxX());
        tag.putInt("quest_max_y", payload.maxY());
        tag.putInt("quest_max_z", payload.maxZ());
    }

    stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.of(tag));
}

private static boolean questRewardMatchesDestroyTarget(String rewardItemId, String destroyItemTag) {
    if (rewardItemId == null || destroyItemTag == null) return false;
    if (rewardItemId.equalsIgnoreCase(destroyItemTag)) return true;
    return rewardItemId.replace("britannia_mod:", "").equalsIgnoreCase(destroyItemTag.replace("britannia_mod:", ""));
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
private static void removeSoldItems(ServerPlayer player, List<GrantCoinsC2SPayload.SoldItem> soldItems) {
    var inventory = player.getInventory();

    for (var soldItem : soldItems) {
        int remainingToRemove = soldItem.quantity();
        List<Integer> matchingSlots = new ArrayList<>();

        // 1. Gather all candidate slots containing the sold item type
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            String invId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

            // Match by ID (or name fallback based on your client logic)
            if (invId.equals(soldItem.itemId()) || invId.endsWith(":" + soldItem.itemName())) {
                
                // --- Strict Matching for Wine / Unique Data ---
                // If it's a wine item, we ONLY want to gather it if the NBT/Data exactly matches.
                // (You may need to adapt this block depending on how you compare WineData on the server)
                if (stack.has(DataComponentRegistry.WINE_DATA) && soldItem.nbt() != null) {
                    // Quick way to check if the tag matches the soldItem's tag
                    CompoundTag invTag = (CompoundTag) stack.save(player.registryAccess());
                    if (!invTag.equals(soldItem.nbt())) {
                        continue; // Skip this wine, it's a different year/winery
                    }
                }

                matchingSlots.add(i);
            }
        }

        // 2. Sort the matched slots by weight (Ascending: smallest first)
        // Items without a weight component will default to 0.0
        matchingSlots.sort((slot1, slot2) -> {
            ItemStack stack1 = inventory.getItem(slot1);
            ItemStack stack2 = inventory.getItem(slot2);

            double weight1 = stack1.getItem() instanceof WeightedFishItem fish1 ? fish1.getWeight(stack1) : 0.0;
            double weight2 = stack2.getItem() instanceof WeightedFishItem fish2 ? fish2.getWeight(stack2) : 0.0;

            return Double.compare(weight1, weight2);
        });

        // 3. Shrink the stacks until the required quantity is removed
        for (int slot : matchingSlots) {
            if (remainingToRemove <= 0) break;

            ItemStack stack = inventory.getItem(slot);
            int amountToTake = Math.min(stack.getCount(), remainingToRemove);
            
            stack.shrink(amountToTake);
            remainingToRemove -= amountToTake;
        }

        // Safety check just in case the inventory changed between client calculation and server execution
        if (remainingToRemove > 0) {
            LOGGER.warn("Could not find enough {} to remove from {}. Missing: {}", 
                soldItem.itemName(), player.getName().getString(), remainingToRemove);
        }
    }
}



private static void giveCoins(ServerPlayer player, int gold, int silver, int copper) {
    giveCoin(player, ItemRegistry.GOLD_COIN.get(), gold);
    giveCoin(player, ItemRegistry.SILVER_COIN.get(), silver);
    giveCoin(player, ItemRegistry.COPPER_COIN.get(), copper);
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

public static void handleCraftBlacksmithItem(CraftBlacksmithItemC2SPayload payload, IPayloadContext context) {
    // ALWAYS enqueue work to the main thread when modifying game state/inventory
    context.enqueueWork(() -> {
        ServerPlayer player = (ServerPlayer) context.player();
        
        // Look up the definition using the ID sent by the client
        CraftableDef def = CraftableRegistry.get(payload.craftableId());
        
        if (def != null) {
            // Hand it off to the crafting logic
            BlacksmithCrafting.processCraftRequest(player, def);
        }
    });
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
