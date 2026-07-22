package com.seggellion.britannia_mod.banner.structure;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import com.seggellion.britannia_mod.banner.placement.BannerBlockItemTransfer;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/** Server-authoritative ownership boundary for every complete-banner removal. */
public final class BannerStructureLifecycle {
    public enum ResolutionStatus {
        VALID,
        ANCHOR_CHUNK_UNLOADED,
        MISSING_ANCHOR,
        INVALID_MEMBERSHIP
    }

    public record Resolution(
            ResolutionStatus status,
            BlockPos anchorPos,
            BannerBlockEntity anchor,
            BannerLocalOffset sourceOffset) {
        public boolean valid() {
            return status == ResolutionStatus.VALID && anchor != null;
        }
    }

    public record RemovalResult(boolean claimed, int removedCells, int drops, BannerRemovalCause cause) {
        public static RemovalResult unclaimed(BannerRemovalCause cause) {
            return new RemovalResult(false, 0, 0, cause);
        }
    }

    private record GuardKey(ServerLevel level, BlockPos anchor) {
        private GuardKey {
            anchor = anchor.immutable();
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<GuardKey> IN_PROGRESS = new HashSet<>();
    private static final Set<GuardKey> PLACING = new HashSet<>();

    private BannerStructureLifecycle() {
    }

    public static synchronized boolean isGuarded(ServerLevel level, BlockPos anchor) {
        GuardKey key = new GuardKey(level, anchor);
        return IN_PROGRESS.contains(key) || PLACING.contains(key);
    }

    public static <T> T duringPlacement(ServerLevel level, BlockPos anchor, Supplier<T> action) {
        GuardKey key = new GuardKey(level, anchor);
        synchronized (BannerStructureLifecycle.class) {
            PLACING.add(key);
        }
        try {
            return action.get();
        } finally {
            synchronized (BannerStructureLifecycle.class) {
                PLACING.remove(key);
            }
        }
    }

    public static Resolution resolve(ServerLevel level, BlockPos sourcePos, BlockState sourceState) {
        if (sourceState.getBlock() instanceof BannerBlock) {
            BlockEntity entity = level.getBlockEntity(sourcePos);
            if (entity instanceof BannerBlockEntity banner && banner.placedStructure().isPresent()) {
                return new Resolution(ResolutionStatus.VALID, sourcePos.immutable(), banner,
                        BannerLocalOffset.ANCHOR);
            }
            return new Resolution(ResolutionStatus.MISSING_ANCHOR, sourcePos.immutable(), null,
                    BannerLocalOffset.ANCHOR);
        }
        if (!(sourceState.getBlock() instanceof BannerPartBlock)) {
            return new Resolution(ResolutionStatus.INVALID_MEMBERSHIP, sourcePos.immutable(), null,
                    BannerLocalOffset.ANCHOR);
        }
        Direction facing = sourceState.getValue(BannerPartBlock.FACING);
        BannerLocalOffset offset = BannerPartBlock.localOffset(sourceState);
        BlockPos anchorPos = BannerStructureTransform.anchorPosition(sourcePos, facing, offset);
        if (!level.getChunkSource().hasChunk(anchorPos.getX() >> 4, anchorPos.getZ() >> 4)) {
            return new Resolution(ResolutionStatus.ANCHOR_CHUNK_UNLOADED, anchorPos, null, offset);
        }
        BlockState anchorState = level.getBlockState(anchorPos);
        BlockEntity entity = level.getBlockEntity(anchorPos);
        if (!(anchorState.getBlock() instanceof BannerBlock) || !(entity instanceof BannerBlockEntity banner)) {
            return new Resolution(ResolutionStatus.MISSING_ANCHOR, anchorPos, null, offset);
        }
        Optional<BannerPlacedStructure> structure = banner.placedStructure();
        boolean matches = anchorState.getValue(BannerBlock.FACING) == facing
                && structure.filter(value -> value.contains(offset)).isPresent()
                && BannerStructureTransform.worldPosition(anchorPos, facing, offset).equals(sourcePos);
        return matches
                ? new Resolution(ResolutionStatus.VALID, anchorPos, banner, offset)
                : new Resolution(ResolutionStatus.INVALID_MEMBERSHIP, anchorPos, banner, offset);
    }

    public static RemovalResult removeFrom(
            ServerLevel level, BlockPos sourcePos, BlockState sourceState,
            BannerRemovalCause cause, @Nullable Player player) {
        Resolution resolution = resolve(level, sourcePos, sourceState);
        if (!resolution.valid()) {
            return RemovalResult.unclaimed(cause);
        }
        return removeAnchor(level, resolution.anchorPos(), resolution.anchor(), cause, player, null);
    }

    public static RemovalResult removeExternalAnchor(
            ServerLevel level, BlockPos anchorPos, BannerBlockEntity anchor) {
        return removeAnchor(level, anchorPos, anchor, BannerRemovalCause.EXTERNAL_REPLACEMENT, null, anchorPos);
    }

    public static RemovalResult removeExternalPart(
            ServerLevel level, BlockPos partPos, BlockState oldPartState) {
        Resolution resolution = resolve(level, partPos, oldPartState);
        if (!resolution.valid()) {
            return RemovalResult.unclaimed(BannerRemovalCause.EXTERNAL_REPLACEMENT);
        }
        return removeAnchor(level, resolution.anchorPos(), resolution.anchor(),
                BannerRemovalCause.EXTERNAL_REPLACEMENT, null, partPos);
    }

    public static RemovalResult removeAnchor(
            ServerLevel level, BlockPos anchorPos, BannerBlockEntity anchor,
            BannerRemovalCause cause, @Nullable Player player, @Nullable BlockPos preservePosition) {
        GuardKey key = new GuardKey(level, anchorPos);
        synchronized (BannerStructureLifecycle.class) {
            if (IN_PROGRESS.contains(key) || PLACING.contains(key)) {
                return RemovalResult.unclaimed(cause);
            }
            IN_PROGRESS.add(key);
        }
        try {
            BannerPlacedStructure structure = anchor.placedStructure().orElse(BannerPlacedStructure.legacyOneCell());
            Direction facing = anchor.getBlockState().hasProperty(BannerBlock.FACING)
                    ? anchor.getBlockState().getValue(BannerBlock.FACING) : Direction.NORTH;
            ItemStack drop = BannerBlockItemTransfer.fromBlockEntity(anchor);
            int removed = 0;
            for (int index = structure.occupiedOffsets().size() - 1; index >= 0; index--) {
                BannerLocalOffset offset = structure.occupiedOffsets().get(index);
                BlockPos cellPos = BannerStructureTransform.worldPosition(anchorPos, facing, offset);
                if (cellPos.equals(preservePosition)) {
                    continue;
                }
                BlockState actual = level.getBlockState(cellPos);
                if (!isExpectedCell(actual, facing, offset)) {
                    continue;
                }
                int flags = Block.UPDATE_ALL_IMMEDIATE | Block.UPDATE_SUPPRESS_DROPS;
                if (level.setBlock(cellPos, Blocks.AIR.defaultBlockState(), flags)) {
                    removed++;
                }
            }
            int drops = 0;
            if (cause.dropsConfiguredItem() && !drop.isEmpty()) {
                Block.popResource(level, anchorPos, drop);
                drops = 1;
            }
            return new RemovalResult(true, removed, drops, cause);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to remove banner structure at {} for cause {}", anchorPos, cause, exception);
            return RemovalResult.unclaimed(cause);
        } finally {
            synchronized (BannerStructureLifecycle.class) {
                IN_PROGRESS.remove(key);
            }
        }
    }

    public static boolean isExpectedCell(BlockState state, Direction facing, BannerLocalOffset offset) {
        if (offset.isAnchor()) {
            return state.getBlock() instanceof BannerBlock && state.getValue(BannerBlock.FACING) == facing;
        }
        return state.getBlock() instanceof BannerPartBlock
                && state.getValue(BannerPartBlock.FACING) == facing
                && BannerPartBlock.localOffset(state).equals(offset);
    }
}
