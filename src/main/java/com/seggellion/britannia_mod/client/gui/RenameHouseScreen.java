
package com.seggellion.britannia_mod.client.gui;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import com.seggellion.britannia_mod.network.RenameHousePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import net.minecraft.core.BlockPos;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;


import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class RenameHouseScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private EditBox nameField;
    private final UUID houseUuid;
    private final String ownerUsername;
    private final String houseType;
    private final BlockPos housePos;


    public RenameHouseScreen(BlockPos housePos, UUID houseUuid, String ownerUsername, String houseType) {
        super(Component.literal("Rename House"));
        this.houseUuid = houseUuid;
        this.ownerUsername = ownerUsername;
        this.houseType = houseType;
        this.housePos = housePos; 
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        nameField = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20, Component.literal("Enter name"));
        this.addRenderableWidget(nameField);

        // Save Button
        this.addRenderableWidget(
            Button.builder(Component.literal("Save"), (btn) -> {
                Minecraft.getInstance().getConnection().send(
                    new ServerboundCustomPayloadPacket(
                        new RenameHousePayload(housePos, houseUuid, nameField.getValue())
                    )
                );
                Minecraft.getInstance().setScreen(
                    new HouseManagementScreen(housePos, houseUuid, ownerUsername, houseType, nameField.getValue())
                );
            }).bounds(centerX - 50, centerY + 20, 100, 20).build()
        );

        // Cancel Button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            Minecraft.getInstance().setScreen(new HouseManagementScreen(housePos, houseUuid, ownerUsername, houseType, null));
        }).bounds(centerX - 50, centerY + 50, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, "Rename Your House", this.width / 2, this.height / 2 - 30, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
