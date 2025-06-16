package com.seggellion.britannia_mod.client.house;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.client.structure.StructureCache;
import com.seggellion.britannia_mod.item.AbstractHouseDeedItem;
import com.seggellion.britannia_mod.structure.HouseStyle;
import com.seggellion.britannia_mod.util.StructureUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

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

        StructureTemplate template = null;

        if (held.getItem() instanceof AbstractHouseDeedItem deed) {
            HouseStyle style = deed.getHouseStyle();
            String nbt = style.getStructureFile().replace(".nbt", "");
            template = StructureCache.get(nbt);
        }

        if (template == null || template.getSize().equals(Vec3i.ZERO)) return;


HitResult hitResult = mc.hitResult;
if (!(hitResult instanceof BlockHitResult blockHit)) return;

BlockPos targetedBlock = blockHit.getBlockPos();
BlockState targetedState = mc.level.getBlockState(targetedBlock);
Block targeted = targetedState.getBlock();

if (targeted != Blocks.GRASS_BLOCK && targeted != Blocks.SAND) return;



        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();

        int deg = HouseRotationData.getRotation(player); // 0, 90, 180, 270
        Rotation baseRotation = StructureUtils.getRotation(deg);

        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setRotation(baseRotation)
            .setIgnoreEntities(true);

        Vec3i size = template.getSize();
        BlockPos doorOffset = StructureUtils.getDoorOffset(size); // e.g., (4, 0, 0)

        // Rotate the door offset
        BlockPos rotatedDoorOffset = StructureTemplate.calculateRelativePosition(
            new StructurePlaceSettings().setRotation(baseRotation),
            doorOffset
        );

        // Position door 2 blocks in front of the player
        BlockPos doorTarget = targetedBlock.relative(player.getDirection(), 1);
        // Calculate where structure origin should be placed
       BlockPos structureStart = doorTarget.above().subtract(rotatedDoorOffset); 

        // Get block data from template
        List<StructureBlockInfo> blockInfos = getBlocksViaReflection(template);
        if (blockInfos.isEmpty()) return;

        for (StructureBlockInfo info : blockInfos) {
            BlockState state = info.state();
            if (state.isAir()) continue;

            try {
                state = state.rotate(baseRotation);
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
