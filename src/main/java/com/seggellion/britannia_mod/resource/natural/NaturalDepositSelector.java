package com.seggellion.britannia_mod.resource.natural;

import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.deposit.DepositIdentity;
import com.seggellion.britannia_mod.resource.shape.ShapeNoise;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Which natural deposits exist, and where — decided from the world seed alone.
 *
 * <h2>Pure on purpose</h2>
 * Nothing here touches a level, a chunk, a biome or a block. Given a world seed and an owner cell,
 * it answers the same thing forever: whether a deposit belongs to that cell, where in the cell it
 * sits, and how big it is. Everything that needs the world — is this biome allowed, where is the
 * surface, is that block an acceptable host — is asked later, on the server thread, by
 * {@code NaturalDepositService}.
 *
 * <p>That split is what makes the whole design safe and testable at once. The distribution can be
 * analysed statistically over millions of cells in a unit test with no server at all, and the part
 * that runs during world generation can be small enough to reason about.
 *
 * <h2>Why owner cells rather than per-chunk rolls</h2>
 * A per-chunk roll gives no spacing control and, worse, no stable answer for a deposit that spans
 * chunks: each chunk would roll its own. Anchoring a deposit to a cell means every chunk that the
 * deposit reaches derives the identical candidate, because they all derive it from the cell rather
 * than from themselves. That is what lets four chunks agree on one deposit without talking to each
 * other, in any order, across a restart.
 *
 * <p>The cell also feeds {@link DepositIdentity#natural}, so the identity contract milestone 4
 * defined is used as written rather than reinvented here.
 */
public final class NaturalDepositSelector {

    private static final int SALT_OCCURRENCE = 61;
    private static final int SALT_ORIGIN_X = 62;
    private static final int SALT_ORIGIN_Z = 63;
    private static final int SALT_RADIUS = 64;
    private static final int SALT_DEPTH = 65;

    private NaturalDepositSelector() {
    }

    /**
     * A deposit the world would place, before anything about the world has been consulted.
     *
     * @param cellX      owner cell, in cell units
     * @param cellZ      owner cell, in cell units
     * @param originX    world X of the deposit's centre
     * @param originZ    world Z of the deposit's centre
     * @param radius     planned horizontal radius
     * @param depth      how far below the local surface the datum plane should sit
     * @param instanceId the M4 identity this deposit will be registered under
     */
    public record Candidate(
            String resourceId, int cellX, int cellZ,
            int originX, int originZ, int radius, int depth, long instanceId) {

        /** The seed the shape planner draws this deposit's geometry from. */
        public long plannerSeed() {
            return DepositIdentity.plannerSeed(instanceId);
        }
    }

    /**
     * The candidate belonging to one owner cell, if that cell has one.
     *
     * <p>Empty is a real answer and the common one: {@link NaturalGeneration#chance} is what makes
     * a resource regionally uncommon rather than present in every cell.
     */
    public static Optional<Candidate> candidateFor(
            long worldSeed, ResourceDefinition resource, NaturalGeneration natural, int cellX, int cellZ) {

        if (ShapeNoise.unit(worldSeed, SALT_OCCURRENCE + natural.salt(), cellX, 0, cellZ)
                >= natural.chance()) {
            return Optional.empty();
        }

        // Placed in the central half of its own cell. The obvious alternative -- a margin the size
        // of the deposit's reach -- lets two deposits in adjacent cells sit as close as twice that
        // margin, which at a small cell size is barely more than their own diameter. Confining the
        // origin to the middle half guarantees adjacent origins are at least half a cell apart
        // whatever the radius, while still leaving a wide window for them to vary within.
        int cellBlocks = natural.cellBlocks();
        int margin = cellBlocks / 4;
        int usable = Math.max(1, cellBlocks - 2 * margin);

        int baseX = cellX * cellBlocks + margin;
        int baseZ = cellZ * cellBlocks + margin;
        int originX = baseX + ShapeNoise.below(
                worldSeed, SALT_ORIGIN_X + natural.salt(), cellX, 0, cellZ, usable);
        int originZ = baseZ + ShapeNoise.below(
                worldSeed, SALT_ORIGIN_Z + natural.salt(), cellX, 0, cellZ, usable);

        int radiusSpan = natural.maxRadius() - natural.minRadius() + 1;
        int radius = natural.minRadius() + ShapeNoise.below(
                worldSeed, SALT_RADIUS + natural.salt(), cellX, 0, cellZ, radiusSpan);

        int depth = natural.depth();
        if (natural.depthJitter() > 0) {
            depth += ShapeNoise.below(worldSeed, SALT_DEPTH + natural.salt(),
                    cellX, 0, cellZ, natural.depthJitter() * 2 + 1) - natural.depthJitter();
        }

        long instanceId = DepositIdentity.natural(
                worldSeed, natural.dimensionId(), resource.id(), cellX, cellZ, natural.salt());

        return Optional.of(new Candidate(
                resource.id(), cellX, cellZ, originX, originZ, radius, Math.max(0, depth), instanceId));
    }

    /**
     * Every candidate that could reach the given chunk.
     *
     * <p>The search window is derived from the configured reach, not from any deposit's actual
     * size, because a chunk has to know which cells to ask before it knows what is in them. With
     * cells far larger than deposits this is one or two cells in practice, and it is bounded by
     * construction rather than by hope.
     *
     * <p>Reads nothing. A chunk asking this question does not cause any other chunk to load.
     */
    public static List<Candidate> candidatesReaching(
            long worldSeed, ResourceDefinition resource, NaturalGeneration natural,
            int chunkX, int chunkZ) {

        int reach = natural.horizontalReach();
        int minBlockX = chunkX * 16 - reach;
        int maxBlockX = chunkX * 16 + 15 + reach;
        int minBlockZ = chunkZ * 16 - reach;
        int maxBlockZ = chunkZ * 16 + 15 + reach;

        int cellBlocks = natural.cellBlocks();
        int fromCellX = Math.floorDiv(minBlockX, cellBlocks);
        int toCellX = Math.floorDiv(maxBlockX, cellBlocks);
        int fromCellZ = Math.floorDiv(minBlockZ, cellBlocks);
        int toCellZ = Math.floorDiv(maxBlockZ, cellBlocks);

        List<Candidate> found = new ArrayList<>();
        for (int cellX = fromCellX; cellX <= toCellX; cellX++) {
            for (int cellZ = fromCellZ; cellZ <= toCellZ; cellZ++) {
                candidateFor(worldSeed, resource, natural, cellX, cellZ)
                        .filter(candidate -> reaches(candidate, minBlockX, maxBlockX, minBlockZ, maxBlockZ))
                        .ifPresent(found::add);
            }
        }
        return found;
    }

    /** How many owner cells one chunk has to consider, for the performance budget. */
    public static int cellsConsideredPerChunk(NaturalGeneration natural) {
        int reach = natural.horizontalReach();
        int span = 16 + 2 * reach;
        int perAxis = span / natural.cellBlocks() + 2;
        return perAxis * perAxis;
    }

    private static boolean reaches(Candidate candidate, int minX, int maxX, int minZ, int maxZ) {
        return candidate.originX() >= minX && candidate.originX() <= maxX
                && candidate.originZ() >= minZ && candidate.originZ() <= maxZ;
    }
}
