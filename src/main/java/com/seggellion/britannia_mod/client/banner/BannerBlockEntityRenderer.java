package com.seggellion.britannia_mod.client.banner;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The one complete placed-banner renderer, owned only by the authoritative anchor entity. */
public final class BannerBlockEntityRenderer implements BlockEntityRenderer<BannerBlockEntity> {
    public static final int VIEW_DISTANCE = 64;

    public BannerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            BannerBlockEntity entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        if (entity.isRemoved() || entity.getLevel() == null) {
            return;
        }
        BannerPlacedRenderState state = BannerPlacedRenderStateExtractor.extract(
                entity, ClientBannerRenderData.current(), BannerModelRepository.availability(),
                BannerModelRepository.generation());
        BannerPlacedRenderPlan plan = BannerPlacedRenderCache.resolve(state);
        int sampledLight = sampleLoadedLighting(entity, state.lightingSamplePositions(), packedLight);
        VertexConsumer vertices = buffers.getBuffer(RenderType.cutout());
        PoseStack.Pose pose = poseStack.last();
        for (BannerPlacedRenderPass pass : plan.passes()) {
            TextureAtlasSprite sprite = Minecraft.getInstance()
                    .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(pass.texture());
            boolean mount = pass.type() == BannerPlacedRenderPass.Type.MOUNT;
            emitTwoSided(pose, vertices, sprite, pass.argb(), sampledLight, packedOverlay,
                    plan.geometry(), mount, pass.depthOffset());
        }
    }

    @Override
    public AABB getRenderBoundingBox(BannerBlockEntity entity) {
        return BannerPlacedRenderBounds.from(entity);
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public boolean shouldRender(BannerBlockEntity entity, Vec3 cameraPosition) {
        AABB bounds = getRenderBoundingBox(entity);
        double dx = outsideDistance(cameraPosition.x, bounds.minX, bounds.maxX);
        double dy = outsideDistance(cameraPosition.y, bounds.minY, bounds.maxY);
        double dz = outsideDistance(cameraPosition.z, bounds.minZ, bounds.maxZ);
        return dx * dx + dy * dy + dz * dz <= (double) VIEW_DISTANCE * VIEW_DISTANCE;
    }

    private static int sampleLoadedLighting(
            BannerBlockEntity entity, List<BlockPos> samplePositions, int fallbackLight) {
        var level = entity.getLevel();
        if (level == null) {
            return fallbackLight;
        }
        int block = LightTexture.block(fallbackLight);
        int sky = LightTexture.sky(fallbackLight);
        int sampled = 0;
        for (BlockPos position : samplePositions) {
            if (sampled >= 6 || !level.hasChunkAt(position)) {
                continue;
            }
            int light = LevelRenderer.getLightColor(level, position);
            block = Math.max(block, LightTexture.block(light));
            sky = Math.max(sky, LightTexture.sky(light));
            sampled++;
        }
        return LightTexture.pack(block, sky);
    }

    private static void emitTwoSided(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            TextureAtlasSprite sprite,
            int argb,
            int packedLight,
            int packedOverlay,
            BannerPlacedGeometryPlan geometry,
            boolean mount,
            double depth) {
        Vec3 a = mount ? geometry.mountTopLeft() : geometry.topLeft();
        Vec3 b = mount ? geometry.mountBottomLeft() : geometry.bottomLeft();
        Vec3 c = mount ? geometry.mountBottomRight() : geometry.bottomRight();
        Vec3 d = mount ? geometry.mountTopRight() : geometry.topRight();
        Direction normal = geometry.frontNormal();
        Vec3 offset = new Vec3(normal.getStepX() * depth, 0, normal.getStepZ() * depth);
        emitFront(pose, vertices, sprite, argb, packedLight, packedOverlay,
                a.add(offset), b.add(offset), c.add(offset), d.add(offset), normal);
        emitBack(pose, vertices, sprite, argb, packedLight, packedOverlay,
                a.subtract(offset), b.subtract(offset), c.subtract(offset), d.subtract(offset),
                normal.getOpposite());
    }

    private static void emitFront(
            PoseStack.Pose pose, VertexConsumer vertices, TextureAtlasSprite sprite,
            int argb, int light, int overlay, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Direction normal) {
        vertex(pose, vertices, a, sprite.getU0(), sprite.getV0(), argb, light, overlay, normal);
        vertex(pose, vertices, b, sprite.getU0(), sprite.getV1(), argb, light, overlay, normal);
        vertex(pose, vertices, c, sprite.getU1(), sprite.getV1(), argb, light, overlay, normal);
        vertex(pose, vertices, d, sprite.getU1(), sprite.getV0(), argb, light, overlay, normal);
    }

    private static void emitBack(
            PoseStack.Pose pose, VertexConsumer vertices, TextureAtlasSprite sprite,
            int argb, int light, int overlay, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Direction normal) {
        vertex(pose, vertices, d, sprite.getU0(), sprite.getV0(), argb, light, overlay, normal);
        vertex(pose, vertices, c, sprite.getU0(), sprite.getV1(), argb, light, overlay, normal);
        vertex(pose, vertices, b, sprite.getU1(), sprite.getV1(), argb, light, overlay, normal);
        vertex(pose, vertices, a, sprite.getU1(), sprite.getV0(), argb, light, overlay, normal);
    }

    private static void vertex(
            PoseStack.Pose pose, VertexConsumer vertices, Vec3 point, float u, float v,
            int argb, int light, int overlay, Direction normal) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, normal.getStepX(), normal.getStepY(), normal.getStepZ());
    }

    private static double outsideDistance(double value, double minimum, double maximum) {
        return value < minimum ? minimum - value : value > maximum ? value - maximum : 0.0;
    }
}
