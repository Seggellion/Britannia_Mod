package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.deposit.ManagedDepositExtraction;
import com.seggellion.britannia_mod.deposit.ManagedDeposits;
import com.seggellion.britannia_mod.event.BlockRestoreHandler;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.MiningBreakGate;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * OreVein milestone 2: the pickaxe and the shovel are parallel extraction tools, and the resource
 * definition's item tag is the only thing that grants either of them authority.
 *
 * <h2>Why the negatives matter more than the positives</h2>
 * A test suite that only proves "the right tool works" cannot tell a real rule from an accident.
 * Before this milestone the Mining side authorised by class — {@code instanceof QualityToolItem} —
 * which happened to admit exactly the right item, but would have admitted any future quality tool
 * too, and said nothing at all about a shovel meeting an ore. So the cross-family refusals are
 * tested in both directions and for every tool family:
 *
 * <pre>
 *                        stone / ore      clay / silica
 *   Britannia pickaxe        yes               no
 *   Britannia shovel          no              yes
 *   two-handed axe            no               no
 *   vanilla pickaxe           no               no
 *   vanilla shovel            no               no
 *   unrelated item            no               no
 *   bare hand                 no               no
 * </pre>
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ResourceExtractionMatrixGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    private ResourceExtractionMatrixGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static ItemStack pickaxe() {
        return ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3);
    }

    private static ItemStack shovel() {
        return ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3);
    }

    /** Every tool that must be refused by both families. */
    private static List<ItemStack> unauthorizedEverywhere() {
        return List.of(
                new ItemStack(ItemRegistry.TWO_HANDED_AXE.get()),
                new ItemStack(Items.DIAMOND_PICKAXE),
                new ItemStack(Items.DIAMOND_SHOVEL),
                new ItemStack(Items.STICK),
                ItemStack.EMPTY);
    }

    private static String name(ItemStack stack) {
        return stack.isEmpty() ? "a bare hand" : stack.getItem().getDescriptionId();
    }

    private static ServerPlayer miner(GameTestHelper helper, ItemStack tool) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, 100.0f);
        return player;
    }

    /* ------------------------------------------------------------------ */
    /*  The tags themselves                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Every extraction tag authorises at least one item.
     *
     * <p>A Minecraft tag always "exists", so an unresolved extraction rule does not throw — it
     * simply authorises nobody, and the resource becomes quietly unworkable. That is the silent
     * failure this milestone was told not to allow, so the server-start validator checks it and
     * this drives the same validator in a live world.
     */
    @GameTest(template = TEMPLATE)
    public static void everyExtractionTagAuthorisesSomething(GameTestHelper helper) {
        Resources.validateExtractionTagsResolve();

        check(ManagedDeposits.isAuthorizedTool(ManagedDeposits.CLAY, shovel()),
                "the clay tag must bind to the project shovel in a running world");
        check(ManagedDeposits.isAuthorizedTool(ManagedDeposits.SILICA_SAND, shovel()),
                "the silica tag must bind to the project shovel in a running world");
        helper.succeed();
    }

    /** Registry-backed content resolves: every catalogued block and yield item really exists. */
    @GameTest(template = TEMPLATE)
    public static void everyCataloguedBlockAndYieldItemResolves(GameTestHelper helper) {
        Resources.validateAgainstRegistries();
        check(ResourceCatalog.instance().all().size() == 29,
                "the shipped catalogue must describe all 29 resources");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Mining family: stone and ore                                       */
    /* ------------------------------------------------------------------ */

    private static void miningGateSays(
            GameTestHelper helper, Block node, ItemStack tool, MiningBreakGate.ResultType expected) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, node);
        ServerPlayer player = miner(helper, tool);

        MiningBreakGate.ResultType actual =
                MiningBreakGate.evaluate(player, level.getBlockState(absolute), level, absolute).type();
        check(actual == expected, name(tool) + " against " + node.getName().getString()
                + " must resolve " + expected + ", not " + actual);
    }

    /** The project pickaxe is authorised for the Mining ladder, top and bottom. */
    @GameTest(template = TEMPLATE)
    public static void theProjectPickaxeIsAuthorisedForStoneAndOre(GameTestHelper helper) {
        miningGateSays(helper, Blocks.STONE, pickaxe(), MiningBreakGate.ResultType.ELIGIBLE);
        miningGateSays(helper, BlockRegistry.VERITE_ORE.get(), pickaxe(),
                MiningBreakGate.ResultType.ELIGIBLE);
        miningGateSays(helper, Blocks.IRON_ORE, pickaxe(), MiningBreakGate.ResultType.ELIGIBLE);
        miningGateSays(helper, BlockRegistry.SANDSTONE_DEPOSIT.get(), pickaxe(),
                MiningBreakGate.ResultType.ELIGIBLE);
        helper.succeed();
    }

    /**
     * The Britannia shovel is a managed-resource tool, and that buys it nothing here. It is not in
     * {@code britannia_mod:mining_pickaxes}, so it has no authority over stone or ore — being
     * another custom quality tool is not the same as being the configured one.
     */
    @GameTest(template = TEMPLATE)
    public static void theProjectShovelIsRefusedByStoneAndOre(GameTestHelper helper) {
        miningGateSays(helper, Blocks.STONE, shovel(), MiningBreakGate.ResultType.WRONG_TOOL);
        miningGateSays(helper, BlockRegistry.SILVER_ORE.get(), shovel(),
                MiningBreakGate.ResultType.WRONG_TOOL);
        helper.succeed();
    }

    /** Every other tool family, refused by the Mining side at full skill. */
    @GameTest(template = TEMPLATE)
    public static void everyUnauthorisedToolIsRefusedByStoneAndOre(GameTestHelper helper) {
        for (ItemStack tool : unauthorizedEverywhere()) {
            miningGateSays(helper, Blocks.STONE, tool, MiningBreakGate.ResultType.WRONG_TOOL);
            miningGateSays(helper, BlockRegistry.VERITE_ORE.get(), tool,
                    MiningBreakGate.ResultType.WRONG_TOOL);
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Sediment family: clay and silica                                   */
    /* ------------------------------------------------------------------ */

    private static void depositSays(
            GameTestHelper helper,
            ResourceDefinition deposit,
            ItemStack tool,
            ManagedDepositExtraction.Result expected) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, ManagedDeposits.block(deposit));
        ServerPlayer player = miner(helper, tool);

        ManagedDepositExtraction.Result actual =
                ManagedDepositExtraction.extract(level, absolute, player, tool);
        check(actual == expected, name(tool) + " against " + deposit.id()
                + " must resolve " + expected + ", not " + actual);

        if (expected == ManagedDepositExtraction.Result.EXTRACTED) {
            check(!level.getBlockState(absolute).is(ManagedDeposits.block(deposit)),
                    deposit.id() + " must be emptied by a successful extraction");
        } else {
            helper.assertBlockPresent(ManagedDeposits.block(deposit), NODE);
        }
    }

    /** The project shovel works both beds — the same shovel, through two resource-specific tags. */
    @GameTest(template = TEMPLATE)
    public static void theProjectShovelWorksClayAndSilica(GameTestHelper helper) {
        depositSays(helper, ManagedDeposits.CLAY, shovel(),
                ManagedDepositExtraction.Result.EXTRACTED);
        depositSays(helper, ManagedDeposits.SILICA_SAND, shovel(),
                ManagedDepositExtraction.Result.EXTRACTED);
        helper.succeed();
    }

    /**
     * The pickaxe is refused by both beds. Being the Mining tool grants no authority over sediment:
     * these are parallel systems, not a hierarchy with the pickaxe at the top.
     */
    @GameTest(template = TEMPLATE)
    public static void theProjectPickaxeIsRefusedByClayAndSilica(GameTestHelper helper) {
        depositSays(helper, ManagedDeposits.CLAY, pickaxe(),
                ManagedDepositExtraction.Result.WRONG_TOOL);
        depositSays(helper, ManagedDeposits.SILICA_SAND, pickaxe(),
                ManagedDepositExtraction.Result.WRONG_TOOL);
        helper.succeed();
    }

    /** Every other tool family, including a vanilla shovel, refused by both beds. */
    @GameTest(template = TEMPLATE)
    public static void everyUnauthorisedToolIsRefusedByClayAndSilica(GameTestHelper helper) {
        for (ItemStack tool : unauthorizedEverywhere()) {
            depositSays(helper, ManagedDeposits.CLAY, tool,
                    ManagedDepositExtraction.Result.WRONG_TOOL);
            depositSays(helper, ManagedDeposits.SILICA_SAND, tool,
                    ManagedDepositExtraction.Result.WRONG_TOOL);
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Behaviour preserved through the migration                          */
    /* ------------------------------------------------------------------ */

    /**
     * Clay still yields exactly one vanilla clay ball, and silica exactly one silica sand — the
     * identities the economy prices. Read from the catalogue now, but unchanged in value.
     */
    @GameTest(template = TEMPLATE)
    public static void theBedsStillYieldTheIdentitiesTheEconomyPrices(GameTestHelper helper) {
        ItemStack clay = ManagedDeposits.yieldStack(ManagedDeposits.CLAY);
        check(clay.is(Items.CLAY_BALL) && clay.getCount() == 1,
                "clay must still be one vanilla clay ball, found " + clay);

        ItemStack silica = ManagedDeposits.yieldStack(ManagedDeposits.SILICA_SAND);
        check(silica.is(ItemRegistry.SILICA_SAND.get()) && silica.getCount() == 1,
                "silica must still be one silica sand, found " + silica);
        helper.succeed();
    }

    /**
     * The regeneration delay is now per resource, resolved at runtime from the recorded state.
     *
     * <p>Silica's approved 24 hours is the first value in the mod that differs from the historical
     * global six, and this is where the data becomes behaviour: the restoration sweep asks the
     * catalogue how long this particular node must wait.
     */
    @GameTest(template = TEMPLATE)
    public static void regenerationDelayIsResolvedPerResource(GameTestHelper helper) {
        long sixHours = 6L * 60 * 60 * 1000;
        long twentyFourHours = 24L * 60 * 60 * 1000;

        check(Resources.regenerationMillis(
                        BlockRegistry.SILICA_SAND_DEPOSIT.get().defaultBlockState())
                .orElse(-1) == twentyFourHours, "silica must resolve 24 hours");
        check(Resources.regenerationMillis(
                        BlockRegistry.CLAY_DEPOSIT.get().defaultBlockState())
                .orElse(-1) == sixHours, "clay must keep six hours");
        check(Resources.regenerationMillis(BlockRegistry.VERITE_ORE.get().defaultBlockState())
                .orElse(-1) == sixHours, "the ore ladder must keep six hours");
        check(Resources.regenerationMillis(Blocks.STONE.defaultBlockState())
                .orElse(-1) == sixHours, "stone must keep six hours");

        // Unmanaged blocks resolve nothing, so the sweep falls back to its own default rather than
        // never becoming due.
        check(Resources.regenerationMillis(Blocks.DIRT.defaultBlockState()).isEmpty(),
                "an unmanaged block must not claim a regeneration policy");
        check(BlockRestoreHandler.RESTORE_DELAY == sixHours,
                "the global default is still six hours for anything without a definition");
        helper.succeed();
    }

    /**
     * The depleted state is the definition's, not an assumption at the call site.
     *
     * <p>Behaviour is unchanged — the cell still becomes fluid-aware air — but it now comes from
     * the resource, which is the seam milestone 4 and 6 need when a bed under water should leave
     * something solid behind instead.
     */
    @GameTest(template = TEMPLATE)
    public static void theDepletedStateComesFromTheDefinition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);

        helper.setBlock(NODE, BlockRegistry.CLAY_DEPOSIT.get());
        check(Resources.depletedState(ManagedDeposits.CLAY, level, absolute).isAir(),
                "on dry land the declared depleted state is air, as it always was");
        helper.succeed();
    }
}
