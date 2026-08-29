package com.seggellion.britannia_mod.crate;

import java.util.Locale;
import java.util.Optional;

/**
 * The kinds of crate a compact column can stack, and the two numbers that decide how.
 *
 * <h2>Height, and why it is an integer</h2>
 *
 * <p>Heights are held in hundredths of a voxel rather than as {@code double}s. Packing decides world
 * occupancy — how many block cells a column claims — and a column whose cell count depends on the
 * last bit of a floating-point sum is a column that can gain or lose a cell between two runs of the
 * same arithmetic. Hundredths are exact for both authored models and keep every comparison integral.
 *
 * <p>Each height is the model's own vertical extent, top minus bottom, because that is the distance
 * one crate raises the next. The small crate is drawn from its local zero, so its extent and its top
 * coincide. The medium crate is drawn from {@code 0.09}, so its extent (11.51) is nine hundredths
 * less than its top (11.60) — which is exactly what makes a column of them touch: crate n's art ends
 * at {@code 11.51n + 11.60} and crate n+1's begins at {@code 11.51(n+1) + 0.09}, the same number.
 * A renderer therefore has to draw each crate at its own authored offset, not at the packed base.
 *
 * <p>The large crate is deliberately absent. It is a 2x2x2 multiblock roughly 19 voxels wide, and
 * folding a footprint that shape into a single-column packer is a separate decision; it stays an
 * ordinary {@code CrateBlock}.
 */
public enum CrateVariant {

    /** {@code small_crate}: one row, model bounds Y 0.00 - 7.15. */
    SMALL(715, 9),

    /** {@code medium_crate}: three rows, model bounds Y 0.09 - 11.60. */
    MEDIUM(1151, 27);

    private final int heightHundredths;
    private final int slotCount;

    CrateVariant(int heightHundredths, int slotCount) {
        this.heightHundredths = heightHundredths;
        this.slotCount = slotCount;
    }

    /** The vertical space one of these occupies in a column, in hundredths of a voxel. */
    public int heightHundredths() {
        return heightHundredths;
    }

    public int slotCount() {
        return slotCount;
    }

    /** The lower-case name this variant is written as in saved data. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The variant a saved name refers to, or empty if it refers to nothing this build knows.
     *
     * <p>Empty rather than a default: guessing a variant would guess an inventory size, and an
     * inventory read at the wrong size is how saved items go missing.
     */
    public static Optional<CrateVariant> byName(String name) {
        for (CrateVariant variant : values()) {
            if (variant.serializedName().equals(name)) {
                return Optional.of(variant);
            }
        }
        return Optional.empty();
    }

    /**
     * The variant a legacy crate of this inventory size becomes when promoted.
     *
     * <p>Keyed on slot count because {@code CrateBlock} already guarantees one of 9, 27 or 54, and it
     * keeps this enum free of any dependency on the block registry — which is what lets the whole
     * packing model be exercised without a world.
     *
     * @return empty for the 54-slot large crate, which compact columns do not carry
     */
    public static Optional<CrateVariant> forSlotCount(int slotCount) {
        for (CrateVariant variant : values()) {
            if (variant.slotCount == slotCount) {
                return Optional.of(variant);
            }
        }
        return Optional.empty();
    }
}
