package com.seggellion.britannia_mod.client.house;

import com.mojang.blaze3d.vertex.PoseStack;
// import com.seggellion.britannia_mod.item.SmallWoodHouseDeedItem;
import com.seggellion.britannia_mod.client.structure.StructureCache;
import com.seggellion.britannia_mod.item.AbstractHouseDeedItem;
import com.seggellion.britannia_mod.client.house.HouseRotationData;
import com.seggellion.britannia_mod.util.StructureUtils;
import com.seggellion.britannia_mod.structure.HouseStyle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
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
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

public class GhostStructurePreviewRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG = Boolean.getBoolean("britannia.ghostPreviewDebug");
    private static final Map<StructureTemplate, List<StructureBlockInfo>> BLOCK_INFO_CACHE = new IdentityHashMap<>();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Player player = mc.player;
        int renderTick = event.getRenderTick();
        if (level == null || player == null) {
            debug(renderTick, "missing level/player level={} player={}", level != null, player != null);
            return;
        }

        ItemStack held = player.getMainHandItem();
        ResourceLocation heldItemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        debug(renderTick, "heldItem={}", heldItemId);

        if (!(held.getItem() instanceof AbstractHouseDeedItem deed)) {
            debug(renderTick, "deedDetected=false");
            return;
        }

        HouseStyle style = deed.getHouseStyle();
        String structureFile = style.getStructureFile();
        String cacheKey = StructureCache.normalizeKey(structureFile);
        StructureTemplate template = StructureCache.get(cacheKey);

        debug(renderTick, "deedDetected=true structureFile={} cacheKey={} templateFound={}",
                structureFile, cacheKey, template != null);

        if (template == null) {
            return;
        }

        Vec3i size = template.getSize();
        debug(renderTick, "templateSize={}", size);

        if (size.equals(Vec3i.ZERO)) {
            debug(renderTick, "return=zeroTemplateSize cacheKey={}", cacheKey);
            return;
        }

        HitResult hitResult = mc.hitResult;
        debug(renderTick, "hitResult={}", hitResult != null ? hitResult.getType() : null);
        if (!(hitResult instanceof BlockHitResult blockHit)) {
            return;
        }

        BlockPos targetedBlock = blockHit.getBlockPos();
        BlockState targetedState = level.getBlockState(targetedBlock);
        Block targeted = targetedState.getBlock();
        boolean allowedSurface = isAllowedPlacementSurface(targeted);

        debug(renderTick, "targetedBlock={} targetedState={} allowedSurface={}",
                targetedBlock, BuiltInRegistries.BLOCK.getKey(targeted), allowedSurface);

        if (!allowedSurface) {
            return;
        }


        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();

        int deg = HouseRotationData.getRotation(player); // 0, 90, 180, 270
        Rotation baseRotation = StructureUtils.getRotation(deg);

        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setRotation(baseRotation)
            .setIgnoreEntities(true);

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
        List<StructureBlockInfo> blockInfos = getBlockInfos(template, level, renderTick);
        debug(renderTick, "blockInfoCount={}", blockInfos.size());
        if (blockInfos.isEmpty()) {
            return;
        }

        debug(renderTick, "rendering at structureStart={} rotation={}", structureStart, deg);
        RandomSource random = RandomSource.create(42L);

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
                    random
                );
            } catch (Exception e) {
                debug(renderTick, "renderException worldPos={} state={} error={}", worldPos, state, e.toString());
            }

            poseStack.popPose();
        }

        buffer.endBatch(RenderType.translucent());
    }

    private static boolean isAllowedPlacementSurface(Block block) {
        return block == Blocks.GRASS_BLOCK || block == Blocks.SAND;
    }

    private static List<StructureBlockInfo> getBlockInfos(StructureTemplate template, Level level, int renderTick) {
        List<StructureBlockInfo> cached = BLOCK_INFO_CACHE.get(template);
        if (cached != null) {
            return cached;
        }

        List<StructureBlockInfo> blockInfos = readBlockInfosFromSavedNbt(template, level, renderTick);
        BLOCK_INFO_CACHE.put(template, blockInfos);
        return blockInfos;
    }

    private static List<StructureBlockInfo> readBlockInfosFromSavedNbt(StructureTemplate template, Level level, int renderTick) {
        try {
            CompoundTag saved = template.save(new CompoundTag());
            ListTag paletteTag = getFirstPaletteTag(saved);
            ListTag blocksTag = saved.getList("blocks", CompoundTag.TAG_COMPOUND);

            if (paletteTag.isEmpty() || blocksTag.isEmpty()) {
                debug(renderTick, "emptySavedTemplate paletteCount={} blockCount={}", paletteTag.size(), blocksTag.size());
                return List.of();
            }

            HolderGetter<Block> blockRegistry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
            List<BlockState> palette = new ArrayList<>(paletteTag.size());
            for (int i = 0; i < paletteTag.size(); i++) {
                palette.add(NbtUtils.readBlockState(blockRegistry, paletteTag.getCompound(i)));
            }

            List<StructureBlockInfo> blockInfos = new ArrayList<>(blocksTag.size());
            for (int i = 0; i < blocksTag.size(); i++) {
                CompoundTag blockTag = blocksTag.getCompound(i);
                ListTag posTag = blockTag.getList("pos", CompoundTag.TAG_INT);
                int stateIndex = blockTag.getInt("state");

                if (stateIndex < 0 || stateIndex >= palette.size() || posTag.size() < 3) {
                    debug(renderTick, "invalidSavedBlock index={} stateIndex={} paletteSize={} posSize={}",
                            i, stateIndex, palette.size(), posTag.size());
                    continue;
                }

                BlockPos pos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
                CompoundTag nbt = blockTag.contains("nbt") ? blockTag.getCompound("nbt").copy() : null;
                blockInfos.add(new StructureBlockInfo(pos, palette.get(stateIndex), nbt));
            }

            return blockInfos;
        } catch (Exception e) {
            LOGGER.error("[GHOST_PREVIEW] Failed to read structure blocks from saved template NBT", e);
        }

        return List.of();
    }

    private static ListTag getFirstPaletteTag(CompoundTag saved) {
        if (saved.contains("palette", CompoundTag.TAG_LIST)) {
            return saved.getList("palette", CompoundTag.TAG_COMPOUND);
        }

        if (saved.contains("palettes", CompoundTag.TAG_LIST)) {
            ListTag palettes = saved.getList("palettes", CompoundTag.TAG_LIST);
            if (!palettes.isEmpty()) {
                return palettes.getList(0);
            }
        }

        return new ListTag();
    }

    private static void debug(int renderTick, String message, Object... args) {
        if (DEBUG && renderTick % 40 == 0) {
            LOGGER.info("[GHOST_PREVIEW] " + message, args);
        }
    }
}
