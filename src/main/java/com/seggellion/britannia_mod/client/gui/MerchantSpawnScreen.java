package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.merchant.MerchantTypes;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.MerchantSpawnConfigC2SPayload;
import com.seggellion.britannia_mod.network.payload.MerchantSpawnResyncC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

public class MerchantSpawnScreen extends Screen {
    private final BlockPos pos;
    private String merchantType;
    private final String cityName;
    private final int townPersonAmount;

    private EditBox cityNameBox;
    private EditBox townPersonBox;
    private Button merchantButton;

    private final List<String> merchantTypes = MerchantTypes.configKeys();
    private int idx = 0;

    public MerchantSpawnScreen(BlockPos pos, String merchantType, String cityName, int townPersonAmount) {
        super(Component.literal("Merchant Spawner"));
        this.pos = pos;
        this.merchantType = merchantType;
        this.cityName = cityName;
        this.townPersonAmount = townPersonAmount;
        this.idx = Math.max(0, merchantTypes.indexOf(merchantType));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        merchantButton = Button.builder(Component.literal(merchantTypes.get(idx)), button -> {
            idx = (idx + 1) % merchantTypes.size();
            merchantType = merchantTypes.get(idx);
            button.setMessage(Component.literal(merchantType));
        }).bounds(cx - 110, cy - 50, 220, 20).build();
        addRenderableWidget(merchantButton);

        cityNameBox = new EditBox(this.font, cx - 110, cy - 20, 220, 20, Component.literal("City Name"));
        cityNameBox.setValue(cityName);
        addRenderableWidget(cityNameBox);

        townPersonBox = new EditBox(this.font, cx - 110, cy + 10, 220, 20, Component.literal("Townspersons"));
        townPersonBox.setValue(Integer.toString(townPersonAmount));
        addRenderableWidget(townPersonBox);

        addRenderableWidget(Button.builder(Component.literal("Save"), button -> saveAndClose())
                .bounds(cx - 110, cy + 40, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Resync"), button -> sendResync())
                .bounds(cx + 30, cy + 40, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(cx - 40, cy + 70, 80, 20).build());
    }

    private void saveAndClose() {
        try {
            String newCity = cityNameBox.getValue().trim();
            int townCount = Math.max(0, Integer.parseInt(townPersonBox.getValue().trim()));
            NetworkHandler.sendToServer(new MerchantSpawnConfigC2SPayload(
                    pos, merchantTypes.get(idx), newCity, townCount
            ));
        } catch (Exception ignored) {
        } finally {
            onClose();
        }
    }

    private void sendResync() {
        NetworkHandler.sendToServer(new MerchantSpawnResyncC2SPayload(pos));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int cy = this.height / 2;

        graphics.drawCenteredString(this.font, this.title, this.width / 2, cy - 100, 0xFFFFFF);
        graphics.drawString(this.font, "Merchant Type", cx - 110, cy - 62, 0xFFFFFF);
        graphics.drawString(this.font, "City Name", cx - 110, cy - 32, 0xFFFFFF);
        graphics.drawString(this.font, "Townspeople", cx - 110, cy - 2, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
