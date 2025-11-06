package com.seggellion.britannia_mod.client.gui;

import com.seggellion.britannia_mod.skill.ClientSkillTable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class SkillTableScreen extends Screen {

    private static final ResourceLocation BG =
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/skill_screen.png");

    private static final int GUI_W = 250;
    private static final int GUI_H = 250;

    /* padding: 26 px vertically, 36 px horizontally (26 + 10) */
    private static final int PAD_V = 26;
    private static final int PAD_H = 36;
    private static final int ROW_H = 12;
    private static final int V_OFFSET = 50;        // extra drop for first row
    private static final int UP_SHIFT = 10;        // move whole GUI up 10 px

    private int guiLeft, guiTop;

    public SkillTableScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        guiLeft = (width  - GUI_W) / 2;
        guiTop  = (height - GUI_H) / 2 - UP_SHIFT; // shift upward

        addRenderableWidget(
            Button.builder(Component.literal("Close"), b -> onClose())
                  .bounds(guiLeft + GUI_W / 2 - 40, guiTop + GUI_H - 28 - 5, 80, 20)
                  .build()
        );
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        renderBackground(gg, mx, my, pt);
        drawPanel(gg);
        super.render(gg, mx, my, pt);
    }

    private void drawPanel(GuiGraphics gg) {
        Minecraft.getInstance().getTextureManager().bindForSetup(BG);
        gg.blit(BG, guiLeft, guiTop, 0, 0, GUI_W, GUI_H);

        int textColor = 0x9f3215;

        int y      = guiTop + PAD_V + V_OFFSET;
        int leftX  = guiLeft + PAD_H;
        int rightX = guiLeft + GUI_W - PAD_H;

        for (Map.Entry<String, Float> e : ClientSkillTable.snapshot().entrySet()) {
            String name  = Character.toUpperCase(e.getKey().charAt(0)) + e.getKey().substring(1);
            String value = String.format("%.1f", e.getValue());

            gg.drawString(font, name,  leftX,           y, textColor, false);

            int vW = font.width(value);
            gg.drawString(font, value, rightX - vW, y, textColor, false);

            y += ROW_H;
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float pt) {}
}
