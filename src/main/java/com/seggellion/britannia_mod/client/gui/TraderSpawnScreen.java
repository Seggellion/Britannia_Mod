package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.TraderSpawnConfigC2SPayload;
import com.seggellion.britannia_mod.network.payload.TraderSpawnResyncC2SPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TraderSpawnScreen extends Screen {
    private final BlockPos pos;
    private String traderType;
    private String cityName;
    private int townPersonAmount;

    private EditBox cityNameBox;
    private EditBox townPersonBox;
    private Button traderButton;
    private static final Logger LOGGER = LogManager.getLogger();

    private final List<String> traderTypes = List.of(
        "wood_trader",
        "fish_trader",
        "salvage_trader",
        "alcohol_trader",
        "meat_trader",
        "metal_trader",
        "stone_trader"
    );
    private int idx = 0;

    public TraderSpawnScreen(BlockPos pos, String traderType, String cityName, int townPersonAmount) {
        super(Component.literal("Trader Spawner"));
        this.pos = pos;
        this.traderType = traderType;
        this.cityName = cityName;
        this.townPersonAmount = townPersonAmount;

        idx = Math.max(0, traderTypes.indexOf(traderType));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        traderButton = Button.builder(Component.literal(traderTypes.get(idx)), b -> {
            idx = (idx + 1) % traderTypes.size();
            traderType = traderTypes.get(idx);
            b.setMessage(Component.literal(traderType));
        }).bounds(cx - 110, cy - 50, 220, 20).build();
        addRenderableWidget(traderButton);

        cityNameBox = new EditBox(this.font, cx - 110, cy - 20, 220, 20, Component.literal("City Name"));
        cityNameBox.setValue(cityName);
        addRenderableWidget(cityNameBox);

        townPersonBox = new EditBox(this.font, cx - 110, cy + 10, 220, 20, Component.literal("Townspersons"));
        townPersonBox.setValue(Integer.toString(townPersonAmount));
        addRenderableWidget(townPersonBox);

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveAndClose())
                .bounds(cx - 110, cy + 40, 80, 20).build());


        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(cx - 40, cy + 70, 80, 20).build());
    }

    private void saveAndClose() {
        try {
            String newCity = cityNameBox.getValue().trim();
            int townCount = Math.max(0, Integer.parseInt(townPersonBox.getValue().trim()));
            LOGGER.info("send to networkhandler");
            NetworkHandler.sendToServer(new TraderSpawnConfigC2SPayload(
                pos, traderTypes.get(idx), newCity, townCount
            ));
        } catch (Exception ignored) {
        } finally {
            onClose();
        }
    }

    private void sendResync() {
        NetworkHandler.sendToServer(new TraderSpawnResyncC2SPayload(pos));
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float pt) {
        this.renderBackground(gg, mouseX, mouseY, pt);
        super.render(gg, mouseX, mouseY, pt);

        int cx = this.width / 2;
        int cy = this.height / 2;

        gg.drawCenteredString(this.font, this.title, this.width / 2, cy - 100, 0xFFFFFF);

        gg.drawString(this.font, "Trader Type", cx - 110, cy - 62, 0xFFFFFF);
        gg.drawString(this.font, "City Name", cx - 110, cy - 32, 0xFFFFFF);
        gg.drawString(this.font, "Townspeople", cx - 110, cy - 2, 0xFFFFFF);
    }

    @Override public boolean isPauseScreen() { return false; }

    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // intentionally blank
    }


    @Override public void onClose() { Minecraft.getInstance().setScreen(null); }
}
