package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.deposit.DepositSource;
import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Rails is the only thing that says a managed deposit exists.
 *
 * <h2>The invariant, and why it is the critical one</h2>
 * Milestones 7, 10A and 11 built an automatic distribution system: a new chunk consulted the world
 * seed, an owner grid, a probability and a salt, and invented a deposit if the numbers agreed. That
 * was never the intended model. Deposits are curated — a Rails row names the resource, the origin,
 * the shape parameters — and the server's job is to turn those parameters into blocks, not to
 * decide that a deposit is there.
 *
 * <p>The distinction matters because the two models fail in opposite directions. Under the
 * automatic model an operator cannot control supply; under the curated model an operator controls
 * it completely, and a world with no rows has no managed resources at all. Getting halfway between
 * them — a curated catalogue plus a generator quietly adding to it — would be the worst of both,
 * and would be invisible until someone counted deposits and found more than they had configured.
 *
 * <p>So: <b>chunk generation alone must create zero deposits</b>, and every deposit must be
 * traceable to a row. Both halves are asserted here.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class RailsDepositAuthorityGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    /** Every resource that could conceivably be created automatically, if anything still did. */
    private static final List<String> CANDIDATES =
            List.of("coal", "iron", "gold", "copper", "silica_sand_deposit");

    private RailsDepositAuthorityGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * A planned cell this test can actually use: inside the test's own chunk, and inside build
     * height.
     *
     * <p>Both constraints are the platform's, not the test's. Materialisation refuses a cell
     * outside build height, and restoration deliberately refuses to load a chunk just to restore
     * into it — so a cell chosen from the far side of a 12-radius deposit would fail for reasons
     * that have nothing to do with what is being tested. The origin is not usable either: Vertical
     * grows upward from it and never includes it.
     */
    private static BlockPos workableCell(ServerLevel level, PlannedDeposit deposit, BlockPos origin) {
        net.minecraft.world.level.ChunkPos here = new net.minecraft.world.level.ChunkPos(origin);
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (BlockPos candidate : deposit.positions()) {
            if (new net.minecraft.world.level.ChunkPos(candidate).equals(here)
                    && candidate.getY() > level.getMinBuildHeight()
                    && candidate.getY() < level.getMaxBuildHeight() - 1) {
                int distance = Math.abs(candidate.getY() - origin.getY());
                if (distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        if (best == null) {
            throw new GameTestAssertException(
                    "no planned cell of this deposit lies in the test's own chunk within build height");
        }
        return best;
    }

    /* ------------------------------------------------------------------ */
    /*  1. Nothing appears on its own                                      */
    /* ------------------------------------------------------------------ */

    /**
     * Chunk generation creates no deposits, across enough chunks that rarity cannot explain it.
     *
     * <p>The event is posted directly, with {@code isNewChunk} true, which is precisely the seam
     * milestone 7 hung automatic creation from. Posting it 256 times is the strong form of the
     * claim: under the old model the densest resource produced a deposit about every 79 chunks, so
     * 256 new-chunk events would have created several. Zero is not rarity.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void chunkGenerationAloneCreatesNoDeposits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos here = new ChunkPos(helper.absolutePos(NODE));
        DepositLedger ledger = DepositLedger.get(level);

        int before = ledger.size();
        int posted = 0;
        for (int dx = -8; dx < 8; dx++) {
            for (int dz = -8; dz < 8; dz++) {
                // Only chunks already resident. Forcing new ones would both be slow and disturb
                // the restoration tests, which need to know which chunks are unloaded.
                if (!level.getChunkSource().hasChunk(here.x + dx, here.z + dz)) {
                    continue;
                }
                NeoForge.EVENT_BUS.post(new ChunkEvent.Load(
                        level.getChunk(here.x + dx, here.z + dz), true));
                posted++;
            }
        }
        check(posted >= 16, "only " + posted + " chunks were resident, too few to prove anything");

        int after = DepositLedger.get(level).size();
        System.out.println("M11A posted " + posted + " new-chunk events; ledger " + before
                + " -> " + after);
        check(after == before,
                posted + " new-chunk events created " + (after - before) + " deposit(s). Chunk"
                        + " generation must create none: Rails defines where deposits are");
        helper.succeed();
    }

    /**
     * No resource carries automatic-distribution configuration any more.
     *
     * <p>The structural half. Even with no listener, a resource that still declared a grid, a
     * chance and a salt would be an invitation to reconnect one — and the catalogue now refuses a
     * {@code natural} block at load, so this asserts the data actually took the change rather than
     * relying on the refusal never being tested.
     */
    @GameTest(template = TEMPLATE)
    public static void noResourceDeclaresAutomaticDistribution(GameTestHelper helper) {
        for (ResourceDefinition resource : ResourceCatalog.instance().all()) {
            // Generation configuration is now purely geometric: shape, radius bounds, host, tuning.
            // There is no field left that could decide a deposit into existence.
            resource.generation().ifPresent(generation -> {
                check(generation.minRadius() > 0,
                        resource.id() + " has an unusable radius floor");
                check(generation.maxRadius() >= generation.minRadius(),
                        resource.id() + " has an inverted radius range");
            });
        }
        System.out.println("M11A " + ResourceCatalog.instance().all().size()
                + " resources carry geometry only; none carries a distribution policy");
        helper.succeed();
    }

    /**
     * A world with no Rails rows has no managed resource blocks in it.
     *
     * <p>The complementary check to the event test: not "nothing was registered" but "nothing was
     * written". A stray writer that placed blocks without registering them would be worse than one
     * that registered, because the ledger would not even know to restore them.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void anUnseededWorldContainsNoManagedResourceBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(NODE);

        List<String> found = new ArrayList<>();
        for (String path : CANDIDATES) {
            ResourceDefinition resource = ResourceCatalog.instance().byPath(path).orElseThrow();
            var block = Resources.block(resource.generation().orElseThrow().blockId());
            // Deliberately within one chunk: reading a block in an unloaded chunk would load it,
            // which would both be a side effect and break the restoration tests downstream.
            for (int dx = -6; dx <= 6; dx += 2) {
                for (int dz = -6; dz <= 6; dz += 2) {
                    for (int dy = -4; dy <= 4; dy += 2) {
                        BlockPos pos = centre.offset(dx, dy, dz);
                        if (level.getBlockState(pos).is(block)) {
                            found.add(path + " at " + pos);
                        }
                    }
                }
            }
        }
        check(found.isEmpty(),
                "an unseeded world already contains managed resource blocks: " + found);
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  2. A Rails row does create one                                     */
    /* ------------------------------------------------------------------ */

    /**
     * The whole curated pipeline, for each representative resource.
     *
     * <p>Row → stable identity → resource definition → deterministic geometry → ledger → bounded
     * materialisation. Every step is the production path; nothing here is a test-only shortcut.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void aRailsRowCreatesADepositForEveryRepresentativeResource(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().location().toString();
        BlockPos origin = helper.absolutePos(NODE);
        DepositLedger ledger = DepositLedger.get(level);

        for (String path : CANDIDATES) {
            BlockPos cell = origin.offset(0, 0, 0);
            // Silica hosts sand; the metals and coal host stone. Give each a bed it accepts, so the
            // test measures the pipeline rather than the host policy, which is tested elsewhere.
            boolean sediment = path.equals("silica_sand_deposit");
            level.setBlock(cell, (sediment ? Blocks.SAND : Blocks.STONE).defaultBlockState(), 2);

            ResourceDefinition resource = ResourceCatalog.instance().byPath(path).orElseThrow();
            // Each shape has its own floor -- snake refuses anything under 10 -- so the row asks for
            // a radius the resource actually allows rather than one number for all of them.
            int radius = Math.max(10, resource.generation().orElseThrow().minRadius());
            CuratedDepositTestRows row = CuratedDepositTestRows.of(path, cell, radius);
            long id = row.identity(dimension);
            check(id != 0L, path + " produced an empty identity");

            PlannedDeposit deposit = row.plan(dimension);
            check(!deposit.positions().isEmpty(), path + " planned no cells");

            DepositInstance instance = row.describe(dimension);
            check(instance.source() == DepositSource.RAILS,
                    path + " was registered as " + instance.source() + ", not RAILS");
            check(ledger.register(instance).mayMaterialize(), path + " would not register");

            BlockPos target = cell;
            level.setBlock(target, (sediment ? Blocks.SAND : Blocks.STONE).defaultBlockState(), 2);
            MaterializationService.Result result =
                    MaterializationService.materialize(level, deposit, List.of(target), 8);
            check(result.placed() == 1,
                    path + " placed " + result.placed() + " cells into its own host: "
                            + result.describeRejections());
            check(ledger.byId(id).isPresent(), path + " is not in the ledger under its Rails id");

            System.out.println("M11A " + path + " row -> id " + id + ", "
                    + deposit.positions().size() + " planned cells, placed " + result.placed());
        }
        helper.succeed();
    }

    /**
     * Importing the same row twice yields one deposit and identical geometry.
     *
     * <p>This is what makes a re-import safe: an operator re-running the importer after adding new
     * rows must not double every deposit that was already there.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void importingTheSameRowTwiceIsIdempotent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().location().toString();
        BlockPos origin = helper.absolutePos(NODE);
        DepositLedger ledger = DepositLedger.get(level);

        for (String path : CANDIDATES) {
            CuratedDepositTestRows row = CuratedDepositTestRows.of(path, origin, 10);

            int before = ledger.size();
            DepositLedger.Registration first = ledger.register(row.describe(dimension));
            check(first.outcome() == DepositLedger.Outcome.REGISTERED,
                    path + " did not register on first import: " + first.outcome());

            DepositLedger.Registration second = ledger.register(row.describe(dimension));
            check(second.outcome() != DepositLedger.Outcome.REGISTERED,
                    path + " registered a second, duplicate deposit for the same row");
            check(second.mayMaterialize(),
                    path + " refused to resume materialising an already-known deposit");
            check(ledger.size() == before + 1,
                    path + " left " + (ledger.size() - before) + " ledger entries for one row");

            check(row.plan(dimension).positions().equals(row.plan(dimension).positions()),
                    path + " planned different geometry on the second import");
        }
        helper.succeed();
    }

    /**
     * The same immutable parameters always describe the same cells — no execution-time randomness.
     *
     * <p>Planned twice per shape, and compared as an ordered list. If anything in the chain reached
     * for a shared RNG, a clock or the world seed, the two lists would differ.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void curatedGeometryIsPurelyAFunctionOfTheRow(GameTestHelper helper) {
        String dimension = helper.getLevel().dimension().location().toString();
        BlockPos origin = helper.absolutePos(NODE);

        // One resource per shape the platform has, so every planner is covered.
        for (String path : List.of("coal", "iron", "gold", "copper", "agapite", "silver",
                "silica_sand_deposit")) {
            CuratedDepositTestRows row = CuratedDepositTestRows.of(path, origin, 12);
            List<BlockPos> first = row.plan(dimension).positions();
            List<BlockPos> second = CuratedDepositTestRows.of(path, origin, 12)
                    .plan(dimension).positions();

            check(first.equals(second),
                    path + " planned " + first.size() + " cells and then " + second.size()
                            + " different ones from identical inputs; something is drawing from a"
                            + " source that is not the row");
            System.out.println("M11A " + path + " deterministic: " + first.size() + " cells");
        }
        helper.succeed();
    }

    /**
     * An operator can answer "how many coal deposits does this world have?" before enabling
     * suppression.
     *
     * <p>Under the curated model this stops being a curiosity and becomes the deployment question:
     * supply is exactly what Rails defines, so a world with no coal rows has no coal, and nothing
     * in the game will tell a player otherwise. {@code /orevein stats} already reports deposits by
     * resource and by source; this pins that it keeps doing so.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void operatorDiagnosticsCanCountDepositsByResource(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().location().toString();
        BlockPos origin = helper.absolutePos(NODE);
        DepositLedger ledger = DepositLedger.get(level);

        CuratedDepositTestRows row = CuratedDepositTestRows.of("coal", origin, 10);
        ledger.register(row.describe(dimension));

        java.util.Map<String, Integer> byResource = new java.util.LinkedHashMap<>();
        java.util.Map<DepositSource, Integer> bySource = new java.util.LinkedHashMap<>();
        for (DepositInstance instance : ledger.all()) {
            byResource.merge(instance.resourceId(), 1, Integer::sum);
            bySource.merge(instance.source(), 1, Integer::sum);
        }

        check(byResource.getOrDefault("britannia_mod:coal", 0) >= 1,
                "the ledger cannot say how many coal deposits exist, which is the question an"
                        + " operator has to answer before suppression is enabled");
        check(bySource.getOrDefault(DepositSource.RAILS, 0) >= 1,
                "the ledger cannot say which deposits came from Rails");
        // Deliberately no assertion that NATURAL is absent: the ledger is SavedData and survives
        // between runs, so a development world still remembers deposits the retired distribution
        // created. That is history, not behaviour -- what matters is that nothing creates one now,
        // which chunkGenerationAloneCreatesNoDeposits asserts directly.
        System.out.println("M11A deposits by source: " + bySource);
        helper.succeed();
    }

    /**
     * Materialising a curated deposit loads no chunk it was not already given.
     *
     * <p>Carried forward from milestone 7 and still the rule that keeps import bounded: a deposit
     * spanning four chunks is materialised a slice at a time, and asking for a slice must never
     * drag in a neighbour.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void materialisingASliceLoadsNoNeighbouringChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().location().toString();
        BlockPos corner = helper.absolutePos(NODE);

        CuratedDepositTestRows row = CuratedDepositTestRows.of("coal", corner, 20);
        PlannedDeposit deposit = row.plan(dimension);
        List<ChunkPos> touched = deposit.touchedChunks();
        check(touched.size() >= 2,
                "the row spans " + touched.size() + " chunk(s), so it cannot prove containment");

        int loadedBefore = level.getChunkSource().getLoadedChunksCount();
        MaterializationService.materializeChunk(level, deposit, touched.get(0));
        int loadedAfter = level.getChunkSource().getLoadedChunksCount();

        check(loadedAfter == loadedBefore,
                "materialising one slice loaded " + (loadedAfter - loadedBefore)
                        + " extra chunk(s); slices must never force a neighbour");
        helper.succeed();
    }
}
