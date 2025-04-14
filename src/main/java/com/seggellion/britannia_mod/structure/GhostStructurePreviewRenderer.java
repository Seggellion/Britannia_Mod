package com.seggellion.britannia_mod.client.house;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.item.SmallWoodHouseDeedItem;
import com.seggellion.britannia_mod.client.structure.StructureCache;
import com.seggellion.britannia_mod.client.house.HouseRotationData;
import com.seggellion.britannia_mod.client.house.GhostPreviewState;
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

    ItemStack held = player.getMainHandItem();
    if (!(held.getItem() instanceof SmallWoodHouseDeedItem)) return;

    StructureTemplate template = StructureCache.getSmallWoodHouseTemplate();
    if (template == null || template.getSize().equals(Vec3i.ZERO)) return;

    PoseStack poseStack = event.getPoseStack();
    MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
    BlockRenderDispatcher dispatcher = mc.getBlockRenderer();

    int deg = HouseRotationData.getRotation(player); // 0, 90, 180, 270
    Rotation rotation = StructureUtils.getRotation(deg);
    StructurePlaceSettings settings = new StructurePlaceSettings()
        .setRotation(rotation)
        .setIgnoreEntities(true);

    Vec3i size = template.getSize();
    BlockPos doorOffset = StructureUtils.getDoorOffset(size); // e.g. (4, 0, 0)

    // 🌀 Place structure CENTER rotated around player, keeping DOOR nearest
    int radius = 3;
    double angle = Math.toRadians(deg + 180);
    double offsetX = Math.cos(angle) * radius;
    double offsetZ = Math.sin(angle) * radius;
BlockPos doorTarget = new BlockPos(
    (int) Math.floor(player.getX() + Math.cos(angle) * radius),
    player.blockPosition().getY(),
    (int) Math.floor(player.getZ() + Math.sin(angle) * radius)
);

    BlockPos structureStart = StructureUtils.getAdjustedPosForDoor(doorTarget, rotation, doorOffset);

    List<StructureBlockInfo> blockInfos = getBlocksViaReflection(template);
    if (blockInfos.isEmpty()) return;

    for (StructureBlockInfo info : blockInfos) {
        BlockState state = info.state();
        if (state.isAir()) continue;

        try {
            state = state.rotate(rotation);
        } catch (Exception ignored) {}

        BlockPos localOffset = StructureTemplate.calculateRelativePosition(settings, info.pos());
        BlockPos worldPos = structureStart.offset(localOffset);

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