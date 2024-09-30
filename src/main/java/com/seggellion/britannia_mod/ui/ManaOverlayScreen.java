package com.seggellion.britannia_mod.ui;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ManaOverlayScreen {

    private static ManaOverlayScreen INSTANCE; // Added this line

    private final Minecraft mc = Minecraft.getInstance();
    private final Font font = mc.font;
    private int clientMana = 100;
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register() {
        INSTANCE = new ManaOverlayScreen(); // Assign the instance
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(INSTANCE);
    }

    public static ManaOverlayScreen getInstance() {
        return INSTANCE;
    }

    public void updateMana(int mana) {
        this.clientMana = mana;
        LOGGER.info("clientMana updated to {}", clientMana);
    }

    @SubscribeEvent
    public void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (mc.player == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int manaBars = clientMana / 10;

        GuiGraphics guiGraphics = event.getGuiGraphics();

        for (int i = 0; i < manaBars; i++) {
            int x = screenWidth / 2 + (i * 10);
            int y = screenHeight - 50;
            guiGraphics.drawString(font, Component.literal("\u2625"), x, y, 0x00FFFF);
        }
    }
}
