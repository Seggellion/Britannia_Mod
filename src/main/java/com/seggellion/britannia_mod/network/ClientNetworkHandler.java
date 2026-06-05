package com.seggellion.britannia_mod.network;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.client.gui.HouseManagementScreen;
import com.seggellion.britannia_mod.client.gui.NpcCatalogScreen;
import com.seggellion.britannia_mod.network.HouseManagementScreenPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import com.seggellion.britannia_mod.network.ManaSyncPayload;
import com.seggellion.britannia_mod.npc.TraderRoleHandler;
import com.seggellion.britannia_mod.network.RenameStorePayload;
import com.seggellion.britannia_mod.network.StoreSignScreenPayload;
import com.seggellion.britannia_mod.npc.NpcRoleHandler;

import com.seggellion.britannia_mod.network.payload.OpenBlacksmithGuiS2CPayload;
import com.seggellion.britannia_mod.network.payload.BritanniaSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.client.screen.BritanniaSpawnScreen;
import com.seggellion.britannia_mod.client.screen.BlacksmithyScreen;
import com.seggellion.britannia_mod.network.payload.TraderSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.network.payload.MerchantSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload;
import com.seggellion.britannia_mod.client.screen.TraderSpawnScreen;
import com.seggellion.britannia_mod.client.screen.MerchantSpawnScreen;
import com.seggellion.britannia_mod.client.screen.StoreSignScreen;
import com.seggellion.britannia_mod.entity.ArchitectEntity;
import com.seggellion.britannia_mod.ui.ManaOverlayScreen;

// --- NEW IMPORTS START ---
import com.seggellion.britannia_mod.network.payload.QuestDestinationScreenS2CPayload;
import com.seggellion.britannia_mod.client.screen.QuestDestinationScreen;
import com.seggellion.britannia_mod.network.payload.EscortArrivedS2CPayload;
import com.seggellion.britannia_mod.network.payload.ClaimQuestRewardC2SPayload;
import com.seggellion.britannia_mod.client.screen.QuestDecisionScreen;
import com.seggellion.britannia_mod.network.payload.QuestGiverSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.client.screen.QuestGiverSpawnScreen;
import com.seggellion.britannia_mod.client.screen.ChessBoardScreen;
// --- NEW IMPORTS END ---

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;

import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ClientNetworkHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final TextColor GRAY_848484 = TextColor.fromRgb(0x848484);
    private static final ResourceLocation FONT_UO_CLASSIC =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");
// 1. Your existing 4-argument method for NPCs
    public static void openQuestDecisionScreen(
        com.seggellion.britannia_mod.quest.network.QuestModels.QuestResponse response, 
        String npcName, 
        String gender, 
        java.util.UUID npcUuid
    ) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
            new com.seggellion.britannia_mod.client.screen.QuestDecisionScreen(response, npcName, gender, npcUuid)
        );
    }

    // 2. ADD THIS: The 3-argument fallback for Environmental Triggers
    public static void openQuestDecisionScreen(
        com.seggellion.britannia_mod.quest.network.QuestModels.QuestResponse response, 
        String title, 
        java.util.UUID npcUuid
    ) {
        // Automatically passes "unknown" for the gender so the 4-arg method is happy
        openQuestDecisionScreen(response, title, "unknown", npcUuid);
    }

    public static void handleHouseScreenOnClient(HouseManagementScreenPayload data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                mc.setScreen(new HouseManagementScreen(
                    data.pos(), // BlockPos
                    data.uuid(),    // UUID
                    data.username(),
                    data.houseType(),
                    data.houseName()
                ));
            }
        });
    }

    public static void handleOpenBlacksmithGui(OpenBlacksmithGuiS2CPayload payload, IPayloadContext context) {
        // enqueueWork ensures this runs on the main client rendering thread
        context.enqueueWork(() -> {
            // Open the screen and pass it the ingotId we sent from the server
            Minecraft.getInstance().setScreen(new BlacksmithyScreen(payload.ingotId()));
        });
    }

    public static void handleOpenNpcScreen(ClientboundOpenNpcScreenPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;

            Player player = mc.player;
            NpcRoleHandler roleHandler = com.seggellion.britannia_mod.npc.TraderRoleHandlers.create(
                    pkt.npcType(), pkt.role(), pkt.city());

            // Fetch catalog before opening the screen
            roleHandler.fetchCatalog(player, pkt.city(), products -> {
              if (products == null || products.isEmpty()) {
                    String msg = pkt.npcType() == com.seggellion.britannia_mod.npc.NpcType.MERCHANT
                            ? pkt.role() + " says: 'I have nothing the city can produce right now.'"
                            : pkt.role() + " says: 'I am not interested in anything you have.'";
                    Style style = Style.EMPTY
                            .withFont(FONT_UO_CLASSIC)
                            .withColor(GRAY_848484);
                    player.sendSystemMessage(Component.literal(msg).withStyle(style));
                    return; // Cancel screen open
                }

                mc.setScreen(new NpcCatalogScreen(
                    pkt.npcType(),
                    pkt.role(),
                    pkt.city(),
                    pkt.entityId(),
                    player,
                    roleHandler,
                    products
                ));
            });
        });
    }

    public static void handleStoreSignScreenOnClient(StoreSignScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                mc.setScreen(new StoreSignScreen(
                    payload.pos(),
                    payload.storeName(),
                    payload.signType(),
                    payload.isAdmin()
                ));
            }
        });
    }

    public static void handleBritanniaSpawnScreen(BritanniaSpawnScreenS2CPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.setScreen(new BritanniaSpawnScreen(
                    p.pos(),
                    p.entityId(),
                    p.radius(),
                    p.minTicks(),
                    p.maxTicks(),
                    p.nightOnly(),
                    p.maxEntities(),
                    p.activeEntities() // ✅ added
            ));
        });
    }

public static void handleTriggerQuest(com.seggellion.britannia_mod.network.payload.TriggerQuestS2CPayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(() -> {
        com.seggellion.britannia_mod.quest.network.QuestClient.sendTrigger(payload.questId(), payload.triggerKey(), response -> {
            if (response != null && response.success) {
                // Claim items if the API granted any
                if (response.granted_items != null && !response.granted_items.isEmpty()) {
                    sendToServer(new ClaimQuestRewardC2SPayload(response.granted_items));
                }
                
                // Open the screen
                openQuestDecisionScreen(response, "The Guardian", null);
            }
        });
    });
}

    public static void handleTraderSpawnScreen(TraderSpawnScreenS2CPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new TraderSpawnScreen(
                payload.pos(),
                payload.traderType(),
                payload.cityName(),
                payload.townPersonAmount()
            ));
        });
    }

    public static void handleMerchantSpawnScreen(MerchantSpawnScreenS2CPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new MerchantSpawnScreen(
                    payload.pos(),
                    payload.merchantType(),
                    payload.cityName(),
                    payload.townPersonAmount()
            ));
        });
    }

    public static void handleManaSyncOnClient(ManaSyncPayload data, IPayloadContext context) {
        context.enqueueWork(() -> {
            ManaOverlayScreen overlay = ManaOverlayScreen.getInstance();
            if (overlay != null) {
                overlay.updateMana(data.mana());
            }
        });
    }

    // --- NEW HANDLERS START ---
    
    public static void handleQuestDestinationScreen(QuestDestinationScreenS2CPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft.getInstance().setScreen(
                new QuestDestinationScreen(
                    payload.pos(),
                    payload.cityName()
                )
            );
        });
    }

    public static void handleEscortArrived(EscortArrivedS2CPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            com.seggellion.britannia_mod.quest.network.QuestClient.sendTrigger(payload.questId(), payload.triggerKey(), response -> {
                if (response != null && response.success) {
                    if (response.granted_items != null && !response.granted_items.isEmpty()) {
                        sendToServer(new ClaimQuestRewardC2SPayload(response.granted_items));
                    }
                    Minecraft.getInstance().setScreen(
                        new QuestDecisionScreen(
                            response, 
                            payload.npcName(), 
                            payload.npcGender(),
                            payload.npcUuid()
                        )
                    );
                }
            });
        });
    }

    public static void handleQuestGiverSpawnScreen(QuestGiverSpawnScreenS2CPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft.getInstance().setScreen(
                new QuestGiverSpawnScreen(
                    payload.pos(),
                    payload.npcName(),
                    payload.cityName(),
                    payload.customApiId(),
                    payload.gender(),
                    payload.spawnRadius()
                )
            );
        });
    }

    public static void handleChessBoardScreen(com.seggellion.britannia_mod.network.payload.ChessBoardScreenS2CPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ChessBoardScreen chessScreen) {
                chessScreen.updateState(payload.state());
            } else {
                mc.setScreen(new ChessBoardScreen(payload.pos(), payload.state()));
            }
        });
    }

    // Helper method to safely send packets to the server from the client side
    public static void sendToServer(CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            Minecraft.getInstance().getConnection().send(payload);
        }
    }
    
    // --- NEW HANDLERS END ---
}
