package com.seggellion.britannia_mod.network;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.client.gui.HouseManagementScreen;
import com.seggellion.britannia_mod.network.HouseManagementScreenPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import com.seggellion.britannia_mod.network.ManaSyncPayload;
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

    public static void handleHouseScreenOnClient(HouseManagementScreenPayload data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                mc.setScreen(new HouseManagementScreen(data.uuid(), data.username(), data.houseType()));
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
