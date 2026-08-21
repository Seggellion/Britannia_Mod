package com.seggellion.britannia_mod.resource.shape;

/**
 * Deterministic per-cell randomness for geological planners.
 *
 * <h2>Why per-cell and not a stream</h2>
 * The legacy shapes drew from {@code ServerLevel#getRandom()} in sequence, which made their output
 * depend on how many times anything else on the server had drawn first. Seeding a stream instead
 * would fix reproducibility but not <em>order independence</em>: a sequential stream only produces
 * the same deposit if every cell is visited in the same order, so slicing a deposit by chunk would
 * change it.
 *
 * <p>Hashing the cell's own coordinates with the deposit seed removes both problems at once. Each
 * candidate cell's fate is a pure function of {@code (seed, salt, x, y, z)}, so a planner may
 * evaluate any subset of cells, in any order, and get the same answer. Chunk slicing is then a
 * filter rather than a re-roll, which is what lets a deposit cross a chunk border without either
 * chunk needing to know about the other.
 *
 * <p>The {@code salt} separates independent decisions about the same cell — density, gaps, edge
 * trimming — so they do not correlate with one another.
 *
 * <h2>Stability</h2>
 * The mixing function is written out rather than delegated, because the output of these planners is
 * a persistent world feature: a library changing its algorithm between versions would silently
 * change everybody's deposits. This is the SplitMix64 finaliser, which is fixed, well distributed,
 * and cheap.
 */
public final class ShapeNoise {

    private ShapeNoise() {
    }

    /** SplitMix64's finalising mix. Fixed by definition, so results never drift. */
    private static long mix(long value) {
        value ^= (value >>> 30);
        value *= 0xBF58476D1CE4E5B9L;
        value ^= (value >>> 27);
        value *= 0x94D049BB133111EBL;
        value ^= (value >>> 31);
        return value;
    }

    /** A 64-bit hash of one cell under one decision. */
    public static long hash(long seed, int salt, int x, int y, int z) {
        long value = seed;
        value = mix(value ^ (salt * 0x9E3779B97F4A7C15L));
        value = mix(value ^ (x * 0xC2B2AE3D27D4EB4FL));
        value = mix(value ^ (y * 0x165667B19E3779F9L));
        value = mix(value ^ (z * 0x27D4EB2F165667C5L));
        return value;
    }

    /** That hash as a float in {@code [0, 1)}. */
    public static float unit(long seed, int salt, int x, int y, int z) {
        // Top 24 bits over 2^24: exactly representable, uniform, and never reaches 1.0f.
        return (hash(seed, salt, x, y, z) >>> 40) / (float) (1 << 24);
    }

    /** That hash as an int in {@code [0, bound)}. */
    public static int below(long seed, int salt, int x, int y, int z, int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive, was " + bound);
        }
        return (int) Long.remainderUnsigned(hash(seed, salt, x, y, z) >>> 1, bound);
    }

    /**
     * A seed for one sub-part of a deposit — a tendril, a walk step — derived from the deposit
     * seed rather than from a running counter, so sub-parts stay independent of each other.
     */
    public static long derive(long seed, int salt, int index) {
        return mix(seed ^ mix((long) salt * 0x9E3779B97F4A7C15L + index));
    }
}
