package com.seggellion.britannia_mod.crate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Direction;

/**
 * What one world cell of a column has to draw and collide with.
 *
 * <h2>Why the views share this</h2>
 *
 * <p>The renderer and the shape builder are both asked the same question — "what of this column is
 * inside this cell?" — and they must answer it identically, or a crate ends up drawn where it cannot
 * be touched. So they read one slice rather than each walking the layout themselves.
 *
 * <p>A slice is derived, never stored. The authority is still the crate list on the block entity;
 * this is a projection of it onto one cell, recomputed whenever the column changes.
 */
public record CrateStackSlice(List<Entry> entries) {

    private static final CrateStackSlice EMPTY = new CrateStackSlice(List.of());

    /**
     * One crate's contribution to one cell.
     *
     * @param crateId the crate this draws, which is how a hit is mapped back to one crate
     * @param variant which model to draw
     * @param facing which way to draw it
     * @param offsetHundredths how far to move the model from this cell's own floor; negative when
     *     the crate began in the cell below and is continuing upward through this one
     */
    public record Entry(int crateId, CrateVariant variant, Direction facing, int offsetHundredths) {

        /** The model translation in blocks, which is what a quad or a shape is actually moved by. */
        public double offsetBlocks() {
            return offsetHundredths / (double) (CrateStackLayout.HUNDREDTHS_PER_VOXEL * 16);
        }

        /** Where this crate's art starts within this cell, from the cell floor, in hundredths. */
        public int artBaseHundredths() {
            return offsetHundredths + variant.authoredMinYHundredths();
        }

        /** Where this crate's art ends within this cell, from the cell floor, in hundredths. */
        public int artTopHundredths() {
            return offsetHundredths + variant.authoredMaxYHundredths();
        }
    }

    public CrateStackSlice {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    public static CrateStackSlice empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Projects a column onto one of its cells.
     *
     * <p>Every crate that reaches into the cell appears, including one that started below it — that
     * crate's offset is simply negative, which both the quad translation and the shape clip handle
     * without needing to know it was a special case.
     */
    public static CrateStackSlice of(CrateStackLayout layout, List<LogicalCrate> crates, int cell) {
        List<Entry> entries = new ArrayList<>();
        for (CratePlacement placement : layout.placementsInCell(cell)) {
            LogicalCrate crate = find(crates, placement.crateId());
            if (crate == null) {
                continue;
            }
            entries.add(new Entry(
                    crate.id(),
                    crate.variant(),
                    crate.facing(),
                    crate.variant().renderOffsetHundredths(placement.localBaseHundredths(cell))));
        }
        return entries.isEmpty() ? EMPTY : new CrateStackSlice(Collections.unmodifiableList(entries));
    }

    private static LogicalCrate find(List<LogicalCrate> crates, int crateId) {
        for (LogicalCrate crate : crates) {
            if (crate.id() == crateId) {
                return crate;
            }
        }
        return null;
    }
}
