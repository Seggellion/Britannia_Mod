package com.seggellion.britannia_mod.client.house;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.item.SmallWoodHouseDeedItem;
import com.seggellion.britannia_mod.client.structure.StructureCache;
import com.seggellion.britannia_mod.client.house.HouseRotationData;
import com.seggellion.britannia_mod.util.StructureUtils;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.core.Vec3i;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;


public class GhostStructurePreviewRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();

@SubscribeEvent
public static void onRenderLevel(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;

    Minecraft mc = Minecraft.getInstance();
    Level level = mc.level;
    Player player = mc.player;
    if (level == null || player == null) return;

    // Must hold the deed
    ItemStack held = player.getMainHandItem();
    if (!(held.getItem() instanceof SmallWoodHouseDeedItem)) return;

    // Load the structure
    StructureTemplate template = StructureCache.getSmallWoodHouseTemplate();
    if (template == null || template.getSize().equals(Vec3i.ZERO)) return;

    PoseStack poseStack = event.getPoseStack();
    MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
    BlockRenderDispatcher dispatcher = mc.getBlockRenderer();

    // 1) Find rotation from HouseRotationData
    int deg = HouseRotationData.getRotation(player); // [0, 90, 180, 270]
    Rotation rotation = StructureUtils.getRotation(deg);

    // 2) Find the block the player is targeting
    HitResult hit = mc.hitResult;
    BlockPos doorAnchor;
    if (hit instanceof BlockHitResult bhr) {
        doorAnchor = bhr.getBlockPos();
    } else {
        // fallback
        doorAnchor = player.blockPosition();
    }

    // 3) Offset the anchor to place door exactly at that position
    Vec3i size = template.getSize();
    BlockPos doorOffset = StructureUtils.getDoorOffset(size); // e.g. (size.x/2, 0, 0)
    BlockPos adjustedPos = StructureUtils.getAdjustedPosForDoor(doorAnchor, rotation, doorOffset);

    // 4) Retrieve block info from the template
    List<StructureBlockInfo> blockInfos = getBlocksViaReflection(template);
    if (blockInfos.isEmpty()) return;

    StructurePlaceSettings settings = new StructurePlaceSettings()
            .setRotation(rotation)
            .setIgnoreEntities(true);

    // 5) Render each block
    for (StructureBlockInfo info : blockInfos) {
        BlockState state = info.state();
        if (state.isAir()) continue;

        // rotate the block state for correct orientation
        try {
            state = state.rotate(rotation);
        } catch (Exception ignored) { }

        // compute final position
        BlockPos localOffset = StructureTemplate.calculateRelativePosition(settings, info.pos());
        BlockPos worldPos = adjustedPos.offset(localOffset);

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
        } catch (Exception e) {
            // If a block doesn't like being batched, ignore
        }

        poseStack.popPose();
    }

    buffer.endBatch(RenderType.translucent());
}

private static List<StructureBlockInfo> getBlocksViaReflection(StructureTemplate template) {
    try {
        Field paletteField = StructureTemplate.class.getDeclaredField("palettes");
        paletteField.setAccessible(true);
        List<?> palettes = (List<?>) paletteField.get(template);

        if (!palettes.isEmpty()) {
            Object palette = palettes.get(0);
            Method blocksMethod = palette.getClass().getMethod("blocks");
            return (List<StructureBlockInfo>) blocksMethod.invoke(palette);
        }
    } catch (Exception e) {
        LOGGER.error("❌ Reflection error on structure palettes", e);
    }
    return List.of();
}

}