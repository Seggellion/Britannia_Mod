package com.seggellion.britannia_mod.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenBackgroundMixin {
    
    @Unique
    private static final ResourceLocation SPRITESHEET = ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/chest_sequence.png");

    @Unique private static final int TOTAL_FRAMES = 15;
    @Unique private static final long MS_PER_FRAME = 100;
    @Unique private boolean drawnViaPreferredHook = false;

    @Unique private long animationStartTime = 0;

    // Added remap = false and dropped the descriptor
    @Inject(method = "init", at = @At("HEAD"), remap = false)
    private void britannia$initAnimationTime(CallbackInfo ci) {
        this.animationStartTime = 0;
    }

    // Added remap = false. The target inside @At still needs its descriptor so Mixin knows exactly which super method to hook before.
    @Inject(
        method = "render", 
        at = @At(
            value = "INVOKE", 
            target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", 
            shift = At.Shift.BEFORE
        ), 
        require = 0,
        remap = false
    )
    private void britannia$bgBeforeWidgets(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        drawAnimatedBg(guiGraphics);
        drawnViaPreferredHook = true;
    }

    // Added remap = false and dropped the descriptor
    @Inject(method = "render", at = @At("TAIL"), require = 0, remap = false)
    private void britannia$fallbackDrawAtTail(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!drawnViaPreferredHook) {
            drawAnimatedBg(guiGraphics);
        }
        drawnViaPreferredHook = false;
    }

    @Unique
    private void drawAnimatedBg(GuiGraphics guiGraphics) {
        int screenW = guiGraphics.guiWidth();
        int screenH = guiGraphics.guiHeight();

        if (this.animationStartTime == 0) {
            this.animationStartTime = System.currentTimeMillis();
        }

        long elapsed = System.currentTimeMillis() - animationStartTime;
        int currentFrame = (int) (elapsed / MS_PER_FRAME);

        if (currentFrame >= TOTAL_FRAMES) {
            currentFrame = TOTAL_FRAMES - 1;
        }

        float u1 = 0.0f;
        float u2 = 1.0f;
        float v1 = (float) currentFrame / TOTAL_FRAMES;
        float v2 = (float) (currentFrame + 1) / TOTAL_FRAMES;

        RenderSystem.setShaderTexture(0, SPRITESHEET);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Matrix4f matrix4f = guiGraphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        bufferBuilder.addVertex(matrix4f, 0.0f, (float) screenH, 0.0f).setUv(u1, v2);
        bufferBuilder.addVertex(matrix4f, (float) screenW, (float) screenH, 0.0f).setUv(u2, v2);
        bufferBuilder.addVertex(matrix4f, (float) screenW, 0.0f, 0.0f).setUv(u2, v1);
        bufferBuilder.addVertex(matrix4f, 0.0f, 0.0f, 0.0f).setUv(u1, v1);
        
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
    }
}