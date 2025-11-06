package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.network.RenameStorePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.FormattedText;

import net.minecraft.network.chat.Style;

import org.jetbrains.annotations.Nullable;

public class StoreSignScreen extends Screen {

    private static final ResourceLocation BACKGROUND =
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/house_sign.png");

    private final BlockPos signPos;
    private final String signType;
    private final boolean isAdmin;

    private String storeName;
    private EditBox nameInput;

    private final int backgroundWidth = 256;
    private final int backgroundHeight = 256;

    public StoreSignScreen(BlockPos pos, String storeName, String signType, boolean isAdmin) {
        super(Component.literal("Store Sign"));
        this.signPos = pos;
        this.storeName = storeName;
        this.signType = signType;
        this.isAdmin = isAdmin;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (isAdmin) {
            nameInput = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20, Component.literal("Store Name"));
            nameInput.setValue(storeName);
            this.addRenderableWidget(nameInput);

            this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
                RenameStorePayload.send(signPos, nameInput.getValue());
                this.onClose();
            }).bounds(centerX - 50, centerY + 20, 100, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
                .bounds(centerX - 50, centerY + 50, 100, 20).build());
    }

@Override
public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    // intentionally blank – skip blur shader
}


@Override
public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
    this.renderBg(guiGraphics, partialTick, mouseX, mouseY);

    int centerX = this.width / 2;
    int centerY = this.height / 2;

    // Adjust Y offset to center text inside the 256x256 background
    int textY = centerY - (font.lineHeight + 2); // line 1 (store name)

Style style = Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("britannia_mod", "avatar"));
String[] words = storeName.trim().split("\\s+");

int lineHeight = (int)(font.lineHeight * 2.2); // Add spacing between lines
int totalHeight = words.length * lineHeight;
int baseY = centerY - (totalHeight / 2);

for (int i = 0; i < words.length; i++) {
    Component line = Component.literal(words[i]).withStyle(style);
    guiGraphics.drawCenteredString(this.font, line, centerX, baseY + i * lineHeight, 0xFFFFFF);
}


    super.render(guiGraphics, mouseX, mouseY, partialTick);
}



protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    int centerX = this.width / 2;
    int centerY = this.height / 2;

    int backgroundX = centerX - (backgroundWidth / 2);
    int backgroundY = centerY - (backgroundHeight / 2);

    Minecraft.getInstance().getTextureManager().bindForSetup(BACKGROUND);
    guiGraphics.blit(BACKGROUND, backgroundX, backgroundY, 0, 0, backgroundWidth, backgroundHeight);
}


    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
