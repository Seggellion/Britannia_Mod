package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.resource.extraction.ExtractionToolPredicates;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Wood chopping as a real adventure-mode activity, pinning the retirement of the last instant
 * left-click harvest: the first swing fells nothing, the completed break invokes the wood
 * handling exactly once, and the two-handed axe is a wood-and-leaves tool and nothing more.
 *
 * <p>The axe's CAN_BREAK is deliberately tag-driven — {@code #minecraft:logs},
 * {@code #minecraft:leaves}, {@code #britannia_mod:logs} and the fruit-tree structure tags — so
 * the scope tracks datapack contents rather than a hand-maintained block array. Fruit is absent
 * on purpose: fruit is picked by interaction, not felled, and the owner's rule is firm — wood and
 * leaves only.
 *
 * <p>The predicate is a client-lifecycle key, never the authority: the forged-predicate test
 * proves that a client-side grant for the wrong block still dies at the server's own gates.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class AdventureWoodChopGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    private AdventureWoodChopGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static ItemStack axe() {
        return new ItemStack(ItemRegistry.TWO_HANDED_AXE.get());
    }

    private static ServerPlayer chopper(ServerLevel level, String name) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, name);
        player.setGameMode(GameType.ADVENTURE);
        ItemStack tool = axe();
        AdventureModePredicate predicate = ExtractionToolPredicates.predicateFor(tool);
        check(predicate != null, "the two-handed axe must earn a wood-and-leaves predicate");
        tool.set(DataComponents.CAN_BREAK, predicate);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        return player;
    }

    private static List<ItemEntity> dropsNear(ServerLevel level, BlockPos absolute) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3.0D));
    }

    /* ------------------------------------------------------------------ */
    /*  The instant left-click is gone                                     */
    /* ------------------------------------------------------------------ */

    /** The regression itself: one swing used to cancel the event and fell the log on the spot. */
    @GameTest(template = TEMPLATE, batch = "adventure_woodchop", timeoutTicks = 60)
    public static void firstClickDoesNotFellTheLog(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, Blocks.OAK_LOG);
        ServerPlayer player = chopper(level, "wood-first-swing");

        PlayerInteractEvent.LeftClickBlock swing = CommonHooks.onLeftClickBlock(
                player, absolute, Direction.UP, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);

        check(!swing.isCanceled(),
                "the swing must proceed into the vanilla lifecycle, not be swallowed by a harvest");
        helper.assertBlockPresent(Blocks.OAK_LOG, NODE);
        check(dropsNear(level, absolute).isEmpty(), "the first swing must fell and award nothing");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "adventure_woodchop", timeoutTicks = 60)
    public static void completedBreakChopsExactlyOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, Blocks.OAK_LOG);
        ServerPlayer player = chopper(level, "wood-chopper");

        // A taken-over break reports false — WoodChopEventHandler cancels vanilla and harvests.
        player.gameMode.destroyBlock(absolute);

        helper.assertBlockNotPresent(Blocks.OAK_LOG, NODE);
        List<ItemEntity> drops = dropsNear(level, absolute);
        check(drops.size() == 1, "exactly one weighted wood expected, found " + drops.size());
        check(drops.getFirst().getItem().getItem() instanceof WeightedWoodItem,
                "the chop must mint the weighted wood item");
        check(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                "chopping must not have moved the player out of adventure");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "adventure_woodchop", timeoutTicks = 60)
    public static void leavesBreakAtCompletionNotAtTheClick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, Blocks.OAK_LEAVES);
        ServerPlayer player = chopper(level, "leaf-chopper");

        CommonHooks.onLeftClickBlock(player, absolute, Direction.UP,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        helper.assertBlockPresent(Blocks.OAK_LEAVES, NODE);

        player.gameMode.destroyBlock(absolute);
        helper.assertBlockNotPresent(Blocks.OAK_LEAVES, NODE);
        // Sapling recovery is a one-in-four roll; the count may be zero or one, never more.
        check(dropsNear(level, absolute).size() <= 1,
                "leaves may recover at most one sapling per break");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "adventure_woodchop", timeoutTicks = 60)
    public static void adventureAxeWithoutThePredicateStaysRestricted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, Blocks.OAK_LOG);
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, "bare-chopper");
        player.setGameMode(GameType.ADVENTURE);
        player.setItemInHand(InteractionHand.MAIN_HAND, axe());

        boolean broke = player.gameMode.destroyBlock(absolute);

        check(!broke, "adventure without the predicate must not chop anything");
        helper.assertBlockPresent(Blocks.OAK_LOG, NODE);
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Wood and leaves only                                               */
    /* ------------------------------------------------------------------ */

    private static boolean covers(GameTestHelper helper, AdventureModePredicate predicate, Block block) {
        helper.setBlock(NODE, block);
        return predicate.test(new BlockInWorld(helper.getLevel(), helper.absolutePos(NODE), false));
    }

    @GameTest(template = TEMPLATE, batch = "adventure_woodchop", timeoutTicks = 60)
    public static void axePredicateIsWoodAndLeavesOnly(GameTestHelper helper) {
        AdventureModePredicate predicate = ExtractionToolPredicates.predicateFor(axe());
        check(predicate != null, "the axe earns a predicate");

        for (Block wood : List.of(Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.CRIMSON_STEM,
                Blocks.OAK_LEAVES, Blocks.AZALEA_LEAVES,
                BlockRegistry.WEIGHTED_WOOD_BLOCK.get(),
                BlockRegistry.ORANGE_TREE_TRUNK_BLOCK.get(),
                BlockRegistry.ORANGE_TREE_BRANCH_BLOCK.get(),
                BlockRegistry.ORANGE_TREE_LEAF_BLOCK.get())) {
            check(covers(helper, predicate, wood), wood + " is wood or leaves and must be covered");
        }
        for (Block other : List.of(Blocks.STONE, Blocks.DIRT, Blocks.COBBLESTONE,
                Blocks.BOOKSHELF, Blocks.CRAFTING_TABLE, Blocks.OAK_PLANKS,
                BlockRegistry.SILVER_ORE.get(), BlockRegistry.CLAY_DEPOSIT.get())) {
            check(!covers(helper, predicate, other),
                    other + " is not wood or leaves and must not be covered");
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  The predicate is not the authority                                 */
    /* ------------------------------------------------------------------ */

    /**
     * A hostile client granting itself a predicate for the wrong block gets exactly nowhere: the
     * component only opens the vanilla lifecycle, and the completed break still dies at the
     * server's own gates — here the Mining gate's tool refusal for an axe against a gated ore.
     */
    @GameTest(template = TEMPLATE, batch = "adventure_woodchop", timeoutTicks = 60)
    public static void aForgedPredicateCannotTurnTheAxeIntoAMiningTool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, BlockRegistry.SILVER_ORE.get());

        ServerPlayer player = ManagedResourceTestPlayers.survival(level, "forged-chopper");
        player.setGameMode(GameType.ADVENTURE);
        ItemStack tool = axe();
        tool.set(DataComponents.CAN_BREAK, new AdventureModePredicate(List.of(new BlockPredicate(
                Optional.of(HolderSet.direct(BlockRegistry.SILVER_ORE.get().builtInRegistryHolder())),
                Optional.empty(), Optional.empty())), false));
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        com.seggellion.britannia_mod.skill.SkillManager.applyConfirmedValue(
                player, com.seggellion.britannia_mod.mining.MiningSkill.SKILL_ID, 100.0f);

        player.gameMode.destroyBlock(absolute);

        helper.assertBlockPresent(BlockRegistry.SILVER_ORE.get(), NODE);
        check(dropsNear(level, absolute).isEmpty(),
                "a forged client predicate must never mint a yield");
        helper.succeed();
    }
}
