package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.mining.MiningBreakGate;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Mining milestone 3: end-to-end proof that the break gate is server-authoritative and that a
 * denial is completely inert — same block, zero drops, zero durability, zero skill movement,
 * zero restoration records — while an authorized break still runs the full managed flow.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class MiningGateGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private MiningGateGameTests() {
    }

    private static ServerPlayer survivalMiner(GameTestHelper helper, float miningSkill) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));
        SkillManager.applyConfirmedValue(player, MiningBreakGate.SKILL_ID, miningSkill);
        return player;
    }

    private static List<ItemEntity> dropsNear(GameTestHelper helper, BlockPos absolute) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3.0));
    }

    private static boolean hasRestoreRecord(ServerLevel level, BlockPos absolute) {
        return BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(absolute);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(message);
        }
    }

    @GameTest(template = TEMPLATE)
    public static void underSkilledBreakIsCompletelyInert(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.SILVER_ORE.get());

        ServerPlayer lowMiner = survivalMiner(helper, 54.9f);
        boolean broke = lowMiner.gameMode.destroyBlock(absolute);

        check(!broke, "a denied break reports failure");
        helper.assertBlockPresent(BlockRegistry.SILVER_ORE.get(), relative);
        check(dropsNear(helper, absolute).isEmpty(), "a denied break must produce zero drops");
        check(!hasRestoreRecord(level, absolute), "a denied break must schedule zero restorations");
        check(lowMiner.getMainHandItem().getDamageValue() == 0,
                "a denied break must consume zero durability");
        check(SkillManager.getSkill(lowMiner, MiningBreakGate.SKILL_ID) == 54.9f,
                "a denied break must award zero Mining");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void twoPlayersWithDifferentSkillsSeeDifferentOutcomes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lowRelative = new BlockPos(1, 1, 1);
        BlockPos highRelative = new BlockPos(3, 1, 1);
        BlockPos lowAbsolute = helper.absolutePos(lowRelative);
        BlockPos highAbsolute = helper.absolutePos(highRelative);
        helper.setBlock(lowRelative, BlockRegistry.SILVER_ORE.get());
        helper.setBlock(highRelative, BlockRegistry.SILVER_ORE.get());

        ServerPlayer lowMiner = survivalMiner(helper, 54.9f);
        ServerPlayer highMiner = survivalMiner(helper, 55.0f);

        lowMiner.gameMode.destroyBlock(lowAbsolute);
        highMiner.gameMode.destroyBlock(highAbsolute);

        helper.assertBlockPresent(BlockRegistry.SILVER_ORE.get(), lowRelative);
        check(!hasRestoreRecord(level, lowAbsolute), "denied node must not be scheduled");

        helper.assertBlockNotPresent(BlockRegistry.SILVER_ORE.get(), highRelative);
        List<ItemEntity> drops = dropsNear(helper, highAbsolute);
        check(drops.size() == 1, "exactly one drop expected, found " + drops.size());
        check(drops.getFirst().getItem().getItem() instanceof PurityOreItem,
                "the managed flow must drop a PurityOreItem");
        check(hasRestoreRecord(level, highAbsolute),
                "an authorized managed break must schedule exactly its one restoration");
        // Exact-threshold inclusivity. Since milestone 4 an authorized break also rolls one
        // activation, so the successful miner sits at the threshold or exactly one 0.1 above it.
        float highSkill = SkillManager.getSkill(highMiner, MiningBreakGate.SKILL_ID);
        check(highSkill == 55.0f || Math.abs(highSkill - 55.1f) < 1.0e-4f,
                "an authorized break must award at most one 0.1 activation, got " + highSkill);
        check(SkillManager.getSkill(lowMiner, MiningBreakGate.SKILL_ID) == 54.9f,
                "denial must not move the low miner's skill");
        helper.succeed();
    }

    /**
     * Milestone 9: crossing a threshold takes effect on the very next attempt — same session, same
     * player, no reconnect and no relog. The gate reads authoritative server state every time, so
     * there is nothing to invalidate.
     */
    @GameTest(template = TEMPLATE)
    public static void crossingTheThresholdTakesEffectImmediately(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos firstRelative = new BlockPos(1, 1, 1);
        BlockPos secondRelative = new BlockPos(3, 1, 1);
        BlockPos first = helper.absolutePos(firstRelative);
        BlockPos second = helper.absolutePos(secondRelative);
        helper.setBlock(firstRelative, BlockRegistry.SILVER_ORE.get());
        helper.setBlock(secondRelative, BlockRegistry.SILVER_ORE.get());

        ServerPlayer miner = survivalMiner(helper, 54.9f);
        check(!miner.gameMode.destroyBlock(first), "precondition: 54.9 is denied");
        helper.assertBlockPresent(BlockRegistry.SILVER_ORE.get(), firstRelative);

        // The skill rises mid-session, exactly as a gain or a Guildmaster purchase would deliver it.
        SkillManager.applyConfirmedValue(miner, MiningBreakGate.SKILL_ID, 55.0f);
        miner.gameMode.destroyBlock(second);

        // Success is judged by the world, not by destroyBlock's return value: the managed flow
        // cancels the event and removes the block itself, so an authorized managed break reports
        // false exactly like a denial does.
        helper.assertBlockNotPresent(BlockRegistry.SILVER_ORE.get(), secondRelative);
        check(hasRestoreRecord(level, second),
                "the very next attempt must mine -- no reconnect may be required");
        helper.assertBlockPresent(BlockRegistry.SILVER_ORE.get(), firstRelative);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stoneTrainsAtZeroAndRunsTheManagedFlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.STONE);

        ServerPlayer miner = survivalMiner(helper, 0.0f);
        miner.gameMode.destroyBlock(absolute);

        helper.assertBlockNotPresent(Blocks.STONE, relative);
        check(dropsNear(helper, absolute).size() == 1, "stone must drop its graded stone item");
        check(hasRestoreRecord(level, absolute), "stone break must schedule restoration");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unavailableSkillDataDeniesEvenWithTheRightTool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.STONE);

        // No applyConfirmedValue: this mock player's skill state is NOT_LOADED.
        ServerPlayer miner = helper.makeMockServerPlayerInLevel();
        miner.setGameMode(GameType.SURVIVAL);
        miner.getInventory().clearContent();
        miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));

        boolean broke = miner.gameMode.destroyBlock(absolute);

        check(!broke, "NOT_LOADED skill data must deny the break");
        helper.assertBlockPresent(Blocks.STONE, relative);
        check(dropsNear(helper, absolute).isEmpty(), "unavailable-data denial must be dropless");
        check(!hasRestoreRecord(level, absolute), "unavailable-data denial must not schedule");
        helper.succeed();
    }

    /**
     * Creative is not stopped by the Mining threshold — and, since the milestone 6 amendment, is
     * not a way to delete a deposit either.
     *
     * <p>This used to assert that the ore was gone, which was the behaviour at the time: the gate
     * approved the bypass and vanilla removed the block. That turned an ordinary click into a
     * permanent, unrecorded deletion of a sited deposit, so the amended policy refuses the break.
     * What the bypass still means is unchanged and is what this test was really about: an operator
     * is not answered "your Mining is too low", and no skill is granted for the attempt.
     */
    @GameTest(template = TEMPLATE)
    public static void creativeBypassesTheThresholdWithoutGainOrDeletion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());

        ServerPlayer admin = helper.makeMockServerPlayerInLevel();
        admin.setGameMode(GameType.CREATIVE);
        admin.getInventory().clearContent();
        SkillManager.applyConfirmedValue(admin, MiningBreakGate.SKILL_ID, 0.0f);

        // Empty hand: the bypass is the gate's, not the Britannia-pickaxe flow's.
        MiningBreakGate.Evaluation evaluation =
                MiningBreakGate.evaluate(admin, level.getBlockState(absolute), level, absolute);
        check(evaluation.type() == MiningBreakGate.ResultType.APPROVED_BYPASS,
                "an operator at zero Mining was answered " + evaluation.type()
                        + " rather than being waved past the threshold");

        admin.gameMode.destroyBlock(absolute);

        helper.assertBlockPresent(BlockRegistry.VALORITE_ORE.get(), relative);
        check(!hasRestoreRecord(level, absolute),
                "a refused creative break must not schedule restoration");
        check(SkillManager.getSkill(admin, MiningBreakGate.SKILL_ID) == 0.0f,
                "bypass must not grant Mining skill");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fakePlayersAreDeniedByDefault(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.STONE);

        FakePlayer automation = new FakePlayer(level,
                new GameProfile(UUID.randomUUID(), "mining_automation"));
        automation.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));
        boolean broke = automation.gameMode.destroyBlock(absolute);

        check(!broke, "a fake player has no authoritative human skill owner and must be denied");
        helper.assertBlockPresent(Blocks.STONE, relative);
        check(dropsNear(helper, absolute).isEmpty(), "fake-player denial must be dropless");
        check(!hasRestoreRecord(level, absolute), "fake-player denial must not schedule");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void vanillaToolCanNoLongerDestroyGatedOreDroplessly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VERITE_ORE.get());

        // Milestone 1 found vanilla-tool breaks destroyed managed ore with no drops and no
        // restoration. The gate now covers every player break, whatever the tool.
        ServerPlayer lowMiner = helper.makeMockServerPlayerInLevel();
        lowMiner.setGameMode(GameType.SURVIVAL);
        lowMiner.getInventory().clearContent();
        SkillManager.applyConfirmedValue(lowMiner, MiningBreakGate.SKILL_ID, 10.0f);

        boolean broke = lowMiner.gameMode.destroyBlock(absolute);

        check(!broke, "an under-skilled bare-hand break of managed ore must be denied");
        helper.assertBlockPresent(BlockRegistry.VERITE_ORE.get(), relative);
        check(!hasRestoreRecord(level, absolute), "denied vanilla-tool break must not schedule");
        helper.succeed();
    }
}
