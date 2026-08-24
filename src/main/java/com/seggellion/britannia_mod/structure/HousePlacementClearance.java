package com.seggellion.britannia_mod.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * How much room a house needs, and what "enough room" is actually made of.
 *
 * <h2>What the old rule asked for</h2>
 *
 * <p>Placement validated a rectangle grown outwards from the footprint and then demanded two
 * things of every column in it: {@code grass_block} or {@code sand} one block below the base, and
 * air or a replaceable block all the way up past the roof. The rectangle was described in the code
 * as a "1-block buffer", and was not symmetric — {@code structureBox.maxX} is the exclusive edge,
 * already footprint+1, and the loop added another — so it reached one block out on the north and
 * west sides and two on the south and east, plus one layer above the roof:
 *
 * <pre>
 *   small house  9x9 footprint  ->  12 x 12 = 144 columns of unbroken flat grass, 12x9x12 clear
 *   castle      34x35 footprint ->  37 x 38 = 1406 columns of unbroken flat grass, 37x21x38 clear
 * </pre>
 *
 * <p>The skirt is what made placement feel impossible. It is not the house's own ground: a fence
 * post, a sapling, a path block or a single step of slope one block outside the wall refused the
 * whole placement, and nothing told the player which cell was at fault. And it did not even buy
 * what it looked like it was buying — two houses could still be placed overlapping, because
 * nothing ever asked the region registry, and a neighbouring house's wall only blocked placement
 * incidentally, by being a solid block in the skirt.
 *
 * <h2>What it asks for now</h2>
 *
 * <p>Three separate questions, each about something real, and none of them padding:
 *
 * <ol>
 *   <li><b>Foundation</b> — every column of the actual footprint sits on grass or sand. This is the
 *       rule the message has always described, applied to the ground the house actually stands on.</li>
 *   <li><b>Volume</b> — every cell the structure will write into is air or replaceable. This is what
 *       stops a house being driven into a hillside, a tree, or a neighbour's wall.</li>
 *   <li><b>Overlap</b> — the new footprint must not intersect a registered house region. This is
 *       the check that actually prevents overlapping houses, and it did not exist before.</li>
 * </ol>
 *
 * <p>Boxes that merely touch do not intersect ({@link AABB#intersects} compares strictly), so two
 * houses may still be built wall to wall — which is how a town is built, and which
 * {@code HouseUtil} already assumes elsewhere.
 *
 * <p>Clearance is therefore derived from the footprint rather than being a constant added to it, so
 * a castle needs castle-sized ground and a cottage needs cottage-sized ground, and neither needs a
 * ring of untouched wilderness around it.
 */
public final class HousePlacementClearance {

    private HousePlacementClearance() {
    }

    /**
     * The columns whose ground must be a valid foundation: exactly the footprint.
     *
     * @return the inclusive block range, as {@code [minX, maxX, minZ, maxZ]}
     */
    public static int[] foundationColumns(AABB structureBox) {
        return new int[] {
                Mth.floor(structureBox.minX),
                Mth.floor(structureBox.maxX) - 1,
                Mth.floor(structureBox.minZ),
                Mth.floor(structureBox.maxZ) - 1
        };
    }

    /** The y of the ground the house stands on: one below the structure's own base. */
    public static int foundationY(AABB structureBox) {
        return Mth.floor(structureBox.minY) - 1;
    }

    /**
     * The cells the structure will write into: exactly the footprint volume.
     *
     * @return the inclusive block range, as {@code [minX, maxX, minY, maxY, minZ, maxZ]}
     */
    public static int[] occupiedVolume(AABB structureBox) {
        return new int[] {
                Mth.floor(structureBox.minX),
                Mth.floor(structureBox.maxX) - 1,
                Mth.floor(structureBox.minY),
                Mth.floor(structureBox.maxY) - 1,
                Mth.floor(structureBox.minZ),
                Mth.floor(structureBox.maxZ) - 1
        };
    }

    /**
     * How many ground columns this box requires. Diagnostics and tests; nothing branches on it.
     *
     * <p>Kept because "how much land does a house need" is the whole subject of this class and it
     * should be answerable without re-deriving the loop bounds.
     */
    public static int foundationColumnCount(AABB structureBox) {
        int[] columns = foundationColumns(structureBox);
        return (columns[1] - columns[0] + 1) * (columns[3] - columns[2] + 1);
    }

    /**
     * The registered house this footprint would sit inside, or {@code null} if the ground is free.
     *
     * <p>Asked of the region registry rather than of the blocks, so it is true even for a house
     * whose chunks are not loaded and even where the two buildings would not physically touch —
     * a basement dug under a neighbour is still their house.
     *
     * <p>Only chunks the footprint spans are consulted, which is the same indexing every other
     * region lookup uses.
     */
    public static StructureRecord overlappingHouse(ResourceKey<Level> dimension, AABB structureBox) {
        int minChunkX = SectionPos.blockToSectionCoord(Mth.floor(structureBox.minX));
        int maxChunkX = SectionPos.blockToSectionCoord(Mth.floor(structureBox.maxX));
        int minChunkZ = SectionPos.blockToSectionCoord(Mth.floor(structureBox.minZ));
        int maxChunkZ = SectionPos.blockToSectionCoord(Mth.floor(structureBox.maxZ));

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                List<StructureRecord> records =
                        StructureRegionManager.getStructuresInChunk(dimension, chunkX, chunkZ);
                for (StructureRecord record : records) {
                    AABB existing = record.getStructureBox();
                    if (existing != null && existing.intersects(structureBox)) {
                        return record;
                    }
                }
            }
        }
        return null;
    }

    /** Where the failure is, so the player can be told rather than left guessing. */
    public record Refusal(String reason, BlockPos where) {
    }
}
