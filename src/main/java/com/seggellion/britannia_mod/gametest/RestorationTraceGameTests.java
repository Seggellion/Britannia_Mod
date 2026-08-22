package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.event.BlockRestoreHandler;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.skill.SkillManager;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Locale;

/**
 * Traces one managed cell all the way from a Rails row to restoration, printing every value.
 *
 * <h2>Why this exists</h2>
 * The milestone 11 amendment found that Vertical (iron) and Snake (gold) deposits had their
 * restoration debt consumed without the resource block returning, while Cluster, Layered and
 * SedimentaryLens restored normally. Two shapes failing and three succeeding is not a shape
 * problem — planners produce coordinates and nothing else — so the divergence had to be found
 * rather than guessed at.
 *
 * <p>This runs the production path for one representative of each shape and reports the values the
 * restoration transition actually depends on, so the first behavioural difference is visible rather
 * than inferred.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class RestorationTraceGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    private RestorationTraceGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static ServerPlayer miner(ServerLevel level, String name, boolean sediment) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, name);
        // A sediment bed is a shovel's business; the ladder is the pickaxe's. Handing over the
        // wrong one would prove only that the tool policy works, which is tested elsewhere.
        ItemStack tool = sediment
                ? ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3)
                : ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, 100.0f);
        return player;
    }

    /** Everything the restoration transition reads, for one resource, in order. */
    private static void trace(GameTestHelper helper, String path, int radius, boolean sediment) {
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().location().toString();
        BlockPos origin = helper.absolutePos(NODE);
        ResourceDefinition resource = ResourceCatalog.instance().byPath(path).orElseThrow();

        CuratedDepositTestRows row = CuratedDepositTestRows.of(path, origin, radius);
        PlannedDeposit deposit = row.plan(dimension);
        long instanceId = row.identity(dimension);
        DepositLedger.get(level).register(row.describe(dimension));

        // A cell of this deposit inside the test's own chunk.
        ChunkPos here = new ChunkPos(origin);
        BlockPos cell = null;
        for (BlockPos candidate : deposit.positionsIn(here)) {
            if (candidate.getY() > level.getMinBuildHeight() + 4
                    && candidate.getY() < level.getMaxBuildHeight() - 1
                    && (cell == null || Math.abs(candidate.getY() - origin.getY())
                            < Math.abs(cell.getY() - origin.getY()))) {
                cell = candidate;
            }
        }
        check(cell != null, path + ": no planned cell in the test's own chunk");

        level.setBlock(cell, (sediment ? Blocks.SAND : Blocks.STONE).defaultBlockState(), 2);
        MaterializationService.materialize(level, deposit, List.of(cell), 8);
        BlockState standing = level.getBlockState(cell);

        System.out.println(String.format(Locale.ROOT,
                "M11TRACE %-20s shape=%-18s instance=%d", path,
                resource.generation().orElseThrow().shape(), instanceId));
        System.out.println(String.format(Locale.ROOT,
                "M11TRACE %-20s cell=%s standing=%s namespace=%s",
                path, cell, standing.getBlock(),
                net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(standing.getBlock()).getNamespace()));
        System.out.println(String.format(Locale.ROOT,
                "M11TRACE %-20s resolves=%s configuredDepleted=%s delay=%d",
                path, Resources.resolve(standing).map(ResourceDefinition::id).orElse("NONE"),
                resource.depleted(),
                Resources.regenerationMillis(standing).orElse(-1L)));

        // Extraction through the production event path.
        ServerPlayer player = miner(level, "trace-" + path, sediment);
        // Stand the player well away: an occupant in the cell blocks restoration by policy.
        player.teleportTo(cell.getX() + 8.5, cell.getY() + 2, cell.getZ() + 8.5);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, cell, standing, player);
        NeoForge.EVENT_BUS.post(event);
        if (!event.isCanceled()) {
            level.destroyBlock(cell, false, player);
        }

        BlockState afterBreak = level.getBlockState(cell);
        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        BrokenBlockData debt = storage.getBrokenBlocks().get(cell);

        System.out.println(String.format(Locale.ROOT,
                "M11TRACE %-20s afterBreak=%s debtRecorded=%s",
                path, afterBreak.getBlock(), debt != null));
        check(debt != null, path + ": extraction enrolled no restoration debt");
        System.out.println(String.format(Locale.ROOT,
                "M11TRACE %-20s debt.originalState=%s debt.resourceId=%s debt.instanceId=%d",
                path, debt.originalState.getBlock(), debt.resourceId, debt.instanceId));

        // The restoration decision, evaluated exactly as production evaluates it.
        boolean fluid = !level.getFluidState(cell).isEmpty();
        boolean air = level.getBlockState(cell).isAir();
        boolean replaceable = level.getBlockState(cell).canBeReplaced();
        boolean blockEntity = level.getBlockEntity(cell) != null;
        List<Entity> occupants = level.getEntitiesOfClass(Entity.class, new AABB(cell));
        boolean policy = BlockRestoreHandler.cellStateAllowsRestoration(
                fluid, air, replaceable, blockEntity);
        boolean canRestore = BlockRestoreHandler.canRestoreInto(level, cell);

        System.out.println(String.format(Locale.ROOT,
                "M11TRACE %-20s fluid=%s air=%s replaceable=%s blockEntity=%s occupants=%d"
                        + " policy=%s canRestoreInto=%s",
                path, fluid, air, replaceable, blockEntity, occupants.size(), policy, canRestore));

        // Every shape must reach the same answers. Two shapes "failing" was never a shape
        // property -- a planner produces coordinates and nothing else -- and this is what says so.
        check(debt.originalState.is(standing.getBlock()),
                path + " recorded " + debt.originalState.getBlock()
                        + " as the state to restore, but the cell held " + standing.getBlock());
        check(debt.resourceId.equals(resource.id()),
                path + " recorded resource " + debt.resourceId);
        check(policy, path + ": the occupancy policy refused a cleared cell");

        // The dropped yield is an occupant of its own cell, so restoration is legitimately blocked
        // until it is gone. Clearing it is what a despawn does six hours later in a real world.
        for (Entity occupant : level.getEntitiesOfClass(Entity.class, new AABB(cell))) {
            occupant.discard();
        }
        check(BlockRestoreHandler.canRestoreInto(level, cell),
                path + ": a cleared, empty cell still refused restoration");

        check(BlockRestoreHandler.restore(level, debt),
                path + ": the production restoration transition refused");
        BlockState afterWrite = level.getBlockState(cell);
        check(afterWrite.is(debt.originalState.getBlock()),
                path + " restored as " + afterWrite.getBlock()
                        + " rather than " + debt.originalState.getBlock());
        System.out.println(String.format(Locale.ROOT,
                "M11TRACE %-20s restored=%s", path, afterWrite.getBlock()));

        storage.remove(cell);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void traceVerticalIron(GameTestHelper helper) {
        trace(helper, "iron", 20, false);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void traceSnakeGold(GameTestHelper helper) {
        trace(helper, "gold", 20, false);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void traceClusterCopper(GameTestHelper helper) {
        trace(helper, "copper", 20, false);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void traceLayeredCoal(GameTestHelper helper) {
        trace(helper, "coal", 20, false);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void traceSedimentaryLensSilica(GameTestHelper helper) {
        trace(helper, "silica_sand_deposit", 10, true);
        helper.succeed();
    }
}
