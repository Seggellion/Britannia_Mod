package com.seggellion.britannia_mod.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;

import com.seggellion.britannia_mod.network.HouseManagementActionPayload;
import java.util.UUID;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;

import com.mojang.logging.LogUtils;

@OnlyIn(Dist.CLIENT)
public class HouseManagementScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Main management background
    private static final ResourceLocation BACKGROUND =
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/house_management.png");

    // New banner for house name display
    private static final ResourceLocation BANNER_BACKGROUND =
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/house_sign.png");

    private final int backgroundWidth = 256;
    private final int backgroundHeight = 180;
private final BlockPos housePos;
    private final UUID houseUuid;
    private final String ownerUsername;
    private final String houseType;
    private String houseName;

    public HouseManagementScreen(BlockPos housePos, UUID houseUuid, String ownerUsername, String houseType, @Nullable String houseName) {
        super(Component.literal("House Management"));
            this.housePos = housePos;
        this.houseUuid = houseUuid;
        this.ownerUsername = ownerUsername;
        this.houseType = houseType;
            this.houseName = (houseName != null && !houseName.trim().isEmpty()) ? houseName.trim() : "An unnamed house";
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addRenderableWidget(
            Button.builder(Component.literal("Re-deed House"), (button) -> {
                HouseManagementActionPayload.sendAction(HouseManagementActionPayload.Action.REDEED);
                this.onClose();
            })
            .bounds(centerX - 50, centerY - 10, 100, 20)
            .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("Rename House"), (button) -> {
                Minecraft.getInstance().setScreen(new RenameHouseScreen(housePos,houseUuid, ownerUsername, houseType));
            })
            .bounds(centerX - 50, centerY - 40, 100, 20)
            .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("Sign Options"), (button) -> {
                Minecraft.getInstance().setScreen(
                    new SignOptionsScreen(housePos, houseUuid, ownerUsername, houseType)
                );
            })
            .bounds(centerX - 50, centerY - 70, 100, 20)
            .build()
        );


        this.addRenderableWidget(
            Button.builder(Component.literal("Cancel"), (button) -> {
                this.onClose();
            })
            .bounds(centerX - 50, centerY + 20, 100, 20)
            .build()
        );
    }

@Override
public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
    this.renderBg(guiGraphics, partialTick, mouseX, mouseY);

    // Centered house name over banner
       int bannerWidth = 256;
        int bannerHeight = 40;
    int bannerY = (this.height / 2) - backgroundHeight / 2 - bannerHeight - 5;
    guiGraphics.drawCenteredString(this.font, houseName, this.width / 2, bannerY + 10, 0xFFFFFF);

    int startX = this.width / 2 - 110;
    int startY = (this.height - backgroundHeight) / 2 + 20;

    guiGraphics.drawCenteredString(this.font, this.title.getString(), this.width / 2, startY - 12, 0xFFFFFF);

    guiGraphics.drawString(this.font, "UUID: " + houseUuid.toString(), startX, startY, 0xCCCCCC);
    guiGraphics.drawString(this.font, "Owner: " + ownerUsername, startX, startY + 12, 0xCCCCCC);
    guiGraphics.drawString(this.font, "Type: " + houseType, startX, startY + 24, 0xCCCCCC);

    super.render(guiGraphics, mouseX, mouseY, partialTick);
}


protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    int centerX = this.width / 2;

    // First: draw the banner background
    this.minecraft.getTextureManager().bindForSetup(BANNER_BACKGROUND);
    int bannerWidth = 256;
    int bannerHeight = 40;
    int bannerX = centerX - (bannerWidth / 2);
    int bannerY = (this.height / 2) - backgroundHeight / 2 - bannerHeight - 5; // 5px spacing
    guiGraphics.blit(BANNER_BACKGROUND, bannerX, bannerY, 0, 0, bannerWidth, bannerHeight);

    // Second: draw the main management panel background
    this.minecraft.getTextureManager().bindForSetup(BACKGROUND);
    int backgroundX = centerX - (backgroundWidth / 2);
    int backgroundY = (this.height - backgroundHeight) / 2;
    guiGraphics.blit(BACKGROUND, backgroundX, backgroundY, 0, 0, backgroundWidth, backgroundHeight);
}


    
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // intentionally blank – skip blur shader
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }



}
