package com.seggellion.britannia_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.block.entity.WineBottleBlockEntity;
import com.seggellion.britannia_mod.component.WineData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class WineBottleBlockEntityRenderer implements BlockEntityRenderer<WineBottleBlockEntity> {
    
    private final Font font;
    
    // Your custom font and blue color (Using 8 hex digits for full opacity)
    private static final ResourceLocation FONT_UO_CLASSIC = ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");
    private static final Style WINE_STYLE = Style.EMPTY
            .withFont(FONT_UO_CLASSIC)
            .withColor(0xFF3366FF); 
    private static final int LINE_HEIGHT = 10;

    public WineBottleBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public AABB getRenderBoundingBox(WineBottleBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(2.0);
    }

    @Override
    public void render(WineBottleBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!(mc.hitResult instanceof BlockHitResult hitResult)
                || hitResult.getType() != HitResult.Type.BLOCK
                || !hitResult.getBlockPos().equals(blockEntity.getBlockPos())) {
            return;
        }

        // Fetch your actual wine data!
        var wineData = blockEntity.getWineData();
        if (isEmptyWineData(wineData)) return;

        Component[] textLines = {
                Component.literal("Winery: " + wineData.wineryName()).withStyle(WINE_STYLE),
                Component.literal("Varietal: " + wineData.grapeType()).withStyle(WINE_STYLE),
                Component.literal("Year: " + wineData.year()).withStyle(WINE_STYLE),
                Component.literal("Region: " + wineData.region()).withStyle(WINE_STYLE)
        };

        poseStack.pushPose();
        
        // Raise it above the bottle
        poseStack.translate(0.5, 1.7, 0.5); 
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);

        Matrix4f matrix4f = poseStack.last().pose();
        float startY = -((textLines.length - 1) * LINE_HEIGHT) / 2.0F;

        // --- THE MAGIC FIX ---
        // Grab the client's global rendering buffer instead of the block's buffer
        MultiBufferSource.BufferSource immediateBuffer = mc.renderBuffers().bufferSource();

        for (int i = 0; i < textLines.length; i++) {
            Component line = textLines[i];
            float textWidth = -this.font.width(line) / 2.0F;

            this.font.drawInBatch(
                    line,
                    textWidth,
                    startY + (i * LINE_HEIGHT),
                    -1,
                    false,
                    matrix4f,
                    immediateBuffer, // <-- Pass the global buffer here
                    Font.DisplayMode.NORMAL,
                    0x40000000,
                    15728880
            );
        }

        // Force flush the global buffer immediately so it actually draws!
        immediateBuffer.endBatch();

        poseStack.popPose();
    }

    private static boolean isEmptyWineData(WineData wineData) {
        if (wineData == null) return true;

        return isBlankOrNone(wineData.wineryName())
                && isBlankOrNone(wineData.grapeType())
                && isBlankOrNone(wineData.region())
                && wineData.year() == 0
                && wineData.quality() == 0;
    }

    private static boolean isBlankOrNone(String value) {
        return value == null || value.isBlank() || value.equalsIgnoreCase("none");
    }
}
