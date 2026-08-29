package com.seggellion.britannia_mod.crate;

import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateShapes;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A large crate carrying a compact column on its lid.
 *
 * <h2>The problem this solves</h2>
 *
 * <p>A large crate is nineteen voxels tall and occupies two world cells, so its lid sits three voxels
 * into the upper one. A compact column may not take either cell — one position holds one block — so
 * the first cell it can own begins sixteen voxels higher still, thirteen voxels above the surface its
 * bottom crate has to rest on. Placing the column there and starting it at that cell's floor is
 * exactly the air gap this whole line of work exists to remove.
 *
 * <p>So the column roots two cells above the large crate's anchor and starts at a negative origin,
 * which puts its lowest crates physically inside the cell the large crate owns. The large crate then
 * draws, collides with and answers for that overhang, in the same way a continuation cell already
 * draws the part of a column that reaches into it. Neither structure joins the other: the large crate
 * keeps its own block entity and its own fifty-four slots, the column keeps its own, and this class is
 * only the arithmetic and the lookups that let them meet.
 *
 * <h2>Why the geometry is measured rather than written down</h2>
 *
 * <p>Every height here is derived from the crate's own authored shapes. Nineteen voxels is what the
 * model happens to measure today; hard-coding it would mean the day the art moved, crates would rest
 * on a surface that was no longer there — which is the defect that produced the floating crate in the
 * first place, one level up.
 */
public final class CrateFoundation {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * How far above a foundation's anchor the column it carries puts its root cell.
     *
     * <p>One past the top cell the foundation itself occupies, because world occupancy cannot overlap.
     */
    public static final int ROOT_CELL_ABOVE_ANCHOR = 2;

    private CrateFoundation() {
    }

    /* ─── what counts as a foundation ────────────────────────── */

    /**
     * Whether this crate carries columns rather than joining them.
     *
     * <p>The distinction is structural, not a list of names: a crate a column can pack is one cell
     * tall with a variant the column understands, and anything else — today only the large crate — is
     * something a column stands on instead.
     */
    public static boolean isFoundation(BlockState state) {
        return state.getBlock() instanceof CrateBlock crate
                && crate.hasValidPart(state)
                && !isCompact(crate);
    }

    private static boolean isCompact(CrateBlock crate) {
        return crate.cells().size() == 1
                && CrateVariant.forSlotCount(crate.slotCount()).isPresent();
    }

    /* ─── the arithmetic ─────────────────────────────────────── */

    /**
     * The physical top of a foundation crate, measured from its anchor cell's floor.
     *
     * <p>Taken from the authored cell shapes: the tallest point of any cell, plus the height of the
     * cells below it. For the large crate that is sixteen voxels of root cell plus the three its upper
     * cells reach — nineteen, the top of its lid.
     */
    public static int topHundredths(CrateBlock crate) {
        int highest = 0;
        for (DecorativeMultiblockBlock.Cell cell : crate.cells()) {
            VoxelShape shape = crate.authoredShape(crate.stateFor(Direction.NORTH, cell));
            if (shape.isEmpty()) {
                continue;
            }
            int top = cell.y() * CrateStackLayout.CELL_HUNDREDTHS
                    + (int) Math.round(shape.max(Direction.Axis.Y)
                            * 16 * CrateStackLayout.HUNDREDTHS_PER_VOXEL);
            highest = Math.max(highest, top);
        }
        return highest;
    }

    /**
     * The origin a column resting on this foundation takes.
     *
     * <p>Negative by construction: the lid is below the first cell the column is allowed to own, and
     * this is exactly how far below.
     */
    public static int originFor(CrateBlock crate) {
        return topHundredths(crate)
                - ROOT_CELL_ABOVE_ANCHOR * CrateStackLayout.CELL_HUNDREDTHS;
    }

    /**
     * The origin a large crate resting on this foundation takes.
     *
     * <h2>Why this is not the same as a column's</h2>
     *
     * <p>A column is packed by art: each crate's authored minimum is subtracted when it is drawn, so
     * aligning a column's origin with the lid puts the bottom crate's art on the lid. A large crate is
     * drawn from its raw model instead, and that model's visible bottom - its base rim - sits a voxel
     * above its own origin. Using the column's origin therefore left the crate hovering by exactly
     * that voxel, which is the gap this corrects.
     */
    public static int originForRestingLarge(CrateBlock foundation) {
        return originFor(foundation)
                - (int) Math.round(
                        CrateShapes.LARGE_VISIBLE_BASE * CrateStackLayout.HUNDREDTHS_PER_VOXEL);
    }

    /** Where a column resting on the foundation anchored here puts its root. */
    public static BlockPos columnRootFor(BlockPos anchor) {
        return anchor.above(ROOT_CELL_ABOVE_ANCHOR);
    }

    /* ─── finding one from the other ─────────────────────────── */

    /** The anchor of the foundation this position belongs to, if it belongs to one. */
    public static Optional<BlockPos> anchorAt(BlockGetter level, BlockPos pos, BlockState state) {
        if (!isFoundation(state) || !(state.getBlock() instanceof CrateBlock crate)) {
            return Optional.empty();
        }
        BlockPos anchor = crate.anchorPosition(pos, state);
        BlockState anchorState = level.getBlockState(anchor);
        return anchorState.is(crate) && crate.isRoot(anchorState)
                ? Optional.of(anchor)
                : Optional.empty();
    }

    /**
     * The column standing on the foundation this position belongs to, if there is one.
     *
     * <p>Used by every part of the large crate that has to answer for the overhang — its shape, its
     * model, the menu it opens and the crate a swing is aimed at.
     */
    public static Optional<Founded> columnOn(BlockGetter level, BlockPos pos, BlockState state) {
        return anchorAt(level, pos, state).flatMap(anchor -> {
            BlockPos root = columnRootFor(anchor);
            if (!(level.getBlockState(root).getBlock() instanceof CrateStackBlock)
                    || !(level.getBlockEntity(root) instanceof CrateStackBlockEntity stack)
                    || !stack.hasFoundation()) {
                return Optional.empty();
            }
            return Optional.of(new Founded(anchor, root, stack));
        });
    }

    /**
     * The foundation a column is standing on, if it is still there.
     *
     * <p>Deliberately derived rather than remembered. A column that stored its foundation's position
     * would have to be told when that block went away, and a column whose stored foundation had gone
     * would be in a state nothing else knew how to read. Looking down costs one block lookup and
     * cannot go stale.
     */
    public static Optional<BlockPos> foundationUnder(BlockGetter level, BlockPos root) {
        BlockPos below = root.below(ROOT_CELL_ABOVE_ANCHOR);
        BlockState state = level.getBlockState(below);
        return isFoundation(state) && state.getBlock() instanceof CrateBlock crate
                        && crate.isRoot(state)
                ? Optional.of(below)
                : Optional.empty();
    }

    /**
     * Whether this cell of a foundation is the one a column overhangs into.
     *
     * <p>A column is one block wide and sits over the anchor, so only the cell directly above the
     * anchor ever holds any of it. The far cells of the large crate are slivers half a voxel across
     * and never meet the crates above.
     */
    public static boolean carriesOverhang(CrateBlock crate, BlockState state) {
        if (!isFoundation(state)) {
            return false;
        }
        DecorativeMultiblockBlock.Cell cell = crate.cell(state);
        return cell.x() == 0 && cell.z() == 0 && cell.y() == ROOT_CELL_ABOVE_ANCHOR - 1;
    }

    /**
     * Lets go of a column standing on a foundation that is about to disappear.
     *
     * <h2>Why the column survives</h2>
     *
     * <p>Those crates hold a player's items. Destroying them, spilling them, or turning them into
     * falling entities because the thing underneath was mined would all trade an inventory for
     * tidiness, and inventory safety wins every time. So the column stays exactly as it is - same
     * crates, same ids, same contents - and only its origin changes: with nothing left to rest on it
     * settles onto its own cell floor.
     *
     * <p>That makes it rise by the height of the lid it was standing on, which looks odd for a moment
     * and is the price of the alternative being worse. Keeping the old origin would leave its lowest
     * crates hanging in a cell that no longer has anything to draw them, which is an invisible crate -
     * present, clickable from nowhere, and impossible for a player to make sense of.
     */
    public static void releaseColumn(ServerLevel level, BlockPos anchor) {
        BlockPos root = columnRootFor(anchor);
        if (level.getBlockEntity(root) instanceof CrateStackBlockEntity stack
                && stack.hasFoundation()) {
            stack.setOriginHundredths(0);
            CrateStackColumnSync.notifyClients(
                    level, root, CrateStackColumnSync.reconcile(level, root, stack));
        }
    }

    /**
     * Whether this cell is part of a foundation's lid - the level a crate placed on top rests against.
     *
     * <p>Wider than {@link #carriesOverhang}: a compact column is one block across and only ever meets
     * the cell above the anchor, but a large crate standing on another covers the whole lid, so all
     * four of its cells count as support.
     */
    public static boolean isLidCell(CrateBlock crate, BlockState state) {
        if (!isFoundation(state)) {
            return false;
        }
        return crate.cell(state).y() == ROOT_CELL_ABOVE_ANCHOR - 1;
    }

    /**
     * The large crate resting on the foundation this position belongs to, if there is one.
     *
     * <p>Recognised by state rather than by position alone: a crate two cells above another is only
     * standing on it if it says it is, which is what stops a crate someone built on a platform from
     * being drawn sunk into the one below.
     */
    public static Optional<FoundedLarge> largeOn(BlockGetter level, BlockPos pos, BlockState state) {
        return anchorAt(level, pos, state).flatMap(anchor -> {
            BlockPos upper = columnRootFor(anchor);
            BlockState upperState = level.getBlockState(upper);
            if (!(upperState.getBlock() instanceof CrateBlock crate)
                    || !crate.isRoot(upperState)
                    || !isFoundation(upperState)
                    || !(level.getBlockEntity(upper) instanceof CrateBlockEntity resting)
                    || !resting.hasFoundation()) {
                return Optional.empty();
            }
            return Optional.of(new FoundedLarge(anchor, upper, upperState, resting));
        });
    }

    /** Whether this crate is itself standing on another crate's lid. */
    public static boolean isFoundedLarge(BlockGetter level, BlockPos pos, BlockState state) {
        if (!isFoundation(state) || !(state.getBlock() instanceof CrateBlock crate)) {
            return false;
        }
        BlockPos anchor = crate.anchorPosition(pos, state);
        return level.getBlockEntity(anchor) instanceof CrateBlockEntity crateEntity
                && crateEntity.hasFoundation();
    }

    /**
     * How far below its own cells a crate at this position is drawn, in blocks.
     *
     * <p>Zero for everything that stands on the ground, which is almost everything.
     */
    public static double originBlocksAt(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CrateBlock crate) || !crate.hasValidPart(state)) {
            return 0.0D;
        }
        BlockPos anchor = crate.anchorPosition(pos, state);
        if (!(level.getBlockEntity(anchor) instanceof CrateBlockEntity crateEntity)) {
            return 0.0D;
        }
        return crateEntity.originHundredths()
                / (double) (CrateStackLayout.HUNDREDTHS_PER_VOXEL * 16);
    }

    /**
     * Whatever of a crate standing above this position hangs down into it.
     *
     * <p>A large crate resting on another begins at that crate's lid, well below the first cell it is
     * allowed to occupy, so its body reaches down through cells belonging to the crate underneath. Each
     * of those cells contributes the slice that is genuinely inside it, which is what lets a ray meet
     * the crate from any direction: a ray only ever tests the blocks it actually passes through.
     */
    public static VoxelShape restingAbove(BlockGetter level, BlockPos pos) {
        VoxelShape reaching = Shapes.empty();
        for (int above = 1; above <= ROOT_CELL_ABOVE_ANCHOR; above++) {
            BlockPos higher = pos.above(above);
            BlockState state = level.getBlockState(higher);
            if (!(state.getBlock() instanceof CrateBlock crate) || !crate.hasValidPart(state)) {
                continue;
            }
            double origin = originBlocksAt(level, higher, state);
            if (origin == 0.0D) {
                continue;
            }
            VoxelShape slice = CrateStackShapes.shiftIntoCell(
                    crate.authoredShape(state), origin + above);
            if (!slice.isEmpty()) {
                reaching = Shapes.or(reaching, slice);
            }
        }
        return reaching;
    }

    /** A large crate standing on another large crate's lid. */
    public record FoundedLarge(
            BlockPos foundationAnchor, BlockPos anchor, BlockState state, CrateBlockEntity crate) {

        /** How far below its own cells this crate is drawn. */
        public int originHundredths() {
            return crate.originHundredths();
        }
    }

    /**
     * Lets go of a large crate standing on a foundation that is disappearing.
     *
     * <h2>Why this has to exist</h2>
     *
     * <p>A crate standing on another is drawn by the crate underneath it, because that is the cell its
     * art is physically inside. Take the lower crate away and the upper one is still entirely there -
     * eight cells, its own block entity, its fifty-four slots - with nothing left in the world that
     * knows how to draw it. It becomes solid, selectable, unbreakable and completely invisible, which
     * is a far worse thing to leave behind than a gap.
     *
     * <p>So it settles onto its own cell floor, exactly as a column does when its foundation goes. It
     * rises by the height of the lid it was resting on, and from that moment it is an ordinary large
     * crate: drawn by itself, breakable, and holding everything it held before.
     */
    public static void releaseRestingLarge(ServerLevel level, BlockPos anchor) {
        BlockPos resting = columnRootFor(anchor);
        if (level.getBlockEntity(resting) instanceof CrateBlockEntity crate && crate.hasFoundation()) {
            settle(level, resting, crate);
        }
    }

    /**
     * Puts a crate back on its own floor when whatever it was standing on has gone.
     *
     * <p>The recovery path for a world that already contains one of these, and the reason the check is
     * a lookup rather than remembered state: a crate that has lost its foundation cannot be told, so
     * it has to be able to notice.
     */
    public static void repairIfOrphaned(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isFoundedLarge(level, pos, state) || !(state.getBlock() instanceof CrateBlock crate)) {
            return;
        }
        BlockPos anchor = crate.anchorPosition(pos, state);
        if (foundationUnder(level, anchor).isPresent()) {
            return;
        }
        if (level.getBlockEntity(anchor) instanceof CrateBlockEntity orphan && orphan.hasFoundation()) {
            LOGGER.info("[crate] {} was standing on a crate that has gone; settling it onto its own "
                    + "floor so it can be seen and removed", anchor);
            settle(level, anchor, orphan);
        }
    }

    /** Drops a crate's origin back to its own cell floor and tells everyone that can see it. */
    private static void settle(ServerLevel level, BlockPos anchor, CrateBlockEntity crate) {
        crate.setOriginHundredths(0);
        // Its art used to be emitted by cells below it, so those need rebuilding too, and its own
        // cells have to start drawing it.
        for (int cell = -ROOT_CELL_ABOVE_ANCHOR; cell <= ROOT_CELL_ABOVE_ANCHOR; cell++) {
            BlockPos pos = anchor.above(cell);
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
    }

    /** A column and the foundation it rests on. */
    public record Founded(BlockPos anchor, BlockPos root, CrateStackBlockEntity stack) {

        /**
         * The part of the column that hangs into the foundation's upper cell.
         *
         * <p>Cell {@code -1} of the column is the same world position as the foundation's upper cell,
         * which is what makes this the ordinary slice lookup rather than a special case.
         */
        public CrateStackSlice overhang() {
            return stack.sliceFor(-1);
        }
    }
}
