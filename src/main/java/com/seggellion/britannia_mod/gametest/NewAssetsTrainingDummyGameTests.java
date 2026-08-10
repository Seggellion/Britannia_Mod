package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.block.TrainingDummyBlock;
import com.seggellion.britannia_mod.block.entity.TrainingDummyBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.training.TrainingDummyService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dedicated-server checks for atomic structure, cooldown isolation, and accepted-hit animation. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class NewAssetsTrainingDummyGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private NewAssetsTrainingDummyGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void validHitsArePerPlayerRateLimitedAndDoNotConsumeDurability(GameTestHelper helper) {
        TrainingDummyBlock block = BlockRegistry.TRAINING_DUMMY.get();
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 2, 3));
        place(helper, block, anchor);

        long blockEntities = block.cells().stream()
                .filter(cell -> helper.getLevel().getBlockEntity(
                        block.worldPosition(anchor, Direction.NORTH, cell)) != null)
                .count();
        check(blockEntities == 1, "training dummy did not create exactly one animation owner");
        check(helper.getLevel().getBlockEntity(anchor) instanceof TrainingDummyBlockEntity,
                "training dummy root has no block entity");
        TrainingDummyBlockEntity entity = (TrainingDummyBlockEntity) helper.getLevel().getBlockEntity(anchor);

        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        first.setGameMode(GameType.SURVIVAL);
        second.setGameMode(GameType.SURVIVAL);
        ItemStack firstSword = new ItemStack(Items.IRON_SWORD);
        ItemStack secondAxe = new ItemStack(Items.IRON_AXE);
        int swordDamage = firstSword.getDamageValue();
        int axeDamage = secondAxe.getDamageValue();

        TrainingDummyService.AttemptResult firstHit = TrainingDummyService.attempt(first, firstSword);
        check(firstHit.accepted(), "first supported strike was rejected");
        block.triggerHit(helper.getLevel(), anchor, helper.getLevel().getBlockState(anchor));
        check(entity.acceptedHitCount() == 1, "accepted strike did not trigger the server animation owner");

        TrainingDummyService.AttemptResult spam = TrainingDummyService.attempt(first, firstSword);
        check(!spam.accepted() && spam.rejection() == TrainingDummyService.Rejection.COOLDOWN,
                "repeated strike bypassed the three-second cooldown");
        check(entity.acceptedHitCount() == 1, "cooldown rejection replayed the hit animation");

        TrainingDummyService.AttemptResult otherPlayer = TrainingDummyService.attempt(second, secondAxe);
        check(otherPlayer.accepted(), "one player's cooldown incorrectly blocked another player");
        check(firstSword.getDamageValue() == swordDamage && secondAxe.getDamageValue() == axeDamage,
                "training changed weapon durability");
        check(!TrainingDummyService.attempt(first, ItemStack.EMPTY).accepted(),
                "unarmed strike was accepted");
        check(!TrainingDummyService.attempt(first, new ItemStack(Items.BOW)).accepted(),
                "unsupported weapon was accepted");
        helper.succeed();
    }

    private static void place(GameTestHelper helper, TrainingDummyBlock block, BlockPos anchor) {
        block.duringMutation(() -> {
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
            for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
                helper.getLevel().setBlock(
                        block.worldPosition(anchor, Direction.NORTH, cell),
                        block.stateFor(Direction.NORTH, cell), flags);
            }
            return null;
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
