package com.seggellion.britannia_mod.resource.natural;

import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.deposit.DepositRegistrar;
import com.seggellion.britannia_mod.resource.deposit.DepositSource;
import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Turning a deterministic candidate into a deposit in the ground, on the server thread.
 *
 * <h2>Where this runs, and why that is the safe place</h2>
 * {@code ChunkEvent.Load} is posted from {@code ChunkStatusTasks.full}, whose task is submitted
 * through {@code mainThreadMailBox()} — so it arrives on the server thread, not on a world-gen
 * worker. That matters because everything below it touches state that has no business being reached
 * from a generation thread: the deposit ledger is {@code SavedData}, and materialisation writes
 * blocks.
 *
 * <p>{@code isNewChunk()} is the other half. NeoForge reports it as
 * {@code !(protochunk instanceof ImposterProtoChunk)}, and a chunk read from disk at full status is
 * always reconstructed as an {@code ImposterProtoChunk}. So the flag means precisely "this chunk has
 * just finished generating", which is exactly the population this milestone is allowed to touch.
 * An existing world is never revisited, and no marker or per-chunk bookkeeping is needed to
 * guarantee it.
 *
 * <h2>What is asked of the world, and what is not</h2>
 * Two questions need the world, and both are answered without loading anything:
 * <ul>
 *   <li><b>Biome</b> comes from the chunk generator's {@code BiomeSource}, which is noise — it
 *       answers for any coordinate whether or not that coordinate's chunk exists.</li>
 *   <li><b>Surface height</b> comes from {@code ChunkGenerator#getBaseHeight}, which is likewise a
 *       noise query rather than a chunk read.</li>
 * </ul>
 *
 * <p>Both are asked at the deposit's <em>origin</em>, never at the current chunk, which is what
 * makes the answer the same from every chunk the deposit reaches. If the biome were sampled per
 * chunk, a bed straddling a desert edge would exist according to one chunk and not according to its
 * neighbour.
 *
 * <p>Writes are confined to the loading chunk's own cells, and use
 * {@link com.seggellion.britannia_mod.resource.placement.MaterializationService#WRITE_FLAGS}, so
 * neither a neighbour update nor a neighbour shape update escapes into an adjacent chunk and
 * nothing else is obliged to load. Confining the write positions is necessary but not sufficient:
 * a write inside the chunk still reads across the border unless the shape update is suppressed
 * too, and that read is what deadlocks the server thread against itself.
 */
public final class NaturalDepositService {

    /** A lens is not directional, but the platform wants a rotation, so it gets the neutral one. */
    private static final ShapeRotation ROTATION = ShapeRotation.XZ;

    private NaturalDepositService() {
    }

    /** What one chunk's pass did, for tests and diagnostics to assert on rather than infer. */
    public record ChunkOutcome(
            int cellsConsidered,
            int candidates,
            int acceptedDeposits,
            int rejectedBiome,
            int rejectedAltitude,
            int rejectedLedger,
            int placed,
            int hostRejected) {

        static ChunkOutcome empty() {
            return new ChunkOutcome(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /** Every resource that occurs naturally in this level's dimension. */
    public static List<ResourceDefinition> naturalResourcesIn(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        List<ResourceDefinition> found = new ArrayList<>();
        for (ResourceDefinition resource : ResourceCatalog.instance().all()) {
            resource.natural()
                    .filter(natural -> natural.dimensionId().equals(dimension))
                    .ifPresent(ignored -> found.add(resource));
        }
        return found;
    }

    /**
     * Populate one freshly generated chunk.
     *
     * <p>Deliberately free of any mention of a particular resource. A second resource joins this
     * by gaining a {@code natural} block in {@code resources.json} and nothing else.
     */
    public static ChunkOutcome populate(ServerLevel level, ChunkPos chunk) {
        List<ResourceDefinition> resources = naturalResourcesIn(level);
        if (resources.isEmpty()) {
            return ChunkOutcome.empty();
        }

        long worldSeed = level.getSeed();
        String dimension = level.dimension().location().toString();
        DepositLedger ledger = DepositLedger.get(level);

        int cellsConsidered = 0;
        int candidates = 0;
        int accepted = 0;
        int rejectedBiome = 0;
        int rejectedAltitude = 0;
        int rejectedLedger = 0;
        int placed = 0;
        int hostRejected = 0;

        for (ResourceDefinition resource : resources) {
            NaturalGeneration natural = resource.natural().orElseThrow();
            cellsConsidered += NaturalDepositSelector.cellsConsideredPerChunk(natural);

            for (NaturalDepositSelector.Candidate candidate :
                    NaturalDepositSelector.candidatesReaching(
                            worldSeed, resource, natural, chunk.x, chunk.z)) {
                candidates++;

                // Biome first, then altitude. The order does not change which deposits exist -- both
                // must pass -- but it changes what the counters mean: asked this way round,
                // "rejected by altitude" is a candidate in the right biome at the wrong height,
                // which is a tuning signal. The other way round it was mostly ocean floor.
                int surface = surfaceHeight(level, candidate);
                if (!biomeAllows(level, natural, candidate, surface)) {
                    rejectedBiome++;
                    continue;
                }
                Integer originY = originHeight(natural, candidate, surface);
                if (originY == null) {
                    rejectedAltitude++;
                    continue;
                }
                BlockPos origin = new BlockPos(candidate.originX(), originY, candidate.originZ());

                PlannedDeposit planned = PlacementPlanner.plan(
                        resource, dimension, origin, candidate.radius(), ROTATION,
                        candidate.plannerSeed());

                DepositInstance described = DepositRegistrar.describe(
                        planned, candidate.instanceId(), DepositSource.NATURAL,
                        naturalIdentity(natural, candidate));
                DepositLedger.Registration registration = ledger.register(described);
                if (!registration.mayMaterialize()) {
                    // A conflict or a revision mismatch. Milestone 4's rule stands: the existing
                    // deposit keeps its identity and nothing is regenerated over it.
                    rejectedLedger++;
                    continue;
                }
                accepted++;

                MaterializationService.Result result =
                        MaterializationService.materializeChunk(level, planned, chunk);
                placed += result.placed();
                hostRejected += result.totalRejected();
            }
        }
        return new ChunkOutcome(cellsConsidered, candidates, accepted,
                rejectedBiome, rejectedAltitude, rejectedLedger, placed, hostRejected);
    }

    /**
     * Where the bed's datum plane sits, or {@code null} when this cell has no room for it.
     *
     * <p>Surface-relative rather than absolute. A fixed altitude band would put beds in stone under
     * high ground and in the air over low, and the host policy would then reject nearly everything —
     * a deposit that exists in the ledger and nowhere in the world. Hanging the bed a configured
     * depth below the local surface puts it inside the sediment that is actually there.
     */
    private static Integer originHeight(
            NaturalGeneration natural, NaturalDepositSelector.Candidate candidate, int surface) {

        int y = surface - candidate.depth();
        if (y < natural.minY() || y > natural.maxY()) {
            return null;
        }
        return y;
    }

    /** The local surface, from the generator's own noise rather than from any chunk. */
    private static int surfaceHeight(ServerLevel level, NaturalDepositSelector.Candidate candidate) {
        return level.getChunkSource().getGenerator().getBaseHeight(
                candidate.originX(), candidate.originZ(),
                Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState());
    }

    /** Whether the biome at the deposit's origin is one this resource is allowed to occur in. */
    private static boolean biomeAllows(ServerLevel level, NaturalGeneration natural,
                                       NaturalDepositSelector.Candidate candidate, int surface) {
        TagKey<Biome> tag = TagKey.create(
                Registries.BIOME, ResourceLocation.parse(natural.biomeTag()));
        Holder<Biome> biome = level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(candidate.originX()),
                QuartPos.fromBlock(surface),
                QuartPos.fromBlock(candidate.originZ()),
                level.getChunkSource().randomState().sampler());
        return biome.is(tag);
    }

    /**
     * The human-readable side of the identity, stored beside the hash.
     *
     * <p>Deliberately the same components the hash is built from, so a deposit in the ledger can be
     * traced back to the cell that produced it without recomputing anything.
     */
    private static String naturalIdentity(
            NaturalGeneration natural, NaturalDepositSelector.Candidate candidate) {
        return "natural|" + natural.dimensionId() + '|' + candidate.resourceId()
                + "|cell " + candidate.cellX() + ',' + candidate.cellZ()
                + "|salt " + natural.salt();
    }
}
