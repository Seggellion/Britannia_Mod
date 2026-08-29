package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateStackBreakTargets;
import com.seggellion.britannia_mod.crate.CrateStackColumnSync;
import com.seggellion.britannia_mod.crate.CrateStackPromotion;
import com.seggellion.britannia_mod.crate.CrateVariant;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Breaking a crate the way a swing actually arrives at the server.
 *
 * <h2>What this covers that the other break tests do not</h2>
 *
 * <p>{@code CrateStackBreakGameTests} hands the target to the block directly, which proves the
 * removal, the repacking and the drops but says nothing about how a target is chosen. Everything
 * before that — the {@code LeftClickBlock} hook, the server's own raycast, the agreement check
 * between the ray and the packet, and the guard that stops a held button eating a column — only runs
 * when a real {@code START_DESTROY_BLOCK} arrives. So these drive
 * {@code ServerPlayerGameMode.handleBlockBreakAction} and let the whole path decide for itself which
 * crate dies.
 *
 * <p>The player is creative, which matters twice: this mod refuses a bare-handed survival break of
 * anything outside a house, and a creative break completes inside the same {@code START} action —
 * which makes it the sharpest possible test of whether the target is captured early enough to be
 * there when destruction runs.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrateStackBreakPacketPathGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private CrateStackBreakPacketPathGameTests() {
    }

    /** A swing aimed at the middle crate destroys the middle crate, and only that one. */
    @GameTest(template = TEMPLATE)
    public static void aRealSwingDestroysTheCrateTheRayLandsOn(GameTestHelper helper) {
        Fixture fixture = column(helper, 3);
        int bottom = fixture.ids().get(0);
        int middle = fixture.ids().get(1);
        int top = fixture.ids().get(2);

        // 10.00 voxels up is inside the middle crate, which spans 7.15 to 14.30.
        swingAt(helper, fixture, 10.0D, fixture.root());

        check(fixture.stack().crateById(middle) == null,
                "the swing did not destroy the crate it was aimed at");
        check(fixture.stack().crateById(bottom) != null, "the bottom crate was destroyed instead");
        check(fixture.stack().crateById(top) != null, "the top crate was destroyed instead");
        check(fixture.stack().crateCount() == 2, "more than one crate was destroyed");
        check(fixture.stack().containerFor(top).getItem(0).is(Items.STICK),
                "the surviving top crate lost its contents");
        finish(helper, fixture);
    }

    /** The same, aimed through the continuation cell the top crate lives in. */
    @GameTest(template = TEMPLATE)
    public static void aRealSwingThroughAContinuationCellStillPicksTheRightCrate(
            GameTestHelper helper) {
        Fixture fixture = column(helper, 3);
        int top = fixture.ids().get(2);
        check(fixture.stack().requiredCellCount() == 2, "this fixture needs a continuation cell");

        // 18.00 voxels up: inside the third crate, and inside the cell above the root.
        swingAt(helper, fixture, 18.0D, fixture.root().above());

        check(fixture.stack().crateById(top) == null,
                "a swing through the continuation cell did not destroy the aimed crate");
        check(fixture.stack().crateCount() == 2, "more than one crate was destroyed");
        check(!helper.getLevel().getBlockState(fixture.root().above())
                        .is(BlockRegistry.CRATE_STACK.get()),
                "the continuation cell should have been released");
        finish(helper, fixture);
    }

    /**
     * A held button cannot walk down the column.
     *
     * <p>This is the case the guard exists for, arriving the way it really does: the client finishes
     * one crate and immediately sends another {@code START} for the same position. The second swing
     * must capture nothing, so the destruction that follows it finds no target and does nothing.
     */
    @GameTest(template = TEMPLATE)
    public static void aSecondSwingImmediatelyAfterABreakDestroysNothing(GameTestHelper helper) {
        Fixture fixture = column(helper, 4);

        swingAt(helper, fixture, 24.0D, fixture.root().above());
        check(fixture.stack().crateCount() == 3, "the first swing should have destroyed one crate");

        // What a held mouse button produces: another start, at once, on the same column.
        swingAt(helper, fixture, 10.0D, fixture.root());
        check(fixture.stack().crateCount() == 3,
                "a swing arriving immediately after a break destroyed a second crate");

        finish(helper, fixture);
    }

    /**
     * A swing whose ray does not reach the column captures nothing.
     *
     * <p>The packet names a position; it does not say where on it the player was looking, or whether
     * they were looking at it at all. The server does its own raycast and refuses to guess, which is
     * what stops a stale or dishonest aim from picking a crate.
     */
    @GameTest(template = TEMPLATE)
    public static void aSwingAimedAwayFromTheColumnCapturesNothing(GameTestHelper helper) {
        Fixture fixture = column(helper, 3);

        // Standing beside the column but looking straight up, so the ray meets open sky.
        fixture.player().absMoveTo(
                fixture.root().getX() + 2.5D, fixture.root().getY(), fixture.root().getZ() + 0.5D);
        look(fixture.player(), 90.0F, -90.0F);
        start(fixture.player(), fixture.root());

        check(fixture.stack().crateCount() == 3, "a swing aimed at nothing destroyed a crate");
        check(helper.getLevel().getBlockState(fixture.root()).is(BlockRegistry.CRATE_STACK.get()),
                "a swing aimed at nothing destroyed the column");
        finish(helper, fixture);
    }

    /**
     * The aim that counts is the one the client just sent, not the one before it.
     *
     * <p>A living entity's view vector comes from its head rotation, and asking for it at partial
     * tick 0 interpolates from the previous tick - so a player who turned onto the crate during the
     * tick they clicked in would be judged by where they had been looking, and the agreement check
     * would throw the swing away. Here the previous-tick head rotation points away from the column
     * while the current one points at it, which is exactly what turning-and-clicking produces.
     */
    @GameTest(template = TEMPLATE)
    public static void aSwingIsJudgedByTheRotationTheClientJustSent(GameTestHelper helper) {
        Fixture fixture = column(helper, 3);
        int middle = fixture.ids().get(1);

        aim(helper, fixture, 10.0D);
        // Last tick they were looking the other way; this tick they are on the crate.
        fixture.player().yRotO = -90.0F;
        fixture.player().yHeadRotO = -90.0F;

        start(fixture.player(), fixture.root());

        check(fixture.stack().crateById(middle) == null,
                "a swing was judged by last tick's aim and destroyed nothing");
        check(fixture.stack().crateCount() == 2, "more than one crate was destroyed");
        finish(helper, fixture);
    }

    /** Aborting a swing forgets the target, so a later destruction has nothing to spend. */
    @GameTest(template = TEMPLATE)
    public static void anAbortedSwingLeavesNoTargetBehind(GameTestHelper helper) {
        Fixture fixture = column(helper, 3);
        aim(helper, fixture, 10.0D);

        fixture.player().gameMode.handleBlockBreakAction(
                fixture.root(), ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                Direction.NORTH, helper.getLevel().getMaxBuildHeight(), nextSequence());
        fixture.player().gameMode.destroyBlock(fixture.root());

        check(fixture.stack().crateCount() == 3, "an aborted swing still destroyed a crate");
        finish(helper, fixture);
    }

    /* ─── driving a swing ────────────────────────────────────── */

    /**
     * Points the player at a height on the column and sends one {@code START}.
     *
     * <p>Creative destruction completes inside that action, so this is a whole swing.
     */
    private static void swingAt(
            GameTestHelper helper, Fixture fixture, double voxelHeight, BlockPos clickedCell) {
        aim(helper, fixture, voxelHeight);
        start(fixture.player(), clickedCell);
    }

    private static void start(ServerPlayer player, BlockPos clickedCell) {
        player.gameMode.handleBlockBreakAction(
                clickedCell, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                Direction.NORTH, player.level().getMaxBuildHeight(), nextSequence());
    }

    /**
     * Stands the player beside the column, at the height being aimed at, looking horizontally.
     *
     * <p>Level aiming on purpose. A pitched ray has to be pointed by arithmetic that is easy to get
     * subtly wrong, and a test that mis-aims does not fail loudly - it simply destroys nothing and
     * looks like a production defect. Level with the crate, the only thing that matters is the yaw,
     * and the ray crosses the column at exactly the height under test.
     *
     * <p>The aim is then checked before the swing, so a fixture that stopped pointing at the crate
     * says so instead of quietly passing.
     */
    private static void aim(GameTestHelper helper, Fixture fixture, double voxelHeight) {
        ServerPlayer player = fixture.player();
        BlockPos root = fixture.root();
        double eyeHeight = player.getEyeHeight();
        double feet = root.getY() + voxelHeight / 16.0D - eyeHeight;

        // Yaw 90 looks along -X, which is straight at a column standing 2.5 blocks that way.
        player.absMoveTo(root.getX() + 2.5D, feet, root.getZ() + 0.5D);
        look(player, 90.0F, 0.0F);

        HitResult aimed = player.pick(player.blockInteractionRange(), 1.0F, false);
        check(aimed instanceof BlockHitResult hit
                        && hit.getType() == HitResult.Type.BLOCK
                        && CrateStackBlock.rootOf(
                                hit.getBlockPos(),
                                helper.getLevel().getBlockState(hit.getBlockPos())).equals(root),
                "the fixture is not pointing at the column at " + voxelHeight + " voxels; aimed at "
                        + aimed.getLocation());
    }

    /**
     * Points a player's line of sight.
     *
     * <p>{@code absMoveTo} sets the body rotation, but a living entity's view vector is taken from
     * {@code yHeadRot}, which nothing here would otherwise touch - a player positioned with yaw alone
     * keeps looking whichever way their head last was. The previous-tick fields are set to the same
     * values so the aim reads identically whatever partial tick asks for it.
     */
    private static void look(ServerPlayer player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yHeadRotO = yaw;
        player.setOldPosAndRot();
    }

    private static int sequence = 1;

    private static int nextSequence() {
        return sequence++;
    }

    /* ─── fixtures ───────────────────────────────────────────── */

    private record Fixture(
            ServerPlayer player, BlockPos root, CrateStackBlockEntity stack, List<Integer> ids) {
    }

    /** A column of small crates, each holding something different, with a creative player beside it. */
    private static Fixture column(GameTestHelper helper, int crates) {
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);
        BlockPos root = helper.absolutePos(floor.above());
        helper.getLevel().setBlock(
                root,
                BlockRegistry.SMALL_CRATE.get().defaultBlockState()
                        .setValue(CrateBlock.FACING, Direction.NORTH),
                Block.UPDATE_ALL);

        CrateStackPromotion.Result promoted = CrateStackPromotion.promote(helper.getLevel(), root);
        check(promoted.succeeded(), "could not build a column: " + promoted.refusal());
        CrateStackBlockEntity stack = promoted.promoted().orElseThrow().stack();
        for (int index = 1; index < crates; index++) {
            check(CrateStackColumnSync.appendCrate(
                            helper.getLevel(), root, CrateVariant.SMALL, Direction.NORTH).succeeded(),
                    "could not grow the column");
        }
        List<Integer> ids = stack.crates().stream().map(crate -> crate.id()).toList();
        stack.containerFor(ids.get(0)).setItem(0, new ItemStack(Items.APPLE, 1));
        if (ids.size() > 1) {
            stack.containerFor(ids.get(1)).setItem(0, new ItemStack(Items.DIAMOND, 2));
        }
        if (ids.size() > 2) {
            stack.containerFor(ids.get(2)).setItem(0, new ItemStack(Items.STICK, 3));
        }

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().mayBuild = true;
        player.getAbilities().instabuild = true;
        player.onUpdateAbilities();
        return new Fixture(player, root, stack, ids);
    }

    private static void finish(GameTestHelper helper, Fixture fixture) {
        CrateStackBreakTargets.clear(fixture.player().getUUID());
        helper.getLevel().getServer().getPlayerList().remove(fixture.player());
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
