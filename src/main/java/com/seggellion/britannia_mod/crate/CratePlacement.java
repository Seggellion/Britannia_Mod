package com.seggellion.britannia_mod.crate;

/**
 * Where one crate ended up when its column was packed.
 *
 * <p>Heights are hundredths of a voxel measured from the column's floor, so the root cell spans
 * {@code 0} to {@link CrateStackLayout#CELL_HUNDREDTHS} and the cell above continues from there.
 * Deliberately carries an id rather than an index: the crate this describes keeps its identity across
 * every repack, and an index would not.
 *
 * @param crateId the crate this describes
 * @param baseHundredths where the crate's art begins, from the column floor
 * @param topHundredths where it ends
 */
public record CratePlacement(int crateId, int baseHundredths, int topHundredths) {

    public CratePlacement {
        if (topHundredths <= baseHundredths) {
            throw new IllegalArgumentException("A crate occupies a positive height");
        }
    }

    public int heightHundredths() {
        return topHundredths - baseHundredths;
    }

    /** The first world cell this crate reaches into, counting from the root at zero. */
    public int firstCell() {
        return baseHundredths / CrateStackLayout.CELL_HUNDREDTHS;
    }

    /**
     * The last world cell this crate reaches into.
     *
     * <p>Measured one hundredth below the top so a crate finishing exactly on a boundary belongs to
     * the cell it filled, not to the empty one it merely touches.
     */
    public int lastCell() {
        return (topHundredths - 1) / CrateStackLayout.CELL_HUNDREDTHS;
    }

    public boolean occupiesCell(int cell) {
        return cell >= firstCell() && cell <= lastCell();
    }

    /** Whether this crate is cut by a cell boundary, and so is drawn from two cells. */
    public boolean crossesCellBoundary() {
        return firstCell() != lastCell();
    }

    /**
     * Where this crate begins relative to one cell's own floor.
     *
     * <p>Negative when the crate started in a cell below, which is precisely the case a renderer or a
     * shape builder has to handle rather than discover.
     */
    public int localBaseHundredths(int cell) {
        return baseHundredths - cell * CrateStackLayout.CELL_HUNDREDTHS;
    }

    public double baseVoxels() {
        return baseHundredths / (double) CrateStackLayout.HUNDREDTHS_PER_VOXEL;
    }

    public double topVoxels() {
        return topHundredths / (double) CrateStackLayout.HUNDREDTHS_PER_VOXEL;
    }
}
