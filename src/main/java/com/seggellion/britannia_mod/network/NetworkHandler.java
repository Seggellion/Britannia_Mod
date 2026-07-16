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
import com.seggellion.britannia_mod.network.payload.ServerboundQuitQuestPayload;
import com.seggellion.britannia_mod.skill.crafting.CraftableDef;
import com.seggellion.britannia_mod.skill.crafting.CraftableRegistry;
import com.seggellion.britannia_mod.skill.BlacksmithCrafting;
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
    private static final Logger LOGGER = LogUtils.getLogger();

 @SubscribeEvent
public static void register(final RegisterPayloadHandlersEvent event) {
    ClientModWhitelist.registerPayloads(event);

    final PayloadRegistrar registrar = event.registrar("1");

    /* ---------- packets that exist on BOTH sides or are SERVER-bound ---------- */

    registrar.playToServer(
        com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnConfigureC2SPayload.TYPE,
        com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnConfigureC2SPayload.STREAM_CODEC,
        com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnPayloadHandler::handleConfigure
    );
    registrar.playToServer(
        com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnResyncC2SPayload.TYPE,
        com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnResyncC2SPayload.STREAM_CODEC,
        com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnPayloadHandler::handleResync
    );
    registrar.playToClient(
        com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnStateS2CPayload.TYPE,
        com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnStateS2CPayload.STREAM_CODEC,
        FMLLoader.getDist().isClient()
            ? ClientNetworkHandler::handleServiceNpcSpawnState
            : (payload, context) -> {}
    );

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
    com.seggellion.britannia_mod.network.payload.QuestActionC2SPayload.TYPE,
    com.seggellion.britannia_mod.network.payload.QuestActionC2SPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        com.seggellion.britannia_mod.quest.QuestProxyService.handle(player, payload);
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
    com.seggellion.britannia_mod.network.payload.QuestActionResultS2CPayload.TYPE,
    com.seggellion.britannia_mod.network.payload.QuestActionResultS2CPayload.STREAM_CODEC,
    net.neoforged.fml.loading.FMLLoader.getDist().isClient()
        ? (payload, context) -> com.seggellion.britannia_mod.quest.network.QuestClient.handleProxyResult(payload)
        : (payload, context) -> {}
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


    
}

@SubscribeEvent
public static void registerConfigurationTasks(final RegisterConfigurationTasksEvent event) {
    ClientModWhitelist.registerConfigurationTasks(event);
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
