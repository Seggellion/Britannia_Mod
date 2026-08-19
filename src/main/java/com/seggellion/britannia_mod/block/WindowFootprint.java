package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which cells of the world an oversized window's art covers.
 *
 * <h2>Why this exists</h2>
 * A {@link net.minecraft.world.phys.shapes.VoxelShape} can never leave its own block, but several
 * of this mod's window models are authored two or three cells wide and two or three cells tall and
 * are rendered from a single block position. Everything outside that one cell used to render with
 * no collision behind it at all, which is how a player could walk through - or clean through - a
 * window that plainly looks solid. The footprint names those extra cells so
 * {@link MultiCellWindowBlock} can back each one with a {@link WindowCollisionBlock}.
 *
 * <h2>Coordinates</h2>
 * Cells are given in the model's own frame, as {@code (right, up)} offsets from the block the
 * player places: {@code right} counts along the model's +X axis and {@code up} along its +Y. Model
 * +X lands on {@link Direction#getClockWise()} of the block's facing for every one of the four
 * blockstate rotations, which is what {@link #worldPos} relies on - so one footprint, authored once
 * against the north-facing model, covers all four orientations.
 *
 * @param cells every cell the art reaches, including the origin cell {@code (0, 0)}
 */
public record WindowFootprint(List<Cell> cells) {

    /**
     * @param right how many cells along the model's +X axis from the placed block
     * @param up    how many cells above the placed block
     * @param span  how much of the cell's height the art fills
     */
    public record Cell(int right, int up, WindowCollisionSpan span) {
        public boolean isOrigin() {
            return this.right == 0 && this.up == 0;
        }
    }

    public WindowFootprint {
        cells = List.copyOf(cells);
    }

    /**
     * Art authored on the block grid, so every cell it reaches is filled top to bottom. Bounds are
     * inclusive, and are read straight off the model: a frame spanning model x -16..16 and y 0..32
     * is {@code aligned(-1, 0, 0, 1)}.
     */
    public static WindowFootprint aligned(int rightFrom, int rightTo, int upFrom, int upTo) {
        List<Cell> cells = new ArrayList<>();
        for (int up = upFrom; up <= upTo; up++) {
            for (int right = rightFrom; right <= rightTo; right++) {
                cells.add(new Cell(right, up, WindowCollisionSpan.FULL));
            }
        }
        return new WindowFootprint(cells);
    }

    /**
     * Art whose two-cell-tall frame is authored half a block low - model y -8..24 rather than
     * 0..32 - so it fills its own row, the top half of the row below and the bottom half of the row
     * above. Used by the {@code window_cross_2x2} and {@code window_cross_2x3} art.
     */
    public static WindowFootprint halfDropped(int rightFrom, int rightTo) {
        List<Cell> cells = new ArrayList<>();
        for (int right = rightFrom; right <= rightTo; right++) {
            cells.add(new Cell(right, -1, WindowCollisionSpan.UPPER));
            cells.add(new Cell(right, 0, WindowCollisionSpan.FULL));
            cells.add(new Cell(right, 1, WindowCollisionSpan.LOWER));
        }
        return new WindowFootprint(cells);
    }

    /** A window whose art stays inside the block it is placed in. */
    public static WindowFootprint single() {
        return new WindowFootprint(Collections.singletonList(
            new Cell(0, 0, WindowCollisionSpan.FULL)));
    }

    /** Where {@code cell} lands in the world for a window placed at {@code origin} facing {@code facing}. */
    public BlockPos worldPos(BlockPos origin, Direction facing, Cell cell) {
        return origin.relative(facing.getClockWise(), cell.right()).above(cell.up());
    }

    /** The cell of this footprint that lands on {@code target}, or {@code null} if none does. */
    public Cell cellAt(BlockPos origin, Direction facing, BlockPos target) {
        for (Cell cell : this.cells) {
            if (worldPos(origin, facing, cell).equals(target)) {
                return cell;
            }
        }
        return null;
    }

    /** How far, in cells, the furthest cell sits from the origin on any axis. Used to bound searches. */
    public int reach() {
        int reach = 0;
        for (Cell cell : this.cells) {
            reach = Math.max(reach, Math.max(Math.abs(cell.right()), Math.abs(cell.up())));
        }
        return reach;
    }

    /** True when the art covers more than the block the player places. */
    public boolean isMultiCell() {
        return this.cells.size() > 1;
    }
}
