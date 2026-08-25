package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.resource.extraction.ExtractionToolPredicates;
import com.seggellion.britannia_mod.deposit.ManagedDepositExtraction;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The skill-progression remediation, proven end to end on the silica bed (required Mining 5.0,
 * the acceptance example from the defect report): a deposit behaves like a real mineable block.
 *
 * <p>Two defects are pinned here. First, extraction ignored Mining entirely — a digger at 0.0
 * emptied any bed. Second, the Adventure left click WAS the extraction: one swing cancelled the
 * event and mutated the world, with no destroy progress and no {@code BreakEvent}. The first
 * swing is now a pure preflight and the one extraction happens at the completed break, so both
 * are asserted: the acceptance matrix on the skill, and the first swing mutating nothing.
 *
 * <p>What a GameTest cannot reproduce is the client's own destroy-progress timing — the crack
 * animation and the hold duration are driven by a real client's tick loop. The server-side halves
 * of that lifecycle (the CAN_BREAK component that unlocks it, and the break-completion gate) are
 * covered; the visible progress needs the live-client check the report calls out.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class DepositMiningGateGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos BED = new BlockPos(1, 1, 1);
    private static final float SILICA_REQUIREMENT = 5.0f;

    private DepositMiningGateGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static BlockPos placeBed(GameTestHelper helper) {
        helper.setBlock(BED, BlockRegistry.SILICA_SAND_DEPOSIT.get());
        return helper.absolutePos(BED);
    }

    private static ItemStack shovel() {
        return ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3);
    }

    private static ServerPlayer digger(ServerLevel level, String name, float mining) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, name);
        player.setItemInHand(InteractionHand.MAIN_HAND, shovel());
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, mining);
        return player;
    }

    private static List<ItemEntity> dropsNear(ServerLevel level, BlockPos absolute) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3.0D));
    }

    private static void assertInert(GameTestHelper helper, BlockPos absolute, ServerPlayer player,
            String situation) {
        helper.assertBlockPresent(BlockRegistry.SILICA_SAND_DEPOSIT.get(), BED);
        check(dropsNear(helper.getLevel(), absolute).isEmpty(),
                situation + " must award nothing");
        check(!BrokenBlockDataStorage.get(helper.getLevel()).getBrokenBlocks().containsKey(absolute),
                situation + " must create no restoration debt");
        check(player.getMainHandItem().getDamageValue() == 0,
                situation + " must charge no durability");
    }

    /* ------------------------------------------------------------------ */
    /*  The acceptance matrix                                              */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, batch = "deposit_mining_gate", timeoutTicks = 60)
    public static void miningZeroCannotExtract(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        ServerPlayer player = digger(level, "silica-zero", 0.0f);

        ManagedDepositExtraction.Result result = ManagedDepositExtraction.extract(
                level, absolute, player, player.getMainHandItem());

        check(result == ManagedDepositExtraction.Result.INSUFFICIENT_SKILL,
                "Mining 0.0 against a 5.0 bed must be INSUFFICIENT_SKILL, was " + result);
        assertInert(helper, absolute, player, "an under-skilled extraction");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "deposit_mining_gate", timeoutTicks = 60)
    public static void miningJustBelowRequirementCannotExtract(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        ServerPlayer player = digger(level, "silica-four-nine", SILICA_REQUIREMENT - 0.1f);

        ManagedDepositExtraction.Result result = ManagedDepositExtraction.extract(
                level, absolute, player, player.getMainHandItem());

        check(result == ManagedDepositExtraction.Result.INSUFFICIENT_SKILL,
                "Mining 4.9 against a 5.0 bed must be INSUFFICIENT_SKILL, was " + result);
        assertInert(helper, absolute, player, "a just-under-skilled extraction");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "deposit_mining_gate", timeoutTicks = 60)
    public static void miningExactlyAtRequirementExtractsOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        ServerPlayer player = digger(level, "silica-exact", SILICA_REQUIREMENT);

        ManagedDepositExtraction.Result result = ManagedDepositExtraction.extract(
                level, absolute, player, player.getMainHandItem());

        check(result == ManagedDepositExtraction.Result.EXTRACTED,
                "Mining 5.0 against a 5.0 bed must extract (inclusive threshold), was " + result);
        helper.assertBlockNotPresent(BlockRegistry.SILICA_SAND_DEPOSIT.get(), BED);
        List<ItemEntity> drops = dropsNear(level, absolute);
        check(drops.size() == 1, "exactly one yield stack expected, found " + drops.size());
        check(BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(absolute),
                "a committed extraction must schedule its one restoration");

        // The bed is gone, so a second attempt at the same cell resolves no deposit at all:
        // one completed break can never award twice.
        ManagedDepositExtraction.Result second = ManagedDepositExtraction.extract(
                level, absolute, player, player.getMainHandItem());
        check(second == ManagedDepositExtraction.Result.NOT_A_DEPOSIT,
                "the emptied cell must answer NOT_A_DEPOSIT, was " + second);
        check(dropsNear(level, absolute).size() == 1,
                "repeating the break on the emptied cell must not add a second yield");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "deposit_mining_gate", timeoutTicks = 60)
    public static void miningAboveRequirementExtracts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        ServerPlayer player = digger(level, "silica-master", 100.0f);

        ManagedDepositExtraction.Result result = ManagedDepositExtraction.extract(
                level, absolute, player, player.getMainHandItem());
        check(result == ManagedDepositExtraction.Result.EXTRACTED,
                "Mining 100 must extract, was " + result);
        helper.assertBlockNotPresent(BlockRegistry.SILICA_SAND_DEPOSIT.get(), BED);
        helper.succeed();
    }

    /** Unloaded skill data is a refusal, never a zero and never a pass — the ladder's own policy. */
    @GameTest(template = TEMPLATE, batch = "deposit_mining_gate", timeoutTicks = 60)
    public static void unloadedSkillDataFailsClosed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, "silica-unloaded");
        player.setItemInHand(InteractionHand.MAIN_HAND, shovel());
        // Deliberately no applyConfirmedValue: this player's skill state is NOT_LOADED.

        ManagedDepositExtraction.Result result = ManagedDepositExtraction.extract(
                level, absolute, player, player.getMainHandItem());
        check(result == ManagedDepositExtraction.Result.SKILL_DATA_UNAVAILABLE,
                "unloaded skill data must refuse the extraction, was " + result);
        assertInert(helper, absolute, player, "an extraction with unloaded skill data");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  The first swing: a question, never an extraction                   */
    /* ------------------------------------------------------------------ */

    /**
     * The instant-break regression itself: the Adventure left click — the event the retired
     * handler used to extract on — now mutates nothing for an eligible digger and leaves the
     * event uncancelled, which is what hands the swing to the ordinary break lifecycle.
     */
    @GameTest(template = TEMPLATE, batch = "deposit_mining_gate", timeoutTicks = 60)
    public static void adventureLeftClickDoesNotInstantlyExtract(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        ServerPlayer player = digger(level, "silica-swinger", 100.0f);
        player.setGameMode(GameType.ADVENTURE);

        PlayerInteractEvent.LeftClickBlock event = CommonHooks.onLeftClickBlock(
                player, absolute, Direction.UP,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);

        check(!event.isCanceled(),
                "an eligible digger's first swing must proceed into the vanilla lifecycle");
        assertInert(helper, absolute, player, "the first swing");
        helper.succeed();
    }

    /** An under-skilled swing is refused at once: cancelled, and just as inert. */
    @GameTest(template = TEMPLATE, batch = "deposit_mining_gate", timeoutTicks = 60)
    public static void adventureLeftClickRefusesTheUnderSkilled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        ServerPlayer player = digger(level, "silica-early-refusal", 0.0f);
        player.setGameMode(GameType.ADVENTURE);

        PlayerInteractEvent.LeftClickBlock event = CommonHooks.onLeftClickBlock(
                player, absolute, Direction.UP,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);

        check(event.isCanceled(), "an under-skilled swing must be cancelled at the first click");
        assertInert(helper, absolute, player, "an under-skilled first swing");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  The completed break, in adventure, through the vanilla path        */
    /* ------------------------------------------------------------------ */

    /**
     * The whole remediated lifecycle without a client: an adventure digger whose tool carries the
     * CAN_BREAK predicate completes a vanilla server-side break, and the BreakEvent chain turns it
     * into exactly one managed extraction.
     */
    @GameTest(template = TEMPLATE, batch = "deposit_mining_gate", timeoutTicks = 60)
    public static void adventureBreakCompletionRunsTheManagedExtraction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        ServerPlayer player = digger(level, "silica-adventurer", 100.0f);
        player.setGameMode(GameType.ADVENTURE);

        // What ExtractionToolPredicates maintains every tick, applied directly so the
        // test does not depend on tick scheduling.
        AdventureModePredicate predicate =
                ExtractionToolPredicates.predicateFor(player.getMainHandItem());
        check(predicate != null, "the project shovel must earn a deposit CAN_BREAK predicate");
        player.getMainHandItem().set(DataComponents.CAN_BREAK, predicate);

        // destroyBlock reports false here BY DESIGN: the deposit handler cancels the vanilla
        // break and commits the managed extraction in its place, so the assertions are about the
        // outcomes — bed emptied, one yield, one restoration — not about vanilla's own verdict.
        player.gameMode.destroyBlock(absolute);

        helper.assertBlockNotPresent(BlockRegistry.SILICA_SAND_DEPOSIT.get(), BED);
        List<ItemEntity> drops = dropsNear(level, absolute);
        check(drops.size() == 1, "exactly one yield expected from one break, found " + drops.size());
        check(BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(absolute),
                "the completed adventure break must schedule its restoration");
        helper.succeed();
    }

    /** Without the scoped predicate, adventure stays exactly as strict as vanilla makes it. */
    @GameTest(template = TEMPLATE, batch = "deposit_mining_gate", timeoutTicks = 60)
    public static void adventureWithoutThePredicateRemainsRestricted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        ServerPlayer player = digger(level, "silica-restricted", 100.0f);
        player.setGameMode(GameType.ADVENTURE);
        // No CAN_BREAK applied: vanilla's own adventure restriction must hold.

        boolean broke = player.gameMode.destroyBlock(absolute);

        check(!broke, "adventure without the deposit predicate must not break anything");
        helper.assertBlockPresent(BlockRegistry.SILICA_SAND_DEPOSIT.get(), BED);
        helper.succeed();
    }

    /** The predicate authorizes the deposit blocks and nothing else. */
    @GameTest(template = TEMPLATE, batch = "deposit_mining_gate", timeoutTicks = 60)
    public static void thePredicateIsScopedToDepositBlocks(GameTestHelper helper) {
        AdventureModePredicate predicate = ExtractionToolPredicates.predicateFor(shovel());
        check(predicate != null, "the project shovel earns the predicate");
        check(predicate.test(new net.minecraft.world.level.block.state.pattern.BlockInWorld(
                        helper.getLevel(), placeBed(helper), false)),
                "the predicate must authorize a standing bed");
        BlockPos dirt = helper.absolutePos(new BlockPos(3, 1, 1));
        helper.setBlock(new BlockPos(3, 1, 1), net.minecraft.world.level.block.Blocks.DIRT);
        check(!predicate.test(new net.minecraft.world.level.block.state.pattern.BlockInWorld(
                        helper.getLevel(), dirt, false)),
                "the predicate must authorize nothing but the deposit blocks");
        check(ExtractionToolPredicates.predicateFor(
                        new ItemStack(net.minecraft.world.item.Items.IRON_SHOVEL)) == null,
                "a vanilla shovel is in no extraction tag and earns no predicate");
        helper.succeed();
    }
}
