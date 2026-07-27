package com.seggellion.britannia_mod.network;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
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
import com.seggellion.britannia_mod.shop.Product;

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
import com.seggellion.britannia_mod.network.payload.QuestTriggerResultS2CPayload;
import com.seggellion.britannia_mod.client.screen.QuestDecisionScreen;
import com.seggellion.britannia_mod.network.payload.QuestGiverSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.client.screen.QuestGiverSpawnScreen;
import com.seggellion.britannia_mod.client.screen.ChessBoardScreen;
import com.seggellion.britannia_mod.client.screen.ServiceNpcSpawnScreen;
import com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnStateS2CPayload;
import com.seggellion.britannia_mod.quest.QuestManager;
import com.seggellion.britannia_mod.quest.network.QuestModels;
// --- NEW IMPORTS END ---

import com.seggellion.britannia_mod.network.payload.BankAccountOpenedS2CPayload;
import com.seggellion.britannia_mod.client.screen.BankScreen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ClientNetworkHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    private static final TextColor GRAY_848484 = TextColor.fromRgb(0x848484);
    private static final ResourceLocation FONT_UO_CLASSIC =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");
    private static final Style UO_STYLE = Style.EMPTY.withFont(FONT_UO_CLASSIC);

    public static void handleServiceNpcSpawnState(
            ServiceNpcSpawnStateS2CPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof ServiceNpcSpawnScreen screen) {
                screen.acceptState(payload);
            }
        });
    }
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

            java.util.List<Product> products = pkt.products().stream().map(product -> new Product(
                    product.itemId(),
                    product.name(),
                    product.price(),
                    product.currency(),
                    product.icon().isBlank() ? null : ResourceLocation.tryParse(product.icon())
            )).toList();
            if (products.isEmpty()) {
                    String msg = pkt.npcType() == com.seggellion.britannia_mod.npc.NpcType.MERCHANT
                            ? pkt.role() + " says: 'I have nothing the city can produce right now.'"
                            : pkt.role() + " says: 'I am not interested in anything you have.'";
                    Style style = Style.EMPTY
                            .withFont(FONT_UO_CLASSIC)
                            .withColor(GRAY_848484);
                    player.sendSystemMessage(Component.literal(msg).withStyle(style));
                    return;
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
                // Open the screen
                openQuestDecisionScreen(response, "The Guardian", null);
            }
        });
    });
}

public static void handleQuestTriggerResult(QuestTriggerResultS2CPayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(() -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            LOGGER.warn("Quest trigger result ignored because client player is null quest_id={} trigger_key={}",
                    payload.questId(), payload.triggerKey());
            return;
        }

        QuestModels.QuestResponse response;
        try {
            response = GSON.fromJson(payload.responseJson(), QuestModels.QuestResponse.class);
        } catch (JsonSyntaxException e) {
            LOGGER.error("Quest trigger result JSON parse failed quest_id={} trigger_key={} body={}",
                    payload.questId(), payload.triggerKey(), payload.responseJson(), e);
            return;
        }

        if (response == null) {
            LOGGER.warn("Quest trigger result was empty quest_id={} trigger_key={}", payload.questId(), payload.triggerKey());
            return;
        }

        if (!response.success) {
            LOGGER.warn("Quest trigger result failure quest_id={} trigger_key={} error={}",
                    payload.questId(), payload.triggerKey(), response.error);
            if (response.error != null && !response.error.isBlank()) {
                mc.player.sendSystemMessage(uoMessage(response.error));
            }
            return;
        }

        QuestManager.getInstance().setCurrentQuestState(response);

        handleQuestClientActions(response, payload.questId(), payload.triggerKey());

        if (response.currentNode != null) {
            openQuestDecisionScreen(response, "The Guardian", null);
        } else {
            LOGGER.warn("Quest trigger response missing node quest_id={} trigger_key={}", payload.questId(), payload.triggerKey());
        }
    });
}

private static void handleQuestClientActions(QuestModels.QuestResponse response, long questId, String triggerKey) {
    Minecraft mc = Minecraft.getInstance();
    if (response.client_actions == null || response.client_actions.isEmpty()) {
        return;
    }

    for (QuestModels.ClientAction action : response.client_actions) {
        String actionType = action.type != null && !action.type.isBlank() ? action.type : action.action;
        if ("achievement".equals(actionType)) {
            mc.getToasts().addToast(
                    SystemToast.multiline(
                            mc,
                            SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                            uoMessage("Achievement Unlocked!").withStyle(UO_STYLE.withColor(TextColor.fromRgb(0xFFAA00))),
                            uoMessage(action.name != null ? action.name : "Quest Completed")
                    )
            );
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            }
        } else if ("stat_gain".equals(actionType)) {
            if (mc.player != null) {
                if (action.karma > 0 && action.fame > 0) {
                    mc.player.sendSystemMessage(uoMessage("+" + action.karma + " Karma, +" + action.fame + " Fame"));
                } else if (action.karma > 0) {
                    mc.player.sendSystemMessage(uoMessage("+" + action.karma + " Karma"));
                } else if (action.fame > 0) {
                    mc.player.sendSystemMessage(uoMessage("+" + action.fame + " Fame"));
                }
            }
        } else if ("spawn_escort".equals(actionType)) {
            // Server-triggered environmental results do not spawn client-side escorts.
        } else {
            LOGGER.warn("Quest client action unknown quest_id={} trigger_key={} type={} action={} name={}",
                    questId, triggerKey, action.type, action.action, action.name);
        }
    }
}

private static MutableComponent uoMessage(String text) {
    return Component.literal(text).withStyle(UO_STYLE);
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

    // Milestone 7 Slice B: real Bank Screen (replaces Slice A's chat-message placeholder)
    public static void handleBankAccountOpened(BankAccountOpenedS2CPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> Minecraft.getInstance().setScreen(new BankScreen(payload)));
    }

    /**
     * Milestone 9 Slice 3a: a clean-rejection or reconciliation-required outcome for an
     * in-flight deposit/withdrawal. A clean confirm never reaches this handler at all -- see
     * {@code BankTransferResultS2CPayload}'s own docs for why the account/bank_items refresh
     * (a fresh {@link BankAccountOpenedS2CPayload}, handled just above) is the success signal
     * instead. If {@code BankScreen} is no longer the open screen (the player closed it while
     * the request was in flight), this is silently dropped -- there is nothing left to update.
     */
    public static void handleBankTransferResult(
            com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload payload, IPayloadContext ctx
    ) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof BankScreen screen) {
                screen.acceptTransferResult(payload);
            } else if (mc.screen instanceof com.seggellion.britannia_mod.client.screen.BankChequeIssuanceScreen screen) {
                // Milestone 11 NeoForge Slice 1: a cheque issuance's CLEAN_REJECTION/
                // PENDING_DELIVERY result arrives while the player is still looking at the
                // create-cheque screen, not BankScreen (a clean CONFIRMED never reaches here at
                // all -- see this payload's own class docs -- it refreshes back to BankScreen
                // via a fresh BankAccountOpenedS2CPayload instead).
                screen.acceptTransferResult(payload);
            }
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
