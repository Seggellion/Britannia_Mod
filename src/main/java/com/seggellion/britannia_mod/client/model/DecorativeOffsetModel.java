package com.seggellion.britannia_mod.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;


import java.util.List;

/**
 * DecorativeOffsetModel – shifts the visual rendering of a baked model using PoseStack translation.
 * Works for X/Y/Z offsets without altering vertex data.
 */
public class DecorativeOffsetModel implements BakedModel {

    private final BakedModel original;
    private final float offsetX;
    private final float offsetY;
    private final float offsetZ;

    public DecorativeOffsetModel(BakedModel original, float offsetYBlocks) {
        this(original, -0.25F, offsetYBlocks, 0.0F); // default 4 voxels west
    }

    public DecorativeOffsetModel(BakedModel original, float offsetXBlocks, float offsetYBlocks, float offsetZBlocks) {
        this.original = original;
        this.offsetX = offsetXBlocks;
        this.offsetY = offsetYBlocks;
        this.offsetZ = offsetZBlocks;
    }

@Override
public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state,
                                                  @NotNull RandomSource rand,
                                                  @NotNull ModelData data) {
    return original.getRenderTypes(state, rand, data);
}

public void renderWithOffset(BlockState state, BlockPos pos, PoseStack poseStack,
                             MultiBufferSource buffer, boolean checkSides,
                             RandomSource random, long seed, int packedLight, int packedOverlay) {

    poseStack.pushPose();
    poseStack.translate(offsetX, offsetY, offsetZ);

    ModelBlockRenderer renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
    ChunkRenderTypeSet layers = original.getRenderTypes(state, RandomSource.create(), ModelData.EMPTY);

    for (RenderType layer : layers.asList()) {
        renderer.renderModel(poseStack.last(), buffer.getBuffer(layer),
                state, original, 1.0F, 1.0F, 1.0F, packedLight, packedOverlay);
    }

    poseStack.popPose();
}

    /* ----------------------------------------------------------------------
       Vanilla-required methods
       ---------------------------------------------------------------------- */
@Override
public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state,
                                         @Nullable Direction side,
                                         @NotNull RandomSource rand,
                                         @NotNull ModelData data,
                                         @Nullable RenderType layer) {
    List<BakedQuad> base = original.getQuads(state, side, rand, data, layer);
    if (base.isEmpty()) return base;

    List<BakedQuad> shifted = new ArrayList<>(base.size());
    float ox = offsetX;
    float oz = offsetZ;

    if (state != null && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
        Direction f = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        switch (f) {
            case NORTH -> { /* no change */ }
            case SOUTH -> { ox = -offsetX; oz = -offsetZ; }
            case WEST  -> { ox =  offsetZ; oz = -offsetX; }
            case EAST  -> { ox = -offsetZ; oz =  offsetX; }
        }
    }

    for (BakedQuad q : base)
        shifted.add(shiftQuad(q, ox, offsetY, oz));

    return shifted;
}


    // Fallback for vanilla signature
    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state,
                                             @Nullable Direction side,
                                             @NotNull RandomSource rand) {
        return original.getQuads(state, side, rand);
    }

    /**
     * Shifts all 4 vertices of a quad by the given world-space offsets (in pixels / model units).
     */
    private BakedQuad shiftQuad(BakedQuad quad, float offsetX, float offsetY, float offsetZ) {
        int[] v = quad.getVertices().clone();

        // Each vertex occupies 8 ints: XYZ (float bits), UV, color/light, normal
        for (int i = 0; i < 4; i++) {
            int base = i * 8;

            float x = Float.intBitsToFloat(v[base]);
            float y = Float.intBitsToFloat(v[base + 1]);
            float z = Float.intBitsToFloat(v[base + 2]);

            // Apply the offsets
            v[base]     = Float.floatToRawIntBits(x + offsetX);
            v[base + 1] = Float.floatToRawIntBits(y + offsetY);
            v[base + 2] = Float.floatToRawIntBits(z + offsetZ);
        }

        return new BakedQuad(
                v,
                quad.getTintIndex(),
                quad.getDirection(),
                quad.getSprite(),
                quad.isShade()
        );
    }



    /* ----------------------------------------------------------------------
       Delegations
       ---------------------------------------------------------------------- */
    @Override public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
    @Override public boolean isGui3d()             { return original.isGui3d(); }
    @Override public boolean usesBlockLight()      { return original.usesBlockLight(); }
    @Override public boolean isCustomRenderer()    { return original.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
    @Override public ItemOverrides getOverrides()  { return original.getOverrides(); }
    @Override public ItemTransforms getTransforms(){ return original.getTransforms(); }
}
