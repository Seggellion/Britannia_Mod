package com.seggellion.britannia_mod.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.seggellion.britannia_mod.block.HouseSignBlock.HolderType;
import com.seggellion.britannia_mod.block.HouseSignBlock.SignType;
import com.seggellion.britannia_mod.network.UpdateSignStylePayload;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import com.seggellion.britannia_mod.block.HouseSignBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SignOptionsScreen extends Screen {
private static final ResourceLocation BACKGROUND =
    ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/house_management.png");

private final int backgroundWidth = 256;
private final int backgroundHeight = 180;


 private final BlockPos housePos;
    private final UUID houseUuid;
    private final String ownerUsername;
    private final String houseType;

    private HolderType selectedHolder;
    private SignType selectedSign;


    private int scrollOffset = 0;
    private final int visibleRows = 6;

    public SignOptionsScreen(BlockPos housePos, UUID houseUuid, String ownerUsername, String houseType) {
        super(Component.literal("Sign Options"));
        this.housePos = housePos;
        this.houseUuid = houseUuid;
        this.ownerUsername = ownerUsername;
        this.houseType = houseType;

        // Load initial state from block
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            BlockState state = level.getBlockState(housePos);
            if (state.hasProperty(HouseSignBlock.SIGN_TYPE)) {
                this.selectedSign = state.getValue(HouseSignBlock.SIGN_TYPE);
            } else {
                this.selectedSign = SignType.DEFAULT;
            }

            if (state.hasProperty(HouseSignBlock.HOLDER_TYPE)) {
                this.selectedHolder = state.getValue(HouseSignBlock.HOLDER_TYPE);
            } else {
                this.selectedHolder = HolderType.WOOD;
            }
        } else {
            this.selectedSign = SignType.DEFAULT;
            this.selectedHolder = HolderType.WOOD;
        }
    }


    @Override
    protected void init() {
        int centerX = width / 2;
        int startY = height / 4;

        int holderX = centerX - (HolderType.values().length * 40 / 2);
        for (HolderType type : HolderType.values()) {
            this.addRenderableWidget(
                Button.builder(Component.literal(type.toString()), btn -> {
                    selectedHolder = type;
                }).bounds(holderX, startY, 60, 20).build()
            );
            holderX += 65;
        }

        updateSignTypeButtons();
        addDoneButton();
    }

    private void updateSignTypeButtons() {
        int centerX = width / 2;
        int startY = height / 2;

        clearSignButtons();

        SignType[] allTypes = SignType.values();
        int row = 0;
        for (int i = scrollOffset; i < Math.min(allTypes.length, scrollOffset + visibleRows); i++) {
            final SignType type = allTypes[i];
            int y = startY + (row * 22);
            this.addRenderableWidget(
                Button.builder(Component.literal(type.toString()), btn -> {
                    selectedSign = type;
                }).bounds(centerX - 100, y, 200, 20).build()
            );
            row++;
        }

        // Scroll buttons
        if (scrollOffset > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"), btn -> {
                scrollOffset = Math.max(0, scrollOffset - 1);
                init(); // refresh
            }).bounds(centerX + 105, startY - 2, 20, 20).build());
        }

        if (scrollOffset + visibleRows < SignType.values().length) {
            this.addRenderableWidget(Button.builder(Component.literal("▼"), btn -> {
                scrollOffset = Math.min(SignType.values().length - visibleRows, scrollOffset + 1);
                init(); // refresh
            }).bounds(centerX + 105, startY + visibleRows * 22 - 2, 20, 20).build());
        }
    }

    private void clearSignButtons() {
        // Remove only sign-type buttons (not holder or Done)
        this.children().removeIf(c -> {
            if (c instanceof Button b) {
                String label = b.getMessage().getString();
                return label.equals("▲") || label.equals("▼") || isSignTypeButton(label);
            }
            return false;
        });

        this.renderables.removeIf(r -> {
            if (r instanceof Button b) {
                String label = b.getMessage().getString();
                return label.equals("▲") || label.equals("▼") || isSignTypeButton(label);
            }
            return false;
        });
    }

    private boolean isSignTypeButton(String label) {
        for (SignType s : SignType.values()) {
            if (s.toString().equals(label)) return true;
        }
        return false;
    }

    private void addDoneButton() {
        this.addRenderableWidget(
            Button.builder(Component.literal("Apply & Back"), btn -> {
                Minecraft.getInstance().getConnection().send(
                    new ServerboundCustomPayloadPacket(
                        new UpdateSignStylePayload(housePos, selectedSign, selectedHolder)
                    )
                );

                Minecraft.getInstance().setScreen(
                    new HouseManagementScreen(housePos, houseUuid, ownerUsername, houseType, null)
                );
            }).bounds(width / 2 - 60, height - 40, 120, 20).build()
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }


protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    int centerX = this.width / 2;
    int backgroundX = centerX - (backgroundWidth / 2);
    int backgroundY = (this.height - backgroundHeight) / 2;

    this.minecraft.getTextureManager().bindForSetup(BACKGROUND);
    guiGraphics.blit(BACKGROUND, backgroundX, backgroundY, 0, 0, backgroundWidth, backgroundHeight);
}

public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    // intentionally blank – skip blur shader
}

@Override
public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
    this.renderBg(guiGraphics, partialTick, mouseX, mouseY);

    int startX = this.width / 2 - 110;
    int startY = (this.height - backgroundHeight) / 2 + 20;

    guiGraphics.drawCenteredString(this.font, this.title.getString(), this.width / 2, startY - 12, 0xFFFFFF);

    guiGraphics.drawString(this.font, "Selected Holder: " + selectedHolder, startX, startY + 48, 0xAAAAAA);
    guiGraphics.drawString(this.font, "Selected Sign: " + selectedSign, startX, startY + 60, 0xAAAAAA);

    super.render(guiGraphics, mouseX, mouseY, partialTick);
}


}
