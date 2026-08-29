package com.seggellion.britannia_mod.crate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Where every crate in a column sits, and how many world cells that column needs.
 *
 * <h2>Integer voxels</h2>
 *
 * <p>All vertical arithmetic is in hundredths of a voxel. Cell count is world occupancy — it decides
 * which block positions a column claims — so it must be a function of the crate list alone and not of
 * how a sum of {@code double}s happened to round. Both authored heights are exact hundredths, so
 * nothing is lost by the choice and every boundary comparison is an integer one.
 *
 * <p>A layout is immutable and computed from the crate list in one pass. Removing a crate produces a
 * new layout; it does not edit this one. That is what lets repacking be a pure recomputation that
 * cannot reach an inventory even by accident.
 */
public final class CrateStackLayout {

    /** One voxel, in the fixed-point unit used throughout. */
    public static final int HUNDREDTHS_PER_VOXEL = 100;

    /** One world block cell is sixteen voxels. */
    public static final int CELL_HUNDREDTHS = 16 * HUNDREDTHS_PER_VOXEL;

    /**
     * The tallest a column may grow, in cells.
     *
     * <p>Four keeps the block entity's saved data, the continuation cells a later milestone has to
     * maintain, and the collision and selection work a column costs all bounded, and keeps
     * thousand-crate towers out of the first implementation.
     */
    public static final int MAX_CELLS = 4;

    /** The same limit as a height, which is what append is actually checked against. */
    public static final int MAX_HEIGHT_HUNDREDTHS = MAX_CELLS * CELL_HUNDREDTHS;

    private static final CrateStackLayout EMPTY =
            new CrateStackLayout(List.of(), 0, 0);

    private final List<CratePlacement> placements;
    private final int totalHundredths;
    private final int requiredCells;

    private CrateStackLayout(List<CratePlacement> placements, int totalHundredths, int requiredCells) {
        this.placements = placements;
        this.totalHundredths = totalHundredths;
        this.requiredCells = requiredCells;
    }

    /**
     * Packs crates bottom upwards, each resting directly on the one below.
     *
     * <p>Order is the column read from the ground up, so the first entry is the crate a player stands
     * beside and the last is the one on top.
     */
    public static CrateStackLayout of(List<LogicalCrate> crates) {
        Objects.requireNonNull(crates, "crates");
        if (crates.isEmpty()) {
            return EMPTY;
        }
        List<CratePlacement> packed = new ArrayList<>(crates.size());
        int base = 0;
        for (LogicalCrate crate : crates) {
            int top = base + crate.heightHundredths();
            packed.add(new CratePlacement(crate.id(), base, top));
            base = top;
        }
        return new CrateStackLayout(
                Collections.unmodifiableList(packed), base, cellsFor(base));
    }

    /**
     * How many cells a column of this height claims.
     *
     * <p>Integer ceiling, so a column exactly filling its top cell does not claim an empty one above
     * it — two small crates measure 14.30 voxels and occupy one cell, three measure 21.45 and occupy
     * two.
     */
    public static int cellsFor(int totalHundredths) {
        if (totalHundredths <= 0) {
            return 0;
        }
        return (totalHundredths + CELL_HUNDREDTHS - 1) / CELL_HUNDREDTHS;
    }

    /** Whether a column of this height is within the cap. */
    public static boolean withinCap(int totalHundredths) {
        return totalHundredths <= MAX_HEIGHT_HUNDREDTHS;
    }

    public List<CratePlacement> placements() {
        return placements;
    }

    public int totalHundredths() {
        return totalHundredths;
    }

    /** The packed height in voxels, for messages and debugging rather than for arithmetic. */
    public double totalVoxels() {
        return totalHundredths / (double) HUNDREDTHS_PER_VOXEL;
    }

    /** How many world cells this column occupies, counting upward from its root. */
    public int requiredCells() {
        return requiredCells;
    }

    public boolean isEmpty() {
        return placements.isEmpty();
    }

    /** Where one crate sits, or empty if this layout does not contain it. */
    public CratePlacement placementOf(int crateId) {
        for (CratePlacement placement : placements) {
            if (placement.crateId() == crateId) {
                return placement;
            }
        }
        return null;
    }

    /**
     * Every crate with any part of itself inside one cell, in bottom-up order.
     *
     * <p>What a renderer and a shape builder both need, and the reason a layout is cached rather than
     * recomputed: a crate crossing a cell boundary appears in both cells, and each needs to know where
     * the crate starts relative to its own floor — which
     * {@link CratePlacement#localBaseHundredths(int)} answers, negative when the crate began below.
     */
    public List<CratePlacement> placementsInCell(int cell) {
        List<CratePlacement> inCell = new ArrayList<>();
        for (CratePlacement placement : placements) {
            if (placement.occupiesCell(cell)) {
                inCell.add(placement);
            }
        }
        return Collections.unmodifiableList(inCell);
    }

    @Override
    public String toString() {
        return "CrateStackLayout[" + placements.size() + " crates, "
                + totalVoxels() + " voxels, " + requiredCells + " cells]";
    }
}
