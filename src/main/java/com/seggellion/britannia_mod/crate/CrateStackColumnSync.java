package com.seggellion.britannia_mod.crate;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Keeps a column's world cells in step with the crates it holds.
 *
 * <h2>The one rule</h2>
 *
 * <p>World occupancy follows the logical column, never the other way round. If the two disagree —
 * after a crash, an interrupted mutation, or a foreign block appearing where a cell used to be — the
 * crates win and the cells are rebuilt around them. Deleting a crate because its cell went missing
 * would trade an inventory for a block, which is never the right way round.
 *
 * <h2>Preflight before mutation</h2>
 *
 * <p>Growth checks every position it will need <em>before</em> touching the column. The alternative —
 * append the crate, discover a ceiling, then unwind — means an inventory briefly exists in a state
 * nothing else expects, and unwinding is exactly where duplication bugs live. A refused growth here
 * leaves the crate list, the inventories, the ids and the existing cells untouched, because it never
 * started.
 */
public final class CrateStackColumnSync {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Cells are placed and removed without drops or neighbour teardown; the column is not dying. */
    private static final int MUTATION_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;

    private CrateStackColumnSync() {
    }

    /** Why a column could not grow. */
    public enum GrowthRefusal {
        /** The column is already as tall as the cap allows. */
        AT_HEIGHT_CAP,
        /** A cell the column would need is outside the world or in unloaded chunks. */
        OUT_OF_WORLD,
        /** Something that is not part of this column already stands where a cell would go. */
        OBSTRUCTED,
        /** There is no column at that position. */
        NOT_A_COLUMN
    }

    /**
     * Adds a crate to a column and claims any cell that needs, or refuses without touching anything.
     *
     * @return the new crate's id, or empty with the reason recorded in {@code refusal}
     */
    public static Growth appendCrate(
            ServerLevel level, BlockPos root, CrateVariant variant, Direction facing) {

        if (!(level.getBlockEntity(root) instanceof CrateStackBlockEntity stack)
                || !CrateStackBlock.isRoot(level.getBlockState(root))) {
            return Growth.refused(GrowthRefusal.NOT_A_COLUMN);
        }
        if (!stack.canAppend(variant)) {
            return Growth.refused(GrowthRefusal.AT_HEIGHT_CAP);
        }

        // What the column will look like if this succeeds, worked out without changing it.
        List<LogicalCrate> projected = new ArrayList<>(stack.crates());
        projected.add(new LogicalCrate(stack.nextCrateId(), variant, facing));
        int neededCells = CrateStackLayout.of(projected).requiredCells();

        GrowthRefusal blocked = preflight(level, root, neededCells);
        if (blocked != null) {
            return Growth.refused(blocked);
        }

        OptionalInt id = stack.appendCrate(variant, facing);
        if (id.isEmpty()) {
            return Growth.refused(GrowthRefusal.AT_HEIGHT_CAP);
        }
        notifyClients(level, root, reconcile(level, root, stack));
        return Growth.grew(id.getAsInt());
    }

    /**
     * Whether every cell a column of this size needs is available.
     *
     * @return the reason it is not, or null when the column may grow
     */
    public static GrowthRefusal preflight(ServerLevel level, BlockPos root, int neededCells) {
        if (neededCells > CrateStackLayout.MAX_CELLS) {
            return GrowthRefusal.AT_HEIGHT_CAP;
        }
        for (int cell = 1; cell < neededCells; cell++) {
            BlockPos pos = root.above(cell);
            if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.hasChunkAt(pos)) {
                return GrowthRefusal.OUT_OF_WORLD;
            }
            if (!isAvailableFor(level, pos, root)) {
                return GrowthRefusal.OBSTRUCTED;
            }
        }
        return null;
    }

    /**
     * Makes the world match the column: claims the cells it needs, releases the ones it does not.
     *
     * <p>Safe to call at any time — after a change, after a load, or on a column already correct — and
     * it never removes anything that is not one of this column's own continuation cells.
     *
     * @return how many cells the column now occupies
     */
    public static int reconcile(ServerLevel level, BlockPos root, CrateStackBlockEntity stack) {
        int needed = stack.requiredCellCount();
        return CrateStackBlock.duringMutation(() -> {
            for (int cell = 1; cell < needed; cell++) {
                BlockPos pos = root.above(cell);
                BlockState present = level.getBlockState(pos);
                if (isContinuationOf(level, pos, present, root)) {
                    continue;
                }
                if (!isAvailableFor(level, pos, root)) {
                    // Inventory safety wins: a foreign block stays, the crates stay, and the column
                    // simply occupies less world than it would like until someone clears the space.
                    LOGGER.warn("[crate-stack] {} wanted cell {} at {} but {} is in the way; leaving "
                                    + "the column short rather than overwriting it",
                            root, cell, pos, present.getBlock());
                    break;
                }
                level.setBlock(pos, continuationState(cell), MUTATION_FLAGS);
            }
            // Anything above what is needed, and belonging to this column, is released.
            for (int cell = Math.max(needed, 1); cell < CrateStackLayout.MAX_CELLS; cell++) {
                BlockPos pos = root.above(cell);
                BlockState present = level.getBlockState(pos);
                if (isContinuationOf(level, pos, present, root)) {
                    level.removeBlock(pos, false);
                }
            }
            return needed;
        });
    }

    /**
     * Tells clients a column's contents changed, even when its cells did not.
     *
     * <p>Adding a crate that fits inside the cells a column already has moves no block, so nothing
     * would otherwise ask those chunk sections to rebuild and the new crate would not appear until
     * something else disturbed them. Every occupied cell is nudged, not just the root, because each
     * cell bakes its own slice of the column.
     */
    public static void notifyClients(ServerLevel level, BlockPos root, int occupiedCells) {
        for (int cell = 0; cell < Math.max(occupiedCells, 1); cell++) {
            BlockPos pos = root.above(cell);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CrateStackBlock) {
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Tells clients about a column on the next tick rather than this one.
     *
     * <p>For breaking specifically. A client predicts that the block it broke is gone, and when the
     * server acknowledges the swing it restores whatever state it last heard about — including, through
     * NeoForge's snapshot restore, the block entity as it stood <em>before</em> the break. A layout
     * sent during the same tick is overwritten by that restore, leaving the client drawing the crate
     * the player just destroyed. Sending a tick later puts the fresh layout after the acknowledgement,
     * which is the only ordering that survives it.
     */
    public static void notifyClientsNextTick(ServerLevel level, BlockPos root) {
        BlockPos immutable = root.immutable();
        level.getServer().tell(new TickTask(
                level.getServer().getTickCount() + 1,
                () -> {
                    if (level.getBlockState(immutable).getBlock() instanceof CrateStackBlock) {
                        notifyClients(level, immutable, reconcile(level, immutable));
                    }
                }));
    }

    /** Reconciles a column found at {@code root}, if there is one. */
    public static int reconcile(ServerLevel level, BlockPos root) {
        return level.getBlockEntity(root) instanceof CrateStackBlockEntity stack
                ? reconcile(level, root, stack)
                : 0;
    }

    /**
     * Whether a position may be taken for this column's use.
     *
     * <p>True for empty replaceable space and for a cell this column already owns; false for anything
     * else, including another column's cells.
     */
    private static boolean isAvailableFor(ServerLevel level, BlockPos pos, BlockPos root) {
        BlockState present = level.getBlockState(pos);
        if (isContinuationOf(level, pos, present, root)) {
            return true;
        }
        if (present.getBlock() instanceof CrateStackBlock) {
            // Another column, or this column's own root. Never claimed silently.
            return false;
        }
        return present.canBeReplaced() && level.getBlockEntity(pos) == null;
    }

    /** Whether this position is a continuation cell belonging to this particular root. */
    private static boolean isContinuationOf(
            ServerLevel level, BlockPos pos, BlockState state, BlockPos root) {
        return state.getBlock() instanceof CrateStackBlock
                && !CrateStackBlock.isRoot(state)
                && CrateStackBlock.rootOf(pos, state).equals(root);
    }

    private static BlockState continuationState(int cell) {
        return BlockRegistry.CRATE_STACK.get().defaultBlockState()
                .setValue(CrateStackBlock.PART, cell);
    }

    /** The outcome of a growth attempt. */
    public record Growth(OptionalInt crateId, GrowthRefusal refusal) {

        static Growth grew(int crateId) {
            return new Growth(OptionalInt.of(crateId), null);
        }

        static Growth refused(GrowthRefusal refusal) {
            return new Growth(OptionalInt.empty(), refusal);
        }

        public boolean succeeded() {
            return crateId.isPresent();
        }
    }
}
