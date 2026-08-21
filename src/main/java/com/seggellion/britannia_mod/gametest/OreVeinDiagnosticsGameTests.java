package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.commands.OreVeinDiagnosticsCommand;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.deposit.DepositIdentity;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.deposit.DepositRegistrar;
import com.seggellion.britannia_mod.resource.deposit.DepositSource;
import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Milestone 8: what an operator can find out, and what an import does twice.
 *
 * <p>The diagnostics are driven through their own entry points rather than through Brigadier
 * parsing, because what is worth testing is that they answer correctly about real ledger and
 * restoration state — not that the command tree is wired, which the dispatcher would refuse at
 * registration if it were not.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class OreVeinDiagnosticsGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    private OreVeinDiagnosticsGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static ResourceDefinition silver() {
        return ResourceCatalog.instance().byId("britannia_mod:silver").orElseThrow();
    }

    /** A silent command source, so the assertions are about behaviour rather than chat. */
    private static CommandSourceStack source(GameTestHelper helper) {
        return helper.getLevel().getServer().createCommandSourceStack();
    }

    /** Register one real deposit at a position unique to this run, and return its id. */
    private static long registerDeposit(ServerLevel level, BlockPos origin, int salt) {
        long instanceId = DepositIdentity.natural(level.getSeed(),
                level.dimension().location().toString(), silver().id(),
                origin.getX(), origin.getZ(), salt);
        PlannedDeposit deposit = PlacementPlanner.plan(silver(),
                level.dimension().location().toString(), origin, 2, ShapeRotation.XZ,
                DepositIdentity.plannerSeed(instanceId));
        DepositInstance instance = DepositRegistrar.describe(
                deposit, instanceId, DepositSource.RAILS, "rails|test|" + salt);
        DepositLedger.get(level).register(instance);
        return instanceId;
    }

    /* ------------------------------------------------------------------ */
    /*  Visibility                                                         */
    /* ------------------------------------------------------------------ */

    /** Operators can count and enumerate persistent deposit instances. */
    @GameTest(template = TEMPLATE)
    public static void operatorsCanCountAndListDeposits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(NODE);
        DepositLedger ledger = DepositLedger.get(level);

        int before = ledger.size();
        long id = registerDeposit(level, origin, 801);
        check(ledger.size() == before + 1, "the deposit did not enter the ledger");

        check(OreVeinDiagnosticsCommand.stats(source(helper)) == ledger.size(),
                "stats reported a different instance count than the ledger holds");
        check(OreVeinDiagnosticsCommand.list(source(helper), null) == ledger.size(),
                "an unfiltered list did not cover every deposit");
        check(OreVeinDiagnosticsCommand.list(source(helper), "silver") >= 1,
                "the silver deposit is missing from a filtered list");
        check(OreVeinDiagnosticsCommand.inspect(source(helper), Long.toHexString(id)) == 1,
                "inspect could not find the deposit it was just given");
        helper.succeed();
    }

    /** Inspect answers every question the playbook asks an operator to be able to answer. */
    @GameTest(template = TEMPLATE)
    public static void inspectExposesIdentityBoundsAndProgress(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(NODE);
        long id = registerDeposit(level, origin, 802);

        DepositInstance instance = DepositLedger.get(level).byId(id).orElseThrow();
        check(instance.instanceId() == id, "instance id");
        check(instance.resourceId().equals(silver().id()), "resource definition");
        check(instance.source() == DepositSource.RAILS, "source");
        check(instance.origin().equals(origin), "origin");
        check(!instance.touchedChunks().isEmpty(), "bounds cover no chunk");
        check(instance.plannedCells() > 0, "planned cells");
        check(DepositRegistrar.depletedCells(level, instance) == 0,
                "an untouched deposit reports depleted cells");
        helper.succeed();
    }

    /** A refused duplicate is remembered, because refusing writes nothing to remember it by. */
    @GameTest(template = TEMPLATE)
    public static void aRefusedDuplicateIsVisibleToTheOperator(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(NODE);
        DepositLedger ledger = DepositLedger.get(level);

        long id = registerDeposit(level, origin, 803);
        DepositInstance existing = ledger.byId(id).orElseThrow();
        int refusalsBefore = ledger.refusals().size();

        // The same id offered for a different place: a genuine conflict.
        PlannedDeposit elsewhere = PlacementPlanner.plan(silver(),
                level.dimension().location().toString(), origin.offset(64, 0, 64), 2,
                ShapeRotation.XZ, DepositIdentity.plannerSeed(id));
        DepositLedger.Registration registration = ledger.register(
                DepositRegistrar.describe(elsewhere, id, DepositSource.RAILS, "rails|test|moved"));

        check(registration.outcome() == DepositLedger.Outcome.CONFLICT,
                "a relocated deposit under the same id answered " + registration.outcome());
        check(!registration.mayMaterialize(), "a conflicting deposit would have been materialised");
        check(ledger.byId(id).orElseThrow().origin().equals(existing.origin()),
                "the conflict moved the deposit that was already there");
        check(ledger.refusals().size() == refusalsBefore + 1,
                "the refusal was not journalled, so an operator cannot ask about it");
        check(OreVeinDiagnosticsCommand.refusals(source(helper)) == ledger.refusals().size(),
                "the refusals command disagrees with the ledger");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Idempotent import                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Importing the same curated row twice registers once and writes nothing the second time.
     *
     * <p>The deterministic-import requirement, exercised end to end through the real identity,
     * planner, ledger and materialisation rather than through the command's network path.
     */
    @GameTest(template = TEMPLATE)
    public static void repeatingAnImportIsIdempotent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(NODE);
        String dimension = level.dimension().location().toString();
        DepositLedger ledger = DepositLedger.get(level);

        // Fill the area with an approved host so there is something for silver to replace.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlock(origin.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }

        long id = DepositIdentity.rails("test-shard", dimension, silver().id(),
                origin.getX(), origin.getY(), origin.getZ(), 2, ShapeRotation.XZ, "test-region");
        String identity = DepositIdentity.railsEncoding("test-shard", dimension, silver().id(),
                origin.getX(), origin.getY(), origin.getZ(), 2, ShapeRotation.XZ, "test-region");

        int ledgerBefore = ledger.size();
        int firstPlaced = importOnce(level, dimension, origin, id, identity);
        int afterFirst = ledger.size();
        int secondPlaced = importOnce(level, dimension, origin, id, identity);

        check(afterFirst == ledgerBefore + 1,
                "the first import registered " + (afterFirst - ledgerBefore) + " deposits");
        check(ledger.size() == afterFirst,
                "the second import registered another deposit for the same row");
        check(firstPlaced > 0, "the first import placed nothing, so this proves nothing");
        check(secondPlaced == 0,
                "the second import wrote " + secondPlaced + " cells over its own work");
        helper.succeed();
    }

    private static int importOnce(ServerLevel level, String dimension, BlockPos origin,
                                  long id, String identity) {
        PlannedDeposit deposit = PlacementPlanner.plan(silver(), dimension, origin, 2,
                ShapeRotation.XZ, DepositIdentity.plannerSeed(id));
        DepositLedger.Registration registration = DepositLedger.get(level).register(
                DepositRegistrar.describe(deposit, id, DepositSource.RAILS, identity));
        if (!registration.mayMaterialize()) {
            return -1;
        }
        MaterializationService.Result result = MaterializationService.materialize(level, deposit);
        DepositRegistrar.recordCompletePass(level, id, result, deposit.count());
        return result.placed();
    }

    /* ------------------------------------------------------------------ */
    /*  regenerate                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * {@code regenerate} brings existing debts forward and invents nothing.
     *
     * <p>Specifically: a deposit that owes nothing is told so rather than having cells conjured for
     * it, and a deposit that owes something has exactly its own debts made due — through the one
     * restoration store, with no second scheduler anywhere.
     */
    @GameTest(template = TEMPLATE)
    public static void regenerateOnlyBringsExistingDebtsForward(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(NODE);
        long id = registerDeposit(level, origin, 804);

        check(OreVeinDiagnosticsCommand.regenerate(source(helper), Long.toHexString(id)) == 0,
                "a deposit owing nothing had something brought forward");

        // One genuine debt, not yet due.
        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        long now = System.currentTimeMillis();
        int debtsBefore = storage.totalCount();
        storage.add(new BrokenBlockData(origin,
                BlockRegistry.SILVER_ORE.get().defaultBlockState(), now, UUID.randomUUID(),
                now + BrokenBlockData.DEFAULT_RESTORE_DELAY, id, silver().id(), 0L, 0));

        check(storage.totalCount() == debtsBefore + 1, "the debt was not recorded");
        check(storage.debtAt(origin).dueAt > now, "the debt is already due, so this proves nothing");

        int brought = OreVeinDiagnosticsCommand.regenerate(source(helper), Long.toHexString(id));

        check(brought == 1, "regenerate brought " + brought + " cells forward, not the one owed");
        check(storage.debtAt(origin) != null, "regenerate removed the debt instead of advancing it");
        check(storage.debtAt(origin).dueAt <= System.currentTimeMillis(),
                "the debt is still not due after being brought forward");
        check(storage.totalCount() == debtsBefore + 1,
                "regenerate changed how many debts exist");
        helper.succeed();
    }

    /** An unknown id is refused rather than guessed at. */
    @GameTest(template = TEMPLATE)
    public static void unknownIdsAreRefusedRatherThanGuessed(GameTestHelper helper) {
        check(OreVeinDiagnosticsCommand.inspect(source(helper), "notanid") == 0,
                "a malformed id was accepted");
        check(OreVeinDiagnosticsCommand.inspect(source(helper), "deadbeefdeadbeef") == 0,
                "an id no deposit has was accepted");
        check(OreVeinDiagnosticsCommand.regenerate(source(helper), "deadbeefdeadbeef") == 0,
                "regenerate accepted an id no deposit has");
        check(OreVeinDiagnosticsCommand.list(source(helper), "not_a_resource") == 0,
                "an unknown resource filter was accepted");
        check(OreVeinDiagnosticsCommand.locate(source(helper), "not_a_resource") == 0,
                "locate accepted an unknown resource");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  The legacy operations stay retired                                 */
    /* ------------------------------------------------------------------ */

    /**
     * The dangerous clear operations are still refused, and are no longer needed.
     *
     * <p>The exit criterion is that legacy clear behaviour is no longer <em>needed</em>, which is a
     * claim about what replaced it: an operator who once reached for {@code /populateores clear} to
     * find out what was in the world now has {@code /orevein stats}, {@code list}, {@code locate}
     * and {@code inspect}, none of which touch a block.
     */
    @GameTest(template = TEMPLATE)
    public static void theDiagnosticsThatReplaceLegacyClearWriteNothing(GameTestHelper helper) {
        // That /populateores clear and /undoores still refuse is already pinned by
        // OreVeinContainmentGameTests; what milestone 8 adds is the reason they are not missed.
        ServerLevel level = helper.getLevel();
        int ledgerBefore = DepositLedger.get(level).size();
        OreVeinDiagnosticsCommand.stats(source(helper));
        OreVeinDiagnosticsCommand.list(source(helper), null);
        check(DepositLedger.get(level).size() == ledgerBefore,
                "a read-only diagnostic changed the ledger");
        helper.succeed();
    }
}
