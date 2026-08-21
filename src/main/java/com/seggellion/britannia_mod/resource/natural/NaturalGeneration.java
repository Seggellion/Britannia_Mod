package com.seggellion.britannia_mod.resource.natural;

import com.seggellion.britannia_mod.resource.shape.ShapeTuning;

import java.util.Objects;

/**
 * How a resource occurs naturally in the world, as data.
 *
 * <h2>Why this is a separate block rather than more fields on {@code Generation}</h2>
 * {@code Generation} answers "if somebody places one of these, what does it look like" — the shape,
 * the block, the size range, what it may replace. Every resource that can be placed at all has it,
 * including the twenty-odd that Rails sites by hand. Occurring naturally is a different question
 * with a different answer, and most resources do not answer it: a curated agapite vein exists
 * because a curator said so, and no grid or biome policy applies to it.
 *
 * <p>Keeping the two apart means a resource opts into natural generation by gaining a block of
 * configuration, rather than by every resource carrying eight fields that only one of them uses.
 *
 * <h2>The distribution model</h2>
 * The world is divided into square <b>owner cells</b> of {@link #cellChunks} chunks. Each cell
 * either contains one deposit or none, decided by hashing the cell's coordinates with the world
 * seed and this resource's {@link #salt}. That gives spacing without any state: two deposits can
 * never be closer than the cell geometry allows, no deposit depends on generation order, and the
 * whole distribution can be recomputed from the seed at any time.
 *
 * <p>{@link #chance} thins the grid further, so a resource can be regionally uncommon without
 * having to use a cell so large that deposits become impossible to find.
 *
 * @param dimensionId the one dimension this resource occurs in
 * @param biomeTag    biome tag whose members may host it, as {@code namespace:path}
 * @param cellChunks  owner-cell edge length in chunks
 * @param chance      probability that an owner cell produces a candidate at all, in {@code (0, 1]}
 * @param minRadius   smallest horizontal radius a natural deposit may take
 * @param maxRadius   largest horizontal radius a natural deposit may take
 * @param depth       how far below the surface the bed's datum plane sits
 * @param depthJitter deterministic variation added to {@link #depth}
 * @param minY        floor; a candidate whose origin would fall below this is not produced
 * @param maxY        ceiling, likewise
 * @param salt        separates this resource's distribution from every other resource's
 * @param tuning      shape knobs handed to the planner
 */
public record NaturalGeneration(
        String dimensionId,
        String biomeTag,
        int cellChunks,
        double chance,
        int minRadius,
        int maxRadius,
        int depth,
        int depthJitter,
        int minY,
        int maxY,
        int salt,
        ShapeTuning tuning) {

    /**
     * The largest owner cell worth allowing.
     *
     * <p>Beyond this a deposit is so rare that a player could cross a continent without meeting
     * one, and — more practically — the search window a chunk has to consider stays trivially small
     * only while cells are much larger than deposits, which this guarantees from the other side.
     */
    public static final int MAX_CELL_CHUNKS = 256;

    public NaturalGeneration {
        Objects.requireNonNull(dimensionId, "natural generation needs a dimension");
        Objects.requireNonNull(biomeTag, "natural generation needs a biome tag");
        Objects.requireNonNull(tuning, "natural generation needs shape tuning");
        if (cellChunks < 1 || cellChunks > MAX_CELL_CHUNKS) {
            throw new IllegalStateException("natural cell size must be between 1 and "
                    + MAX_CELL_CHUNKS + " chunks, was " + cellChunks);
        }
        if (!(chance > 0.0) || chance > 1.0) {
            throw new IllegalStateException("natural occurrence chance must be in (0, 1], was " + chance);
        }
        if (minRadius < 1 || maxRadius < minRadius) {
            throw new IllegalStateException("natural radius range " + minRadius + ".." + maxRadius
                    + " is not a range");
        }
        if (depth < 0) {
            throw new IllegalStateException("natural depth must not be negative, was " + depth);
        }
        if (depthJitter < 0) {
            throw new IllegalStateException("natural depth jitter must not be negative, was " + depthJitter);
        }
        if (minY > maxY) {
            throw new IllegalStateException("natural altitude band " + minY + ".." + maxY
                    + " is inverted");
        }
    }

    /** Owner-cell edge length in blocks. */
    public int cellBlocks() {
        return cellChunks * 16;
    }

    /**
     * How far a deposit's cells can reach from its origin, horizontally.
     *
     * <p>Used to decide which owner cells a chunk has to consider. Deliberately the configured
     * maximum rather than the planned radius: a chunk must know which deposits could reach it
     * before it knows how big any of them turned out to be.
     */
    public int horizontalReach() {
        return maxRadius;
    }
}
