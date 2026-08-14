package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.grabbyhands.GrabbyActor;
import com.seggellion.britannia_mod.grabbyhands.GrabbyEligibility;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPlacementOutcome;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPlacementResult;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPlacementTransaction;
import com.seggellion.britannia_mod.grabbyhands.GrabbyProvenanceAccess;
import com.seggellion.britannia_mod.grabbyhands.GrabbyWorld;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Milestone 13, automated portion: the Adventure-mode narrowness claim, end to end.
 *
 * <p>The central promise of this epic is that Grabby Hands opens exactly one door and no other:
 * enrolled objects become placeable in Adventure mode, and nothing else changes. Until the GameTest
 * server booted, that was argued from the decompiled sources rather than demonstrated.
 *
 * <p>These tests disable {@code mayBuild}, which is precisely what Adventure mode means to
 * {@code ItemStack.useOn}, and then check both directions: an ordinary block stays refused, and an
 * enrolled chair is <em>also</em> refused by the ordinary path while succeeding through the Grabby
 * transaction. The second half matters as much as the first — it shows the enrollment tag is not
 * quietly widening vanilla placement.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GrabbyAdventureGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private GrabbyAdventureGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * A player who cannot build, which is what Adventure mode means to the placement gate.
     *
     * <p>Stood <em>beside</em> the target rather than on it. {@code BlockItem.place} calls
     * {@code isUnobstructed}, so a player standing in the destination legitimately blocks their own
     * placement — you cannot put a chair inside yourself. Getting this wrong first time made three
     * tests fail with {@code REFUSED_BY_BLOCK} and made the obstruction test pass for the wrong
     * reason.
     */
    @SuppressWarnings("removal")
    private static ServerPlayer adventurePlayerNear(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(pos.getX() + 2.5, pos.getY(), pos.getZ() + 2.5);
        player.getAbilities().mayBuild = false;
        // instabuild too: a mock player defaults to it, and ItemStack.consume skips shrinking for a
        // player with infinite materials, which would silently hide the item-consumption invariant.
        player.getAbilities().instabuild = false;
        player.onUpdateAbilities();
        return player;
    }

    private static BlockHitResult topFaceOf(BlockPos pos) {
        return new BlockHitResult(
                Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0), Direction.UP, pos, false);
    }

    /** A solid floor to build on, and the space above it. */
    private static BlockPos floorAt(GameTestHelper helper, int x, int z) {
        BlockPos floor = helper.absolutePos(new BlockPos(x, 1, z));
        helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        return floor;
    }

    // ------------------------------------------------------------------
    // The tags are real
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void theEnrollmentTagsActuallyLoadAndBind(GameTestHelper helper) {
        // Hand-written tag files with a typo would silently enroll nothing. Only a running server
        // proves they parsed and bound.
        check(GrabbyEligibility.movableType(BlockRegistry.WOODEN_CHAIR.get().defaultBlockState()),
                "britannia_mod:grabby_movable did not bind to the wooden chair");
        check(GrabbyEligibility.axeDestroyableType(BlockRegistry.WOODEN_CHAIR.get().defaultBlockState()),
                "britannia_mod:grabby_axe_destroyable did not bind to the wooden chair");
        check(!GrabbyEligibility.movableType(Blocks.STONE.defaultBlockState()),
                "an unenrolled block reported itself as movable");
        check(GrabbyEligibility.deedPlaced(BlockRegistry.DOUBLE_BED.get().defaultBlockState()),
                "britannia_mod:grabby_deed_placed did not bind to the double bed");
        check(!GrabbyEligibility.movableType(BlockRegistry.DOUBLE_BED.get().defaultBlockState()),
                "the deed-placed veto did not override movability");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theLooseItemTagActuallyLoadsAndBinds(GameTestHelper helper) {
        check(GrabbyEligibility.hostablePlainItem(new ItemStack(ItemRegistry.CHEESE.get())),
                "britannia_mod:grabby_placeable_items did not bind to cheese");
        check(!GrabbyEligibility.hostablePlainItem(new ItemStack(Items.STICK)),
                "an untagged item reported itself as placeable");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Playbook step 2: ordinary placement is still blocked
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void anOrdinaryBlockStillCannotBePlacedInAdventureMode(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 1, 1);
        BlockPos above = floor.above();
        ServerLevel level = helper.getLevel();
        ServerPlayer player = adventurePlayerNear(helper, floor);

        ItemStack stone = new ItemStack(Items.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        stone.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, topFaceOf(floor)));

        check(level.getBlockState(above).isAir(),
                "an ordinary block was placed in Adventure mode; the gate has been widened");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anEnrolledChairIsAlsoStillRefusedByTheOrdinaryPlacementPath(GameTestHelper helper) {
        // Enrollment must not quietly relax vanilla placement. The only door is the Grabby
        // transaction; the ordinary path stays shut for enrolled content too.
        BlockPos floor = floorAt(helper, 1, 1);
        BlockPos above = floor.above();
        ServerLevel level = helper.getLevel();
        ServerPlayer player = adventurePlayerNear(helper, floor);

        ItemStack chair = new ItemStack(ItemRegistry.WOODEN_CHAIR_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, chair);
        chair.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, topFaceOf(floor)));

        check(level.getBlockState(above).isAir(),
                "an enrolled chair was placed by the ordinary Adventure path; enrollment must not"
                        + " widen vanilla placement");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anEnrolledChairCanBePlacedInAdventureModeThroughGrabbyHands(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 1, 1);
        BlockPos above = floor.above();
        ServerLevel level = helper.getLevel();
        ServerPlayer player = adventurePlayerNear(helper, floor);

        ItemStack chair = new ItemStack(ItemRegistry.WOODEN_CHAIR_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, chair);

        GrabbyPlacementResult result = GrabbyPlacementTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(player), chair, topFaceOf(floor));

        check(result.outcome() == GrabbyPlacementOutcome.SUCCESS,
                "the chair was not placed: " + result.outcome());
        check(level.getBlockState(above).is(BlockRegistry.WOODEN_CHAIR.get()),
                "the chair did not land where the transaction said it would");
        check(GrabbyProvenanceAccess.read(level, above).grabbyManaged(),
                "the placed chair was not marked player-placed");
        check(player.getMainHandItem().isEmpty(),
                "the source item was not consumed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anUnenrolledBlockIsRefusedByGrabbyHandsToo(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 1, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = adventurePlayerNear(helper, floor);

        ItemStack stone = new ItemStack(Items.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);

        GrabbyPlacementResult result = GrabbyPlacementTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(player), stone, topFaceOf(floor));

        check(result.outcome() == GrabbyPlacementOutcome.TYPE_NOT_ENROLLED,
                "Grabby Hands accepted an unenrolled block: " + result.outcome());
        check(level.getBlockState(floor.above()).isAir(), "an unenrolled block was placed");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Playbook steps 10 and 11: stacking, and rejecting an intersection
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void aChairStacksOnAChair(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 1, 1);
        BlockPos lower = floor.above();
        BlockPos upper = lower.above();
        ServerLevel level = helper.getLevel();
        ServerPlayer player = adventurePlayerNear(helper, floor);

        for (BlockPos expected : new BlockPos[]{lower, upper}) {
            ItemStack chair = new ItemStack(ItemRegistry.WOODEN_CHAIR_ITEM.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, chair);
            BlockPos clicked = expected.below();
            GrabbyPlacementResult result = GrabbyPlacementTransaction.execute(
                    GrabbyWorld.of(level), GrabbyActor.of(player), chair, topFaceOf(clicked));
            check(result.outcome() == GrabbyPlacementOutcome.SUCCESS,
                    "placing at " + expected + " failed: " + result.outcome());
        }

        check(level.getBlockState(lower).is(BlockRegistry.WOODEN_CHAIR.get()), "the lower chair is missing");
        check(level.getBlockState(upper).is(BlockRegistry.WOODEN_CHAIR.get()),
                "a chair could not be stacked on a chair; furniture stacking has regressed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anObstructedPlacementIsRejectedAndConsumesNothing(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 1, 1);
        BlockPos occupied = floor.above();
        ServerLevel level = helper.getLevel();
        // Fill the destination with something solid that is not replaceable.
        level.setBlockAndUpdate(occupied, Blocks.STONE.defaultBlockState());

        ServerPlayer player = adventurePlayerNear(helper, floor);
        ItemStack chair = new ItemStack(ItemRegistry.WOODEN_CHAIR_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, chair);

        GrabbyPlacementResult result = GrabbyPlacementTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(player), chair, topFaceOf(floor));

        check(result.outcome() != GrabbyPlacementOutcome.SUCCESS,
                "a chair was placed into an occupied space");
        check(level.getBlockState(occupied).is(Blocks.STONE),
                "the obstructing block was replaced");
        check(!player.getMainHandItem().isEmpty(),
                "a refused placement consumed the item anyway");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aWineBottleNeedsSomethingSturdyUnderneath(GameTestHelper helper) {
        // The bottle's own canSurvive rule, exercised for real: it may stand on a chair but not in
        // mid-air.
        BlockPos floor = floorAt(helper, 1, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = adventurePlayerNear(helper, floor);

        ItemStack bottle = new ItemStack(ItemRegistry.WINE_BOTTLE_GREEN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
        GrabbyPlacementResult onStone = GrabbyPlacementTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(player), bottle, topFaceOf(floor));
        check(onStone.outcome() == GrabbyPlacementOutcome.SUCCESS,
                "a bottle could not stand on solid ground: " + onStone.outcome());

        // Now a position with nothing below it at all.
        BlockPos midAir = helper.absolutePos(new BlockPos(3, 3, 3));
        ItemStack second = new ItemStack(ItemRegistry.WINE_BOTTLE_GREEN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, second);
        player.setPos(midAir.getX() + 2.5, midAir.getY(), midAir.getZ() + 2.5);
        GrabbyPlacementResult floating = GrabbyPlacementTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(player), second,
                new BlockHitResult(Vec3.atCenterOf(midAir), Direction.UP, midAir, false));
        check(floating.outcome() != GrabbyPlacementOutcome.SUCCESS,
                "a bottle was placed with nothing underneath it");
        helper.succeed();
    }
}
