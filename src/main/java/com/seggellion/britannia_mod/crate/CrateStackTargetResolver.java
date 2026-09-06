package com.seggellion.britannia_mod.crate;

import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Works out which visible crate a player is pointing at.
 *
 * <h2>One place for the arithmetic</h2>
 *
 * <p>A column mixes four coordinate systems — world blocks, a cell's own space, voxels, and the
 * hundredths the layout packs in — and every one of interaction, placement, menus, breaking and
 * Grabby targeting needs the same answer from them. Working it out separately in each would
 * eventually give five slightly different answers, and the one that mattered would be whichever
 * decided where a player's items went. So it is worked out here, once, and everything else asks.
 *
 * <h2>Boundary convention</h2>
 *
 * <p>Crates in a column touch exactly: one ends where the next begins. A ray meeting that shared
 * plane belongs to the crate above it — each crate owns {@code [base, top)} — so a hit at 7.15 in a
 * column of small crates is the second crate, not the first. The exception is the very top of the
 * column, where {@code [base, top)} would own nothing at all: a hit at or above the top surface is
 * clamped to the top crate, which is what makes the exposed lid of a stack reliably clickable.
 */
public final class CrateStackTargetResolver {

    private static final int HUNDREDTHS_PER_BLOCK =
            CrateStackLayout.HUNDREDTHS_PER_VOXEL * 16;

    private CrateStackTargetResolver() {
    }

    /** A crate a player is pointing at, and the column it belongs to. */
    public record Target(CrateStackBlockEntity stack, BlockPos root, int crateId) {

        public LogicalCrate crate() {
            return stack.crateById(crateId);
        }

        public LogicalCrateContainer container() {
            return stack.containerFor(crateId);
        }

        /** Where this crate sits in its column, for sounds and effects. */
        public CratePlacement placement() {
            return stack.placementOf(crateId);
        }
    }

    /**
     * The crate under a hit, from whichever cell of the column was clicked.
     *
     * <p>Empty when the position is not part of a column, when the root has gone, or when the column
     * holds no crates — never an exception, because a stale click is an ordinary thing to receive and
     * the caller simply wants to know there is nothing there.
     */
    public static Optional<Target> resolve(BlockGetter level, BlockHitResult hit) {
        BlockPos clicked = hit.getBlockPos();
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof CrateStackBlock)) {
            // A crate resting on a large crate's lid is physically inside the large crate's cell, so
            // this is where a hit on it actually arrives.
            return CrateFoundation.columnOn(level, clicked, state)
                    .flatMap(founded -> crateAt(founded.stack(), founded.root(), hit.getLocation())
                            .map(crateId -> new Target(founded.stack(), founded.root(), crateId)));
        }
        BlockPos root = CrateStackBlock.rootOf(clicked, state);
        if (!(level.getBlockEntity(root) instanceof CrateStackBlockEntity stack)) {
            return Optional.empty();
        }
        return crateAt(stack, root, hit.getLocation())
                .map(crateId -> new Target(stack, root, crateId));
    }

    /**
     * Which crate occupies the height a hit landed on.
     *
     * <p>Measured from the root's floor so the answer does not depend on which cell was clicked; a
     * player pointing at the same crate through the root or through a continuation cell gets the same
     * crate.
     */
    public static Optional<Integer> crateAt(
            CrateStackBlockEntity stack, BlockPos root, Vec3 location) {
        return crateAtHeight(stack, stackHeightOf(root, location));
    }

    /** A world position expressed as height above the column's floor, in the layout's own units. */
    public static int stackHeightOf(BlockPos root, Vec3 location) {
        return (int) Math.round((location.y - root.getY()) * HUNDREDTHS_PER_BLOCK);
    }

    /** The crate owning a height, under the half-open convention described above. */
    public static Optional<Integer> crateAtHeight(CrateStackBlockEntity stack, int heightHundredths) {
        CrateStackLayout layout = stack.layout();
        if (layout.isEmpty()) {
            return Optional.empty();
        }
        // Below where the column begins is not the column. For a freestanding stack that is the cell
        // floor and nothing changes; for one standing on a large crate it is the lid, and without this
        // every click on the foundation beneath would resolve to the bottom crate.
        if (heightHundredths < layout.originHundredths()) {
            return Optional.empty();
        }
        for (CratePlacement placement : layout.placements()) {
            if (heightHundredths < placement.topHundredths()) {
                return Optional.of(placement.crateId());
            }
        }
        // At or above the top of the column: the lid of the top crate, which is the surface a player
        // is most likely to be aiming at and the one a half-open range would otherwise disown.
        return Optional.of(layout.placements().get(layout.placements().size() - 1).crateId());
    }

    /**
     * Whether a hit is on the exposed top of the whole column rather than an interior surface.
     *
     * <p>What separates "put another crate on this" from "open this crate". Crates pack flush, so the
     * only horizontal surface a ray can actually reach from outside is the top of the topmost crate —
     * the merged selection shape leaves no interior ledge exposed. The height is still checked rather
     * than trusted, because a hit arriving from anywhere other than the outline would otherwise be
     * read as a stacking gesture.
     */
    public static boolean isColumnTop(CrateStackBlockEntity stack, BlockPos root, BlockHitResult hit) {
        return isColumnTop(stack, root, hit.getDirection(), hit.getLocation());
    }

    /** The same question from a context that has the face and the point but not the hit result. */
    public static boolean isColumnTop(
            CrateStackBlockEntity stack, BlockPos root, Direction face, Vec3 location) {
        if (face != Direction.UP || stack.isEmpty()) {
            return false;
        }
        int height = stackHeightOf(root, location);
        // A tolerance of one hundredth of a voxel: the ray meets the surface, and float arithmetic on
        // the way in should not decide whether a player may stack.
        return Math.abs(height - stack.totalHeightHundredths()) <= 1;
    }
}
