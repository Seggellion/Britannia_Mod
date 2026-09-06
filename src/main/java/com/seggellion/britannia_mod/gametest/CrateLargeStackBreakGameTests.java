package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.crate.CrateFoundation;
import com.seggellion.britannia_mod.grabbyhands.GrabbyActor;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupOutcome;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupResult;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupTransaction;
import com.seggellion.britannia_mod.grabbyhands.GrabbyWorld;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Breaking one large crate out of a stacked pair.
 *
 * <h2>Why aiming matters more than position here</h2>
 *
 * <p>A crate standing on another is physically inside the lower crate's cells, so a swing at its body
 * arrives at the server as a swing at the lower crate's block. Deciding what to break from that
 * position alone destroys the crate underneath the one the player was pointing at - fifty-four slots
 * belonging to something they never aimed at.
 *
 * <p>So these swing through {@code handleBlockBreakAction} with the player really pointing at a crate,
 * which is the half of the path every previous crate bug of this kind lived in.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrateLargeStackBreakGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** The lid of a large crate, where the crate above it rests. */
    private static final int LID = 1900;

    private CrateLargeStackBreakGameTests() {
    }

    /* ─── breaking the upper crate ───────────────────────────── */

    /** Aiming at the upper crate breaks the upper crate and leaves the lower one alone. */
    @GameTest(template = TEMPLATE, batch = "crate_large_stack_break")
    public static void breakingUpperLargeLeavesLowerLargeIntact(GameTestHelper helper) {
        Pair pair = stackedPair(helper, Direction.NORTH);
        pair.lower().setItem(0, new ItemStack(Items.APPLE, 4));
        pair.upper().setItem(0, new ItemStack(Items.DIAMOND, 3));

        // Twenty-five voxels: the middle of the upper crate's body, inside the lower crate's cell.
        swingAt(helper, pair, 2500);

        check(!(helper.getLevel().getBlockState(pair.upperAnchor()).getBlock() instanceof CrateBlock),
                "the upper crate survived the swing aimed at it");
        CrateBlockEntity lower = crateAt(helper, pair.anchor());
        check(lower.getItem(0).is(Items.APPLE) && lower.getItem(0).getCount() == 4,
                "the lower crate's contents were disturbed");
        check(!lower.hasFoundation(), "the lower crate was given a foundation offset");
        check(cellsAround(helper, pair.anchor(), 0) == 4 && cellsAround(helper, pair.anchor(), 1) == 4,
                "the lower crate lost cells");
        finish(helper, pair);
    }

    /**
     * And the swing never takes the crate underneath, from whichever side it comes.
     *
     * <p>Approaching from each side lands the ray on a different cell of the pair, which is what the
     * quadrant concern really is: the crate is two cells across, and each of them must give the same
     * answer about which crate the player was pointing at.
     */
    @GameTest(template = TEMPLATE, batch = "crate_large_stack_break")
    public static void breakingUpperNeverDestroysLower(GameTestHelper helper) {
        ServerPlayer player = breaker(helper);
        for (Direction from : Direction.Plane.HORIZONTAL) {
            Pair pair = stackedPair(helper, player, Direction.NORTH);
            swingFrom(helper, pair, 2500, from);

            check(helper.getLevel().getBlockEntity(pair.anchor()) instanceof CrateBlockEntity,
                    "approaching from " + from + " destroyed the lower crate");
            check(!(helper.getLevel().getBlockState(pair.upperAnchor()).getBlock()
                            instanceof CrateBlock),
                    "approaching from " + from + " did not break the upper crate");
            clearCrates(helper, pair.anchor());
        }
        finish(helper, player);
    }

    /* ─── breaking the lower crate ───────────────────────────── */

    /** Aiming at the lower crate breaks it, and the crate above settles onto its own floor. */
    @GameTest(template = TEMPLATE, batch = "crate_large_stack_break")
    public static void breakingLowerLargeSettlesUpperLarge(GameTestHelper helper) {
        Pair pair = stackedPair(helper, Direction.NORTH);
        pair.lower().setItem(0, new ItemStack(Items.APPLE, 4));
        pair.upper().setItem(0, new ItemStack(Items.DIAMOND, 3));

        // Ten voxels: the lower crate's own body, below its lid.
        swingAt(helper, pair, 1000);

        check(!(helper.getLevel().getBlockState(pair.anchor()).getBlock() instanceof CrateBlock),
                "the lower crate survived the swing aimed at it");
        CrateBlockEntity survivor = crateAt(helper, pair.upperAnchor());
        check(!survivor.hasFoundation(),
                "the survivor is still standing on a crate that has gone, so nothing draws it");
        check(survivor.originHundredths() == 0,
                "the survivor settled to " + survivor.originHundredths() + " rather than its floor");
        check(survivor.getItem(0).is(Items.DIAMOND) && survivor.getItem(0).getCount() == 3,
                "settling touched the survivor's contents");
        check(!helper.getLevel().getBlockState(pair.upperAnchor())
                        .getShape(helper.getLevel(), pair.upperAnchor()).isEmpty(),
                "the survivor has no shape to aim at");
        finish(helper, pair);
    }

    /** The survivor can then be broken by an ordinary swing. */
    @GameTest(template = TEMPLATE, batch = "crate_large_stack_break")
    public static void settledUpperCanBeBrokenNormally(GameTestHelper helper) {
        Pair pair = stackedPair(helper, Direction.NORTH);
        pair.upper().setItem(0, new ItemStack(Items.DIAMOND, 3));

        swingAt(helper, pair, 1000);
        check(crateAt(helper, pair.upperAnchor()).hasFoundation() == false,
                "the survivor never settled, so this proves nothing");

        // Now aimed at the survivor where it actually stands.
        swingAtPos(helper, pair, pair.upperAnchor(), 800);

        check(!(helper.getLevel().getBlockState(pair.upperAnchor()).getBlock() instanceof CrateBlock),
                "the settled crate could not be broken");
        finish(helper, pair);
    }

    /** And by Grabby, which is the other path a player would reach for. */
    @GameTest(template = TEMPLATE, batch = "crate_large_stack_break")
    public static void settledUpperCanBeGrabbed(GameTestHelper helper) {
        Pair pair = stackedPair(helper, Direction.NORTH);

        swingAt(helper, pair, 1000);
        check(!crateAt(helper, pair.upperAnchor()).hasFoundation(), "the survivor never settled");

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(
                GrabbyWorld.of(helper.getLevel()), GrabbyActor.of(pair.player()), pair.upperAnchor());

        check(result.outcome() == GrabbyPickupOutcome.SUCCESS,
                "Grabby refused the settled crate: " + result.outcome());
        finish(helper, pair);
    }

    /* ─── inventories ────────────────────────────────────────── */

    /** Neither crate's contents reach the other, whichever is broken. */
    @GameTest(template = TEMPLATE, batch = "crate_large_stack_break")
    public static void largePairInventoriesRemainIndependent(GameTestHelper helper) {
        Pair pair = stackedPair(helper, Direction.NORTH);
        pair.lower().setItem(0, new ItemStack(Items.APPLE, 4));
        pair.upper().setItem(0, new ItemStack(Items.DIAMOND, 3));

        swingAt(helper, pair, 2500);

        CrateBlockEntity lower = crateAt(helper, pair.anchor());
        check(lower.getItem(0).is(Items.APPLE) && lower.getItem(0).getCount() == 4,
                "the survivor's contents changed when the other crate was broken");
        for (int slot = 1; slot < lower.getContainerSize(); slot++) {
            check(lower.getItem(slot).isEmpty(),
                    "the broken crate's contents were copied into the survivor");
        }
        finish(helper, pair);
    }

    /* ─── every facing ───────────────────────────────────────── */

    /** Vertical targeting does not depend on which way the crates face. */
    @GameTest(template = TEMPLATE, batch = "crate_large_stack_break")
    public static void largeStackBreakWorksAcrossAllFacings(GameTestHelper helper) {
        ServerPlayer player = breaker(helper);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Pair pair = stackedPair(helper, player, facing);
            swingAt(helper, pair, 2500);

            check(helper.getLevel().getBlockEntity(pair.anchor()) instanceof CrateBlockEntity,
                    "facing " + facing + ": the lower crate was destroyed");
            check(!(helper.getLevel().getBlockState(pair.upperAnchor()).getBlock()
                            instanceof CrateBlock),
                    "facing " + facing + ": the upper crate was not broken");
            clearCrates(helper, pair.anchor());
        }
        finish(helper, player);
    }

    /* ─── fixtures ───────────────────────────────────────────── */

    private record Pair(
            GameTestHelper helper, ServerPlayer player, BlockPos anchor, BlockPos upperAnchor,
            CrateBlockEntity lower, CrateBlockEntity upper) {
    }

    private static Pair stackedPair(GameTestHelper helper, Direction facing) {
        return stackedPair(helper, breaker(helper), facing);
    }

    /**
     * One creative player, made once per test.
     *
     * <p>Deliberately not one per round of a loop. Every test in a batch shares a world, and a player
     * joining and leaving moves chunk tickets around in it - which is felt by anything else in that
     * batch waiting on a chunk to stay loaded.
     */
    private static ServerPlayer breaker(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().mayBuild = true;
        player.getAbilities().instabuild = true;
        player.onUpdateAbilities();
        return player;
    }

    private static Pair stackedPair(GameTestHelper helper, ServerPlayer player, Direction facing) {
        for (int x = 1; x <= 4; x++) {
            for (int z = 1; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        player.setYRot(facing.getOpposite().toYRot());
        player.setYHeadRot(facing.getOpposite().toYRot());
        player.absMoveTo(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D);
        player.setOldPosAndRot();

        place(helper, player, floor, floor.getY() + 1.0D, floor);
        BlockPos anchor = floor.above();
        place(helper, player, anchor, anchor.getY() + LID / 1600.0D, anchor.above());
        BlockPos upperAnchor = CrateFoundation.columnRootFor(anchor);

        check(helper.getLevel().getBlockEntity(anchor) instanceof CrateBlockEntity,
                "the lower crate was not placed");
        check(helper.getLevel().getBlockEntity(upperAnchor) instanceof CrateBlockEntity resting
                        && resting.hasFoundation(),
                "the upper crate is not standing on the lower one");
        return new Pair(helper, player, anchor, upperAnchor,
                (CrateBlockEntity) helper.getLevel().getBlockEntity(anchor),
                (CrateBlockEntity) helper.getLevel().getBlockEntity(upperAnchor));
    }

    private static void place(
            GameTestHelper helper, ServerPlayer player, BlockPos at, double y, BlockPos clicked) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(large(), 8));
        Vec3 hit = new Vec3(at.getX() + 0.5D, y, at.getZ() + 0.5D);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, clicked, false));
    }

    private static void swingAt(GameTestHelper helper, Pair pair, int heightHundredths) {
        swingFrom(helper, pair, heightHundredths, Direction.EAST);
    }

    /** Stands on one side of the pair, level with a height on it, and swings. */
    private static void swingFrom(
            GameTestHelper helper, Pair pair, int heightHundredths, Direction from) {
        aimFrom(helper, pair.player(), pair.anchor(), heightHundredths, from);
        pair.player().gameMode.handleBlockBreakAction(
                aimedBlock(pair.player()), ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                Direction.NORTH, helper.getLevel().getMaxBuildHeight(), nextSequence());
    }

    private static void swingAtPos(
            GameTestHelper helper, Pair pair, BlockPos base, int heightHundredths) {
        aimFrom(helper, pair.player(), base, heightHundredths, Direction.EAST);
        pair.player().gameMode.handleBlockBreakAction(
                aimedBlock(pair.player()), ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                Direction.NORTH, helper.getLevel().getMaxBuildHeight(), nextSequence());
    }

    /**
     * Stands the player on one side of the pair, level with a height on it, looking inward.
     *
     * <p>Level on purpose: only the yaw then matters, and the ray crosses the crates at exactly the
     * height under test. The aim is asserted before the swing, because a mis-aimed fixture that
     * destroys nothing looks exactly like a production defect.
     */
    private static void aimFrom(
            GameTestHelper helper, ServerPlayer player, BlockPos base, int heightHundredths,
            Direction from) {

        double eye = base.getY() + heightHundredths / 1600.0D;
        // The pair is two cells across. Travel along the middle of it, but stand on the centre line
        // of a cell rather than on the seam between the two - a ray exactly on a block boundary is
        // decided by rounding rather than by geometry.
        double x = from.getStepX() == 0
                ? base.getX() + 0.5D
                : base.getX() + standoff(from.getStepX());
        double z = from.getStepZ() == 0
                ? base.getZ() + 0.5D
                : base.getZ() + standoff(from.getStepZ());
        float yaw = from.getOpposite().toYRot();
        player.absMoveTo(x, eye - player.getEyeHeight(), z);
        player.setYRot(yaw);
        player.setXRot(0.0F);
        player.setYHeadRot(yaw);
        player.yRotO = yaw;
        player.xRotO = 0.0F;
        player.yHeadRotO = yaw;
        player.setOldPosAndRot();

        HitResult aimed = player.pick(player.blockInteractionRange(), 1.0F, false);
        check(aimed instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                        && helper.getLevel().getBlockState(hit.getBlockPos())
                                .getBlock() instanceof CrateBlock,
                "the fixture is not pointing at a crate at " + heightHundredths
                        + " hundredths from " + from + "; aimed at " + aimed.getLocation());
    }

    /**
     * How far along an axis to stand, measured from the pair's near corner.
     *
     * <p>Deliberately close. The test structure is seven blocks across and the crates take two of
     * them, so standing several blocks clear puts the player outside it entirely - where whatever the
     * runner laid down next is, and the ray meets that instead of the crate.
     */
    private static double standoff(int step) {
        return step > 0 ? 3.5D : -1.5D;
    }

    private static BlockPos aimedBlock(ServerPlayer player) {
        return ((BlockHitResult) player.pick(player.blockInteractionRange(), 1.0F, false))
                .getBlockPos();
    }

    private static int cellsAround(GameTestHelper helper, BlockPos anchor, int level) {
        int found = 0;
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                if (helper.getLevel().getBlockState(anchor.offset(x, level, z))
                        .getBlock() instanceof CrateBlock) {
                    found++;
                }
            }
        }
        return found;
    }

    private static CrateBlockEntity crateAt(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getLevel().getBlockState(pos);
        check(state.getBlock() instanceof CrateBlock,
                "no crate at " + pos + "; found " + state.getBlock());
        check(helper.getLevel().getBlockEntity(pos) instanceof CrateBlockEntity,
                "the crate at " + pos + " has no inventory");
        return (CrateBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    /** Clears the crates, leaving the player where they are for the next round. */
    private static void clearCrates(GameTestHelper helper, BlockPos anchor) {
        for (int y = 0; y <= 5; y++) {
            for (int x = -1; x <= 2; x++) {
                for (int z = -1; z <= 2; z++) {
                    helper.getLevel().removeBlock(anchor.offset(x, y, z), false);
                }
            }
        }
    }

    private static void finish(GameTestHelper helper, Pair pair) {
        finish(helper, pair.player());
    }

    private static void finish(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static int sequence = 1;

    private static int nextSequence() {
        return sequence++;
    }

    private static Item large() {
        return ItemRegistry.LARGE_CRATE_ITEM.get();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
