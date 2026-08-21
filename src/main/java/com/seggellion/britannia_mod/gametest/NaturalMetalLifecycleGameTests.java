package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
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
import com.seggellion.britannia_mod.resource.natural.NaturalDepositSelector;
import com.seggellion.britannia_mod.resource.natural.NaturalGeneration;
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
 * Milestone 10A: iron, gold and copper from natural selection through to restoration.
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
public final class NaturalMetalLifecycleGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    private NaturalMetalLifecycleGameTests() {
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

    /** The block a resource's natural generation actually places. */
    private static Block managedBlock(ResourceDefinition resource) {
        return Resources.block(resource.generation().orElseThrow().blockId());
    }

    private static ServerPlayer miner(ServerLevel level, ItemStack tool) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, "metal-miner");
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, 100.0f);
        return player;
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

    /** The first owner cell that actually produces a candidate for this resource. */
    private static NaturalDepositSelector.Candidate anyCandidate(ResourceDefinition resource) {
        NaturalGeneration natural = resource.natural().orElseThrow();
        for (int cellX = 0; cellX < 40; cellX++) {
            for (int cellZ = 0; cellZ < 40; cellZ++) {
                var candidate = NaturalDepositSelector.candidateFor(4242L, resource, natural, cellX, cellZ);
                if (candidate.isPresent()) {
                    return candidate.get();
                }
            }
        }
        throw new GameTestAssertException(resource.id() + " produced no candidate in 1600 cells");
    }

    /**
     * The whole chain for one metal: selection → identity → planner → ledger → materialisation →
     * extraction → debt → restoration.
     */
    private static void runLifecycle(GameTestHelper helper, String path, Runnable onRestored) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(NODE);
        String dimension = level.dimension().location().toString();
        ResourceDefinition resource = resource(path);
        Block block = managedBlock(resource);

        // A real natural candidate supplies the identity and the planner seed; the origin is moved
        // to the test cell so the slice lands somewhere the harness can see.
        NaturalDepositSelector.Candidate candidate = anyCandidate(resource);
        long instanceId = DepositIdentity.natural(level.getSeed(), dimension, resource.id(),
                cell.getX(), cell.getZ(), resource.natural().orElseThrow().salt());

        level.setBlock(cell, Blocks.STONE.defaultBlockState(), 2);
        PlannedDeposit deposit = PlacementPlanner.plan(resource, dimension, cell,
                candidate.radius(), ShapeRotation.XZ, candidate.plannerSeed());

        DepositLedger ledger = DepositLedger.get(level);
        DepositInstance instance = DepositRegistrar.describe(
                deposit, instanceId, DepositSource.NATURAL, "natural|m10a|" + path);
        check(ledger.register(instance).mayMaterialize(), path + " would not register");

        MaterializationService.materialize(level, deposit, List.of(cell), 8);
        check(level.getBlockState(cell).is(block),
                path + " did not materialise as " + block + " into approved host stone");

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

        helper.runAfterDelay(30L, () -> {
            check(level.getBlockState(cell).is(block), path + " did not come back");
            check(DepositLedger.get(level).byId(instanceId).isPresent(),
                    path + " lost its ledger entry across restoration");
            onRestored.run();
        });
    }

    /* ------------------------------------------------------------------ */
    /*  End to end, one per metal                                          */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void naturalIronGeneratesIsMinedAndComesBack(GameTestHelper helper) {
        runLifecycle(helper, "iron", helper::succeed);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void naturalGoldGeneratesIsMinedAndComesBack(GameTestHelper helper) {
        runLifecycle(helper, "gold", helper::succeed);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void naturalCopperGeneratesIsMinedAndComesBack(GameTestHelper helper) {
        runLifecycle(helper, "copper", helper::succeed);
    }

    /* ------------------------------------------------------------------ */
    /*  The M6 policies still apply to a naturally generated metal          */
    /* ------------------------------------------------------------------ */

    /**
     * A naturally generated metal obeys every extraction policy the platform already had.
     *
     * <p>Integration regression, not a re-specification: wrong tools, automation and Creative are
     * milestone 6's rules, and this proves the new supply channel did not route around them.
     */
    @GameTest(template = TEMPLATE)
    public static void aNaturallyGeneratedMetalObeysEveryExtractionPolicy(GameTestHelper helper) {
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

            // Creative, on a sited deposit.
            level.setBlock(cell, block.defaultBlockState(), 2);
            ServerPlayer operator = miner(level, pickaxe());
            operator.setGameMode(GameType.CREATIVE);
            breakThroughTheEventBus(level, cell, operator);
            check(level.getBlockState(cell).is(block),
                    path + " was deleted by an ordinary Creative break");
            check(takeDrops(level, cell).isEmpty(), path + " paid a Creative operator");
        }
        helper.succeed();
    }

    /** Fortune cannot multiply a natural metal's yield, and Silk Touch cannot carry it away. */
    @GameTest(template = TEMPLATE)
    public static void enchantmentsBuyNothingFromANaturalMetal(GameTestHelper helper) {
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
            ResourceDefinition resource = resource(path);
            NaturalDepositSelector.Candidate candidate = anyCandidate(resource);
            long instanceId = DepositIdentity.natural(level.getSeed(), dimension, resource.id(),
                    corner.getX(), corner.getZ(), resource.natural().orElseThrow().salt());

            PlannedDeposit deposit = PlacementPlanner.plan(resource, dimension, corner,
                    candidate.radius(), ShapeRotation.XZ, candidate.plannerSeed());
            List<ChunkPos> touched = deposit.touchedChunks();
            check(touched.size() >= 2,
                    path + " spans only " + touched.size() + " chunk(s), so it proves nothing");

            DepositInstance instance = DepositRegistrar.describe(
                    deposit, instanceId, DepositSource.NATURAL, "natural|m10a-span|" + path);

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

    /** Natural identity is a pure function of the cell, so a restart re-derives it exactly. */
    @GameTest(template = TEMPLATE)
    public static void naturalMetalIdentityIsStableAcrossARestart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().location().toString();

        for (String path : List.of("iron", "gold", "copper")) {
            ResourceDefinition resource = resource(path);
            NaturalGeneration natural = resource.natural().orElseThrow();
            var candidate = NaturalDepositSelector
                    .candidateFor(99L, resource, natural, 3, 5).orElse(null);
            if (candidate == null) {
                continue;
            }
            long expected = DepositIdentity.natural(
                    99L, natural.dimensionId(), resource.id(), 3, 5, natural.salt());
            check(candidate.instanceId() == expected,
                    path + " does not use the milestone 4 natural identity contract");

            // Re-deriving after a notional restart gives the identical candidate and seed.
            var again = NaturalDepositSelector
                    .candidateFor(99L, resource, natural, 3, 5).orElseThrow();
            check(again.equals(candidate), path + " re-derived a different candidate");
            check(again.plannerSeed() == candidate.plannerSeed(),
                    path + " re-derived a different planner seed");
        }
        helper.succeed();
    }

    private static BlockPos alignedToChunkCorner(BlockPos near) {
        return new BlockPos((near.getX() >> 4) << 4, near.getY(), (near.getZ() >> 4) << 4);
    }
}
