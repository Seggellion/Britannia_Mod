package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Mining milestone 4: proves a completed managed break awards Mining through the existing skill
 * engine exactly once, and that every excluded case awards nothing.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class MiningSkillGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private MiningSkillGameTests() {
    }

    private static ServerPlayer miner(GameTestHelper helper, GameType mode, float miningSkill) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(mode);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, miningSkill);
        return player;
    }

    private static float skillOf(ServerPlayer player) {
        return SkillManager.getSkill(player, MiningSkill.SKILL_ID);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** End-to-end: a real dig runs the managed flow and yields at most one 0.1 activation. */
    @GameTest(template = TEMPLATE)
    public static void oneManagedBreakAwardsAtMostOneActivation(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.STONE);
        ServerPlayer player = miner(helper, GameType.SURVIVAL, 0.0f);

        player.gameMode.destroyBlock(helper.absolutePos(relative));

        helper.assertBlockNotPresent(Blocks.STONE, relative);
        float gained = skillOf(player);
        check(gained == 0.0f || Math.abs(gained - MiningSkill.GAIN_UNIT) < 1.0e-4f,
                "one break must award either nothing or exactly one 0.1 activation, got " + gained);
        helper.succeed();
    }

    /**
     * Stone trains Mining (design §9.2). Drives the production award path directly with distinct
     * node positions rather than filling the template with 200 blocks; the gate, the roll and the
     * skill engine are all the real ones. With the shipped base chance a run of 200 activations
     * failing every roll has probability ~1e-48, so this is deterministic in practice.
     */
    @GameTest(template = TEMPLATE)
    public static void repeatedStoneActivationsTrainMining(GameTestHelper helper) {
        ServerPlayer player = miner(helper, GameType.SURVIVAL, 0.0f);
        BlockState stone = Blocks.STONE.defaultBlockState();

        for (int i = 0; i < 200; i++) {
            MiningSkill.awardForBreak(player, stone, new BlockPos(i, 64, 0));
        }

        float gained = skillOf(player);
        check(gained > 0.0f, "200 Stone activations must train Mining, got " + gained);
        check(gained <= 200 * MiningSkill.GAIN_UNIT,
                "gain must never exceed one unit per activation, got " + gained);
        helper.succeed();
    }

    /** The duplicate-callback guard, exercised against a live player and level clock. */
    @GameTest(template = TEMPLATE)
    public static void repeatedCallbacksForOneBreakAwardOnce(GameTestHelper helper) {
        ServerPlayer player = miner(helper, GameType.SURVIVAL, 0.0f);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockPos node = new BlockPos(7, 64, 7);

        MiningSkill.awardForBreak(player, stone, node);
        float afterFirst = skillOf(player);
        for (int i = 0; i < 5; i++) {
            check(MiningSkill.awardForBreak(player, stone, node) == 0.0f,
                    "a repeated callback for the same break must award nothing");
        }
        check(skillOf(player) == afterFirst, "duplicate callbacks moved the skill");
        helper.succeed();
    }

    /** A denied break is still award-free now that awards exist. */
    @GameTest(template = TEMPLATE)
    public static void deniedBreakAwardsNothing(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, BlockRegistry.SILVER_ORE.get());
        ServerPlayer player = miner(helper, GameType.SURVIVAL, 54.9f);

        player.gameMode.destroyBlock(helper.absolutePos(relative));

        helper.assertBlockPresent(BlockRegistry.SILVER_ORE.get(), relative);
        check(skillOf(player) == 54.9f, "a denied break must award nothing, got " + skillOf(player));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void creativeBypassAwardsNothing(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());
        ServerPlayer admin = miner(helper, GameType.CREATIVE, 0.0f);

        admin.gameMode.destroyBlock(helper.absolutePos(relative));

        check(skillOf(admin) == 0.0f,
                "a Creative bypass must never grant Mining, got " + skillOf(admin));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void automationAwardsNothing(GameTestHelper helper) {
        FakePlayer automation = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "mining_award_automation"));
        automation.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));

        float gained = MiningSkill.awardForBreak(
                automation, Blocks.STONE.defaultBlockState(), new BlockPos(3, 64, 3));

        check(gained == 0.0f, "automation has no skill identity and must award nothing");
        helper.succeed();
    }

    /** Unmanaged blocks are outside Mining entirely, so they cannot be farmed for skill. */
    @GameTest(template = TEMPLATE)
    public static void unmanagedBlocksAwardNothing(GameTestHelper helper) {
        ServerPlayer player = miner(helper, GameType.SURVIVAL, 0.0f);

        float gained = MiningSkill.awardForBreak(
                player, Blocks.DIRT.defaultBlockState(), new BlockPos(5, 64, 5));

        check(gained == 0.0f, "dirt is not a Mining resource and must award nothing");
        check(skillOf(player) == 0.0f, "an unmanaged block moved the skill");
        helper.succeed();
    }
}
