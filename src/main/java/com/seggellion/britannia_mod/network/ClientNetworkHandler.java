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

import com.seggellion.britannia_mod.network.payload.MonsterSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.client.screen.MonsterSpawnScreen;
import com.seggellion.britannia_mod.network.payload.TraderSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload;
import com.seggellion.britannia_mod.client.screen.TraderSpawnScreen;
import com.seggellion.britannia_mod.client.screen.StoreSignScreen;
import com.seggellion.britannia_mod.entity.ArchitectEntity;
import com.seggellion.britannia_mod.ui.ManaOverlayScreen;

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


public static void handleOpenNpcScreen(ClientboundOpenNpcScreenPayload pkt, IPayloadContext ctx) {
    ctx.enqueueWork(() -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Player player = mc.player;
        TraderRoleHandler roleHandler = new TraderRoleHandler(pkt.role(), pkt.city());

        // Fetch catalog before opening the screen
        roleHandler.fetchCatalog(player, pkt.city(), products -> {
          if (products == null || products.isEmpty()) {
                String msg = pkt.role() + " says: 'I am not interested in anything you have.'";
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
                player
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

public static void handleMonsterSpawnScreen(MonsterSpawnScreenS2CPayload p,
                                            net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
    ctx.enqueueWork(() -> {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new MonsterSpawnScreen(
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


    public static void handleManaSyncOnClient(ManaSyncPayload data, IPayloadContext context) {
        context.enqueueWork(() -> {
            ManaOverlayScreen overlay = ManaOverlayScreen.getInstance();
            if (overlay != null) {
                overlay.updateMana(data.mana());
            }
        });
    }
}
