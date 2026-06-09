package com.seggellion.britannia_mod.ui;

import com.seggellion.britannia_mod.magic.ManaHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;  
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class ManaOverlayScreen {

    private static ManaOverlayScreen INSTANCE;

    private final Minecraft mc = Minecraft.getInstance();
    private final Font font = mc.font;
    private int clientMana = 100;
    private static final Logger LOGGER = LogUtils.getLogger();

    // Define exactly which vanilla layers we want to shift downwards
    private static final Set<ResourceLocation> SHIFTED_LAYERS = Set.of(
        VanillaGuiLayers.PLAYER_HEALTH,
        VanillaGuiLayers.FOOD_LEVEL,
        VanillaGuiLayers.ARMOR_LEVEL,
        VanillaGuiLayers.VEHICLE_HEALTH // For horses, pigs, etc.
    );

    public static void register() {
        INSTANCE = new ManaOverlayScreen();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(INSTANCE);
    }

    public static ManaOverlayScreen getInstance() {
        return INSTANCE;
    }

    public void updateMana(int mana) {
        this.clientMana = mana;
    }

    @SubscribeEvent
    public void onPreRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        ResourceLocation layerName = event.getName();

        // 1. Cancel the XP bar entirely
        if (layerName.equals(VanillaGuiLayers.EXPERIENCE_BAR)) {
            event.setCanceled(true);
            return;
        }

        // 2. Shift the targeted vanilla layers downwards
        if (SHIFTED_LAYERS.contains(layerName)) {
            event.getGuiGraphics().pose().pushPose(); // Save current position
            event.getGuiGraphics().pose().translate(0, 7, 0); // Shift Y down by 14 pixels
        }
    }

    @SubscribeEvent
    public void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        ResourceLocation layerName = event.getName();

        // 3. Reset the position shift so we don't break other UI elements
        if (SHIFTED_LAYERS.contains(layerName)) {
            event.getGuiGraphics().pose().popPose(); // Restore previous position
        }

        // 4. Draw the custom Mana Bar ONLY ONCE per frame (attached to the hotbar)
        if (layerName.equals(VanillaGuiLayers.HOTBAR)) {
            if (mc.player == null) return;
            if (mc.options.hideGui) return;

            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            int manaBars = clientMana / 10;
            GuiGraphics guiGraphics = event.getGuiGraphics();

            for (int i = 0; i < manaBars; i++) {
                int x = screenWidth / 2 + (i * 10);
                // Shifted down from -50 to -39 to fill the void left by the XP bar
                int y = screenHeight - 42; 
                guiGraphics.drawString(font, Component.literal("\u2625"), x, y, 0x00FFFF);
            }
        }
    }
}