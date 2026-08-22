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
 * The foundation stairs are permanent, and no game mode is an exception.
 *
 * <p>Every attempt here is arranged so that the block itself is the only thing left that can
 * refuse it. That matters, because several other rules would otherwise answer first and this
 * would pass without testing anything:
 *
 * <ul>
 *   <li><b>Survival</b> holds a project quality tool, which is the case
 *       {@code StructureProtectionHandler} explicitly stands down for.</li>
 *   <li><b>Adventure</b> is given {@code mayBuild} directly -- the one ability adventure mode
 *       actually gates block editing on, and the thing an owner is lent inside their own house.
 *       It is granted here rather than through {@code SurvivalZoneHandler} so that the housing
 *       reach rule has no opinion either.</li>
 *   <li><b>Creative</b> needs no arranging: {@code StructureProtectionHandler} returns early for
 *       creative by design, so this path was already down to the block alone. It is also the case
 *       hardness cannot answer, because {@code ServerPlayerGameMode.destroyBlock} skips the break
 *       gate for a creative player entirely.</li>
 * </ul>
 *
 * <p>No house is registered in any of these, so the {@code house_foundation} membership the stairs
 * also carry is not what is being measured; that rule is covered by
 * {@code HouseOwnerBuildRightsGameTests}.
 *
 * <p>Each assertion reads the world rather than {@code destroyBlock}'s return value, which reports
 * true in creative whatever the removal actually did.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class FoundationStairsIndestructibilityGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** Clear of the structure edge, so nothing else is standing where the stair goes. */
    private static final BlockPos TARGET = new BlockPos(2, 1, 2);

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void asurvivalplayercannotbreakfoundationstairs(GameTestHelper helper) {
        attempt(helper, GameType.SURVIVAL, "fs-survival", true);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void anadventureplayerwithbuildrightscannotbreakfoundationstairs(
            GameTestHelper helper) {
        attempt(helper, GameType.ADVENTURE, "fs-adventure", false);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void acreativeplayercannotbreakfoundationstairs(GameTestHelper helper) {
        attempt(helper, GameType.CREATIVE, "fs-creative", false);
        helper.succeed();
    }

    /**
     * The other half of the survival answer: a player cannot chip one down either.
     *
     * <p>{@link #asurvivalplayercannotbreakfoundationstairs} calls {@code destroyBlock} straight
     * out, which is not how a survival player breaks anything -- they hold the button and the
     * server accumulates progress. A negative hardness makes that progress zero forever, so the
     * break never completes and {@code destroyBlock} is never reached at all.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void foundationstairsnevermakebreakingprogress(GameTestHelper helper) {
        Block stairs = BlockRegistry.BRICK_FOUNDATION_STAIRS.get();
        helper.setBlock(TARGET, stairs);

        ServerPlayer player = actor(helper, GameType.SURVIVAL, "fs-progress");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));

        float progress = helper.getBlockState(TARGET)
                .getDestroyProgress(player, player.level(), helper.absolutePos(TARGET));
        if (progress != 0.0F) {
            throw new GameTestAssertException(
                    "a foundation stair reported " + progress + " break progress per tick, so a "
                    + "survival player would eventually mine one down");
        }
        helper.succeed();
    }

    /** An ordinary stair in the same spot still breaks, so none of the above is a dead harness. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void anordinarystairinthesamespotstillbreaks(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE_STAIRS);

        ServerPlayer player = actor(helper, GameType.CREATIVE, "fs-control");
        player.gameMode.destroyBlock(helper.absolutePos(TARGET));

        if (helper.getBlockState(TARGET).is(Blocks.STONE_STAIRS)) {
            throw new GameTestAssertException(
                    "a vanilla stone stair survived this harness too, so the foundation-stair "
                    + "results above prove nothing about the foundation stairs");
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */

    private static void attempt(
            GameTestHelper helper, GameType mode, String name, boolean withQualityTool) {
        Block stairs = BlockRegistry.BRICK_FOUNDATION_STAIRS.get();
        helper.setBlock(TARGET, stairs);

        ServerPlayer player = actor(helper, mode, name);
        if (withQualityTool) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));
        }

        player.gameMode.destroyBlock(helper.absolutePos(TARGET));

        if (!helper.getBlockState(TARGET).is(stairs)) {
            throw new GameTestAssertException(
                    "a " + mode.getName() + " player destroyed a foundation stair, which is a "
                    + "permanent housing-boundary block in every game mode");
        }
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
