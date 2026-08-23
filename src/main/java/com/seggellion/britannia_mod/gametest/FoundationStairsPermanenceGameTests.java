package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * A foundation stair is permanent to a player and removable by an operator.
 *
 * <h2>Why survival and adventure are measured as progress, not as a break</h2>
 * A negative hardness does not refuse a removal -- it makes one unreachable. It is read in exactly
 * one place, {@code getDestroyProgress}, which returns zero and so never lets the server accumulate
 * enough to finish: {@code handleBlockBreakAction} declines to insta-mine at zero, the per-tick
 * accumulation stays at zero however long the button is held, and the closing STOP action finds
 * nothing finished. That is the whole mechanism, and it is the same one bedrock uses.
 *
 * <p>So these tests ask {@code getDestroyProgress}, which is the real question. Calling
 * {@code destroyBlock} directly would prove nothing here, because it is downstream of the gate that
 * a survival player can never pass -- vanilla bedrock is removed by that call too.
 *
 * <p>The hardness check short-circuits before the break-speed event is consulted, so what the player
 * holds cannot matter. The survival case asserts that twice over, bare-handed and holding the
 * project pickaxe.
 *
 * <h2>Why creative is measured as a break</h2>
 * {@code ServerPlayerGameMode.destroyBlock} skips the break gate entirely for a creative player and
 * goes straight to {@code removeBlock}, so hardness is never consulted and the removal happens.
 * That is the operator's way to take out a misplaced stair, and it is deliberate rather than
 * incidental -- an {@code onDestroyedByPlayer} override would close it, because that method is the
 * one question asked in every game mode.
 *
 * <p>No house is registered in any of these, so the {@code house_foundation} membership the stairs
 * also carry is not what is being measured; that rule is covered by
 * {@code HouseOwnerBuildRightsGameTests}.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class FoundationStairsPermanenceGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** Clear of the structure edge, so nothing else is standing where the stair goes. */
    private static final BlockPos TARGET = new BlockPos(2, 1, 2);

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void asurvivalplayercannevermineafoundationstairdown(GameTestHelper helper) {
        helper.setBlock(TARGET, BlockRegistry.BRICK_FOUNDATION_STAIRS.get());
        ServerPlayer player = actor(helper, GameType.SURVIVAL, "fs-survival");

        makesNoProgress(helper, player, "a bare-handed survival player");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));
        makesNoProgress(helper, player, "a survival player holding the project pickaxe");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void anadventureplayerwithbuildrightscannevermineafoundationstairdown(
            GameTestHelper helper) {
        helper.setBlock(TARGET, BlockRegistry.BRICK_FOUNDATION_STAIRS.get());
        ServerPlayer player = actor(helper, GameType.ADVENTURE, "fs-adventure");

        // The strongest adventure case: one who has been lent mayBuild, which is the ability
        // adventure mode actually gates block editing on and what an owner gets in their own house.
        makesNoProgress(helper, player, "an adventure player with build rights");
        helper.succeed();
    }

    /**
     * The operator's way out of a misplaced stair.
     *
     * <p>Reads the world rather than {@code destroyBlock}'s return value, which reports true in
     * creative whatever the removal actually did.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void acreativeoperatorremovesafoundationstair(GameTestHelper helper) {
        Block stairs = BlockRegistry.BRICK_FOUNDATION_STAIRS.get();
        helper.setBlock(TARGET, stairs);

        ServerPlayer operator = actor(helper, GameType.CREATIVE, "fs-operator");
        operator.gameMode.destroyBlock(helper.absolutePos(TARGET));

        if (helper.getBlockState(TARGET).is(stairs)) {
            throw new GameTestAssertException(
                    "a creative operator could not remove a foundation stair, so a misplaced one "
                    + "would be permanent for everybody including whoever placed it");
        }
        helper.succeed();
    }

    /**
     * Zero progress is a property of the stair, not of this harness or this player.
     *
     * <p>Bare-handed deliberately: a project quality tool cancels the break-speed event on anything
     * outside its own mineable set, which would drive an ordinary stair to a negative speed and make
     * this control prove the wrong thing.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void anordinarystairreportsrealbreakprogressforthesameplayer(
            GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE_STAIRS);
        ServerPlayer player = actor(helper, GameType.SURVIVAL, "fs-control");

        float progress = progressFor(helper, player);
        if (progress <= 0.0F) {
            throw new GameTestAssertException(
                    "a vanilla stone stair reported " + progress + " break progress for this player "
                    + "too, so the zero measured on the foundation stairs proves nothing about them");
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */

    private static void makesNoProgress(GameTestHelper helper, ServerPlayer player, String who) {
        float progress = progressFor(helper, player);
        if (progress != 0.0F) {
            throw new GameTestAssertException(
                    who + " accumulates " + progress + " break progress per tick on a foundation "
                    + "stair, so they would eventually mine one down");
        }
    }

    private static float progressFor(GameTestHelper helper, ServerPlayer player) {
        return helper.getBlockState(TARGET)
                .getDestroyProgress(player, player.level(), helper.absolutePos(TARGET));
    }

    private static ServerPlayer actor(GameTestHelper helper, GameType mode, String name) {
        ServerPlayer player =
                FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));

        Vec3 beside = Vec3.atCenterOf(helper.absolutePos(TARGET.east()));
        player.setPos(beside.x, beside.y, beside.z);
        player.gameMode.changeGameModeForPlayer(mode);

        if (mode == GameType.ADVENTURE) {
            player.getAbilities().mayBuild = true;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return player;
    }
}
