package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockTracker;
import com.seggellion.britannia_mod.event.BlockRestoreHandler;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Mining milestone 7: the renewable restoration system and the provenance model that keeps the
 * place-break loop from being a Mining exploit.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class MiningRestorationGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private MiningRestorationGameTests() {
    }

    private static ServerPlayer miner(GameTestHelper helper, float miningSkill) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, miningSkill);
        return player;
    }

    private static boolean scheduled(ServerLevel level, BlockPos pos) {
        return BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(pos);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    // ---------------------------------------------------------------- provenance

    /**
     * The design §13 exploit: place a block, break it, repeat. It must yield no Mining, no managed
     * drop and no restoration — otherwise a player could farm the skill from one cobblestone.
     */
    @GameTest(template = TEMPLATE)
    public static void placeBreakLoopGrantsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        ServerPlayer player = miner(helper, 20.0f);

        // Exactly what a player does: put the block there, then mine it.
        helper.setBlock(relative, Blocks.STONE);
        MiningProvenance.markPlayerPlaced(level, absolute);

        player.gameMode.destroyBlock(absolute);

        check(SkillManager.getSkill(player, MiningSkill.SKILL_ID) == 20.0f,
                "breaking a self-placed block must award no Mining");
        check(!scheduled(level, absolute),
                "a self-placed block must not be scheduled for restoration -- that would mint material");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, new AABB(absolute).inflate(2.0));
        check(drops.stream().noneMatch(entity -> entity.getItem().getItem()
                        == com.seggellion.britannia_mod.registry.ItemRegistry.GRADE_STONE_ITEM.get()),
                "a self-placed block must not yield the managed graded-stone drop");
        check(!MiningProvenance.isPlayerPlaced(level, absolute),
                "the marker must be cleared once the block is gone, so the set stays bounded");
        helper.succeed();
    }

    /**
     * The same loop driven through the real placement path, so the provenance handler itself is
     * exercised rather than a hand-set marker: place a block from the hotbar, then mine it.
     */
    @GameTest(template = TEMPLATE)
    public static void realPlacementMarksProvenanceAndClosesTheLoop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floorRelative = new BlockPos(1, 1, 1);
        BlockPos targetRelative = new BlockPos(1, 2, 1);
        BlockPos target = helper.absolutePos(targetRelative);
        helper.setBlock(floorRelative, Blocks.STONE);
        helper.setBlock(targetRelative, Blocks.AIR);

        ServerPlayer player = miner(helper, 20.0f);
        ItemStack cobblestone = new ItemStack(Blocks.COBBLESTONE.asItem(), 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, cobblestone);

        // Click the top face of the floor block, exactly as a player placing a block does.
        BlockPos floor = helper.absolutePos(floorRelative);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(floor).add(0.0, 0.5, 0.0),
                net.minecraft.core.Direction.UP, floor, false);
        player.gameMode.useItemOn(player, level, cobblestone, InteractionHand.MAIN_HAND, hit);

        helper.assertBlockPresent(Blocks.COBBLESTONE, targetRelative);
        check(MiningProvenance.isPlayerPlaced(level, target),
                "a real player placement of a mineable must be recorded as construction");

        float before = SkillManager.getSkill(player, MiningSkill.SKILL_ID);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));
        player.gameMode.destroyBlock(target);

        check(SkillManager.getSkill(player, MiningSkill.SKILL_ID) == before,
                "the place-break loop must award no Mining through the real placement path");
        check(!scheduled(level, target), "the loop must not schedule a restoration either");
        check(!MiningProvenance.isPlayerPlaced(level, target), "the marker must be cleared after the break");
        helper.succeed();
    }

    /** A player must always be able to dismantle their own construction, whatever their skill. */
    @GameTest(template = TEMPLATE)
    public static void ownConstructionIsBreakableBelowItsMiningTier(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        // Deepslate is gated at 30.0; this player has none of it.
        helper.setBlock(relative, Blocks.DEEPSLATE);
        MiningProvenance.markPlayerPlaced(level, absolute);
        ServerPlayer builder = miner(helper, 0.0f);

        check(builder.gameMode.destroyBlock(absolute),
                "a player must be able to break a gated block they placed themselves");
        helper.assertBlockNotPresent(Blocks.DEEPSLATE, relative);
        helper.succeed();
    }

    /** Natural nodes are unaffected: absence of a marker means natural, so old saves keep working. */
    @GameTest(template = TEMPLATE)
    public static void unmarkedNodesRemainNaturalAndManaged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.SILVER_ORE.get());
        ServerPlayer player = miner(helper, 60.0f);

        check(!MiningProvenance.isPlayerPlaced(level, absolute), "precondition: nothing marked here");
        player.gameMode.destroyBlock(absolute);

        check(scheduled(level, absolute), "a natural node must still schedule its restoration");
        helper.succeed();
    }

    // ---------------------------------------------------------------- restoration

    /** Restoration must not delete what somebody built in the meantime; it waits instead. */
    @GameTest(template = TEMPLATE)
    public static void restorationWaitsWhenTheCellWasBuiltOver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);

        helper.setBlock(relative, Blocks.CHEST);
        check(!BlockRestoreHandler.canRestoreInto(level, absolute),
                "restoring into a player's chest would destroy it");

        helper.setBlock(relative, Blocks.AIR);
        check(BlockRestoreHandler.canRestoreInto(level, absolute),
                "a free cell must accept its node back");
        helper.succeed();
    }

    /** Nor may a node materialise inside a player or their animals. */
    @GameTest(template = TEMPLATE)
    public static void restorationWaitsWhileAnEntityOccupiesTheCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.AIR);

        var cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, relative);
        cow.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        check(!BlockRestoreHandler.canRestoreInto(level, absolute),
                "restoring into an occupied cell would suffocate whatever stands there");

        cow.discard();
        check(BlockRestoreHandler.canRestoreInto(level, absolute),
                "once the cell is clear the node may return");
        helper.succeed();
    }

    /** One position holds one pending restoration, and it remembers the original node. */
    @GameTest(template = TEMPLATE)
    public static void oneRecordPerPositionSurvivesSaveAndLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(new BlockPos(1, 1, 1));
        java.util.UUID breaker = java.util.UUID.randomUUID();

        BrokenBlockTracker.recordBrokenBlock(
                level, absolute, BlockRegistry.VERITE_ORE.get().defaultBlockState(), breaker);
        BrokenBlockTracker.recordBrokenBlock(
                level, absolute, BlockRegistry.VERITE_ORE.get().defaultBlockState(), breaker);

        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        long here = storage.getBrokenBlocks().keySet().stream().filter(absolute::equals).count();
        check(here == 1, "a position must hold exactly one pending restoration, found " + here);

        CompoundTag saved = storage.save(new CompoundTag(), level.registryAccess());
        BrokenBlockDataStorage reloaded = new BrokenBlockDataStorage(saved, level.registryAccess());
        BrokenBlockData record = reloaded.getBrokenBlocks().get(absolute);
        check(record != null, "the pending restoration must survive a restart");
        check(record.originalState.is(BlockRegistry.VERITE_ORE.get()),
                "the reloaded record must still know which node it owes");

        BrokenBlockTracker.removeBlock(level, absolute);
        helper.succeed();
    }

    /** Neighbouring nodes mined by two different players stay independent. */
    @GameTest(template = TEMPLATE)
    public static void neighbouringNodesAndTwoPlayersStayIndependent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos firstRelative = new BlockPos(1, 1, 1);
        BlockPos secondRelative = new BlockPos(2, 1, 1);
        BlockPos first = helper.absolutePos(firstRelative);
        BlockPos second = helper.absolutePos(secondRelative);
        helper.setBlock(firstRelative, Blocks.STONE);
        helper.setBlock(secondRelative, Blocks.STONE);

        ServerPlayer low = miner(helper, 0.0f);
        ServerPlayer high = miner(helper, 80.0f);
        low.gameMode.destroyBlock(first);
        high.gameMode.destroyBlock(second);

        BrokenBlockData firstRecord = BrokenBlockDataStorage.get(level).getBrokenBlocks().get(first);
        BrokenBlockData secondRecord = BrokenBlockDataStorage.get(level).getBrokenBlocks().get(second);
        check(firstRecord != null && secondRecord != null, "both nodes must be scheduled");
        check(firstRecord.playerUUID.equals(low.getUUID()), "first node credited to the wrong miner");
        check(secondRecord.playerUUID.equals(high.getUUID()), "second node credited to the wrong miner");
        helper.succeed();
    }

    /** A denied break leaves the restoration ledger completely untouched. */
    @GameTest(template = TEMPLATE)
    public static void deniedBreakAddsNoRestorationRecord(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());
        int before = BrokenBlockDataStorage.get(level).getBrokenBlocks().size();

        ServerPlayer player = miner(helper, 10.0f);
        check(!player.gameMode.destroyBlock(absolute), "10 Mining must not break Valorite");

        check(BrokenBlockDataStorage.get(level).getBrokenBlocks().size() == before,
                "a denied break must not add a restoration record");
        check(!scheduled(level, absolute), "the denied position must hold no record");
        helper.succeed();
    }
}
