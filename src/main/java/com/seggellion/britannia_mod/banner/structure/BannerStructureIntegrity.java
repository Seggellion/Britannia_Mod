package com.seggellion.britannia_mod.banner.structure;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import com.seggellion.britannia_mod.registry.BannerBlockRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/** Conservative loaded-chunk integrity checks and non-destructive repair for anchor-owned cells. */
public final class BannerStructureIntegrity {
    public enum Result {
        VALID,
        DEFERRED_UNLOADED_CHUNK,
        REPAIRED_MISSING_PARTS,
        REMOVED_FOR_SUPPORT_LOSS,
        REMOVED_OBSTRUCTED_STRUCTURE,
        REMOVED_ORPHAN,
        INVALID_ANCHOR
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_DIAGNOSTICS = 1024;
    private static final Map<String, Boolean> DIAGNOSTICS = new LinkedHashMap<>();

    private BannerStructureIntegrity() {
    }

    public static Result checkAnchor(ServerLevel level, BlockPos anchorPos) {
        BlockState anchorState = level.getBlockState(anchorPos);
        BlockEntity entity = level.getBlockEntity(anchorPos);
        if (!(anchorState.getBlock() instanceof BannerBlock) || !(entity instanceof BannerBlockEntity anchor)
                || anchor.placedStructure().isEmpty()) {
            return Result.INVALID_ANCHOR;
        }
        BannerPlacedStructure structure = anchor.placedStructure().orElseThrow();
        Direction facing = anchorState.getValue(BannerBlock.FACING);

        for (BannerLocalOffset offset : structure.occupiedOffsets()) {
            BlockPos cellPos = BannerStructureTransform.worldPosition(anchorPos, facing, offset);
            if (!loaded(level, cellPos)) {
                return Result.DEFERRED_UNLOADED_CHUNK;
            }
            if (offset.vertical() == 0) {
                BlockPos supportPos = cellPos.relative(facing.getOpposite());
                if (!loaded(level, supportPos)) {
                    return Result.DEFERRED_UNLOADED_CHUNK;
                }
            }
        }

        for (BannerLocalOffset offset : structure.occupiedOffsets()) {
            if (offset.vertical() != 0) continue;
            BlockPos cellPos = BannerStructureTransform.worldPosition(anchorPos, facing, offset);
            BlockPos supportPos = cellPos.relative(facing.getOpposite());
            if (!level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing)) {
                BannerStructureLifecycle.removeAnchor(level, anchorPos, anchor,
                        BannerRemovalCause.SUPPORT_LOSS, null, null);
                return Result.REMOVED_FOR_SUPPORT_LOSS;
            }
        }

        List<Map.Entry<BlockPos, BlockState>> repairs = new ArrayList<>();
        for (BannerLocalOffset offset : structure.occupiedOffsets()) {
            BlockPos cellPos = BannerStructureTransform.worldPosition(anchorPos, facing, offset);
            BlockState actual = level.getBlockState(cellPos);
            if (BannerStructureLifecycle.isExpectedCell(actual, facing, offset)) {
                continue;
            }
            if (offset.isAnchor()) {
                return Result.INVALID_ANCHOR;
            }
            BlockState expected = BannerBlockRegistry.BANNER_PART.get().stateFor(facing, offset);
            if (actual.canBeReplaced() || actual.getBlock() instanceof BannerPartBlock) {
                repairs.add(Map.entry(cellPos, expected));
                continue;
            }
            diagnostic("obstruction", anchorPos,
                    "Banner at {} could not repair occupied cell {}; obstruction was preserved", anchorPos, cellPos);
            BannerStructureLifecycle.removeAnchor(level, anchorPos, anchor,
                    BannerRemovalCause.ADMINISTRATIVE, null, null);
            return Result.REMOVED_OBSTRUCTED_STRUCTURE;
        }

        if (repairs.isEmpty()) {
            return Result.VALID;
        }
        return BannerStructureLifecycle.duringPlacement(level, anchorPos, () -> {
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
            for (Map.Entry<BlockPos, BlockState> repair : repairs) {
                if (!level.getBlockState(repair.getKey()).canBeReplaced()
                        && !(level.getBlockState(repair.getKey()).getBlock() instanceof BannerPartBlock)) {
                    diagnostic("repair_race", anchorPos,
                            "Banner repair at {} was aborted because {} became obstructed",
                            anchorPos, repair.getKey());
                    return Result.REMOVED_OBSTRUCTED_STRUCTURE;
                }
                level.setBlock(repair.getKey(), repair.getValue(), flags);
            }
            for (Map.Entry<BlockPos, BlockState> repair : repairs) {
                if (!level.getBlockState(repair.getKey()).equals(repair.getValue())) {
                    BannerStructureLifecycle.removeAnchor(level, anchorPos, anchor,
                            BannerRemovalCause.ADMINISTRATIVE, null, null);
                    return Result.REMOVED_OBSTRUCTED_STRUCTURE;
                }
            }
            anchor.synchronize();
            return Result.REPAIRED_MISSING_PARTS;
        });
    }

    public static Result checkPart(ServerLevel level, BlockPos partPos, BlockState partState) {
        var resolution = BannerStructureLifecycle.resolve(level, partPos, partState);
        if (resolution.status() == BannerStructureLifecycle.ResolutionStatus.ANCHOR_CHUNK_UNLOADED) {
            return Result.DEFERRED_UNLOADED_CHUNK;
        }
        if (!resolution.valid()) {
            BannerStructureLifecycle.duringPlacement(level, resolution.anchorPos(), () -> {
                int flags = Block.UPDATE_ALL_IMMEDIATE | Block.UPDATE_SUPPRESS_DROPS;
                level.setBlock(partPos, Blocks.AIR.defaultBlockState(), flags);
                return true;
            });
            diagnostic("orphan", partPos, "Orphan banner part removed at {}", partPos);
            return Result.REMOVED_ORPHAN;
        }
        return checkAnchor(level, resolution.anchorPos());
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static synchronized void diagnostic(
            String kind, BlockPos pos, String message, Object... arguments) {
        String key = kind + ":" + pos.asLong();
        if (DIAGNOSTICS.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        while (DIAGNOSTICS.size() > MAX_DIAGNOSTICS) {
            DIAGNOSTICS.remove(DIAGNOSTICS.keySet().iterator().next());
        }
        LOGGER.warn(message, arguments);
    }
}
