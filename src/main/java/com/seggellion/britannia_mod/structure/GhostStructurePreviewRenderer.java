package com.seggellion.britannia_mod.client.house;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.item.SmallHouseDeedItem;
import com.seggellion.britannia_mod.client.structure.StructureCache;
import com.seggellion.britannia_mod.client.house.HouseRotationData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.core.Vec3i;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.util.List;

public class GhostStructurePreviewRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation STRUCTURE = ResourceLocation.fromNamespaceAndPath("britannia_mod", "small_house");

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;


        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Player player = mc.player;
        if (level == null || player == null) return;

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof SmallHouseDeedItem)) return;
        LOGGER.info("holding SmallHouseDeedItem, held: {}", held.getItem().getDescriptionId());
        StructureTemplate template = StructureCache.getSmallHouseTemplate();
        if (template == null || template.getSize().equals(Vec3i.ZERO)) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();

        int rotationDeg = HouseRotationData.getRotation(player);
        Rotation rotation = Rotation.values()[(rotationDeg / 90) % 4];

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setIgnoreEntities(true)
                .setKnownShape(true);

        BlockPos origin = player.blockPosition();
        Vec3i vecSize = template.getSize();
        BlockPos size = new BlockPos(vecSize.getX(), vecSize.getY(), vecSize.getZ());
        BlockPos start = origin.offset(-size.getX() / 2, 0, -size.getZ() / 2);

        // ✅ Correct way to get blocks
        List<StructureBlockInfo> blocks = template.filterBlocks(BlockPos.ZERO, settings, null);

        for (StructureBlockInfo blockInfo : blocks) {
            BlockState state = blockInfo.state();
            if (state.isAir()) continue;

            BlockPos localOffset = StructureTemplate.calculateRelativePosition(settings, blockInfo.pos());
            BlockPos worldPos = start.offset(localOffset);

            poseStack.pushPose();
            poseStack.translate(
                    worldPos.getX() - mc.gameRenderer.getMainCamera().getPosition().x,
                    worldPos.getY() - mc.gameRenderer.getMainCamera().getPosition().y,
                    worldPos.getZ() - mc.gameRenderer.getMainCamera().getPosition().z
            );

            try {
                dispatcher.renderBatched(
                        state,
                        worldPos,
                        level,
                        poseStack,
                        buffer.getBuffer(RenderType.translucent()),
                        false,
                        RandomSource.create()
                );
            } catch (Exception ignored) {}

            poseStack.popPose();
        }

        buffer.endBatch(RenderType.translucent());
    }
}