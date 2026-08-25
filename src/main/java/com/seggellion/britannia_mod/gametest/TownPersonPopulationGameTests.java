package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.TownPersonEntity;
import com.seggellion.britannia_mod.population.TownPersonPlacement;
import com.seggellion.britannia_mod.population.TownPersonPopulationManager;
import com.seggellion.britannia_mod.population.TownPersonPopulationPlan;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Vendor/Trader Milestone 20: proves the regional population system obeys owner
 * decision #12 — gradual convergence toward the Rails number, placement only
 * where a person may legitimately stand, and legacy TownPersons preserved
 * rather than culled.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class TownPersonPopulationGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private TownPersonPopulationGameTests() {
    }

    // ------------------------------------------------------ convergence math

    @GameTest(template = TEMPLATE)
    public static void convergenceIsGradualAndRateLimitedInBothDirections(GameTestHelper helper) {
        TownPersonPopulationPlan plan = plan(helper, 20, 2, 3);

        check(TownPersonPopulationManager.convergenceDelta(plan, 0) == 2,
                "a large shortfall must add at most max_spawn_per_cycle");
        check(TownPersonPopulationManager.convergenceDelta(plan, 19) == 1,
                "a shortfall smaller than the limit adds only what is missing");
        check(TownPersonPopulationManager.convergenceDelta(plan, 20) == 0,
                "a satisfied city changes nothing");
        check(TownPersonPopulationManager.convergenceDelta(plan, 100) == -3,
                "a large surplus removes at most max_despawn_per_cycle");
        check(TownPersonPopulationManager.convergenceDelta(plan, 21) == -1,
                "a surplus smaller than the limit removes only the excess");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aCityConvergesOverSeveralCyclesRatherThanAllAtOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TownPersonPopulationPlan plan = groundedPlan(helper, 3, 1, 1);

        int firstCycle = TownPersonPopulationManager.applyPlan(level, plan);
        check(firstCycle == 1, "one cycle must add exactly one person, added " + firstCycle);
        check(TownPersonPopulationManager.livingTownPeople(level, plan).size() == 1,
                "the town should hold exactly one person after one cycle");

        TownPersonPopulationManager.applyPlan(level, plan);
        int living = TownPersonPopulationManager.livingTownPeople(level, plan).size();
        check(living == 2, "a second cycle adds one more, found " + living);
        check(living < plan.desiredPopulation(),
                "the town must still be growing, not instantly complete");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void legacyTownPersonsCountAsPopulationAndAreNeverCulled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TownPersonPopulationPlan plan = groundedPlan(helper, 2, 5, 5);

        // A person the OLD block system spawned, standing in the city region.
        BlockPos legacyPos = helper.absolutePos(new BlockPos(1, 2, 1));
        TownPersonEntity legacy = EntityRegistry.TOWNSPERSON.get().create(level);
        check(legacy != null, "could not create a legacy townsperson");
        legacy.moveTo(legacyPos.getX() + 0.5D, legacyPos.getY(), legacyPos.getZ() + 0.5D, 0, 0);
        legacy.addTag("britannia_townsperson_spawn");
        check(level.addFreshEntity(legacy), "could not add the legacy townsperson");

        check(TownPersonPopulationManager.livingTownPeople(level, plan).size() == 1,
                "a legacy TownPerson must count toward this city's population");

        TownPersonPopulationManager.applyPlan(level, plan);
        check(legacy.isAlive(), "migration must never cull an existing TownPerson");
        int living = TownPersonPopulationManager.livingTownPeople(level, plan).size();
        check(living == 2, "the city should top up to its target of 2, found " + living);
        helper.succeed();
    }

    // -------------------------------------------------------- placement rules

    @GameTest(template = TEMPLATE)
    public static void placementRejectsEveryInvalidCandidateForItsOwnReason(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TownPersonPopulationPlan plan = groundedPlan(helper, 5, 1, 1);
        BlockPos valid = helper.absolutePos(new BlockPos(1, 2, 1));

        check(TownPersonPlacement.rejectionFor(level, plan, valid) == null,
                "a clear, grounded spot inside the region must be accepted");

        BlockPos outside = valid.offset(5000, 0, 5000);
        check(TownPersonPlacement.rejectionFor(level, plan, outside)
                        == TownPersonPlacement.Rejection.OUTSIDE_REGION,
                "a position outside every city region must be rejected");

        // No ground beneath: floating placement is refused. A taller region is
        // used so the candidate is inside the region but has only air below.
        TownPersonPopulationPlan tallPlan = tallPlan(helper);
        BlockPos floating = helper.absolutePos(new BlockPos(1, 5, 1));
        check(TownPersonPlacement.rejectionFor(level, tallPlan, floating)
                        == TownPersonPlacement.Rejection.NO_SOLID_GROUND,
                "a position with no sturdy ground must be rejected");

        // Feet blocked by a solid block.
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE);
        BlockPos blocked = helper.absolutePos(new BlockPos(2, 2, 1));
        check(TownPersonPlacement.rejectionFor(level, plan, blocked)
                        == TownPersonPlacement.Rejection.FEET_BLOCKED,
                "a solid block where the feet go must be rejected");

        // Head blocked.
        helper.setBlock(new BlockPos(3, 3, 1), Blocks.STONE);
        BlockPos lowCeiling = helper.absolutePos(new BlockPos(3, 2, 1));
        check(TownPersonPlacement.rejectionFor(level, plan, lowCeiling)
                        == TownPersonPlacement.Rejection.HEAD_BLOCKED,
                "insufficient headroom must be rejected");

        // Liquid.
        helper.setBlock(new BlockPos(4, 2, 1), Blocks.WATER);
        BlockPos water = helper.absolutePos(new BlockPos(4, 2, 1));
        check(TownPersonPlacement.rejectionFor(level, plan, water)
                        == TownPersonPlacement.Rejection.LIQUID,
                "standing in liquid must be rejected");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void placementNeverConsidersAnUnloadedChunkAndGivesUpQuietly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A region far from any loaded chunk: every candidate is unloaded, so
        // the manager must place nobody rather than force-load anything.
        TownPersonPopulationPlan remote = new TownPersonPopulationPlan(
                UUID.randomUUID(), "Remote",
                10, 5, 5,
                List.of(new TownPersonPopulationPlan.Bounds(
                        "remote", 9_000_000, 9_000_064, 60, 70, 9_000_000, 9_000_064)));

        BlockPos candidate = new BlockPos(9_000_010, 64, 9_000_010);
        check(!level.hasChunkAt(candidate), "the test premise requires an unloaded chunk");
        check(TownPersonPlacement.rejectionFor(level, remote, candidate)
                        == TownPersonPlacement.Rejection.CHUNK_NOT_LOADED,
                "an unloaded candidate must be rejected, never loaded");

        check(TownPersonPlacement.findSpawnPosition(level, remote, level.random) == null,
                "with no loaded candidate the cycle must place nobody");
        check(TownPersonPopulationManager.applyPlan(level, remote) == 0,
                "a remote city must not spawn anyone into unloaded chunks");
        check(!level.hasChunkAt(candidate), "population must never force-load a chunk");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aCityWithNoRegionPlacesNobody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TownPersonPopulationPlan homeless = new TownPersonPopulationPlan(
                UUID.randomUUID(), "Regionless", 10, 5, 5, List.of());
        check(!homeless.placeable(), "a city with no region is not placeable");
        check(TownPersonPopulationManager.applyPlan(level, homeless) == 0,
                "a region-less city must spawn nobody rather than guess a location");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void malformedPopulationRowsAreSkippedNotGuessedAt(GameTestHelper helper) {
        String body = """
                {"shard":"Test","cities":[
                  {"city_public_id":"not-a-uuid","city_name":"Broken","desired_population":5,
                   "max_spawn_per_cycle":1,"max_despawn_per_cycle":1,"regions":[]},
                  {"city_public_id":"11111111-2222-3333-4444-555555555555","city_name":"Good",
                   "desired_population":7,"max_spawn_per_cycle":2,"max_despawn_per_cycle":3,
                   "regions":[{"name":"r","min_x":0,"max_x":10,"min_y":60,"max_y":70,
                               "min_z":0,"max_z":10}]}
                ]}""";
        List<TownPersonPopulationPlan> plans = TownPersonPopulationManager.parsePlans(body);
        check(plans.size() == 1, "the malformed row must be skipped, parsed " + plans.size());
        TownPersonPopulationPlan good = plans.getFirst();
        check(good.desiredPopulation() == 7 && good.maxSpawnPerCycle() == 2
                        && good.maxDespawnPerCycle() == 3 && good.regions().size() == 1,
                "the valid row must survive intact");
        helper.succeed();
    }

    // ------------------------------------------------------------- helpers

    private static TownPersonPopulationPlan plan(GameTestHelper helper, int desired,
                                                 int spawnLimit, int despawnLimit) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new TownPersonPopulationPlan(
                UUID.randomUUID(), "TestTown", desired, spawnLimit, despawnLimit,
                List.of(new TownPersonPopulationPlan.Bounds("test",
                        origin.getX(), origin.getX() + 8,
                        origin.getY(), origin.getY() + 6,
                        origin.getZ(), origin.getZ() + 8)));
    }

    /**
     * Builds a real stone floor at relative y=1 and returns a plan whose region
     * is the standing level directly above it. Building the ground rather than
     * assuming the template's own terrain keeps these tests independent of the
     * structure file.
     */
    private static TownPersonPopulationPlan groundedPlan(GameTestHelper helper, int desired,
                                                         int spawnLimit, int despawnLimit) {
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new TownPersonPopulationPlan(
                UUID.randomUUID(), "TestTown", desired, spawnLimit, despawnLimit,
                List.of(new TownPersonPopulationPlan.Bounds("test",
                        origin.getX(), origin.getX() + 6,
                        origin.getY() + 2, origin.getY() + 2,
                        origin.getZ(), origin.getZ() + 6)));
    }

    /** Same footprint as groundedPlan but several blocks tall. */
    private static TownPersonPopulationPlan tallPlan(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new TownPersonPopulationPlan(
                UUID.randomUUID(), "TallTown", 1, 1, 1,
                List.of(new TownPersonPopulationPlan.Bounds("tall",
                        origin.getX(), origin.getX() + 6,
                        origin.getY() + 2, origin.getY() + 6,
                        origin.getZ(), origin.getZ() + 6)));
    }

    /**
     * Throws {@link GameTestAssertException}, never {@link IllegalStateException}. When a check runs
     * inside a {@code succeedWhen} or sequence callback -- directly or through any helper called
     * from one -- {@code GameTestSequence.tickAndContinue} swallows only that one type, which is how
     * a polled condition retries until it holds. {@code GameTestInfo} ticks its sequences outside
     * any try/catch, so anything else escapes into the server tick loop and crashes the whole
     * GameTest server, ending the run and every result in it.
     */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
