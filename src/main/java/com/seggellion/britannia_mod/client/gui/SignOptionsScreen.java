package com.seggellion.britannia_mod.client.gui;

import com.seggellion.britannia_mod.client.gui.widget.SignIconButton;
import com.seggellion.britannia_mod.network.UpdateSignStylePayload;
import com.seggellion.britannia_mod.structure.HouseSignBlock;
import com.seggellion.britannia_mod.structure.HouseSignBlock.SignType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class SignOptionsScreen extends Screen {
private static final ResourceLocation BACKGROUND =
    ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/house_management.png");

private final int backgroundWidth = 256;
private final int backgroundHeight = 230;

private int currentPage = 0;
private static final int COLS = 6;
private static final int ROWS = 4;
private static final int TYPES_PER_PAGE = COLS * ROWS;



 private final BlockPos housePos;
    private final UUID houseUuid;
    private final String ownerUsername;
    private final String houseType;

   private HouseSignBlock.HolderType selectedHolder;
    private HouseSignBlock.SignType selectedSign;

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
                this.selectedSign = HouseSignBlock.SignType.DEFAULT;
            }

            if (state.hasProperty(HouseSignBlock.HOLDER_TYPE)) {
                this.selectedHolder = state.getValue(HouseSignBlock.HOLDER_TYPE);
            } else {
               this.selectedHolder = HouseSignBlock.HolderType.WOOD_1;
            }
        } else {
            this.selectedSign = HouseSignBlock.SignType.DEFAULT;
            this.selectedHolder = HouseSignBlock.HolderType.WOOD_1;
        }
    }


@Override
protected void init() {
    int centerX = width / 2;
    int startY = (this.height - backgroundHeight) / 2 + 30; // Move it to top section of your screen

    CycleButton<HouseSignBlock.HolderType> holderCycle =
        CycleButton.<HouseSignBlock.HolderType>builder(type ->
                Component.translatable("holder_type." + type.getSerializedName()))
            .withValues(HouseSignBlock.HolderType.values())
            .withInitialValue(selectedHolder)
            .create(centerX - 60, startY, 120, 20,
                    Component.literal("Holder"),
                    (btn, value) -> selectedHolder = value);

    this.addRenderableWidget(holderCycle);

    updateSignTypeButtons();
    addDoneButton();
}

 
 

private void updateSignTypeButtons() {
    clearSignButtons();

    SignType[] all = SignType.values();
    int startIdx = currentPage * TYPES_PER_PAGE;
    int endIdx = Math.min(startIdx + TYPES_PER_PAGE, all.length);

    int iconSize = 28;
    int totalGridWidth = COLS * iconSize; // 270px
    int backgroundX = (this.width - backgroundWidth) / 2;
    int baseX = backgroundX + (backgroundWidth - totalGridWidth) / 2; // centers grid
    int baseY = (this.height - backgroundHeight) / 2 + 80;

    int idx = 0;
    for (int i = startIdx; i < endIdx; i++) {
        SignType type = all[i];
        int col = idx % COLS;
        int row = idx / COLS;

        this.addRenderableWidget(new SignIconButton(
            baseX + col * iconSize,
            baseY + row * iconSize,
            type,
            ignored -> selectedSign = type
        ));

        idx++;
    }

    int arrowY = baseY + ROWS * iconSize - 10;

    if (currentPage > 0) {
        this.addRenderableWidget(Button.builder(Component.literal("◄"), btn -> {
            currentPage--;
            updateSignTypeButtons();
        }).bounds(baseX - 24, arrowY, 20, 20).build());
    }

    if (endIdx < all.length) {
        this.addRenderableWidget(Button.builder(Component.literal("►"), btn -> {
            currentPage++;
            updateSignTypeButtons();
        }).bounds(baseX + totalGridWidth + 4, arrowY, 20, 20).build());
    }
}







   private void clearSignButtons() {
    this.children().removeIf(c -> c instanceof SignIconButton);
    this.renderables.removeIf(r -> r instanceof SignIconButton);
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
    }).bounds(width / 2 - 60, (this.height + backgroundHeight) / 2 - 25, 120, 20).build()  // ✅ ← ADD THIS
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


    super.render(guiGraphics, mouseX, mouseY, partialTick);
}


}
