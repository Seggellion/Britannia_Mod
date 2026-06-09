package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.QuestDestinationConfigC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class QuestDestinationScreen extends Screen {
    private final BlockPos pos;
    private final String initialCityName;

    private EditBox cityNameBox;

    public QuestDestinationScreen(BlockPos pos, String cityName) {
        super(Component.literal("Quest Destination Config"));
        this.pos = pos;
        this.initialCityName = cityName != null ? cityName : "";
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        cityNameBox = new EditBox(this.font, cx - 110, cy - 20, 220, 20, Component.literal("City Name"));
        cityNameBox.setValue(initialCityName);
        addRenderableWidget(cityNameBox);

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveAndClose())
                .bounds(cx - 110, cy + 20, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(cx + 10, cy + 20, 100, 20).build());
    }

    private void saveAndClose() {
        try {
            String newCity = cityNameBox.getValue().trim();
            NetworkHandler.sendToServer(new QuestDestinationConfigC2SPayload(pos, newCity));
        } finally {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float pt) {
        this.renderBackground(gg, mouseX, mouseY, pt);
        super.render(gg, mouseX, mouseY, pt);

        int cx = this.width / 2;
        int cy = this.height / 2;

        gg.drawCenteredString(this.font, "Configure Destination Block", cx, cy - 60, 0xFFFFFF);
        gg.drawString(this.font, "Target City (Must match Rails)", cx - 110, cy - 35, 0xAAAAAA);
        
        // Helpful hint
        String currentSafeName = cityNameBox.getValue().trim().toLowerCase().replace(" ", "_");
        gg.drawCenteredString(this.font, "§eFires trigger_key: arrived_" + currentSafeName, cx, cy + 55, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}