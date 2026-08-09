package com.seggellion.britannia_mod.structure.lifecycle;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.lifecycle.ShrineLifecycleService.AnchorSnapshot;
import com.seggellion.britannia_mod.structure.lifecycle.ShrineLifecycleService.WorldAccess;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.placement.ShrinePlacementPlanner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/** Conservative, persisted-footprint integrity checks for already-loaded chunks only. */
public final class ShrineIntegrityService {
    public enum Result {
        VALID,
        DEFERRED_UNLOADED_CHUNK,
        REPAIRED_MISSING_PARTS,
        REMOVED_OBSTRUCTED_STRUCTURE,
        REMOVED_ORPHAN,
        DEFERRED_ACTIVE_MUTATION,
        INVALID_ANCHOR
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_DIAGNOSTICS = 1024;
    private static final Map<String, Boolean> DIAGNOSTICS = new LinkedHashMap<>();

    private ShrineIntegrityService() {
    }

    public static Result checkAnchor(ServerLevel level, BlockPos anchorPos) {
        return checkAnchor(ShrineLifecycleService.serverWorld(level), anchorPos);
    }

    public static Result checkAnchor(WorldAccess world, BlockPos anchorPos) {
        if (ShrineLifecycleService.isGuarded(world.levelIdentity(), anchorPos)) {
            return Result.DEFERRED_ACTIVE_MUTATION;
        }
        AnchorSnapshot anchor = world.anchor(anchorPos).orElse(null);
        if (anchor == null) {
            return Result.INVALID_ANCHOR;
        }
        var state = anchor.state();
        for (LocalOffset offset : state.footprint()) {
            BlockPos cellPos = ShrinePlacementPlanner.worldPosition(anchorPos, state.facing(), offset);
            if (!world.chunkLoaded(cellPos)) {
                return Result.DEFERRED_UNLOADED_CHUNK;
            }
        }

        List<Map.Entry<BlockPos, BlockState>> repairs = new ArrayList<>();
        for (LocalOffset offset : state.footprint()) {
            BlockPos cellPos = ShrinePlacementPlanner.worldPosition(anchorPos, state.facing(), offset);
            BlockState actual = world.blockState(cellPos);
            if (ShrineLifecycleService.isExpectedCell(world, actual, state.facing(), offset)) {
                continue;
            }
            if (offset.equals(LocalOffset.ANCHOR)) {
                return Result.INVALID_ANCHOR;
            }
            if (world.replaceable(cellPos)) {
                repairs.add(Map.entry(cellPos, world.expectedPart(state.facing(), offset)));
                continue;
            }
            diagnostic("obstruction", anchorPos,
                    "Shrine at {} could not repair {}; obstruction was preserved", anchorPos, cellPos);
            ShrineLifecycleService.removeAnchor(
                    world, anchor, ShrineRemovalCause.OBSTRUCTED_REPAIR, null);
            return Result.REMOVED_OBSTRUCTED_STRUCTURE;
        }

        if (repairs.isEmpty()) {
            return Result.VALID;
        }
        Result result = ShrineLifecycleService.duringPlacement(world.levelIdentity(), anchorPos, () -> {
            for (Map.Entry<BlockPos, BlockState> repair : repairs) {
                AnchorSnapshot current = world.anchor(anchorPos).orElse(null);
                if (current == null || !current.state().equals(state) || !world.replaceable(repair.getKey())) {
                    diagnostic("repair_race", anchorPos,
                            "Shrine repair at {} stopped because {} was no longer safely replaceable",
                            anchorPos, repair.getKey());
                    return Result.REMOVED_OBSTRUCTED_STRUCTURE;
                }
                if (!world.placePart(repair.getKey(), repair.getValue())) {
                    return Result.REMOVED_OBSTRUCTED_STRUCTURE;
                }
            }
            world.synchronizeAnchor(anchorPos);
            return Result.REPAIRED_MISSING_PARTS;
        });
        if (result == Result.REMOVED_OBSTRUCTED_STRUCTURE) {
            ShrineLifecycleService.removeAnchor(
                    world, anchor, ShrineRemovalCause.OBSTRUCTED_REPAIR, null);
        }
        return result;
    }

    public static Result checkPart(ServerLevel level, BlockPos partPos, BlockState partState) {
        return checkPart(ShrineLifecycleService.serverWorld(level), partPos, partState);
    }

    public static Result checkPart(WorldAccess world, BlockPos partPos, BlockState partState) {
        BlockPos candidateAnchor = world.isPart(partState)
                ? LargeStructurePartBlock.anchorPosition(partPos, partState) : partPos;
        if (ShrineLifecycleService.isGuarded(world.levelIdentity(), candidateAnchor)) {
            return Result.DEFERRED_ACTIVE_MUTATION;
        }
        var resolution = ShrineLifecycleService.resolve(world, partPos, partState);
        if (resolution.status() == ShrineLifecycleService.ResolutionStatus.ANCHOR_CHUNK_UNLOADED) {
            return Result.DEFERRED_UNLOADED_CHUNK;
        }
        if (!resolution.valid()) {
            ShrineLifecycleService.duringPlacement(world.levelIdentity(), resolution.anchorPosition(), () -> {
                if (world.isPart(world.blockState(partPos))) {
                    world.removeCell(partPos);
                }
                return true;
            });
            diagnostic("orphan", partPos, "Definitive orphan shrine part removed at {}", partPos);
            return Result.REMOVED_ORPHAN;
        }
        return checkAnchor(world, resolution.anchorPosition());
    }

    public static synchronized int diagnosticCount() {
        return DIAGNOSTICS.size();
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
