package com.seggellion.britannia_mod.crate;

import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns a single legacy crate into a one-crate column, carrying its contents across intact.
 *
 * <h2>The hazard this class exists to avoid</h2>
 *
 * <p>A crate is a {@link DecorativeMultiblockBlock}, and that family answers {@code onRemove} by
 * dismantling itself — which for a crate means {@code beforeDismantle} spilling its inventory onto
 * the floor. Replacing the block with a column the ordinary way therefore runs this sequence:
 *
 * <pre>
 *   setBlock(crate_stack)
 *     -> CrateBlock.onRemove
 *       -> dismantle
 *         -> Containers.dropContents      // the contents are now on the ground
 *   ...and the column then adopts the copy taken beforehand
 * </pre>
 *
 * <p>which is a duplication: one set of items on the floor and the same set inside the column. The
 * defence is the family's own {@code duringMutation} guard, which every {@code onRemove},
 * {@code onDestroyedByPlayer} and explosion path in {@link DecorativeMultiblockBlock} already checks
 * before dismantling. Doing the swap inside it makes the teardown a no-op, so the contents leave the
 * old block entity exactly once — by being moved into the new one.
 *
 * <h2>Promotion is one-way</h2>
 *
 * <p>Nothing here converts a column back. A one-crate column stays a column: each conversion is a
 * moment when an inventory exists in two places at once, and allowing demotion would put one of those
 * moments into ordinary play every time a stack was emptied down to its last crate. Promoting only,
 * and only once, keeps the count at one for the lifetime of the column.
 */
public final class CrateStackPromotion {

    private CrateStackPromotion() {
    }

    /**
     * Why a promotion could not happen. Success carries the new column instead.
     */
    public enum Refusal {
        /** The position does not hold a single-cell crate that can become a column. */
        NOT_A_PROMOTABLE_CRATE,
        /** A large crate: a 2x2x2 multiblock, which compact columns do not carry. */
        VARIANT_NOT_SUPPORTED,
        /** The crate has no block entity to take an inventory from. */
        NO_INVENTORY,
        /** The world refused the block swap; nothing was changed. */
        WORLD_REFUSED
    }

    /** What a promotion produced: the column, and the id its first crate was given. */
    public record Promoted(CrateStackBlockEntity stack, int crateId) {
    }

    /**
     * Replaces the crate at {@code pos} with a column holding that same crate.
     *
     * <p>Atomic from a player's point of view: either the position holds a column carrying the
     * original contents, or it is untouched. Nothing is dropped and nothing is duplicated on either
     * outcome.
     */
    public static Result promote(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CrateBlock crate)) {
            return Result.refused(Refusal.NOT_A_PROMOTABLE_CRATE);
        }
        if (!crate.hasValidPart(state) || !crate.isRoot(state)) {
            return Result.refused(Refusal.NOT_A_PROMOTABLE_CRATE);
        }
        // Asked before the cell count, because the large crate fails both and the variant is the
        // reason that says something: this is a kind of crate columns do not carry, rather than a
        // crate that merely happens to be shaped wrongly.
        Optional<CrateVariant> variant = CrateVariant.forSlotCount(crate.slotCount());
        if (variant.isEmpty()) {
            return Result.refused(Refusal.VARIANT_NOT_SUPPORTED);
        }
        // A column is a single position. Anything spreading one object over several cells has no
        // single cell to convert.
        if (crate.cells().size() != 1) {
            return Result.refused(Refusal.NOT_A_PROMOTABLE_CRATE);
        }
        if (!(level.getBlockEntity(pos) instanceof CrateBlockEntity legacy)) {
            return Result.refused(Refusal.NO_INVENTORY);
        }

        Direction facing = state.getValue(CrateBlock.FACING);
        NonNullList<ItemStack> carried = detach(legacy, variant.get());

        // Inside the guard the crate's own teardown does nothing, so the contents that just left the
        // old block entity cannot also be spilled by it.
        boolean swapped = crate.duringMutation(() -> level.setBlock(
                pos,
                BlockRegistry.CRATE_STACK.get().defaultBlockState(),
                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS));

        if (!swapped || !(level.getBlockEntity(pos) instanceof CrateStackBlockEntity stack)) {
            // Put the contents back where they came from rather than leaving them nowhere.
            restore(level, pos, carried);
            return Result.refused(Refusal.WORLD_REFUSED);
        }

        OptionalInt id = stack.appendCrate(variant.get(), facing, carried);
        if (id.isEmpty()) {
            // Unreachable for one crate, which is far below the cap, but a lost inventory is not the
            // failure mode to discover that with.
            restore(level, pos, carried);
            return Result.refused(Refusal.WORLD_REFUSED);
        }
        stack.setChanged();
        return Result.promoted(stack, id.getAsInt());
    }

    /**
     * Moves the legacy crate's storage out of it in one step.
     *
     * <p>Cleared as it is taken, so from this moment exactly one list holds those stacks. If anything
     * afterwards spills the old block entity it spills nothing.
     */
    private static NonNullList<ItemStack> detach(CrateBlockEntity legacy, CrateVariant variant) {
        NonNullList<ItemStack> carried =
                NonNullList.withSize(variant.slotCount(), ItemStack.EMPTY);
        int slots = Math.min(legacy.getContainerSize(), carried.size());
        for (int slot = 0; slot < slots; slot++) {
            carried.set(slot, legacy.getItem(slot));
        }
        legacy.clearContent();
        return carried;
    }

    /** Undoes a detach when the swap could not be completed. */
    private static void restore(ServerLevel level, BlockPos pos, NonNullList<ItemStack> carried) {
        if (level.getBlockEntity(pos) instanceof CrateBlockEntity legacy) {
            for (int slot = 0; slot < carried.size() && slot < legacy.getContainerSize(); slot++) {
                legacy.setItem(slot, carried.get(slot));
            }
            legacy.setChanged();
        }
    }

    /** The outcome of a promotion attempt. */
    public record Result(Optional<Promoted> promoted, Optional<Refusal> refusal) {

        static Result promoted(CrateStackBlockEntity stack, int crateId) {
            return new Result(Optional.of(new Promoted(stack, crateId)), Optional.empty());
        }

        static Result refused(Refusal refusal) {
            return new Result(Optional.empty(), Optional.of(refusal));
        }

        public boolean succeeded() {
            return promoted.isPresent();
        }
    }
}
