package com.seggellion.britannia_mod.resource.shape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A planned deposit: which cells it occupies, and the box they lie in.
 *
 * <p>Immutable, unique and canonically ordered. Uniqueness is structural — the plan is built from a
 * {@link TreeSet}, so a planner cannot report the same cell twice however its algorithm arrives
 * there. That matters beyond tidiness: every legacy shape incremented its placement counter once
 * per {@code setBlock} call rather than once per distinct cell, so the numbers they reported were
 * inflated by however often their random walks crossed themselves.
 */
public record ShapePlan(List<ShapeOffset> offsets, ShapeBounds bounds) {

    /** The most cells any single plan may contain, whatever its configuration asks for. */
    public static final int MAX_CELLS = 20_000;

    public ShapePlan {
        offsets = List.copyOf(offsets);
    }

    /**
     * Build a plan from however a planner accumulated its cells.
     *
     * @param declared the bounds the planner promised before it planned; every cell must be inside
     */
    public static ShapePlan of(Collection<ShapeOffset> cells, ShapeBounds declared) {
        Set<ShapeOffset> unique = new TreeSet<>(cells);
        if (unique.size() > MAX_CELLS) {
            throw new IllegalArgumentException("A single deposit may not plan more than "
                    + MAX_CELLS + " cells, this one planned " + unique.size()
                    + "; narrow the configured radius");
        }
        for (ShapeOffset offset : unique) {
            if (!declared.contains(offset)) {
                throw new IllegalStateException(
                        "Planner produced " + offset + " outside its declared bounds " + declared);
            }
        }
        return new ShapePlan(new ArrayList<>(unique), declared);
    }

    public int count() {
        return offsets.size();
    }

    public boolean isEmpty() {
        return offsets.isEmpty();
    }
}
