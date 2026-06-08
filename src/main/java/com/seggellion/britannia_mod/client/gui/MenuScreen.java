package com.seggellion.britannia_mod.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class MenuScreen extends Screen {
    private static final int PANEL_W = 180;
    private static final int PANEL_H = 120;
    private static final int BUTTON_W = 120;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_GAP = 8;

    private int panelLeft;
    private int panelTop;

    public MenuScreen() {
        super(Component.literal("Menu"));
    }

    @Override
    protected void init() {
        panelLeft = (this.width - PANEL_W) / 2;
        panelTop = (this.height - PANEL_H) / 2;

        List<MenuLink> links = List.of(
                new MenuLink("Skills", () -> Minecraft.getInstance().setScreen(new SkillTableScreen())),
                new MenuLink("Quests", () -> Minecraft.getInstance().setScreen(new QuestJournalScreen()))
        );

        int totalHeight = links.size() * BUTTON_H + (links.size() - 1) * BUTTON_GAP;
        int startY = panelTop + 48;
        if (totalHeight < PANEL_H - 58) {
            startY = panelTop + 54;
        }

        for (int i = 0; i < links.size(); i++) {
            MenuLink link = links.get(i);
            int x = panelLeft + (PANEL_W - BUTTON_W) / 2;
            int y = startY + i * (BUTTON_H + BUTTON_GAP);
            addRenderableWidget(Button.builder(Component.literal(link.label()), b -> link.action().run())
                    .bounds(x, y, BUTTON_W, BUTTON_H)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphics graphics) {
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_W, panelTop + PANEL_H, 0xDD1A1713);
        graphics.fill(panelLeft + 1, panelTop + 1, panelLeft + PANEL_W - 1, panelTop + PANEL_H - 1, 0xFFE8D4A5);
        graphics.fill(panelLeft + 5, panelTop + 5, panelLeft + PANEL_W - 5, panelTop + PANEL_H - 5, 0xFFF7EAC6);

        Component title = Component.literal("Menu");
        int titleX = panelLeft + (PANEL_W - font.width(title)) / 2;
        graphics.drawString(font, title, titleX, panelTop + 18, 0xFF4A2415, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !isInsidePanel(mouseX, mouseY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isInsidePanel(double mouseX, double mouseY) {
        return mouseX >= panelLeft && mouseX <= panelLeft + PANEL_W
                && mouseY >= panelTop && mouseY <= panelTop + PANEL_H;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    private record MenuLink(String label, Runnable action) {}
}
