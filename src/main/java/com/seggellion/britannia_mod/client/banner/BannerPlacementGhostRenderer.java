package com.seggellion.britannia_mod.client.banner;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.preview.BannerPlacementPreview;
import com.seggellion.britannia_mod.banner.preview.BannerPlacementPreviewPlanner;
import com.seggellion.britannia_mod.banner.preview.BannerPlacementPreviewStatus;
import com.seggellion.britannia_mod.banner.preview.BannerPlacementPreviewWorld;
import com.seggellion.britannia_mod.banner.structure.BannerCellRole;
import com.seggellion.britannia_mod.bannerdyeing.BannerDyeingConstants;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Simple advisory cell/support wireframe. It never mutates the level or sends a per-frame packet. */
@EventBusSubscriber(modid = BritanniaMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class BannerPlacementGhostRenderer {
    private BannerPlacementGhostRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) {
            return;
        }
        ItemStack held = heldBanner(minecraft);
        if (!(held.getItem() instanceof BannerItem item)) {
            return;
        }
        var publication = ClientBannerRenderData.current();
        if (!publication.available()) {
            return;
        }
        var bannerState = item.stateAccess().read(held).orElse(null);
        if (bannerState == null) {
            return;
        }
        var definition = publication.snapshot().banners().get(bannerState.bannerDefinitionId());
        if (definition == null || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || !hit.getDirection().getAxis().isHorizontal()) {
            return;
        }
        BannerOrientation selected = ClientBannerPlacementState.normalized(definition.supportedOrientations());
        Optional<BannerPlacementPreview> planned = BannerPlacementPreviewPlanner.plan(
                definition, bannerState, selected, hit.getBlockPos(), hit.getDirection(),
                clientWorld(minecraft));
        if (planned.isEmpty() || planned.orElseThrow().cells().isEmpty()) {
            return;
        }
        renderPreview(event, minecraft, planned.orElseThrow());
    }

    private static ItemStack heldBanner(Minecraft minecraft) {
        ItemStack main = minecraft.player.getMainHandItem();
        return main.getItem() instanceof BannerItem ? main : minecraft.player.getOffhandItem();
    }

    private static BannerPlacementPreviewWorld clientWorld(Minecraft minecraft) {
        return new BannerPlacementPreviewWorld() {
            @Override public boolean inWorldBounds(BlockPos position) {
                return minecraft.level.isInWorldBounds(position)
                        && minecraft.level.getWorldBorder().isWithinBounds(position);
            }

            @Override public boolean chunkLoaded(BlockPos position) {
                return minecraft.level.hasChunkAt(position);
            }

            @Override public boolean replaceable(BlockPos position) {
                return minecraft.level.getBlockEntity(position) == null
                        && minecraft.level.getBlockState(position).canBeReplaced();
            }

            @Override public boolean validWallSupport(BlockPos supportPosition, Direction outwardFacing) {
                return minecraft.level.getBlockState(supportPosition)
                        .isFaceSturdy(minecraft.level, supportPosition, outwardFacing);
            }

            @Override public boolean serverProtectionKnownAllowed() {
                return false;
            }
        };
    }

    private static void renderPreview(
            RenderLevelStageEvent event, Minecraft minecraft, BannerPlacementPreview preview) {
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType lineType = RenderType.lines();
        VertexConsumer lines = buffers.getBuffer(lineType);
        var camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (var cell : preview.cells()) {
            float[] colour = cell.blocked() ? new float[]{1.0F, 0.15F, 0.15F}
                    : mountColour(preview, cell.role());
            LevelRenderer.renderLineBox(pose, lines, new AABB(cell.worldPosition()).inflate(0.003),
                    colour[0], colour[1], colour[2], 0.95F);
        }
        for (BlockPos support : preview.requiredSupportPositions()) {
            boolean invalid = preview.invalidSupportPositions().contains(support);
            LevelRenderer.renderLineBox(pose, lines, new AABB(support).deflate(0.08),
                    invalid ? 1.0F : 0.35F, invalid ? 0.2F : 0.75F, 0.1F, 0.8F);
        }
        pose.popPose();
        buffers.endBatch(lineType);
        renderLabel(event, minecraft, buffers, preview);
        buffers.endBatch();
    }

    private static float[] mountColour(BannerPlacementPreview preview, BannerCellRole role) {
        float scale = role == BannerCellRole.ANCHOR ? 1.0F : 0.78F;
        if (preview.mountId().equals(BannerDyeingConstants.BRASS_MOUNT_ID)) {
            return new float[]{0.95F * scale, 0.72F * scale, 0.20F * scale};
        }
        if (preview.mountId().equals(BannerDyeingConstants.IRON_MOUNT_ID)) {
            return new float[]{0.72F * scale, 0.78F * scale, 0.84F * scale};
        }
        return new float[]{0.85F * scale, 0.25F * scale, 0.85F * scale};
    }

    private static void renderLabel(
            RenderLevelStageEvent event, Minecraft minecraft,
            MultiBufferSource.BufferSource buffers, BannerPlacementPreview preview) {
        Component orientation = Component.translatable(preview.orientation() == BannerOrientation.WALL_PARALLEL
                ? "message.britannia_mod.banner.orientation.parallel"
                : "message.britannia_mod.banner.orientation.perpendicular");
        Component mount = Component.translatable("mount." + preview.mountId().value().getNamespace()
                + "." + preview.mountId().value().getPath());
        Component status = Component.translatable("message.britannia_mod.banner.preview.status."
                + preview.status().name().toLowerCase(java.util.Locale.ROOT));
        Component label = Component.translatable("message.britannia_mod.banner.preview.label",
                orientation, preview.dimensions().widthBlocks(), preview.dimensions().heightBlocks(), mount, status);

        PoseStack pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(preview.anchorPosition().getX() + 0.5 - camera.x,
                preview.anchorPosition().getY() + 1.25 - camera.y,
                preview.anchorPosition().getZ() + 0.5 - camera.z);
        pose.mulPose(event.getCamera().rotation());
        pose.scale(0.025F, -0.025F, 0.025F);
        Font font = minecraft.font;
        font.drawInBatch(label, -font.width(label) / 2.0F, 0.0F, 0xFFFFFFFF, false,
                pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0x50000000,
                LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
