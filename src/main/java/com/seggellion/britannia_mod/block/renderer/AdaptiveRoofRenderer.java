package com.seggellion.britannia_mod.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Vector3f;

import java.util.Map;

import com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntity;

public class AdaptiveRoofRenderer implements BlockEntityRenderer<AdaptiveRoofBlockEntity> {


    public AdaptiveRoofRenderer(BlockEntityRendererProvider.Context ctx) { }

    /* --------------------------------------------------------------------- */
    /*  Entry‑point                                                          */
    /* --------------------------------------------------------------------- */

    @Override
    public void render(AdaptiveRoofBlockEntity entity,
                       float partialTicks,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {

        ResourceLocation texture = entity.getBottomTexture();

        if (texture == null) return;

        poseStack.pushPose();
        renderBottomCube(poseStack, bufferSource, texture, packedLight, packedOverlay, entity);
        poseStack.popPose();
    }

    /* --------------------------------------------------------------------- */
    /*  Helpers                                                               */
    /* --------------------------------------------------------------------- */

private static float shadeFor(Direction dir) {
    return switch (dir) {
        case UP      -> 1.0f;
        case NORTH,
             SOUTH   -> 0.8f;
        case EAST,
             WEST    -> 0.6f;
        case DOWN    -> 0.5f;
    };
}


/** Emit one vertex with vanilla light + directional shading. */
private static void addVertex(VertexConsumer vc, PoseStack.Pose pose,
                              float x, float y, float z,
                              float u, float v,
                              BlockAndTintGetter level, BlockPos pos,
                              Direction face, int overlay) {

    /* block / sky light from the neighbour behind the face */
    int packed = LevelRenderer.getLightColor(level, pos.relative(face));
    int block  =  packed        & 0xFFFF;
    int sky    = (packed >> 16) & 0xFFFF;

    /* directional shade */
    float s = shadeFor(face);
    int r = (int)(255 * s);
    int g = r, b = r, a = 255;

    vc.addVertex(pose, x, y, z)
      .setColor(r, g, b, a)
      .setUv(u, v)
      .setUv2(block, sky)                           // block first, sky second
      .setOverlay(overlay)
      .setNormal(pose,
                 face.getStepX(),                   // <-- fixed accessors
                 face.getStepY(),
                 face.getStepZ());
}


/* --------------------------------------------------------------------- */
/*  Draw the lower half‑height cube                                      */
/* --------------------------------------------------------------------- */
private void renderBottomCube(PoseStack poseStack,
                              MultiBufferSource buffers,
                              ResourceLocation texture,
                              int packedLight,
                              int packedOverlay,
                              AdaptiveRoofBlockEntity be) {

    VertexConsumer builder = buffers.getBuffer(RenderType.solid());
    TextureAtlasSprite sprite = Minecraft.getInstance()
                                         .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                                         .apply(texture);

    PoseStack.Pose pose = poseStack.last();

    float u0 = sprite.getU0(), v0 = sprite.getV0();
    float u1 = sprite.getU1(), v1 = sprite.getV1();
    float vHalf = (v0 + v1) * 0.5f;

    int block =  packedLight        & 0xFFFF;   // lower 16 bits
    int sky   = (packedLight >> 16) & 0xFFFF;   // upper 16 bits

    /* Geometry bounds */
    float min = 0.0f, max = 1.0f;
    float y0  = 0.0f, y1  = 0.5f;                 // ½‑height

    BlockAndTintGetter level = be.getLevel();
    BlockPos pos   = be.getBlockPos();

    /* ------------  -Y   (BOTTOM)  ------------ */
    if (!level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
         addVertex(builder, pose, min, y0, min, u0, v0, level, pos, Direction.DOWN , packedOverlay);
        addVertex(builder, pose, max, y0, min, u1, v0, level, pos, Direction.DOWN , packedOverlay);
        addVertex(builder, pose, max, y0, max, u1, v1, level, pos, Direction.DOWN , packedOverlay);
        addVertex(builder, pose, min, y0, max, u0, v1, level, pos, Direction.DOWN , packedOverlay);
    }

    /* --------  -Z (NORTH) -------- */
    addVertex(builder, pose, max, y0, min, u0, v1, level, pos, Direction.NORTH, packedOverlay);
    addVertex(builder, pose, min, y0, min, u1, v1, level, pos, Direction.NORTH, packedOverlay);
    addVertex(builder, pose, min, y1, min, u1, vHalf, level, pos, Direction.NORTH, packedOverlay);
    addVertex(builder, pose, max, y1, min, u0, vHalf, level, pos, Direction.NORTH, packedOverlay);

    /* --------  +Z (SOUTH) -------- */
    addVertex(builder, pose, min, y0, max, u0, v1, level, pos, Direction.SOUTH, packedOverlay);
    addVertex(builder, pose, max, y0, max, u1, v1, level, pos, Direction.SOUTH, packedOverlay);
    addVertex(builder, pose, max, y1, max, u1, vHalf, level, pos, Direction.SOUTH, packedOverlay);
    addVertex(builder, pose, min, y1, max, u0, vHalf, level, pos, Direction.SOUTH, packedOverlay);

    /* --------  -X (WEST)  -------- */
    addVertex(builder, pose, min, y0, min, u0, v1, level, pos, Direction.WEST , packedOverlay);
    addVertex(builder, pose, min, y0, max, u1, v1, level, pos, Direction.WEST , packedOverlay);
    addVertex(builder, pose, min, y1, max, u1, vHalf, level, pos, Direction.WEST , packedOverlay);
    addVertex(builder, pose, min, y1, min, u0, vHalf, level, pos, Direction.WEST , packedOverlay);

    /* --------  +X (EAST)  -------- */
    addVertex(builder, pose, max, y0, max, u0, v1, level, pos, Direction.EAST , packedOverlay);
    addVertex(builder, pose, max, y0, min, u1, v1, level, pos, Direction.EAST , packedOverlay);
    addVertex(builder, pose, max, y1, min, u1, vHalf, level, pos, Direction.EAST , packedOverlay);
    addVertex(builder, pose, max, y1, max, u0, vHalf, level, pos, Direction.EAST , packedOverlay);
}

/* --------------------------------------------------------------------- */
/*  One‑vertex helper (unchanged)                                        */
/* --------------------------------------------------------------------- */
private static void add(VertexConsumer b, PoseStack.Pose pose,
                        float x, float y, float z,
                        float u, float v,
                        int sky, int block, int overlay,
                        float nx, float ny, float nz) {

    b.addVertex(pose, x, y, z)
     .setColor(255, 255, 255, 255)
     .setUv(u, v)
     .setUv2(block, sky)
     .setOverlay(overlay)
     .setNormal(pose, nx, ny, nz);
}


    /**
     * Emits an axis‑aligned rectangle:
     * Z is fixed at {@code z1}; X spans {@code x1 → x2} and Y spans {@code y1 → y2}.
     */
    private void addQuad(VertexConsumer builder,
                         PoseStack.Pose pose,
                         float x1, float y1, float z1,
                         float x2, float y2,
                         TextureAtlasSprite sprite,
                         Vector3f faceNormal,
                         int packedLight,
                         int packedOverlay) {

        float u1 = sprite.getU0(), v1 = sprite.getV0();
        float u2 = sprite.getU1(), v2 = sprite.getV1();

        int r = 255, g = 255, b = 255, a = 255;
        int sky = (packedLight >> 16) & 0xFFFF;
        int block = packedLight & 0xFFFF;

        // bottom‑left
        builder.addVertex(pose, x1, y1, z1)
               .setColor(r, g, b, a)
               .setUv(u1, v1)
               .setUv2(block, sky)
               .setOverlay(packedOverlay)
               .setNormal(pose, faceNormal.x(), faceNormal.y(), faceNormal.z());

        // bottom‑right
        builder.addVertex(pose, x2, y1, z1)
               .setColor(r, g, b, a)
               .setUv(u2, v1)
               .setUv2(block, sky)
               .setOverlay(packedOverlay)
               .setNormal(pose, faceNormal.x(), faceNormal.y(), faceNormal.z());

        // top‑right
        builder.addVertex(pose, x2, y2, z1)
               .setColor(r, g, b, a)
               .setUv(u2, v2)
               .setUv2(block, sky)
               .setOverlay(packedOverlay)
               .setNormal(pose, faceNormal.x(), faceNormal.y(), faceNormal.z());

        // top‑left
        builder.addVertex(pose, x1, y2, z1)
               .setColor(r, g, b, a)
               .setUv(u1, v2)
               .setUv2(block, sky)
               .setOverlay(packedOverlay)
               .setNormal(pose, faceNormal.x(), faceNormal.y(), faceNormal.z());
    }
}
