package com.seggellion.britannia_mod.resource.placement;

import com.seggellion.britannia_mod.mining.MiningProvenance;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The only place a geological resource is written into the world.
 *
 * <h2>Why one boundary</h2>
 * Before milestone 3 there were six. Each legacy shape called {@code ServerLevel#setBlock} itself,
 * and each had its own idea of what it was allowed to overwrite: {@code ClusterVein},
 * {@code SnakeVein} and {@code GeodeVein} checked nothing at all and would take bedrock, water,
 * lava or a chest; {@code VerticalVein} checked only for bedrock; {@code LayeredVein} refused
 * everything that was not air in two phases and accepted everything that was not air in a third;
 * {@code VerticalLayeredVein} accepted only air, which is why silver could not generate in rock.
 * Six opinions, none of them the same, all of them inside geometry that had no business holding
 * one.
 *
 * <p>There is now one opinion, in one place, and the shapes cannot reach past it. Every rule below
 * is checked for every candidate cell, in this order, before anything is written.
 *
 * <h2>The policy</h2>
 * <ol>
 *   <li><b>Build height.</b> Outside the level's range, {@code setBlock} silently does nothing, and
 *       the legacy shapes counted those as placements anyway.</li>
 *   <li><b>Already correct.</b> A cell already holding this resource's block is left alone and
 *       reported separately. This is what makes re-running a placement a no-op rather than a
 *       rewrite.</li>
 *   <li><b>Fluid.</b> Never overwrite water or lava. The same rule milestone 1 established for
 *       restoration, for the same reason: the resource system does not get to edit the world's
 *       water.</li>
 *   <li><b>Block entity.</b> Never overwrite a container or anything else carrying its own state.</li>
 *   <li><b>Indestructible.</b> Bedrock, barriers, and anything else with a negative hardness.</li>
 *   <li><b>Player construction.</b> {@link MiningProvenance} already knows which catalogued blocks
 *       a player placed; a deposit does not overwrite somebody's wall.</li>
 *   <li><b>Another managed resource.</b> An ore or a deposit bed belonging to a different resource
 *       is left where it is. STONE-family resources are excluded from this rule, because stone,
 *       granite and deepslate are terrain as well as resources — they are the host rock, and
 *       refusing to replace them would mean refusing to place anything at all. That is the same
 *       family boundary the explosion policy drew at milestone 1.</li>
 *   <li><b>Host rock.</b> Finally, the cell must be in the resource's configured host tag. Air is
 *       not a host, which is the correction to the two shapes that used to place into it.</li>
 * </ol>
 *
 * <h2>Bounded work</h2>
 * A run writes at most {@code budget} blocks and reports what is left. Because the plan is
 * deterministic and a correct cell is a no-op, running again resumes rather than repeats — so a
 * large deposit can be completed in bounded slices without any state being carried between them.
 */
public final class MaterializationService {

    /** Blocks one invocation may write. Chosen so a full deposit lands well inside one tick. */
    public static final int DEFAULT_BUDGET = 4_096;

    /**
     * The flags every managed write uses, and the reason they are not negotiable.
     *
     * <p>{@code UPDATE_CLIENTS} sends the change to players without asking neighbouring blocks to
     * react to it. That is the obvious half and it is not the important half.
     *
     * <p>{@code UPDATE_KNOWN_SHAPE} is what keeps this safe to call while a chunk is still being
     * promoted to full status. Without it, {@code Level#markAndNotifyBlock} calls
     * {@code updateNeighbourShapes}, which reads all six neighbours of every cell written. A cell on
     * a chunk border therefore reads a block in the next chunk, and reading a block in a chunk that
     * is not loaded asks the chunk source to produce it. During {@code ChunkEvent.Load} the thread
     * making that request is the same server thread that would have to generate the answer, so it
     * parks in {@code ServerChunkCache$MainThreadExecutor.managedBlock} waiting for itself: the
     * server stops with no exception and no crash report, mid "Preparing spawn area".
     *
     * <p>Flag 2 alone suppresses neighbour <em>block</em> updates. Only flag 16 suppresses neighbour
     * <em>shape</em> updates. Bulk terrain writes want both.
     */
    public static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private MaterializationService() {
    }

    /** Why a candidate cell was not written. */
    public enum Rejection {
        OUTSIDE_BUILD_HEIGHT,
        ALREADY_PRESENT,
        FLUID,
        BLOCK_ENTITY,
        INDESTRUCTIBLE,
        PLAYER_PLACED,
        OTHER_MANAGED_RESOURCE,
        NOT_A_HOST
    }

    /** What one materialisation run did. */
    public record Result(int placed, int remaining, Map<Rejection, Integer> rejections) {

        public Result {
            rejections = Map.copyOf(rejections);
        }

        public int rejected(Rejection reason) {
            return rejections.getOrDefault(reason, 0);
        }

        public int totalRejected() {
            return rejections.values().stream().mapToInt(Integer::intValue).sum();
        }

        /** Whether the budget ran out before the plan did. */
        public boolean truncated() {
            return remaining > 0;
        }

        /** An operator-facing summary of everything that was refused, most common first. */
        public String describeRejections() {
            return rejections.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .sorted(Map.Entry.<Rejection, Integer>comparingByValue().reversed())
                    .map(entry -> entry.getValue() + " " + entry.getKey().name().toLowerCase(java.util.Locale.ROOT))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("none");
        }
    }

    /** Materialise a whole deposit, up to the default budget. */
    public static Result materialize(ServerLevel level, PlannedDeposit deposit) {
        return materialize(level, deposit, deposit.positions(), DEFAULT_BUDGET);
    }

    /**
     * Materialise only the part of a deposit that lies in one chunk.
     *
     * <p>Reads and writes nothing outside that chunk, so a deposit spanning several does not oblige
     * any of the others to be loaded.
     */
    public static Result materializeChunk(
            ServerLevel level, PlannedDeposit deposit, net.minecraft.world.level.ChunkPos chunk) {
        return materialize(level, deposit, deposit.positionsIn(chunk), DEFAULT_BUDGET);
    }

    /** The one write path. Every rule is applied here and nowhere else. */
    public static Result materialize(
            ServerLevel level, PlannedDeposit deposit, List<BlockPos> candidates, int budget) {

        ResourceDefinition resource = deposit.resource();
        ResourceDefinition.Generation generation = resource.generation().orElseThrow();
        Block block = Resources.block(generation.blockId());
        BlockState placed = block.defaultBlockState();
        TagKey<Block> hostTag = hostTag(generation);

        Map<Rejection, Integer> rejections = new EnumMap<>(Rejection.class);
        int written = 0;
        int index = 0;

        for (BlockPos pos : candidates) {
            if (written >= budget) break;
            index++;

            Rejection rejection = evaluate(level, pos, block, hostTag);
            if (rejection != null) {
                rejections.merge(rejection, 1, Integer::sum);
                continue;
            }
            level.setBlock(pos, placed, WRITE_FLAGS);
            written++;
        }
        return new Result(written, Math.max(0, candidates.size() - index), rejections);
    }

    /** The policy, as a pure decision over one cell. Null means "write it". */
    private static Rejection evaluate(
            ServerLevel level, BlockPos pos, Block resourceBlock, TagKey<Block> hostTag) {

        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return Rejection.OUTSIDE_BUILD_HEIGHT;
        }
        BlockState current = level.getBlockState(pos);

        if (current.is(resourceBlock)) {
            return Rejection.ALREADY_PRESENT;
        }
        if (!level.getFluidState(pos).isEmpty()) {
            return Rejection.FLUID;
        }
        if (level.getBlockEntity(pos) != null) {
            return Rejection.BLOCK_ENTITY;
        }
        if (current.getDestroySpeed(level, pos) < 0.0f) {
            return Rejection.INDESTRUCTIBLE;
        }
        if (MiningProvenance.isPlayerPlaced(level, pos)) {
            return Rejection.PLAYER_PLACED;
        }
        boolean otherManagedResource = Resources.resolve(current)
                .map(definition -> definition.family() != ResourceDefinition.Family.STONE)
                .orElse(false);
        if (otherManagedResource) {
            return Rejection.OTHER_MANAGED_RESOURCE;
        }
        if (!current.is(hostTag)) {
            return Rejection.NOT_A_HOST;
        }
        return null;
    }

    private static TagKey<Block> hostTag(ResourceDefinition.Generation generation) {
        return BlockTags.create(ResourceLocation.parse(generation.hostTag()));
    }
}
