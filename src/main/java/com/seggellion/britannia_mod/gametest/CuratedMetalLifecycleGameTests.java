package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.event.BlockRestoreHandler;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.resource.deposit.DepositIdentity;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.deposit.DepositRegistrar;
import com.seggellion.britannia_mod.resource.deposit.DepositSource;
import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;
import com.seggellion.britannia_mod.skill.SkillManager;

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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Milestone 10A, amended at milestone 11: iron, gold and copper from a curated Rails row
 * through to restoration. The row is what says the deposit exists; nothing here consults a
 * world seed, a grid or a biome probability, because none of those decide anything any more.
 *
 * <h2>Why one test per metal rather than one shared one</h2>
 * The three take different shapes — a vertical column, a wandering snake, a cluster — and land at
 * different depths through different host material. Running them through one parameterised path
 * proves the parameterisation works; running each end to end proves the resource works. Both matter,
 * so the lifecycle is shared and the subjects are named.
 *
 * <p>Extraction goes through the Mining path rather than the deposit path, because these are ORE
 * family: the break event, the Mining gate, the skill requirement and the purity-ore yield. That is
 * the existing behaviour and milestone 10A does not touch it — these tests are here to prove the new
 * distribution data feeds it correctly, not to re-specify it.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CuratedMetalLifecycleGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    private CuratedMetalLifecycleGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static ResourceDefinition resource(String path) {
        return ResourceCatalog.instance().byPath(path).orElseThrow();
    }

    private static ItemStack pickaxe() {
        return ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3);
    }

    private static ItemStack shovel() {
        return ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3);
    }

    /** The block this resource's curated deposits are made of. */
    private static Block managedBlock(ResourceDefinition resource) {
        return Resources.block(resource.generation().orElseThrow().blockId());
    }

    private static ServerPlayer miner(ServerLevel level, ItemStack tool) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, "metal-miner");
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, 150.0f);
        return player;
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
        // positionsIn is the same slice materialisation uses, so the cell is guaranteed both to
        // belong to the deposit and to lie in the chunk this test may safely touch.
        for (BlockPos candidate : deposit.positionsIn(here)) {
            if (candidate.getY() > level.getMinBuildHeight() + 4
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

    private static List<ItemStack> takeDrops(ServerLevel level, BlockPos around) {
        List<ItemEntity> entities =
                level.getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(3.0D));
        List<ItemStack> stacks = entities.stream().map(ItemEntity::getItem).toList();
        entities.forEach(ItemEntity::discard);
        return stacks;
    }

    private static void breakThroughTheEventBus(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockState state = level.getBlockState(pos);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
        NeoForge.EVENT_BUS.post(event);
        if (!event.isCanceled()) {
            level.destroyBlock(pos, !player.isCreative(), player);
        }
    }

    /**
     * The whole chain for one metal: selection → identity → planner → ledger → materialisation →
     * extraction → debt → restoration.
     */
    private static void runLifecycle(GameTestHelper helper, String path, Runnable onRestored) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(NODE);
        String dimension = level.dimension().location().toString();
        ResourceDefinition resource = resource(path);
        Block block = managedBlock(resource);

        // A curated Rails row: the only thing that says this deposit exists. Its immutable
        // parameters derive the identity, and the identity derives the planner seed.
        // A radius every one of the three allows: copper tops out at 22.
        int radius = Math.min(20, resource.generation().orElseThrow().maxRadius());
        CuratedDepositTestRows row = CuratedDepositTestRows.of(path, origin, radius);
        long instanceId = row.identity(dimension);
        PlannedDeposit deposit = row.plan(dimension);

        // A cell the deposit actually owns, in the test's own chunk.
        //
        // The origin will not do. Vertical grows upward from its origin and Snake wanders away from
        // it, so neither includes it -- and restoration correctly refuses to restore a cell that is
        // not part of the deposit, which is what made the iron and gold cases fail: the debt was
        // consumed and the block stayed air. Copper only passed because Cluster happens to include
        // its centre. Mining a cell the deposit owns is what the test meant all along.
        BlockPos cell = workableCell(level, deposit, origin);
        level.setBlock(cell, Blocks.STONE.defaultBlockState(), 2);

        DepositLedger ledger = DepositLedger.get(level);
        DepositInstance instance = row.describe(dimension);
        check(ledger.register(instance).mayMaterialize(), path + " would not register");

        MaterializationService.Result placement =
                MaterializationService.materialize(level, deposit, List.of(cell), 8);
        check(level.getBlockState(cell).is(block),
                path + " did not materialise as " + block + " into approved host stone at " + cell
                        + ": " + placement.describeRejections());

        takeDrops(level, cell);
        ServerPlayer miner = miner(level, pickaxe());
        breakThroughTheEventBus(level, cell, miner);

        List<ItemStack> drops = takeDrops(level, cell);
        check(drops.size() == 1, path + " yielded " + drops.size() + " stacks: " + drops);
        check(drops.get(0).getItem() instanceof PurityOreItem,
                path + " yielded " + drops.get(0).getItem() + ", not a purity ore");
        check(miner.getMainHandItem().getDamageValue() == 1,
                path + " cost " + miner.getMainHandItem().getDamageValue() + " durability");

        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        BrokenBlockData record = storage.getBrokenBlocks().get(cell);
        check(record != null, path + " was not enrolled for restoration");
        check(record.originalState.is(block), path + " would restore as " + record.originalState);

        long delay = Resources.regenerationMillis(record.originalState).orElseThrow();
        check(delay == 6L * 60L * 60L * 1000L,
                path + "'s regeneration resolved to " + delay + "ms rather than the configured 6 hours");

        storage.add(new BrokenBlockData(record.pos, record.originalState,
                record.brokenTime - delay - 1_000L, record.playerUUID,
                record.brokenTime - delay - 1_000L, record.instanceId, record.resourceId, 0L, 0));

        // Restoration runs on the scheduler's own bounded cadence -- a fixed number of debts per
        // pass, shared with every other test in this world -- so this waits for several passes rather
        // than one. The debt was backdated past its due time above, so only the queue is pending.
        // Restoration, asserted as the production transition rather than as a race.
        //
        // BlockRestoreHandler.restore is exactly what the scheduler calls, and it is what the
        // milestone 11 amendment found to be lying: it discarded setBlockAndUpdate's result and
        // returned an unconditional true, so a refused write consumed the debt anyway. Calling it
        // here asserts the transition itself -- occupancy, target resolution, write, read-back --
        // deterministically.
        //
        // The scheduler's own ordering, budget and backoff are covered exhaustively by the
        // milestone 4 and 9 suites, and the full scheduler-driven round trip is proven end to end
        // by ManagedCoalLifecycleGameTests. Waiting on the shared queue here as well only made this
        // test depend on which other tests in the world happened to run first.
        BrokenBlockData due = storage.getBrokenBlocks().get(cell);
        check(due != null, path + " lost its debt before restoration");
        check(BlockRestoreHandler.restore(level, due),
                path + " refused restoration: occupants="
                        + level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                                new net.minecraft.world.phys.AABB(cell)).size()
                        + " state=" + level.getBlockState(cell).getBlock());
        check(level.getBlockState(cell).is(block), path + " did not come back");
        storage.remove(cell);
        check(DepositLedger.get(level).byId(instanceId).isPresent(),
                path + " lost its ledger entry across restoration");
        onRestored.run();
    }

    /* ------------------------------------------------------------------ */
    /*  End to end, one per metal                                          */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, timeoutTicks = 260)
    public static void curatedIronIsPlacedMinedAndComesBack(GameTestHelper helper) {
        runLifecycle(helper, "iron", helper::succeed);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260)
    public static void curatedGoldIsPlacedMinedAndComesBack(GameTestHelper helper) {
        runLifecycle(helper, "gold", helper::succeed);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260)
    public static void curatedCopperIsPlacedMinedAndComesBack(GameTestHelper helper) {
        runLifecycle(helper, "copper", helper::succeed);
    }

    /* ------------------------------------------------------------------ */
    /*  The M6 policies still apply to a curated metal                      */
    /* ------------------------------------------------------------------ */

    /**
     * A curated metal obeys every extraction policy the platform already had.
     *
     * <p>Integration regression, not a re-specification: wrong tools, automation and Creative are
     * the extraction policy's rules, and this proves the new supply channel did not route around
     * them — including the creative rule's two halves, plain removal without the Britannia
     * pickaxe and the full managed flow with it.
     */
    @GameTest(template = TEMPLATE)
    public static void aCuratedMetalObeysEveryExtractionPolicy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(NODE);

        for (String path : List.of("iron", "gold", "copper")) {
            Block block = managedBlock(resource(path));

            record Attempt(String label, ItemStack tool) {
            }
            for (Attempt attempt : List.of(
                    new Attempt("a Britannia shovel", shovel()),
                    new Attempt("a vanilla pickaxe", new ItemStack(Items.DIAMOND_PICKAXE)),
                    new Attempt("a two-handed axe", new ItemStack(ItemRegistry.TWO_HANDED_AXE.get())),
                    new Attempt("a bare hand", ItemStack.EMPTY))) {

                level.setBlock(cell, block.defaultBlockState(), 2);
                takeDrops(level, cell);
                ServerPlayer player = miner(level, attempt.tool());

                breakThroughTheEventBus(level, cell, player);

                check(level.getBlockState(cell).is(block),
                        path + " was removed by " + attempt.label());
                check(takeDrops(level, cell).isEmpty(),
                        path + " yielded something to " + attempt.label());
                check(player.getMainHandItem().getDamageValue() == 0,
                        path + " cost durability for a refusal with " + attempt.label());
            }

            // Automation, holding the correct tool.
            level.setBlock(cell, block.defaultBlockState(), 2);
            FakePlayer machine = FakePlayerFactory.getMinecraft(level);
            machine.setItemInHand(InteractionHand.MAIN_HAND, pickaxe());
            breakThroughTheEventBus(level, cell, machine);
            check(level.getBlockState(cell).is(block), path + " was mined by a fake player");
            check(takeDrops(level, cell).isEmpty(), path + " paid a fake player");

            // Creative without the Britannia pickaxe: an ordinary creative removal, nothing paid.
            level.setBlock(cell, block.defaultBlockState(), 2);
            ServerPlayer operator = miner(level, ItemStack.EMPTY);
            operator.setGameMode(GameType.CREATIVE);
            breakThroughTheEventBus(level, cell, operator);
            check(!level.getBlockState(cell).is(block),
                    path + " survived a bare-handed Creative break; a managed path is still"
                            + " intercepting an ordinary creative break");
            check(takeDrops(level, cell).isEmpty(), path + " paid a bare-handed Creative operator");

            // Creative attacking with the Britannia pickaxe: a tester, and the managed flow runs.
            level.setBlock(cell, block.defaultBlockState(), 2);
            operator.setItemInHand(InteractionHand.MAIN_HAND, pickaxe());
            breakThroughTheEventBus(level, cell, operator);
            check(!level.getBlockState(cell).is(block),
                    path + " was not extracted by a Creative tester's Britannia pickaxe");
            List<ItemStack> testerDrops = takeDrops(level, cell);
            check(testerDrops.size() == 1 && testerDrops.get(0).getItem() instanceof PurityOreItem,
                    path + " did not pay a Creative tester the purity ore, got " + testerDrops);
            check(BrokenBlockDataStorage.get(level).getBrokenBlocks().get(cell) != null,
                    path + " extracted by a Creative tester filed no restoration debt");
            BrokenBlockDataStorage.get(level).remove(cell);
        }
        helper.succeed();
    }

    /** Fortune cannot multiply a curated metal's yield, and Silk Touch cannot carry it away. */
    @GameTest(template = TEMPLATE)
    public static void enchantmentsBuyNothingFromACuratedMetal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(NODE);
        var enchantments = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);

        for (String path : List.of("iron", "gold", "copper")) {
            Block block = managedBlock(resource(path));
            for (var which : List.of(net.minecraft.world.item.enchantment.Enchantments.FORTUNE,
                    net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH)) {

                level.setBlock(cell, block.defaultBlockState(), 2);
                takeDrops(level, cell);
                ItemStack tool = pickaxe();
                tool.enchant(enchantments.getHolderOrThrow(which), 3);
                ServerPlayer player = miner(level, tool);

                breakThroughTheEventBus(level, cell, player);

                List<ItemStack> drops = takeDrops(level, cell);
                check(drops.size() == 1,
                        path + " with " + which.location().getPath() + " yielded " + drops.size()
                                + " stacks");
                check(drops.get(0).getCount() == 1,
                        path + " with " + which.location().getPath() + " yielded "
                                + drops.get(0).getCount() + " items");
                check(drops.get(0).getItem() instanceof PurityOreItem,
                        path + " with " + which.location().getPath() + " yielded "
                                + drops.get(0).getItem());
                check(!drops.get(0).is(block.asItem()),
                        path + " with " + which.location().getPath() + " handed over the block itself");
            }
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Multi-chunk identity                                               */
    /* ------------------------------------------------------------------ */

    /**
     * Each metal's normal geometry spans chunks, and one deposit stays one deposit.
     *
     * <p>All three measure at 100% multi-chunk in the distribution audit, so this is the ordinary
     * case rather than a contrived one. Forward and reverse slice order are compared, the ledger is
     * checked for a single entry, and the loaded-chunk count is checked before and after.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void eachMetalIsOneDepositAcrossEveryChunkItTouches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().location().toString();
        BlockPos corner = alignedToChunkCorner(helper.absolutePos(NODE));
        DepositLedger ledger = DepositLedger.get(level);

        for (String path : List.of("iron", "gold", "copper")) {
            CuratedDepositTestRows row = CuratedDepositTestRows.of(path, corner, 20);
            long instanceId = row.identity(dimension);

            PlannedDeposit deposit = row.plan(dimension);
            List<ChunkPos> touched = deposit.touchedChunks();
            check(touched.size() >= 2,
                    path + " spans only " + touched.size() + " chunk(s), so it proves nothing");

            DepositInstance instance = row.describe(dimension);

            int ledgerBefore = ledger.size();
            int loadedBefore = level.getChunkSource().getLoadedChunksCount();

            Set<BlockPos> forwards = new LinkedHashSet<>();
            int created = 0;
            for (ChunkPos chunk : touched) {
                DepositLedger.Registration registration = ledger.register(instance);
                check(registration.mayMaterialize(), path + " refused at " + chunk);
                if (registration.outcome() == DepositLedger.Outcome.REGISTERED) {
                    created++;
                }
                forwards.addAll(deposit.positionsIn(chunk));
            }

            List<ChunkPos> reversed = new ArrayList<>(touched);
            java.util.Collections.reverse(reversed);
            Set<BlockPos> backwards = new LinkedHashSet<>();
            for (ChunkPos chunk : reversed) {
                check(ledger.register(instance).outcome() == DepositLedger.Outcome.ALREADY_REGISTERED,
                        path + " created a second deposit on a repeat pass over " + chunk);
                backwards.addAll(deposit.positionsIn(chunk));
            }

            check(created == 1, path + " created " + created + " deposits across its chunks");
            check(ledger.size() == ledgerBefore + 1,
                    path + " grew the ledger by " + (ledger.size() - ledgerBefore));
            check(forwards.equals(backwards), path + " differed between forward and reverse order");
            check(forwards.size() == deposit.count(),
                    path + " sliced to " + forwards.size() + " of " + deposit.count() + " cells");
            check(level.getChunkSource().getLoadedChunksCount() == loadedBefore,
                    path + " loaded extra chunks while slicing");
        }
        helper.succeed();
    }

    /**
     * A curated identity is a pure function of the row, so a restart re-derives it exactly.
     *
     * <p>This is what makes a Rails import resumable and idempotent: the server keeps no memory of
     * how it computed the id, because there is nothing to remember — the row's own immutable
     * parameters are the whole input.
     */
    @GameTest(template = TEMPLATE)
    public static void curatedMetalIdentityIsStableAcrossARestart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().location().toString();
        BlockPos origin = helper.absolutePos(NODE);

        for (String path : List.of("iron", "gold", "copper")) {
            CuratedDepositTestRows row = CuratedDepositTestRows.of(path, origin, 14);

            long first = row.identity(dimension);
            PlannedDeposit firstPlan = row.plan(dimension);

            // Re-deriving after a notional restart: a fresh row object, same parameters.
            CuratedDepositTestRows again = CuratedDepositTestRows.of(path, origin, 14);
            check(again.identity(dimension) == first,
                    path + " re-derived a different identity from the same Rails parameters");
            check(again.plan(dimension).positions().equals(firstPlan.positions()),
                    path + " re-derived different geometry from the same Rails parameters");

            // And a different radius is deliberately a different deposit, per the identity contract.
            check(CuratedDepositTestRows.of(path, origin, 15).identity(dimension) != first,
                    path + " gave two different radii the same identity");
        }
        helper.succeed();
    }

    private static BlockPos alignedToChunkCorner(BlockPos near) {
        return new BlockPos((near.getX() >> 4) << 4, near.getY(), (near.getZ() >> 4) << 4);
    }
}
