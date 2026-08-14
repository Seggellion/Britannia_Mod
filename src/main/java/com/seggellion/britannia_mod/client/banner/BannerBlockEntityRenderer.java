package com.seggellion.britannia_mod.client.banner;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
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

    /**
     * Every culling side plus the {@code null} bucket. Assembly models are drawn in isolation
     * rather than as part of a chunk mesh, so nothing else can supply the faces a side-specific
     * query would omit -- all six sides and the general bucket must be emitted.
     */
    private static final Direction[] QUAD_SIDES = {
        Direction.DOWN, Direction.UP, Direction.NORTH,
        Direction.SOUTH, Direction.WEST, Direction.EAST, null
    };

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
        PoseStack.Pose pose = poseStack.last();
        for (BannerPlacedRenderPass pass : plan.passes()) {
            // MOUNT is no longer a flat quad in the cloth's own plane -- it is real geometry
            // rendered below, in the wall's plane where a bracket actually belongs.
            if (pass.type() == BannerPlacedRenderPass.Type.MOUNT) {
                continue;
            }
            VertexConsumer vertices = buffers.getBuffer(
                    pass.type() == BannerPlacedRenderPass.Type.DYE_MASK
                            ? RenderType.translucent() : RenderType.cutout());
            TextureAtlasSprite sprite = Minecraft.getInstance()
                    .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(pass.texture());
            emitTwoSided(pose, vertices, sprite, pass.argb(), sampledLight, packedOverlay,
                    plan.geometry(), false, pass.depthOffset());
        }
        if (!plan.fallback()) {
            renderAssembly(entity, state, plan, poseStack, buffers, sampledLight, packedOverlay);
        }
    }

    /**
     * Draws the wooden pole the cloth hangs from and the metal bracket(s) bolting it to the
     * wall, as baked models rather than quads: a pole has to read as round from every angle and
     * a bracket has to have depth, neither of which a flat quad can do. Positions come from
     * {@link BannerPlacedAssembly}, which derives them from the cloth's own geometry.
     *
     * <p>Any part whose model or texture failed to bake is skipped individually. The cloth is
     * already drawn by this point, so a missing pole costs its pole and nothing else.
     */
    private static void renderAssembly(
            BannerBlockEntity entity,
            BannerPlacedRenderState state,
            BannerPlacedRenderPlan plan,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        BannerPlacedAssembly assembly = BannerPlacedAssembly.from(plan.geometry(), state.orientation());
        VertexConsumer vertices = buffers.getBuffer(RenderType.cutout());

        BannerModelRepository.model(BannerAssemblyAssets.poleModelFor(entity.getBlockPos()))
                .ifPresent(pole -> renderPart(poseStack, vertices, pole, assembly.poleCenter(),
                        assembly.yRotationDegrees(), 0.0F, assembly.poleLength(),
                        packedLight, packedOverlay));

        Optional<BakedModel> bracket = BannerModelRepository.model(BannerAssemblyAssets.BRACKET_MODEL);
        Optional<TextureAtlasSprite> metal = state.appearance().mountTexture()
                .flatMap(BannerModelRepository::texture);
        if (bracket.isEmpty() || metal.isEmpty()) {
            return;
        }
        // The bracket is authored against the brass sheet; this re-maps its quads onto whichever
        // metal the banner's mount actually selected. BASE_TEXTURE keeps every untinted quad,
        // which is all of them -- the bracket carries no dye-mask layer.
        BakedModel materialised = new BannerFilteredBakedModel(
                bracket.orElseThrow(), BannerFilteredBakedModel.Selection.BASE_TEXTURE,
                metal.orElseThrow());
        for (Vec3 anchor : assembly.bracketAnchors()) {
            renderPart(poseStack, vertices, materialised, anchor, assembly.yRotationDegrees(),
                    assembly.bracketExtraYRotationDegrees(), 1.0, packedLight, packedOverlay);
        }
    }

    /**
     * Places one assembly model so its canonical pole-axis point lands on {@code anchor}, then
     * emits its quads. {@code scaleAlongPole} stretches the model along its own +X, which is how
     * one authored pole serves every banner width; the trailing translate happens in that scaled
     * frame, so the model's x=0 end lands half a pole-length back from the anchor.
     */
    private static void renderPart(
            PoseStack poseStack,
            VertexConsumer vertices,
            BakedModel model,
            Vec3 anchor,
            float yRotationDegrees,
            float extraYRotationDegrees,
            double scaleAlongPole,
            int packedLight,
            int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(anchor.x, anchor.y, anchor.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRotationDegrees));
        if (extraYRotationDegrees != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(extraYRotationDegrees));
        }
        poseStack.scale((float) scaleAlongPole, 1.0F, 1.0F);
        poseStack.translate(-BannerPlacedAssembly.MODEL_POLE_AXIS.x,
                -BannerPlacedAssembly.MODEL_POLE_AXIS.y,
                -BannerPlacedAssembly.MODEL_POLE_AXIS.z);
        PoseStack.Pose pose = poseStack.last();
        RandomSource random = RandomSource.create(0L);
        for (Direction side : QUAD_SIDES) {
            for (BakedQuad quad : model.getQuads(null, side, random)) {
                vertices.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, packedLight, packedOverlay);
            }
        }
        poseStack.popPose();
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
            int argb, int light, int packedOverlay, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Direction normal) {
        vertex(pose, vertices, a, sprite.getU0(), sprite.getV0(), argb, light, packedOverlay, normal);
        vertex(pose, vertices, b, sprite.getU0(), sprite.getV1(), argb, light, packedOverlay, normal);
        vertex(pose, vertices, c, sprite.getU1(), sprite.getV1(), argb, light, packedOverlay, normal);
        vertex(pose, vertices, d, sprite.getU1(), sprite.getV0(), argb, light, packedOverlay, normal);
    }

    private static void emitBack(
            PoseStack.Pose pose, VertexConsumer vertices, TextureAtlasSprite sprite,
            int argb, int light, int packedOverlay, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Direction normal) {
        vertex(pose, vertices, d, sprite.getU0(), sprite.getV0(), argb, light, packedOverlay, normal);
        vertex(pose, vertices, c, sprite.getU0(), sprite.getV1(), argb, light, packedOverlay, normal);
        vertex(pose, vertices, b, sprite.getU1(), sprite.getV1(), argb, light, packedOverlay, normal);
        vertex(pose, vertices, a, sprite.getU1(), sprite.getV0(), argb, light, packedOverlay, normal);
    }

    private static void vertex(
            PoseStack.Pose pose, VertexConsumer vertices, Vec3 point, float u, float v,
            int argb, int light, int packedOverlay, Direction normal) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(light)
                .setNormal(pose, normal.getStepX(), normal.getStepY(), normal.getStepZ());
    }

    private static double outsideDistance(double value, double minimum, double maximum) {
        return value < minimum ? minimum - value : value > maximum ? value - maximum : 0.0;
    }
}
