package com.seggellion.britannia_mod.client.gui.widget;

import com.seggellion.britannia_mod.structure.HouseSignBlock.SignType;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import java.util.function.IntConsumer;

public class SignIconButton extends AbstractWidget {
    private final SignType type;
    private final IntConsumer onClick;
    private final Minecraft minecraft = Minecraft.getInstance();


    public SignIconButton(int x, int y, SignType type, IntConsumer onClick) {
        super(x, y, 26, 26 , Component.empty());
        this.type = type;
        this.onClick = onClick;
    }

@Override
protected void updateWidgetNarration(NarrationElementOutput narration) {
    // Optional: narrate sign type
    narration.add(NarratedElementType.TITLE, Component.literal(type.getSerializedName()));
}

 @Override
public void onClick(double mouseX, double mouseY) {
    onClick.accept(type.ordinal());
}

    @Override
   protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
    ResourceLocation icon = type.icon();

    RenderSystem.enableBlend();
    g.blit(icon, getX() + 2, getY() + 2, 0, 0, 23, 23, 23, 23);
    RenderSystem.disableBlend();

    if (isHoveredOrFocused()) {
        g.renderOutline(getX(), getY(), width, height, 0xFFFFA000);
        g.renderTooltip(minecraft.font, Component.translatable("sign_type." + type.getSerializedName()), mouseX, mouseY);
    }
}

}
