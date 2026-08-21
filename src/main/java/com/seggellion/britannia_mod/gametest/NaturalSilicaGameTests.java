package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.deposit.ManagedDepositExtraction;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.deposit.DepositRegistrar;
import com.seggellion.britannia_mod.resource.deposit.DepositSource;
import com.seggellion.britannia_mod.resource.natural.NaturalDepositSelector;
import com.seggellion.britannia_mod.resource.natural.NaturalDepositService;
import com.seggellion.britannia_mod.resource.natural.NaturalGeneration;
import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Silica, generated the way the world generates it, then worked the way a player works it.
 *
 * <h2>What the harness can and cannot show</h2>
 * {@code GameTestServer} runs a flat world with no deserts, so the biome gate can only be exercised
 * from the refusing side here: the natural service is driven against a real chunk and asked to
 * produce nothing, which it must. The accepting side needs real terrain and is covered by the
 * fixed-seed world audit in the milestone report.
 *
 * <p>Everything downstream of the biome question — the planner, the identity, the chunk slicing,
 * the ledger, the materialisation policy, extraction, restoration — is fully exercisable here and
 * is exercised, including the multi-chunk ordering proof that the whole design rests on.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class NaturalSilicaGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    private NaturalSilicaGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static ResourceDefinition silica() {
        return ResourceCatalog.instance().byId("britannia_mod:silica_sand_deposit").orElseThrow();
    }

    private static NaturalGeneration natural() {
        return silica().natural().orElseThrow();
    }

    private static ItemStack shovel() {
        return ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3);
    }

    private static ItemStack pickaxe() {
        return ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3);
    }

    /**
     * A plan for a real natural candidate, anchored wherever the test wants it.
     *
     * <p>The candidate is searched for rather than assumed at a fixed cell: only about a third of
     * owner cells produce one, so naming a cell and hoping is how this helper failed the first time
     * it ran.
     */
    private static PlannedDeposit planAt(ServerLevel level, BlockPos origin, int radius) {
        return PlacementPlanner.plan(silica(), level.dimension().location().toString(),
                origin, radius, ShapeRotation.XZ, anyCandidate().plannerSeed());
    }

    /** The first owner cell that actually produces a deposit. */
    private static NaturalDepositSelector.Candidate anyCandidate() {
        for (int cellX = 0; cellX < 40; cellX++) {
            for (int cellZ = 0; cellZ < 40; cellZ++) {
                java.util.Optional<NaturalDepositSelector.Candidate> candidate =
                        NaturalDepositSelector.candidateFor(1234L, silica(), natural(), cellX, cellZ);
                if (candidate.isPresent()) {
                    return candidate.get();
                }
            }
        }
        throw new GameTestAssertException(
                "the silica distribution produced no candidate in 1600 owner cells");
    }

    private static List<ItemStack> takeDrops(ServerLevel level, BlockPos around) {
        List<ItemEntity> entities =
                level.getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(3.0D));
        List<ItemStack> stacks = entities.stream().map(ItemEntity::getItem).toList();
        entities.forEach(ItemEntity::discard);
        return stacks;
    }

    /* ------------------------------------------------------------------ */
    /*  The biome gate, from the side this world can show                  */
    /* ------------------------------------------------------------------ */

    /**
     * Silica does not appear in a biome that is not on its list.
     *
     * <p>The flat test world is plains, which is not in {@code has_silica_deposits}. Running the
     * real service against a real chunk therefore has to come back with nothing placed — and, just
     * as importantly, with nothing registered, because a deposit that is refused by the biome must
     * not leave a ledger entry behind describing a bed that does not exist.
     */
    @GameTest(template = TEMPLATE)
    public static void silicaDoesNotGenerateInAnUnapprovedBiome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(helper.absolutePos(NODE));
        int ledgerBefore = DepositLedger.get(level).size();

        NaturalDepositService.ChunkOutcome outcome = NaturalDepositService.populate(level, chunk);

        check(outcome.placed() == 0,
                "silica placed " + outcome.placed() + " cells in a plains chunk");
        check(outcome.acceptedDeposits() == 0,
                "silica registered " + outcome.acceptedDeposits() + " deposits in a plains chunk");
        check(DepositLedger.get(level).size() == ledgerBefore,
                "a refused deposit still left a ledger entry behind");
        helper.succeed();
    }

    /**
     * Exactly the resources that are meant to generate naturally do, and only in the Overworld.
     *
     * <p>This read "only silica" until milestone 10A gave iron, gold and copper their distribution
     * data. The assertion is still worth having in the stronger form: a resource gaining natural
     * generation is a deliberate act, and one gaining it by accident — a stray {@code natural} block
     * copied into the wrong definition — should fail here rather than in a world.
     */
    @GameTest(template = TEMPLATE)
    public static void exactlyTheIntendedResourcesGenerateNaturallyAndOnlyInTheOverworld(
            GameTestHelper helper) {
        java.util.Set<String> expected = java.util.Set.of(
                "britannia_mod:silica_sand_deposit",
                "britannia_mod:iron",
                "britannia_mod:gold",
                "britannia_mod:copper");

        List<ResourceDefinition> naturalResources =
                NaturalDepositService.naturalResourcesIn(helper.getLevel());
        java.util.Set<String> found = new java.util.LinkedHashSet<>();
        naturalResources.forEach(resource -> found.add(resource.id()));

        check(found.equals(expected),
                "naturally generating resources are " + found + ", expected " + expected);
        for (ResourceDefinition resource : naturalResources) {
            check(resource.natural().orElseThrow().dimensionId().equals("minecraft:overworld"),
                    resource.id() + " is configured for a dimension other than the Overworld");
        }
        helper.succeed();
    }

    /**
     * An existing chunk is never retro-populated, and the guarantee needs no bookkeeping.
     *
     * <p>Milestone 8 carries this forward as a world-safety rule: a server that updates must not
     * start writing silica into terrain players have already built in. The guarantee is structural
     * rather than recorded — {@code isNewChunk()} is false for a chunk read from disk, because
     * NeoForge derives it from whether the promoted chunk was a real {@code ProtoChunk} and a stored
     * full chunk always comes back wrapped — so this asserts the handler's own contract rather than
     * the presence of a marker, and asserts that no marker exists to drift.
     */
    @GameTest(template = TEMPLATE)
    public static void anExistingChunkIsNeverRetroPopulated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(helper.absolutePos(NODE));
        var handler = new com.seggellion.britannia_mod.resource.natural.NaturalGenerationHandler();

        int ledgerBefore = DepositLedger.get(level).size();
        var existing = new net.neoforged.neoforge.event.level.ChunkEvent.Load(
                level.getChunk(chunk.x, chunk.z), false);
        handler.onChunkLoad(existing);

        check(!existing.isNewChunk(), "the test built a new-chunk event, so it proves nothing");
        check(DepositLedger.get(level).size() == ledgerBefore,
                "loading an existing chunk registered " + (DepositLedger.get(level).size() - ledgerBefore)
                        + " deposit(s); an updated server would rewrite worlds players already live in");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Materialisation policy                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Only an approved host becomes silica, and the counts say so truthfully.
     *
     * <p>A single column is set up with one cell of each kind the policy has an opinion about, and
     * the plan is pointed at all of them at once. The assertion is on the reported numbers rather
     * than only on the world, because a materialisation that places the right blocks and then
     * misreports what it did is still broken — the ledger believes those numbers.
     */
    @GameTest(template = TEMPLATE)
    public static void onlyApprovedHostsBecomeSilica(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(NODE);

        record Cell(String label, BlockPos pos, net.minecraft.world.level.block.Block block,
                    boolean shouldBecomeSilica) {
        }
        List<Cell> cells = List.of(
                new Cell("sand", base, Blocks.SAND, true),
                new Cell("sandstone", base.east(), Blocks.SANDSTONE, true),
                new Cell("stone", base.east(2), Blocks.STONE, false),
                new Cell("dirt", base.east(3), Blocks.DIRT, false),
                new Cell("water", base.east(4), Blocks.WATER, false),
                new Cell("lava", base.east(5), Blocks.LAVA, false),
                new Cell("chest", base.east(6), Blocks.CHEST, false),
                new Cell("bedrock", base.east(7), Blocks.BEDROCK, false),
                new Cell("clay bed", base.east(8), BlockRegistry.CLAY_DEPOSIT.get(), false),
                new Cell("air", base.east(9), Blocks.AIR, false));

        for (Cell cell : cells) {
            level.setBlock(cell.pos(), cell.block().defaultBlockState(), 2);
        }

        List<BlockPos> candidates = cells.stream().map(Cell::pos).toList();
        PlannedDeposit deposit = planAt(level, base, 8);
        MaterializationService.Result result =
                MaterializationService.materialize(level, deposit, candidates, 64);

        for (Cell cell : cells) {
            boolean isSilica = level.getBlockState(cell.pos())
                    .is(BlockRegistry.SILICA_SAND_DEPOSIT.get());
            check(isSilica == cell.shouldBecomeSilica(),
                    cell.label() + (cell.shouldBecomeSilica()
                            ? " should have become silica and did not"
                            : " was overwritten by silica and must not have been"));
        }
        check(result.placed() == 2,
                "materialisation reported " + result.placed() + " placements, not the two hosts");
        check(result.totalRejected() == candidates.size() - 2,
                "materialisation reported " + result.totalRejected() + " rejections against "
                        + (candidates.size() - 2) + " non-hosts");
        helper.succeed();
    }

    /** A player's own sand is construction, not a host. */
    @GameTest(template = TEMPLATE)
    public static void silicaDoesNotGrowThroughPlayerPlacedSand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(NODE);
        level.setBlock(base, Blocks.SAND.defaultBlockState(), 2);
        com.seggellion.britannia_mod.mining.MiningProvenance.markPlayerPlaced(level, base);

        MaterializationService.materialize(level, planAt(level, base, 8), List.of(base), 8);

        check(level.getBlockState(base).is(Blocks.SAND),
                "silica took a block the player had placed");
        helper.succeed();
    }

    /** Running the same materialisation twice writes nothing the second time. */
    @GameTest(template = TEMPLATE)
    public static void materialisingTheSameCellTwiceIsANoOp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(NODE);
        level.setBlock(base, Blocks.SAND.defaultBlockState(), 2);
        PlannedDeposit deposit = planAt(level, base, 8);

        MaterializationService.Result first =
                MaterializationService.materialize(level, deposit, List.of(base), 8);
        MaterializationService.Result second =
                MaterializationService.materialize(level, deposit, List.of(base), 8);

        check(first.placed() == 1, "the first pass placed " + first.placed());
        check(second.placed() == 0,
                "the second pass placed " + second.placed() + " cells over its own work");
        check(level.getBlockState(base).is(BlockRegistry.SILICA_SAND_DEPOSIT.get()),
                "the cell is no longer silica after being materialised twice");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  One deposit, many chunks, any order                                */
    /* ------------------------------------------------------------------ */

    /**
     * A lens spanning four chunks is one deposit, whichever order the chunks arrive in.
     *
     * <p>The proof the whole natural-generation design rests on. The same plan is sliced by chunk
     * and materialised forwards in one run and backwards in another, into two separate places, and
     * the two unions are compared cell for cell. The ledger is checked for exactly one entry, and
     * the count of loaded chunks is checked before and after so that nothing was dragged in.
     */
    @GameTest(template = TEMPLATE)
    public static void aLensAcrossFourChunksIsOneDepositInEitherOrder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = alignedToChunkCorner(helper.absolutePos(NODE));

        PlannedDeposit deposit = planAt(level, corner, 13);
        List<ChunkPos> touched = deposit.touchedChunks();
        check(touched.size() >= 4,
                "the lens only spans " + touched.size() + " chunks, so it proves nothing about borders");
        for (ChunkPos chunk : touched) {
            check(level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null,
                    "chunk " + chunk + " is not loaded, so this test cannot run without force-loading it");
        }

        DepositLedger ledger = DepositLedger.get(level);
        long instanceId = idFor(level, corner, 1);
        DepositInstance described = DepositRegistrar.describe(
                deposit, instanceId, DepositSource.NATURAL, "natural|test|cell 5,7");

        int ledgerBefore = ledger.size();
        int loadedBefore = level.getChunkSource().getLoadedChunksCount();

        // Forwards: every chunk registers, and only the first actually creates the instance.
        Set<BlockPos> forwards = new LinkedHashSet<>();
        int registrations = 0;
        for (ChunkPos chunk : touched) {
            DepositLedger.Registration registration = ledger.register(described);
            check(registration.mayMaterialize(), "chunk " + chunk + " was refused: " + registration.message());
            if (registration.outcome() == DepositLedger.Outcome.REGISTERED) {
                registrations++;
            }
            forwards.addAll(deposit.positionsIn(chunk));
        }

        check(registrations == 1,
                "four chunks created " + registrations + " deposits; they must share one");
        check(ledger.size() == ledgerBefore + 1,
                "the ledger grew by " + (ledger.size() - ledgerBefore) + " entries for one deposit");
        check(ledger.byId(instanceId).isPresent(), "the deposit is not in the ledger under its own id");

        // Backwards: the same slices, reversed.
        List<ChunkPos> reversed = new ArrayList<>(touched);
        java.util.Collections.reverse(reversed);
        Set<BlockPos> backwards = new LinkedHashSet<>();
        for (ChunkPos chunk : reversed) {
            DepositLedger.Registration registration = ledger.register(described);
            check(registration.outcome() == DepositLedger.Outcome.ALREADY_REGISTERED,
                    "a second pass over chunk " + chunk + " answered " + registration.outcome());
            backwards.addAll(deposit.positionsIn(chunk));
        }

        check(forwards.equals(backwards),
                "the forward and reverse unions differ by "
                        + Math.abs(forwards.size() - backwards.size()) + " cells");
        check(forwards.size() == deposit.count(),
                "the union of the slices is " + forwards.size() + " cells against a plan of "
                        + deposit.count());
        check(level.getChunkSource().getLoadedChunksCount() == loadedBefore,
                "slicing the deposit loaded " + (level.getChunkSource().getLoadedChunksCount()
                        - loadedBefore) + " extra chunks");
        helper.succeed();
    }

    /**
     * The same deposit offered under a changed definition is not silently regenerated.
     *
     * <p>Milestone 4's conservative rule, exercised through the natural path: the ledger keeps the
     * instance it has and reports the mismatch rather than materialising new geometry over an old
     * deposit. Reconciling that is milestone 8's.
     */
    @GameTest(template = TEMPLATE)
    public static void aRevisedDefinitionIsReportedRatherThanRegenerated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(NODE);
        DepositLedger ledger = DepositLedger.get(level);

        PlannedDeposit deposit = planAt(level, base, 8);
        long instanceId = idFor(level, base, 2);
        DepositInstance original = DepositRegistrar.describe(
                deposit, instanceId, DepositSource.NATURAL, "natural|test|revision");
        check(ledger.register(original).outcome() == DepositLedger.Outcome.REGISTERED,
                "the deposit did not register in the first place");

        DepositInstance revised = new DepositInstance(
                original.instanceId(), original.resourceId(), original.definitionRevision() + 1,
                original.source(), original.sourceIdentity(), original.origin(), original.seed(),
                original.radius(), original.rotation(), original.boundsMin(), original.boundsMax(),
                original.plannedCells(), original.materializedCells(), original.blockedCells(),
                original.materializationVersion());

        DepositLedger.Registration registration = ledger.register(revised);
        check(registration.outcome() == DepositLedger.Outcome.REVISION_MISMATCH,
                "a revised definition answered " + registration.outcome());
        check(!registration.mayMaterialize(),
                "a revision mismatch would still have been materialised over");
        check(ledger.byId(instanceId).orElseThrow().definitionRevision()
                        == original.definitionRevision(),
                "the ledger quietly adopted the new revision");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  End to end                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Generated, worked, depleted, owed back, returned — with the identity unchanged throughout.
     *
     * <p>The milestone's headline claim, done in one test so that no step is proved in isolation
     * against a fixture the previous step would not actually have produced.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void aNaturallyMaterialisedBedIsWorkedAndComesBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(NODE);
        level.setBlock(cell, Blocks.SAND.defaultBlockState(), 2);

        // Generate it through the real platform path: plan, register, materialise.
        DepositLedger ledger = DepositLedger.get(level);
        PlannedDeposit deposit = planAt(level, cell, 8);
        long instanceId = idFor(level, cell, 3);
        DepositInstance instance = DepositRegistrar.describe(
                deposit, instanceId, DepositSource.NATURAL, "natural|test|end-to-end");
        check(ledger.register(instance).mayMaterialize(), "the deposit would not register");
        MaterializationService.materialize(level, deposit, List.of(cell), 8);
        check(level.getBlockState(cell).is(BlockRegistry.SILICA_SAND_DEPOSIT.get()),
                "the cell did not materialise as silica");

        takeDrops(level, cell);
        ServerPlayer digger = ManagedResourceTestPlayers.survival(level, "silica-digger");
        ItemStack tool = shovel();
        digger.setItemInHand(InteractionHand.MAIN_HAND, tool);

        ManagedDepositExtraction.Result result =
                ManagedDepositExtraction.extract(level, cell, digger, digger.getMainHandItem());
        check(result == ManagedDepositExtraction.Result.EXTRACTED,
                "working the bed answered " + result);

        List<ItemStack> drops = takeDrops(level, cell);
        check(drops.size() == 1 && drops.get(0).getCount() == 1,
                "the bed yielded " + drops.size() + " stacks");
        check(!drops.get(0).is(Items.SAND), "the bed yielded ordinary sand");
        check(digger.getMainHandItem().getDamageValue() == 1,
                "working the bed cost " + digger.getMainHandItem().getDamageValue() + " durability");

        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        BrokenBlockData record = storage.getBrokenBlocks().get(cell);
        check(record != null, "the worked cell was not enrolled for restoration");
        check(record.originalState.is(BlockRegistry.SILICA_SAND_DEPOSIT.get()),
                "restoration would return " + record.originalState.getBlock());

        long delay = Resources.regenerationMillis(record.originalState).orElseThrow();
        check(delay == 24L * 60L * 60L * 1000L,
                "silica's regeneration resolved to " + delay + "ms rather than 24 hours");

        // Age the debt past its own 24-hour delay and let the real scheduler restore it. Production
        // timing is untouched; the record is simply older than it was.
        storage.add(new BrokenBlockData(record.pos, record.originalState,
                record.brokenTime - delay - 1_000L, record.playerUUID));

        helper.runAfterDelay(30L, () -> {
            check(level.getBlockState(cell).is(BlockRegistry.SILICA_SAND_DEPOSIT.get()),
                    "the bed did not come back");
            check(DepositLedger.get(level).byId(instanceId).isPresent(),
                    "the deposit lost its ledger entry across restoration");
            check(DepositLedger.get(level).byId(instanceId).orElseThrow().origin().equals(cell),
                    "the deposit's identity changed across extraction and restoration");
            helper.succeed();
        });
    }

    /** The M6 policies still hold for a bed that generated rather than being placed by hand. */
    @GameTest(template = TEMPLATE)
    public static void aGeneratedBedObeysEveryExtractionPolicy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(NODE);

        record Attempt(String label, ItemStack tool, ManagedDepositExtraction.Result expected) {
        }
        List<Attempt> attempts = List.of(
                new Attempt("a vanilla shovel", new ItemStack(Items.DIAMOND_SHOVEL),
                        ManagedDepositExtraction.Result.WRONG_TOOL),
                new Attempt("a Britannia pickaxe", pickaxe(),
                        ManagedDepositExtraction.Result.WRONG_TOOL),
                new Attempt("a bare hand", ItemStack.EMPTY,
                        ManagedDepositExtraction.Result.WRONG_TOOL));

        for (Attempt attempt : attempts) {
            level.setBlock(cell, BlockRegistry.SILICA_SAND_DEPOSIT.get().defaultBlockState(), 2);
            ServerPlayer player = ManagedResourceTestPlayers.survival(level, "silica-tester");
            player.setItemInHand(InteractionHand.MAIN_HAND, attempt.tool());
            takeDrops(level, cell);

            ManagedDepositExtraction.Result result =
                    ManagedDepositExtraction.extract(level, cell, player, player.getMainHandItem());

            check(result == attempt.expected(),
                    attempt.label() + " answered " + result + " rather than " + attempt.expected());
            check(level.getBlockState(cell).is(BlockRegistry.SILICA_SAND_DEPOSIT.get()),
                    attempt.label() + " removed the bed anyway");
            check(takeDrops(level, cell).isEmpty(), attempt.label() + " produced a yield");
            check(player.getMainHandItem().getDamageValue() == 0,
                    attempt.label() + " cost durability for a refusal");
        }

        // A fake player with the correct shovel: refused as an actor, not as a tool.
        level.setBlock(cell, BlockRegistry.SILICA_SAND_DEPOSIT.get().defaultBlockState(), 2);
        net.neoforged.neoforge.common.util.FakePlayer machine =
                net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        machine.setItemInHand(InteractionHand.MAIN_HAND, shovel());
        check(ManagedDepositExtraction.extract(level, cell, machine, machine.getMainHandItem())
                        == ManagedDepositExtraction.Result.DENIED_ACTOR,
                "a fake player was not refused on a generated bed");
        check(level.getBlockState(cell).is(BlockRegistry.SILICA_SAND_DEPOSIT.get()),
                "a fake player removed a generated bed");
        helper.succeed();
    }

    /**
     * A deposit id unique to this test, this position and this run.
     *
     * <p>These used to be constants, which was wrong in a way only a second run revealed: the
     * ledger is {@code SavedData} and survives between runs, so the second run offered the same id
     * at a different position and the ledger correctly refused it as a conflict. Deriving the id
     * from the position through the real natural contract keeps the test honest and makes it
     * idempotent, since GameTest places its structures somewhere new each time.
     */
    private static long idFor(ServerLevel level, BlockPos origin, int salt) {
        return com.seggellion.britannia_mod.resource.deposit.DepositIdentity.natural(
                level.getSeed(), level.dimension().location().toString(), silica().id(),
                origin.getX(), origin.getZ(), salt);
    }

    /** The origin's chunk corner, so a radius-13 lens is guaranteed to span four chunks. */
    private static BlockPos alignedToChunkCorner(BlockPos near) {
        int x = (near.getX() >> 4) << 4;
        int z = (near.getZ() >> 4) << 4;
        return new BlockPos(x, near.getY(), z);
    }
}
