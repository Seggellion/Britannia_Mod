package com.seggellion.britannia_mod.structure.lifecycle;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.registry.LargeStructureRegistry;
import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.item.ConfiguredStructureItem;
import com.seggellion.britannia_mod.structure.item.ShrineItemTransfer;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlock;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlockEntity;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import com.seggellion.britannia_mod.structure.placement.ShrinePlacementPlanner;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelReader;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/** Central server-authoritative ownership boundary for teardown, drops, membership, and pick. */
public final class ShrineLifecycleService {
    public enum ResolutionStatus { VALID, ANCHOR_CHUNK_UNLOADED, MISSING_ANCHOR, INVALID_MEMBERSHIP }

    public record AnchorSnapshot(BlockPos position, BlockState blockState, PlacedStructureState state) {
        public AnchorSnapshot {
            position = Objects.requireNonNull(position, "position").immutable();
            Objects.requireNonNull(blockState, "blockState");
            Objects.requireNonNull(state, "state");
        }
    }

    public record Resolution(
            ResolutionStatus status, BlockPos anchorPosition,
            Optional<AnchorSnapshot> anchor, LocalOffset sourceOffset) {
        public boolean valid() {
            return status == ResolutionStatus.VALID && anchor.isPresent();
        }
    }

    public record RemovalResult(boolean claimed, int removedCells, int drops, ShrineRemovalCause cause) {
        public static RemovalResult unclaimed(ShrineRemovalCause cause) {
            return new RemovalResult(false, 0, 0, cause);
        }
    }

    /** Testable mutation boundary; the live implementation below never force-loads a chunk. */
    public interface WorldAccess {
        Object levelIdentity();
        boolean chunkLoaded(BlockPos pos);
        BlockState blockState(BlockPos pos);
        Optional<AnchorSnapshot> anchor(BlockPos pos);
        boolean isAnchor(BlockState state);
        boolean isPart(BlockState state);
        boolean replaceable(BlockPos pos);
        boolean removeCell(BlockPos pos);
        boolean placePart(BlockPos pos, BlockState state);
        BlockState expectedPart(Direction facing, LocalOffset offset);
        ItemStack configuredItem(PlacedStructureState state);
        void drop(BlockPos pos, ItemStack stack);
        void synchronizeAnchor(BlockPos pos);
    }

    private static final class GuardKey {
        private final Object level;
        private final BlockPos anchor;

        private GuardKey(Object level, BlockPos anchor) {
            this.level = Objects.requireNonNull(level, "level");
            this.anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof GuardKey key && level == key.level && anchor.equals(key.anchor);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(level) + anchor.hashCode();
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<GuardKey> IN_PROGRESS = new HashSet<>();
    private static final Set<GuardKey> PLACING = new HashSet<>();

    private ShrineLifecycleService() {
    }

    public static Resolution resolve(ServerLevel level, BlockPos sourcePos, BlockState sourceState) {
        return resolve(serverWorld(level), sourcePos, sourceState);
    }

    public static Resolution resolve(WorldAccess world, BlockPos sourcePos, BlockState sourceState) {
        if (world.isAnchor(sourceState)) {
            Optional<AnchorSnapshot> anchor = world.anchor(sourcePos);
            if (anchor.filter(ShrineLifecycleService::validAnchor).isPresent()) {
                return new Resolution(ResolutionStatus.VALID, sourcePos.immutable(), anchor, LocalOffset.ANCHOR);
            }
            return new Resolution(ResolutionStatus.MISSING_ANCHOR, sourcePos.immutable(), Optional.empty(),
                    LocalOffset.ANCHOR);
        }
        if (!world.isPart(sourceState)) {
            return new Resolution(ResolutionStatus.INVALID_MEMBERSHIP, sourcePos.immutable(), Optional.empty(),
                    LocalOffset.ANCHOR);
        }
        Direction facing;
        LocalOffset offset;
        BlockPos anchorPos;
        try {
            facing = sourceState.getValue(LargeStructurePartBlock.FACING);
            offset = LargeStructurePartBlock.localOffset(sourceState);
            anchorPos = LargeStructurePartBlock.anchorPosition(sourcePos, sourceState);
        } catch (RuntimeException exception) {
            return new Resolution(ResolutionStatus.INVALID_MEMBERSHIP, sourcePos.immutable(), Optional.empty(),
                    LocalOffset.ANCHOR);
        }
        if (offset.equals(LocalOffset.ANCHOR)) {
            return new Resolution(ResolutionStatus.INVALID_MEMBERSHIP, anchorPos, Optional.empty(), offset);
        }
        if (!world.chunkLoaded(anchorPos)) {
            return new Resolution(ResolutionStatus.ANCHOR_CHUNK_UNLOADED, anchorPos, Optional.empty(), offset);
        }
        Optional<AnchorSnapshot> anchor = world.anchor(anchorPos);
        if (anchor.isEmpty()) {
            return new Resolution(ResolutionStatus.MISSING_ANCHOR, anchorPos, Optional.empty(), offset);
        }
        AnchorSnapshot snapshot = anchor.orElseThrow();
        PlacedStructureState state = snapshot.state();
        boolean matches = validAnchor(snapshot)
                && snapshot.blockState().getValue(LargeStructureAnchorBlock.FACING) == facing
                && state.contains(offset)
                && ShrinePlacementPlanner.worldPosition(anchorPos, facing, offset).equals(sourcePos)
                && isExpectedCell(world, sourceState, facing, offset);
        return matches
                ? new Resolution(ResolutionStatus.VALID, anchorPos, anchor, offset)
                : new Resolution(ResolutionStatus.INVALID_MEMBERSHIP, anchorPos, anchor, offset);
    }

    public static RemovalResult removeFrom(
            ServerLevel level, BlockPos sourcePos, BlockState sourceState, ShrineRemovalCause cause) {
        return removeFrom(serverWorld(level), sourcePos, sourceState, cause);
    }

    public static RemovalResult removeFrom(
            WorldAccess world, BlockPos sourcePos, BlockState sourceState, ShrineRemovalCause cause) {
        Resolution resolution = resolve(world, sourcePos, sourceState);
        if (!resolution.valid()) {
            if (resolution.status() == ResolutionStatus.ANCHOR_CHUNK_UNLOADED) {
                return RemovalResult.unclaimed(cause);
            }
            return removeInvalidSource(world, sourcePos, sourceState, cause);
        }
        return removeAnchor(world, resolution.anchor().orElseThrow(), cause, null);
    }

    public static RemovalResult removeExternalAnchor(
            ServerLevel level, BlockPos anchorPos, LargeStructureAnchorBlockEntity anchor) {
        if (anchor.placedState().isEmpty()) {
            return RemovalResult.unclaimed(ShrineRemovalCause.EXTERNAL_REPLACEMENT);
        }
        AnchorSnapshot snapshot = new AnchorSnapshot(anchorPos, anchor.getBlockState(),
                anchor.placedState().orElseThrow());
        return removeAnchor(serverWorld(level), snapshot, ShrineRemovalCause.EXTERNAL_REPLACEMENT, anchorPos);
    }

    public static RemovalResult removeExternalPart(
            ServerLevel level, BlockPos partPos, BlockState oldPartState) {
        return removeExternalPart(serverWorld(level), partPos, oldPartState);
    }

    public static RemovalResult removeExternalPart(
            WorldAccess world, BlockPos partPos, BlockState oldPartState) {
        Resolution resolution = resolve(world, partPos, oldPartState);
        if (!resolution.valid()) {
            return RemovalResult.unclaimed(ShrineRemovalCause.EXTERNAL_REPLACEMENT);
        }
        return removeAnchor(world, resolution.anchor().orElseThrow(),
                ShrineRemovalCause.EXTERNAL_REPLACEMENT, partPos);
    }

    public static RemovalResult removeAnchor(
            WorldAccess world, AnchorSnapshot anchor, ShrineRemovalCause cause, @Nullable BlockPos preservePosition) {
        GuardKey key = new GuardKey(world.levelIdentity(), anchor.position());
        synchronized (ShrineLifecycleService.class) {
            if (IN_PROGRESS.contains(key) || PLACING.contains(key)) {
                return RemovalResult.unclaimed(cause);
            }
            IN_PROGRESS.add(key);
        }
        try {
            PlacedStructureState state = anchor.state();
            ItemStack drop = world.configuredItem(state);
            int removed = 0;
            for (int index = state.footprint().size() - 1; index >= 0; index--) {
                LocalOffset offset = state.footprint().get(index);
                BlockPos cellPos = ShrinePlacementPlanner.worldPosition(
                        anchor.position(), state.facing(), offset);
                if (cellPos.equals(preservePosition)) {
                    continue;
                }
                BlockState actual = world.blockState(cellPos);
                if (isExpectedCell(world, actual, state.facing(), offset) && world.removeCell(cellPos)) {
                    removed++;
                }
            }
            int drops = 0;
            if (cause.dropsConfiguredItem() && !drop.isEmpty()) {
                world.drop(anchor.position(), drop);
                drops = 1;
            }
            return new RemovalResult(true, removed, drops, cause);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to remove shrine structure at {} for cause {}",
                    anchor.position(), cause, exception);
            return RemovalResult.unclaimed(cause);
        } finally {
            synchronized (ShrineLifecycleService.class) {
                IN_PROGRESS.remove(key);
            }
        }
    }

    public static ItemStack pick(ServerLevel level, BlockPos sourcePos, BlockState sourceState) {
        return pick(serverWorld(level), sourcePos, sourceState);
    }

    /** Client/server clone-stack path using only already-loaded state and synchronized anchor data. */
    public static ItemStack pick(LevelReader level, BlockPos sourcePos, BlockState sourceState) {
        BlockPos anchorPos;
        LocalOffset offset;
        Direction facing;
        if (sourceState.getBlock() == LargeStructureRegistry.LARGE_STRUCTURE_ANCHOR.get()) {
            anchorPos = sourcePos;
            offset = LocalOffset.ANCHOR;
            facing = sourceState.getValue(LargeStructureAnchorBlock.FACING);
        } else if (sourceState.getBlock() == LargeStructureRegistry.LARGE_STRUCTURE_PART.get()) {
            offset = LargeStructurePartBlock.localOffset(sourceState);
            facing = sourceState.getValue(LargeStructurePartBlock.FACING);
            anchorPos = LargeStructurePartBlock.anchorPosition(sourcePos, sourceState);
        } else {
            return ItemStack.EMPTY;
        }
        if (!level.hasChunkAt(anchorPos)) {
            return ItemStack.EMPTY;
        }
        BlockState anchorState = level.getBlockState(anchorPos);
        BlockEntity blockEntity = level.getBlockEntity(anchorPos);
        if (anchorState.getBlock() != LargeStructureRegistry.LARGE_STRUCTURE_ANCHOR.get()
                || !(blockEntity instanceof LargeStructureAnchorBlockEntity anchor)
                || anchor.placedState().isEmpty()) {
            return ItemStack.EMPTY;
        }
        PlacedStructureState state = anchor.placedState().orElseThrow();
        AnchorSnapshot snapshot = new AnchorSnapshot(anchorPos, anchorState, state);
        if (!validAnchor(snapshot)
                || facing != state.facing()
                || !state.contains(offset)
                || !ShrinePlacementPlanner.worldPosition(anchorPos, facing, offset).equals(sourcePos)) {
            return ItemStack.EMPTY;
        }
        return configuredItem(state);
    }

    public static ItemStack pick(WorldAccess world, BlockPos sourcePos, BlockState sourceState) {
        Resolution resolution = resolve(world, sourcePos, sourceState);
        return resolution.valid()
                ? world.configuredItem(resolution.anchor().orElseThrow().state()) : ItemStack.EMPTY;
    }

    public static boolean isExpectedCell(
            WorldAccess world, BlockState state, Direction facing, LocalOffset offset) {
        if (offset.equals(LocalOffset.ANCHOR)) {
            return world.isAnchor(state)
                    && state.hasProperty(LargeStructureAnchorBlock.FACING)
                    && state.getValue(LargeStructureAnchorBlock.FACING) == facing;
        }
        return world.isPart(state)
                && state.hasProperty(LargeStructurePartBlock.FACING)
                && state.getValue(LargeStructurePartBlock.FACING) == facing
                && LargeStructurePartBlock.localOffset(state).equals(offset);
    }

    public static synchronized boolean isGuarded(Object levelIdentity, BlockPos anchor) {
        GuardKey key = new GuardKey(levelIdentity, anchor);
        return IN_PROGRESS.contains(key) || PLACING.contains(key);
    }

    public static <T> T duringPlacement(Object levelIdentity, BlockPos anchor, Supplier<T> action) {
        GuardKey key = new GuardKey(levelIdentity, anchor);
        synchronized (ShrineLifecycleService.class) {
            PLACING.add(key);
        }
        try {
            return action.get();
        } finally {
            synchronized (ShrineLifecycleService.class) {
                PLACING.remove(key);
            }
        }
    }

    static WorldAccess serverWorld(ServerLevel level) {
        return new WorldAccess() {
            @Override public Object levelIdentity() { return level; }
            @Override public boolean chunkLoaded(BlockPos pos) {
                return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
            }
            @Override public BlockState blockState(BlockPos pos) { return level.getBlockState(pos); }
            @Override public Optional<AnchorSnapshot> anchor(BlockPos pos) {
                BlockState state = level.getBlockState(pos);
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (state.getBlock() != LargeStructureRegistry.LARGE_STRUCTURE_ANCHOR.get()
                        || !(blockEntity instanceof LargeStructureAnchorBlockEntity anchor)
                        || anchor.placedState().isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(new AnchorSnapshot(pos, state, anchor.placedState().orElseThrow()));
            }
            @Override public boolean isAnchor(BlockState state) {
                return state.getBlock() == LargeStructureRegistry.LARGE_STRUCTURE_ANCHOR.get();
            }
            @Override public boolean isPart(BlockState state) {
                return state.getBlock() == LargeStructureRegistry.LARGE_STRUCTURE_PART.get();
            }
            @Override public boolean replaceable(BlockPos pos) {
                return level.getBlockEntity(pos) == null && level.getBlockState(pos).canBeReplaced();
            }
            @Override public boolean removeCell(BlockPos pos) {
                int flags = Block.UPDATE_ALL_IMMEDIATE | Block.UPDATE_SUPPRESS_DROPS;
                return level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags)
                        || level.getBlockState(pos).isAir();
            }
            @Override public boolean placePart(BlockPos pos, BlockState state) {
                int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
                return level.setBlock(pos, state, flags) || level.getBlockState(pos).equals(state);
            }
            @Override public BlockState expectedPart(Direction facing, LocalOffset offset) {
                return LargeStructureRegistry.LARGE_STRUCTURE_PART.get().stateFor(facing, offset);
            }
            @Override public ItemStack configuredItem(PlacedStructureState state) {
                return ShrineLifecycleService.configuredItem(state);
            }
            @Override public void drop(BlockPos pos, ItemStack stack) { Block.popResource(level, pos, stack); }
            @Override public void synchronizeAnchor(BlockPos pos) {
                if (level.getBlockEntity(pos) instanceof LargeStructureAnchorBlockEntity anchor) {
                    anchor.synchronize();
                }
            }
        };
    }

    private static boolean validAnchor(AnchorSnapshot anchor) {
        PlacedStructureState state = anchor.state();
        int expectedCells = state.familyId().equals(ShrineMonolithDefinitions.SHRINE) ? 4
                : state.familyId().equals(ShrineMonolithDefinitions.MONOLITH) ? 18 : -1;
        return state.footprint().size() == expectedCells
                && PlacedStructureState.isStructurallyValidFootprint(state.footprint())
                && anchor.blockState().hasProperty(LargeStructureAnchorBlock.FACING)
                && anchor.blockState().getValue(LargeStructureAnchorBlock.FACING) == state.facing();
    }

    private static ItemStack configuredItem(PlacedStructureState state) {
        ConfiguredStructureItem item;
        if (state.familyId().equals(ShrineMonolithDefinitions.SHRINE)) {
            item = LargeStructureRegistry.SHRINE.get();
        } else if (state.familyId().equals(ShrineMonolithDefinitions.MONOLITH)) {
            item = LargeStructureRegistry.MONOLITH.get();
        } else {
            return ItemStack.EMPTY;
        }
        return ShrineItemTransfer.fromPlacedState(item, state);
    }

    private static RemovalResult removeInvalidSource(
            WorldAccess world, BlockPos sourcePos, BlockState sourceState, ShrineRemovalCause requestedCause) {
        if (!world.isAnchor(sourceState) && !world.isPart(sourceState)) {
            return RemovalResult.unclaimed(requestedCause);
        }
        BlockPos guardAnchor = world.isPart(sourceState)
                ? LargeStructurePartBlock.anchorPosition(sourcePos, sourceState) : sourcePos;
        GuardKey key = new GuardKey(world.levelIdentity(), guardAnchor);
        synchronized (ShrineLifecycleService.class) {
            if (IN_PROGRESS.contains(key) || PLACING.contains(key)) {
                return RemovalResult.unclaimed(requestedCause);
            }
            IN_PROGRESS.add(key);
        }
        try {
            int removed = (world.isAnchor(world.blockState(sourcePos)) || world.isPart(world.blockState(sourcePos)))
                    && world.removeCell(sourcePos) ? 1 : 0;
            return new RemovalResult(removed == 1, removed, 0, ShrineRemovalCause.INVALID_ANCHOR);
        } finally {
            synchronized (ShrineLifecycleService.class) {
                IN_PROGRESS.remove(key);
            }
        }
    }
}
