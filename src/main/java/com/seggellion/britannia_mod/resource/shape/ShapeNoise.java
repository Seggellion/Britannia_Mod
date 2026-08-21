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
     * Smooth, low-frequency value noise over the horizontal plane, in {@code [0, 1]}.
     *
     * <h2>Why the per-cell hash is not enough on its own</h2>
     * {@link #unit} is white noise: neighbouring cells are uncorrelated, which is exactly right for
     * deciding whether one cell has a gap in it and exactly wrong for deciding where the top of a
     * bed lies. A surface built from white noise is not a surface, it is static. A sedimentary bed
     * needs its rim and its thickness to <em>drift</em> — smoothly, over tens of blocks — which is
     * what makes it read as geology rather than as scatter.
     *
     * <p>So the same hash is sampled on a coarse lattice of spacing {@code scale} and interpolated
     * between the four corners with a smoothstep, which is ordinary value noise. It keeps every
     * property the planners rely on: pure, order-independent, and identical for identical inputs,
     * because it is still only ever a function of the lattice coordinates.
     *
     * @param scale lattice spacing in blocks; larger is smoother and lower frequency
     */
    public static double smooth2(long seed, int salt, int x, int z, double scale) {
        if (!(scale > 0.0)) {
            throw new IllegalArgumentException("noise scale must be positive, was " + scale);
        }
        double sx = x / scale;
        double sz = z / scale;
        int x0 = (int) Math.floor(sx);
        int z0 = (int) Math.floor(sz);
        double fx = smoothstep(sx - x0);
        double fz = smoothstep(sz - z0);

        double n00 = unit(seed, salt, x0, 0, z0);
        double n10 = unit(seed, salt, x0 + 1, 0, z0);
        double n01 = unit(seed, salt, x0, 0, z0 + 1);
        double n11 = unit(seed, salt, x0 + 1, 0, z0 + 1);

        double top = n00 + (n10 - n00) * fx;
        double bottom = n01 + (n11 - n01) * fx;
        return top + (bottom - top) * fz;
    }

    /** The same, centred on zero: {@code [-1, 1]}. */
    public static double smoothSigned2(long seed, int salt, int x, int z, double scale) {
        return smooth2(seed, salt, x, z, scale) * 2.0 - 1.0;
    }

    /** Hermite ease, so the lattice corners do not show as creases. */
    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * A seed for one sub-part of a deposit — a tendril, a walk step — derived from the deposit
     * seed rather than from a running counter, so sub-parts stay independent of each other.
     */
    public static long derive(long seed, int salt, int index) {
        return mix(seed ^ mix((long) salt * 0x9E3779B97F4A7C15L + index));
    }
}
