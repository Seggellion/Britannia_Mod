package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.BritanniaChestBlockEntity;
import com.seggellion.britannia_mod.grabbyhands.GrabbyActor;
import com.seggellion.britannia_mod.grabbyhands.GrabbyDestructionOutcome;
import com.seggellion.britannia_mod.grabbyhands.GrabbyDestructionResult;
import com.seggellion.britannia_mod.grabbyhands.GrabbyDestructionTransaction;
import com.seggellion.britannia_mod.grabbyhands.GrabbyInstanceState;
import com.seggellion.britannia_mod.grabbyhands.GrabbyMutationGuard;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupOutcome;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupResult;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupTransaction;
import com.seggellion.britannia_mod.grabbyhands.GrabbyProvenanceAccess;
import com.seggellion.britannia_mod.grabbyhands.GrabbyWorld;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Milestone 12: concurrency and the things two players can do to each other.
 *
 * <p>The fake-driven unit tests already prove the transaction ordering. What they cannot prove is
 * that the real world behaves the way the fakes model it — in particular that
 * {@code Containers.dropContents} inside {@code onRemove} spills a destroyed container's contents
 * exactly once, which up to this milestone had never actually been executed.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GrabbyMultiplayerGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final UUID PLACER = UUID.fromString("1f1f1f1f-2a2a-3b3b-4c4c-5d5d5d5d5d5d");

    /*
     * Deliberately no guard reset between tests. Every transaction releases its claim through
     * try-with-resources, so a claim surviving into the next test would be a real defect and should
     * fail loudly rather than be tidied away.
     */

    private GrabbyMultiplayerGameTests() {
    }

    /**
     * Throws {@link GameTestAssertException}, never {@link IllegalStateException} or
     * {@link AssertionError}. When a check runs inside a {@code succeedWhen} or sequence callback --
     * directly or through any helper called from one -- {@code GameTestSequence.tickAndContinue}
     * swallows only that one type, which is how a polled condition retries until it holds.
     * {@code GameTestInfo} ticks its sequences outside any try/catch, so anything else escapes into
     * the server tick loop and crashes the whole GameTest server, ending the run and every result in
     * it. {@code AssertionError} is worse still: being an Error rather than an Exception, it is not
     * caught by the {@code catch (Exception)} that guards a test body either.
     */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer playerNear(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // Reach is validated server-side, so the mock has to actually be standing near the object.
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    /** A player-placed chair at a known position. */
    private static BlockPos placedChair(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(pos, BlockRegistry.WOODEN_CHAIR.get().defaultBlockState());
        GrabbyProvenanceAccess.write(level.getBlockEntity(pos),
                GrabbyInstanceState.playerPlaced(PLACER, 1L));
        return pos;
    }

    private static List<ItemEntity> droppedAround(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4.0));
    }

    // ------------------------------------------------------------------
    // One winner
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void twoPlayersCannotBothPickUpTheSameObject(GameTestHelper helper) {
        BlockPos pos = placedChair(helper);
        ServerLevel level = helper.getLevel();

        ServerPlayer first = playerNear(helper, pos);
        ServerPlayer second = playerNear(helper, pos);

        GrabbyPickupResult a = GrabbyPickupTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(first), pos);
        GrabbyPickupResult b = GrabbyPickupTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(second), pos);

        check(a.outcome() == GrabbyPickupOutcome.SUCCESS, "the first pickup should have succeeded");
        check(b.outcome() != GrabbyPickupOutcome.SUCCESS, "two players both picked up the same chair");
        check(b.worldObjectsRemoved() == 0, "the second pickup removed something");
        check(level.getBlockState(pos).isAir(), "the chair is still standing after a successful pickup");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anObjectAlreadyInATransactionRefusesASecondOne(GameTestHelper helper) {
        // The guard itself, against a real level: whoever holds the claim owns the object.
        BlockPos pos = placedChair(helper);
        ServerLevel level = helper.getLevel();
        GrabbyWorld world = GrabbyWorld.of(level);
        ServerPlayer player = playerNear(helper, pos);

        try (GrabbyMutationGuard.Claim held = GrabbyMutationGuard.claim(world.levelIdentity(), pos)) {
            check(held.held(), "the test could not take the claim");

            GrabbyPickupResult pickup = GrabbyPickupTransaction.execute(
                    world, GrabbyActor.of(player), pos);
            GrabbyDestructionResult chop = GrabbyDestructionTransaction.execute(
                    world, GrabbyActor.of(player), new ItemStack(Items.IRON_AXE), pos);

            check(pickup.outcome() == GrabbyPickupOutcome.ALREADY_IN_PROGRESS,
                    "a pickup ignored an outstanding claim");
            check(chop.outcome() == GrabbyDestructionOutcome.ALREADY_IN_PROGRESS,
                    "an axe ignored an outstanding claim");
        }
        check(!level.getBlockState(pos).isAir(), "the contested object should still be there");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPickupAndAnAxeCannotBothConsumeTheSameObject(GameTestHelper helper) {
        BlockPos pos = placedChair(helper);
        ServerLevel level = helper.getLevel();

        ServerPlayer collector = playerNear(helper, pos);
        ServerPlayer chopper = playerNear(helper, pos);

        GrabbyPickupResult pickup = GrabbyPickupTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(collector), pos);
        GrabbyDestructionResult chop = GrabbyDestructionTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(chopper), new ItemStack(Items.IRON_AXE), pos);

        int consumed = (pickup.worldObjectsRemoved()) + (chop.objectsDestroyed());
        check(consumed == 1, "the object was consumed " + consumed + " times, not once");
        check(level.getBlockState(pos).isAir(), "the object should be gone exactly once");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // The spill, finally executed
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void destroyingAFilledContainerSpillsItsContentsExactlyOnce(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(pos, BlockRegistry.CHEST_WOODEN.get().defaultBlockState());

        BritanniaChestBlockEntity chest = (BritanniaChestBlockEntity) level.getBlockEntity(pos);
        check(chest != null, "no chest block entity");
        chest.setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
        chest.setItem(3, new ItemStack(Items.BREAD, 2));
        GrabbyProvenanceAccess.write(chest, GrabbyInstanceState.playerPlaced(PLACER, 1L));

        ServerPlayer chopper = playerNear(helper, pos);
        GrabbyDestructionResult result = GrabbyDestructionTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(chopper), new ItemStack(Items.IRON_AXE), pos);

        check(result.outcome() == GrabbyDestructionOutcome.SUCCESS,
                "the chest was not destroyed: " + result.outcome());
        check(level.getBlockState(pos).isAir(), "the chest is still standing");

        List<ItemEntity> dropped = droppedAround(helper, pos);
        int gold = 0;
        int bread = 0;
        int chests = 0;
        for (ItemEntity entity : dropped) {
            ItemStack stack = entity.getItem();
            if (stack.getItem() == Items.GOLD_INGOT) {
                gold += stack.getCount();
            } else if (stack.getItem() == Items.BREAD) {
                bread += stack.getCount();
            } else if (stack.getItem() == BlockRegistry.CHEST_WOODEN.get().asItem()) {
                chests++;
            }
        }
        check(gold == 5, "expected 5 gold ingots on the floor, found " + gold);
        check(bread == 2, "expected 2 bread on the floor, found " + bread);
        check(chests == 0, "the chest itself dropped as well as its contents");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void pickingUpAFilledContainerLeavesNothingOnTheFloor(GameTestHelper helper) {
        // The mirror image: the same onRemove spill must NOT happen during a pickup, because the
        // contents are already travelling inside the item.
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(pos, BlockRegistry.CHEST_WOODEN.get().defaultBlockState());

        BritanniaChestBlockEntity chest = (BritanniaChestBlockEntity) level.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
        GrabbyProvenanceAccess.write(chest, GrabbyInstanceState.playerPlaced(PLACER, 1L));

        ServerPlayer collector = playerNear(helper, pos);
        GrabbyPickupResult result = GrabbyPickupTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(collector), pos);

        check(result.outcome() == GrabbyPickupOutcome.SUCCESS,
                "the chest was not picked up: " + result.outcome());
        for (ItemEntity entity : droppedAround(helper, pos)) {
            check(entity.getItem().getItem() != Items.GOLD_INGOT,
                    "the contents spilled as well as travelling in the item; that is duplication");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // A second player can still use what the first put down
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void aSecondPlayerCanSitOnAChairSomebodyElsePlaced(GameTestHelper helper) {
        BlockPos pos = placedChair(helper);
        ServerLevel level = helper.getLevel();

        ServerPlayer stranger = playerNear(helper, pos);
        level.getBlockState(pos).useWithoutItem(level, stranger,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(pos),
                        net.minecraft.core.Direction.UP, pos, false));

        check(stranger.isPassenger(),
                "a player who did not place the chair could not sit on it; placement must not"
                        + " privatise ordinary use");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void sittingStillWorksAfterAChairHasBeenMoved(GameTestHelper helper) {
        BlockPos from = placedChair(helper);
        ServerLevel level = helper.getLevel();

        ServerPlayer mover = playerNear(helper, from);
        GrabbyPickupResult pickup = GrabbyPickupTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(mover), from);
        check(pickup.outcome() == GrabbyPickupOutcome.SUCCESS, "the chair could not be picked up");

        BlockPos to = helper.absolutePos(new BlockPos(2, 1, 1));
        level.setBlockAndUpdate(to, BlockRegistry.WOODEN_CHAIR.get().defaultBlockState());
        GrabbyProvenanceAccess.write(level.getBlockEntity(to),
                GrabbyInstanceState.playerPlaced(PLACER, 2L));

        ServerPlayer sitter = playerNear(helper, to);
        level.getBlockState(to).useWithoutItem(level, sitter,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(to),
                        net.minecraft.core.Direction.UP, to, false));

        check(sitter.isPassenger(), "a moved chair stopped being sittable");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void sceneryStaysImmovableForEveryone(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        // No provenance: this is a Britannia scenery chair.
        level.setBlockAndUpdate(pos, BlockRegistry.WOODEN_CHAIR.get().defaultBlockState());

        ServerPlayer player = playerNear(helper, pos);
        GrabbyPickupResult pickup = GrabbyPickupTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(player), pos);
        GrabbyDestructionResult chop = GrabbyDestructionTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(player), new ItemStack(Items.IRON_AXE), pos);

        check(pickup.outcome() == GrabbyPickupOutcome.NOT_GRABBY_MANAGED, "scenery was picked up");
        check(chop.outcome() == GrabbyDestructionOutcome.NOT_GRABBY_MANAGED, "scenery was chopped");
        check(!level.getBlockState(pos).isAir(), "scenery was removed");
        helper.succeed();
    }
}
