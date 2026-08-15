package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockTracker;
import com.seggellion.britannia_mod.mining.MiningProvenance;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Mining milestone 10: the whole approved ladder proven in a running world, tier by tier.
 *
 * <p>The unit suite already pins the gate's policy for every tier; this is the integration half —
 * a real player, a real block, a real break — so the ladder is proven end to end rather than only
 * in the decision core.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class MiningLadderGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** The owner-approved ladder, exactly as encoded in the catalogue. */
    private record Tier(String id, float required, Block block) {}

    private static List<Tier> ladder() {
        return List.of(
                new Tier("stone", 0.0f, Blocks.STONE),
                new Tier("iron", 0.0f, Blocks.IRON_ORE),
                new Tier("silver", 55.0f, BlockRegistry.SILVER_ORE.get()),
                new Tier("tin", 65.0f, BlockRegistry.TIN_ORE.get()),
                new Tier("shadow_iron", 70.0f, BlockRegistry.SHADOW_IRON_ORE.get()),
                new Tier("copper", 75.0f, BlockRegistry.COPPER_ORE.get()),
                new Tier("gold", 85.0f, Blocks.GOLD_ORE),
                new Tier("agapite", 90.0f, BlockRegistry.AGAPITE_ORE.get()),
                new Tier("verite", 95.0f, BlockRegistry.VERITE_ORE.get()),
                new Tier("valorite", 99.0f, BlockRegistry.VALORITE_ORE.get()));
    }

    private MiningLadderGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * Every tier: one tenth below the requirement is denied and completely inert, the requirement
     * exactly is mined and scheduled for restoration. One player whose authoritative skill is moved
     * between attempts, which is also how a real gain or Guildmaster purchase arrives.
     */
    @GameTest(template = TEMPLATE)
    public static void everyTierDeniesJustBelowAndMinesAtItsRequirement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos pos = helper.absolutePos(relative);

        ServerPlayer miner = helper.makeMockServerPlayerInLevel();
        miner.setGameMode(GameType.SURVIVAL);
        miner.getInventory().clearContent();
        miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));

        for (Tier tier : ladder()) {
            if (tier.required() > 0.0f) {
                helper.setBlock(relative, tier.block());
                SkillManager.applyConfirmedValue(miner, MiningSkill.SKILL_ID, tier.required() - 0.1f);
                miner.gameMode.destroyBlock(pos);

                helper.assertBlockPresent(tier.block(), relative);
                check(!BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(pos),
                        tier.id() + " scheduled a restoration for a denied break");
            }

            helper.setBlock(relative, tier.block());
            SkillManager.applyConfirmedValue(miner, MiningSkill.SKILL_ID, tier.required());
            miner.gameMode.destroyBlock(pos);

            helper.assertBlockNotPresent(tier.block(), relative);
            check(BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(pos),
                    tier.id() + " did not schedule its restoration at exactly " + tier.required());

            // Leave a clean ledger for the next tier.
            BrokenBlockTracker.removeBlock(level, pos);
        }
        helper.succeed();
    }

    /** Pending provenance must survive a restart, or the exploit reopens on the next boot. */
    @GameTest(template = TEMPLATE)
    public static void playerPlacedProvenanceIsPersisted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));

        MiningProvenance.markPlayerPlaced(level, pos);
        CompoundTag saved = MiningProvenance.get(level).save(new CompoundTag(), level.registryAccess());

        long[] stored = saved.getLongArray("placed");
        boolean present = false;
        for (long packed : stored) {
            if (packed == pos.asLong()) {
                present = true;
                break;
            }
        }
        check(present, "a player-placed mineable must be written to disk, not just held in memory");

        MiningProvenance.forget(level, pos);
        check(!MiningProvenance.isPlayerPlaced(level, pos), "forget must clear the marker");
        helper.succeed();
    }

    /** Stone is the training resource: it must always be mineable and always schedule its return. */
    @GameTest(template = TEMPLATE)
    public static void stoneRemainsTrainableAtEverySkillLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos pos = helper.absolutePos(relative);

        ServerPlayer miner = helper.makeMockServerPlayerInLevel();
        miner.setGameMode(GameType.SURVIVAL);
        miner.getInventory().clearContent();
        miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));

        for (float skill : new float[] {0.0f, 50.0f, 99.9f}) {
            helper.setBlock(relative, Blocks.STONE);
            SkillManager.applyConfirmedValue(miner, MiningSkill.SKILL_ID, skill);
            miner.gameMode.destroyBlock(pos);

            helper.assertBlockNotPresent(Blocks.STONE, relative);
            check(BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(pos),
                    "Stone must stay a renewable training resource at skill " + skill);
            BrokenBlockTracker.removeBlock(level, pos);
        }
        helper.succeed();
    }
}
