package com.seggellion.britannia_mod.network;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.client.gui.HouseManagementScreen;
import com.seggellion.britannia_mod.network.HouseManagementScreenPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import com.seggellion.britannia_mod.network.ManaSyncPayload;
import com.seggellion.britannia_mod.network.RenameStorePayload;
import com.seggellion.britannia_mod.network.StoreSignScreenPayload;
import com.seggellion.britannia_mod.client.screen.StoreSignScreen;
import com.seggellion.britannia_mod.client.gui.screen.ArchitectScreen;
import com.seggellion.britannia_mod.entity.ArchitectEntity;
import net.minecraft.world.entity.Entity;

import com.seggellion.britannia_mod.ui.ManaOverlayScreen;

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


    public static void handleOpenArchitectScreen(
            ClientboundOpenArchitectScreenPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;

            Entity e = mc.level.getEntity(pkt.entityId());
            if (e instanceof ArchitectEntity architect) {
                mc.setScreen(new ArchitectScreen(
                    pkt.catalog(), architect.getId(), mc.player));
            }
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


    public static void handleManaSyncOnClient(ManaSyncPayload data, IPayloadContext context) {
        context.enqueueWork(() -> {
            ManaOverlayScreen overlay = ManaOverlayScreen.getInstance();
            if (overlay != null) {
                overlay.updateMana(data.mana());
            }
        });
    }
}
