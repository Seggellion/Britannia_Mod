package com.seggellion.britannia_mod.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.seggellion.britannia_mod.network.HouseManagementActionPayload;
import java.util.UUID;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@OnlyIn(Dist.CLIENT)
public class HouseManagementScreen extends Screen {

   private static final Logger LOGGER = LogUtils.getLogger();

    // Create the ResourceLocation using the static factory method.
    private static final ResourceLocation BACKGROUND =
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/gui/house_management.png");

    // Dimensions of the background image (adjust as needed for your PNG)
    private final int backgroundWidth = 256;
    private final int backgroundHeight = 180;

 private final UUID houseUuid;
    private final String ownerUsername;
    private final String houseType;

    public HouseManagementScreen(UUID houseUuid, String ownerUsername, String houseType) {
        // Use Component.literal(...) to create the title text.
        super(Component.literal("House Management"));
                this.houseUuid = houseUuid;
        this.ownerUsername = ownerUsername;
        this.houseType = houseType;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Create a "Re-deed House" button using the builder API.
        this.addRenderableWidget(
            Button.builder(Component.literal("Re-deed House"), (button) -> {
                // Call your networking code to send the REDEED action.
                HouseManagementActionPayload.sendAction(HouseManagementActionPayload.Action.REDEED);
                this.onClose();
            })
            .bounds(centerX - 50, centerY - 10, 100, 20)
            .build()
        );

        // Create a "Cancel" button using the builder API.
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
        // Render the default background using the full signature.

        // Render our custom GUI background.
        renderBg(guiGraphics, partialTick, mouseX, mouseY);
         int titleY = (this.height - backgroundHeight) / 2 + 10;
 int startX = this.width / 2 - 110;
        int startY = titleY + 20;
               


        // Draw the screen title centered over the background.
        guiGraphics.drawCenteredString(this.font, this.title.getString(), this.width / 2, titleY, 0xFFFFFF);


        guiGraphics.drawString(this.font, "UUID: " + houseUuid.toString(), startX, startY, 0xCCCCCC);
        guiGraphics.drawString(this.font, "Owner: " + ownerUsername, startX, startY + 12, 0xCCCCCC);
        guiGraphics.drawString(this.font, "Type: " + houseType, startX, startY + 24, 0xCCCCCC);


        // Render the buttons and other widgets.
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * Renders the custom GUI background image.
     */
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // Bind the custom background texture.
        this.minecraft.getTextureManager().bindForSetup(BACKGROUND);
        // Calculate coordinates to center the image.
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        // Draw the background image.
        guiGraphics.blit(BACKGROUND, x, y, 0, 0, backgroundWidth, backgroundHeight);
    }

    @Override
public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    // Do nothing — this disables default blur or shader backgrounds
}



    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
