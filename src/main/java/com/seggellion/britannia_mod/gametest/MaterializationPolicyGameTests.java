package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.mining.MiningProvenance;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * OreVein milestone 3: the one guarded mutation boundary, exercised against a real world.
 *
 * <h2>What this replaces</h2>
 * Six shapes each called {@code setBlock} themselves and each had a different opinion about what
 * they were allowed to overwrite — from "anything at all", which took bedrock, water and chests, to
 * "only air", which is why silver could not generate in rock. There is one opinion now, in one
 * place, and these drive it cell by cell.
 *
 * <p>Each case uses the explicit-candidate overload so it can put exactly one block in the way and
 * name the reason it expects back. A test that only checked "the deposit came out smaller" would
 * pass for the wrong reason.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class MaterializationPolicyGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos CELL = new BlockPos(1, 1, 1);

    private MaterializationPolicyGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** A silver deposit, used only for its block, host tag and definition. */
    private static PlannedDeposit silverDeposit(ServerLevel level, BlockPos origin) {
        ResourceDefinition silver = ResourceCatalog.instance().byPath("silver").orElseThrow();
        return PlacementPlanner.plan(silver, level.dimension().location().toString(),
                origin, 2, ShapeRotation.ZW, 1234L);
    }

    /** Put {@code standing} in the cell, try to write silver into it, and expect {@code reason}. */
    private static void refuses(
            GameTestHelper helper, Block standing, MaterializationService.Rejection reason) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(CELL);
        helper.setBlock(CELL, standing);

        MaterializationService.Result result = MaterializationService.materialize(
                level, silverDeposit(level, absolute), List.of(absolute),
                MaterializationService.DEFAULT_BUDGET);

        check(result.placed() == 0,
                standing.getName().getString() + " must not be overwritten, but "
                        + result.placed() + " cell was written");
        check(result.rejected(reason) == 1,
                standing.getName().getString() + " must be refused as " + reason
                        + ", got: " + result.describeRejections());
        helper.assertBlockPresent(standing, CELL);
    }

    /* ------------------------------------------------------------------ */
    /*  What it refuses                                                    */
    /* ------------------------------------------------------------------ */

    /** The same fluid rule milestone 1 gave restoration: the resource system does not edit water. */
    @GameTest(template = TEMPLATE)
    public static void materialisationRefusesWater(GameTestHelper helper) {
        refuses(helper, Blocks.WATER, MaterializationService.Rejection.FLUID);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void materialisationRefusesLava(GameTestHelper helper) {
        refuses(helper, Blocks.LAVA, MaterializationService.Rejection.FLUID);
        helper.succeed();
    }

    /** {@code ClusterVein}, {@code SnakeVein} and {@code GeodeVein} would all have taken this. */
    @GameTest(template = TEMPLATE)
    public static void materialisationRefusesBedrock(GameTestHelper helper) {
        refuses(helper, Blocks.BEDROCK, MaterializationService.Rejection.INDESTRUCTIBLE);
        helper.succeed();
    }

    /** A container carries state that a vein has no business deleting. */
    @GameTest(template = TEMPLATE)
    public static void materialisationRefusesABlockEntity(GameTestHelper helper) {
        refuses(helper, Blocks.CHEST, MaterializationService.Rejection.BLOCK_ENTITY);
        helper.succeed();
    }

    /** Air is not host rock. Two legacy shapes placed into it exclusively; this one refuses it. */
    @GameTest(template = TEMPLATE)
    public static void materialisationRefusesAir(GameTestHelper helper) {
        refuses(helper, Blocks.AIR, MaterializationService.Rejection.NOT_A_HOST);
        helper.succeed();
    }

    /** Nor is every solid block: a vein grows in stone, not in dirt or oak planks. */
    @GameTest(template = TEMPLATE)
    public static void materialisationRefusesBlocksThatAreNotHostRock(GameTestHelper helper) {
        for (Block notHost : List.of(Blocks.DIRT, Blocks.OAK_PLANKS, Blocks.SAND, Blocks.GLASS)) {
            refuses(helper, notHost, MaterializationService.Rejection.NOT_A_HOST);
        }
        helper.succeed();
    }

    /** Somebody's own wall is construction, and provenance already knows which blocks those are. */
    @GameTest(template = TEMPLATE)
    public static void materialisationRefusesPlayerPlacedBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(CELL);
        helper.setBlock(CELL, Blocks.STONE);
        MiningProvenance.markPlayerPlaced(level, absolute);

        MaterializationService.Result result = MaterializationService.materialize(
                level, silverDeposit(level, absolute), List.of(absolute),
                MaterializationService.DEFAULT_BUDGET);

        check(result.placed() == 0, "a player's own block must not be built over");
        check(result.rejected(MaterializationService.Rejection.PLAYER_PLACED) == 1,
                "expected PLAYER_PLACED, got " + result.describeRejections());
        helper.assertBlockPresent(Blocks.STONE, CELL);

        MiningProvenance.forget(level, absolute);
        helper.succeed();
    }

    /** One deposit does not eat another. Persistent instance identity is milestone 4's; this is not. */
    @GameTest(template = TEMPLATE)
    public static void materialisationRefusesAnotherManagedResource(GameTestHelper helper) {
        refuses(helper, BlockRegistry.VERITE_ORE.get(),
                MaterializationService.Rejection.OTHER_MANAGED_RESOURCE);
        refuses(helper, BlockRegistry.CLAY_DEPOSIT.get(),
                MaterializationService.Rejection.OTHER_MANAGED_RESOURCE);
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  What it accepts                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * Host rock is written, including the stone-family blocks that are themselves resources.
     *
     * <p>Stone, granite and deepslate are catalogued mineables <em>and</em> the rock ore grows in.
     * The "do not overwrite another managed resource" rule therefore excludes the STONE family, or
     * a deposit could never be placed anywhere at all — the same family boundary the explosion
     * policy drew at milestone 1.
     */
    @GameTest(template = TEMPLATE)
    public static void materialisationAcceptsHostRock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(CELL);

        for (Block host : List.of(Blocks.STONE, Blocks.GRANITE, Blocks.ANDESITE,
                Blocks.DIORITE, Blocks.TUFF, Blocks.DEEPSLATE)) {
            helper.setBlock(CELL, host);
            MaterializationService.Result result = MaterializationService.materialize(
                    level, silverDeposit(level, absolute), List.of(absolute),
                    MaterializationService.DEFAULT_BUDGET);

            check(result.placed() == 1,
                    host.getName().getString() + " is host rock and must be written, got "
                            + result.describeRejections());
            helper.assertBlockPresent(BlockRegistry.SILVER_ORE.get(), CELL);
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Idempotence and counting                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Writing the same deposit twice writes nothing the second time.
     *
     * <p>Placement-level idempotence, which is what milestone 3 claims. It is <b>not</b> duplicate
     * detection: nothing yet records that this deposit exists, so nothing could report it or refuse
     * a second one at a different origin. That is milestone 4's ledger.
     */
    @GameTest(template = TEMPLATE)
    public static void repeatedMaterialisationWritesNothingAndRerollsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        PlannedDeposit deposit = silverDeposit(level, origin);

        for (BlockPos pos : deposit.positions()) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }

        MaterializationService.Result first = MaterializationService.materialize(level, deposit);
        check(first.placed() == deposit.count(),
                "every cell had a host: placed " + first.placed() + " of " + deposit.count());

        MaterializationService.Result second = MaterializationService.materialize(level, deposit);
        check(second.placed() == 0, "a repeat must write nothing, wrote " + second.placed());
        check(second.rejected(MaterializationService.Rejection.ALREADY_PRESENT) == deposit.count(),
                "every cell must be recognised as already correct");

        // And the plan itself did not move: replanning the same row gives the same cells.
        PlannedDeposit replanned = silverDeposit(level, origin);
        check(replanned.positions().equals(deposit.positions()),
                "replanning the same deposit must not reroll its geometry");
        helper.succeed();
    }

    /**
     * The count is of state transitions, not of candidates.
     *
     * <p>Every legacy shape incremented its counter once per {@code setBlock} call, whether or not
     * the call did anything — so a write outside build height, or onto a cell it had already
     * written, was reported as a placement.
     */
    @GameTest(template = TEMPLATE)
    public static void placementCountsOnlyRealStateTransitions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(CELL);
        PlannedDeposit deposit = silverDeposit(level, absolute);

        helper.setBlock(CELL, Blocks.STONE);
        List<BlockPos> candidates = List.of(
                absolute,                                   // a host: written
                absolute,                                   // the same cell again: already present
                new BlockPos(absolute.getX(), level.getMaxBuildHeight() + 5, absolute.getZ()),
                new BlockPos(absolute.getX(), level.getMinBuildHeight() - 5, absolute.getZ()));

        MaterializationService.Result result = MaterializationService.materialize(
                level, deposit, candidates, MaterializationService.DEFAULT_BUDGET);

        check(result.placed() == 1, "only one candidate was a real transition, counted "
                + result.placed());
        check(result.rejected(MaterializationService.Rejection.ALREADY_PRESENT) == 1,
                "the repeat must be recognised, got " + result.describeRejections());
        check(result.rejected(MaterializationService.Rejection.OUTSIDE_BUILD_HEIGHT) == 2,
                "both out-of-world candidates must be refused, got " + result.describeRejections());
        helper.succeed();
    }

    /** A run writes at most its budget, says what is left, and resumes on the next run. */
    @GameTest(template = TEMPLATE)
    public static void materialisationRespectsItsWorkBudgetAndResumes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        PlannedDeposit deposit = silverDeposit(level, origin);
        check(deposit.count() >= 3, "this test needs a deposit of at least three cells");

        for (BlockPos pos : deposit.positions()) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }

        MaterializationService.Result first = MaterializationService.materialize(
                level, deposit, deposit.positions(), 2);
        check(first.placed() == 2, "the budget must cap the writes, wrote " + first.placed());
        check(first.truncated(), "a truncated run must say so");
        check(first.remaining() == deposit.count() - 2,
                "expected " + (deposit.count() - 2) + " left, said " + first.remaining());

        MaterializationService.Result second = MaterializationService.materialize(level, deposit);
        check(second.placed() == deposit.count() - 2,
                "the second run must finish the rest, wrote " + second.placed());
        check(!second.truncated(), "the deposit is complete now");

        for (BlockPos pos : deposit.positions()) {
            check(level.getBlockState(pos).is(BlockRegistry.SILVER_ORE.get()),
                    "resuming must complete the deposit, missing " + pos.toShortString());
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Chunk slicing in a world                                           */
    /* ------------------------------------------------------------------ */

    /**
     * A chunk slice writes only into its own chunk.
     *
     * <p>Which is why a deposit spanning several chunks never needs a neighbour loaded: the plan is
     * computed without reading the world at all, and materialising one chunk's share touches
     * nothing outside it.
     */
    @GameTest(template = TEMPLATE)
    public static void materialisingOneChunkTouchesOnlyThatChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        PlannedDeposit deposit = silverDeposit(level, origin);

        for (BlockPos pos : deposit.positions()) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }

        ChunkPos chunk = new ChunkPos(origin);
        MaterializationService.Result result =
                MaterializationService.materializeChunk(level, deposit, chunk);

        int expected = deposit.positionsIn(chunk).size();
        check(result.placed() == expected,
                "the slice must write exactly its own share: " + result.placed() + " of " + expected);

        for (BlockPos pos : deposit.positions()) {
            boolean inChunk = (pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z;
            boolean isSilver = level.getBlockState(pos).is(BlockRegistry.SILVER_ORE.get());
            check(inChunk == isSilver,
                    "cell " + pos.toShortString() + " is " + (inChunk ? "in" : "outside")
                            + " the sliced chunk but " + (isSilver ? "was" : "was not") + " written");
        }
        helper.succeed();
    }
}
