package com.seggellion.britannia_mod.client;

import com.seggellion.britannia_mod.farming.FarmingPlotStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@OnlyIn(Dist.CLIENT)
public final class FarmingPlotOverlay {
    private FarmingPlotOverlay() {}

    @SubscribeEvent
    public static void render(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        // Re-read the current hit/root on every render, including after packets, removal and reconnect.
        FarmingPlotStatus.at(mc.level, hit.getBlockPos()).ifPresent(status -> {
            var graphics = event.getGuiGraphics();
            var text = status.text();
            int x = (graphics.guiWidth() - mc.font.width(text)) / 2;
            int y = graphics.guiHeight() / 2 + 17;
            graphics.fill(x - 3, y - 2, x + mc.font.width(text) + 3, y + mc.font.lineHeight + 2, 0x90000000);
            graphics.drawString(mc.font, text, x, y, 0xFFFFFF, true);
        });
    }
}
