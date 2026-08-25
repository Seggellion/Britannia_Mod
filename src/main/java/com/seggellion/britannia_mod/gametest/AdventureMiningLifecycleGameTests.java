package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.resource.extraction.ExtractionToolPredicates;
import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.structure.SurvivalZoneHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
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

/**
 * Ore mining as a real adventure-mode activity: no Survival switch, vanilla destroy progress,
 * per-block break durations, and the managed extraction landing exactly once at the completed
 * break.
 *
 * <p>This pins the retirement of the last game-mode workaround. {@code CityGameModeHandler} used
 * to park pickaxe-holders in Survival because adventure could not break anything; the pickaxe now
 * carries a catalogue-scoped CAN_BREAK predicate ({@link ExtractionToolPredicates}) instead, and
 * {@code SurvivalZoneHandler} holds every non-operator in adventure unconditionally.
 *
 * <p>What a GameTest cannot reproduce is a real client holding the dig — the crack animation and
 * wall-clock duration are client-loop territory. The timing INPUTS are asserted instead: each
 * representative resource exposes a non-zero destroy time, differently-configured resources
 * produce genuinely different per-tick destroy progress, and no authorized case computes
 * instant-break progress.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class AdventureMiningLifecycleGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    private AdventureMiningLifecycleGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static ItemStack pickaxe() {
        return ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3);
    }

    /** An adventure miner whose pickaxe carries the predicate the tick handler maintains. */
    private static ServerPlayer adventureMiner(ServerLevel level, String name, float mining) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, name);
        player.setGameMode(GameType.ADVENTURE);
        ItemStack tool = pickaxe();
        AdventureModePredicate predicate = ExtractionToolPredicates.predicateFor(tool);
        check(predicate != null, "the Britannia pickaxe must earn a catalogue CAN_BREAK predicate");
        tool.set(DataComponents.CAN_BREAK, predicate);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, mining);
        return player;
    }

    private static List<ItemEntity> dropsNear(ServerLevel level, BlockPos absolute) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3.0D));
    }

    /* ------------------------------------------------------------------ */
    /*  The Survival switch is gone                                        */
    /* ------------------------------------------------------------------ */

    /**
     * The exact behaviour that defined the old workaround, inverted: a survival player holding
     * the mining tool used to be exempt from adventure enforcement (that exemption existed so the
     * retired CityGameModeHandler could park them in Survival to dig). The zone rule now returns
     * every non-operator to adventure, tool in hand or not.
     */
    @GameTest(template = TEMPLATE, batch = "adventure_mining", timeoutTicks = 60)
    public static void theSurvivalSwitchIsGone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, "ex-survival-miner");
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe());
        check(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "precondition: survival");

        SurvivalZoneHandler.applyTo(player);

        check(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                "holding a mining tool must no longer exempt anyone from adventure");
        helper.succeed();
    }

    /**
     * No held item is a game-mode grant, and none of them is a stale-Survival preserver either.
     *
     * <p>Every tool the retired workaround recognised, plus the ones it did not, driven through
     * the live zone rule outside any house: each starts in Survival — the state the old exemption
     * would have protected — and each must be returned to Adventure. Item identity is simply not
     * an input to the decision any more; location and ownership are.
     */
    @GameTest(template = TEMPLATE, batch = "adventure_mining", timeoutTicks = 60)
    public static void noHeldToolInfluencesGameMode(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        record Held(String label, ItemStack stack) {}
        List<Held> hands = List.of(
                new Held("the Britannia pickaxe", pickaxe()),
                new Held("the Britannia shovel",
                        ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3)),
                new Held("the two-handed axe",
                        new ItemStack(com.seggellion.britannia_mod.registry.ItemRegistry.TWO_HANDED_AXE.get())),
                new Held("a vanilla pickaxe",
                        new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE)),
                new Held("an empty hand", ItemStack.EMPTY));

        ServerPlayer player = ManagedResourceTestPlayers.survival(level, "tool-mode");
        for (Held held : hands) {
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, held.stack());
            check(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
                    "precondition: survival while holding " + held.label());

            SurvivalZoneHandler.applyTo(player);

            check(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                    held.label() + " must not hold anyone out of adventure outside a house");
            check(!player.getAbilities().mayBuild,
                    held.label() + " must not earn build rights outside a house");
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  The full adventure lifecycle                                       */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, batch = "adventure_mining", timeoutTicks = 60)
    public static void adventureMinerExtractsOnceAndStaysAdventure(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, BlockRegistry.SILVER_ORE.get());
        ServerPlayer miner = adventureMiner(level, "adventure-silver-miner", 100.0f);

        // A managed break always reports false: the handler cancels vanilla and commits itself.
        miner.gameMode.destroyBlock(absolute);

        helper.assertBlockNotPresent(BlockRegistry.SILVER_ORE.get(), NODE);
        List<ItemEntity> drops = dropsNear(level, absolute);
        check(drops.size() == 1, "exactly one yield expected, found " + drops.size());
        check(drops.getFirst().getItem().getItem() instanceof PurityOreItem,
                "the managed flow must mint the purity ore");
        check(BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(absolute),
                "the completed break must schedule its one restoration");
        check(miner.getMainHandItem().getDamageValue() == 1,
                "exactly one durability charge for one committed extraction");
        check(miner.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                "mining must not have moved the player out of adventure");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "adventure_mining", timeoutTicks = 60)
    public static void deniedMinerIsToldAtTheFirstSwingAndStaysAdventure(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, BlockRegistry.SILVER_ORE.get());
        ServerPlayer miner = adventureMiner(level, "adventure-low-miner", 0.0f);

        PlayerInteractEvent.LeftClickBlock swing = CommonHooks.onLeftClickBlock(
                miner, absolute, Direction.UP, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        check(swing.isCanceled(), "an under-skilled first swing must be refused immediately");

        miner.gameMode.destroyBlock(absolute);

        helper.assertBlockPresent(BlockRegistry.SILVER_ORE.get(), NODE);
        check(dropsNear(level, absolute).isEmpty(), "a denied dig must award nothing");
        check(!BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(absolute),
                "a denied dig must create no restoration debt");
        check(miner.getMainHandItem().getDamageValue() == 0,
                "a denied dig must charge no durability");
        check(miner.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                "denial must not have moved the player out of adventure");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "adventure_mining", timeoutTicks = 60)
    public static void eligibleFirstSwingMutatesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, BlockRegistry.SILVER_ORE.get());
        ServerPlayer miner = adventureMiner(level, "adventure-first-swing", 100.0f);

        PlayerInteractEvent.LeftClickBlock swing = CommonHooks.onLeftClickBlock(
                miner, absolute, Direction.UP, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);

        check(!swing.isCanceled(), "an eligible swing proceeds into the vanilla lifecycle");
        helper.assertBlockPresent(BlockRegistry.SILVER_ORE.get(), NODE);
        check(dropsNear(level, absolute).isEmpty(), "the first swing must award nothing");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "adventure_mining", timeoutTicks = 60)
    public static void adventureWithoutThePredicateStaysRestricted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, BlockRegistry.SILVER_ORE.get());
        ServerPlayer miner = ManagedResourceTestPlayers.survival(level, "adventure-bare-miner");
        miner.setGameMode(GameType.ADVENTURE);
        miner.setItemInHand(InteractionHand.MAIN_HAND, pickaxe());
        SkillManager.applyConfirmedValue(miner, MiningSkill.SKILL_ID, 100.0f);
        // Deliberately no CAN_BREAK component.

        boolean broke = miner.gameMode.destroyBlock(absolute);

        check(!broke, "adventure without the predicate must not break anything");
        helper.assertBlockPresent(BlockRegistry.SILVER_ORE.get(), NODE);
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Predicate scope                                                    */
    /* ------------------------------------------------------------------ */

    private static boolean covers(GameTestHelper helper, AdventureModePredicate predicate, Block block) {
        helper.setBlock(NODE, block);
        return predicate.test(new BlockInWorld(helper.getLevel(), helper.absolutePos(NODE), false));
    }

    @GameTest(template = TEMPLATE, batch = "adventure_mining", timeoutTicks = 60)
    public static void toolPredicatesAreScopedToTheirOwnResources(GameTestHelper helper) {
        AdventureModePredicate pickaxe = ExtractionToolPredicates.predicateFor(pickaxe());
        check(pickaxe != null, "the pickaxe earns a predicate");
        check(covers(helper, pickaxe, BlockRegistry.SILVER_ORE.get()), "pickaxe covers a managed ore");
        check(covers(helper, pickaxe, Blocks.STONE), "pickaxe covers catalogued stone");
        check(covers(helper, pickaxe, Blocks.DEEPSLATE), "pickaxe covers catalogued deepslate");
        check(!covers(helper, pickaxe, Blocks.DIRT), "pickaxe must not cover dirt");
        check(!covers(helper, pickaxe, Blocks.OAK_LOG), "pickaxe must not cover wood");
        check(!covers(helper, pickaxe, BlockRegistry.CLAY_DEPOSIT.get()),
                "pickaxe must not cover a shovel's bed");

        ItemStack shovelStack = ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3);
        AdventureModePredicate shovel = ExtractionToolPredicates.predicateFor(shovelStack);
        check(shovel != null, "the shovel earns a predicate");
        check(covers(helper, shovel, BlockRegistry.CLAY_DEPOSIT.get()), "shovel covers the clay bed");
        check(covers(helper, shovel, BlockRegistry.SILICA_SAND_DEPOSIT.get()), "shovel covers the silica bed");
        check(!covers(helper, shovel, Blocks.STONE), "shovel must not cover stone");
        check(!covers(helper, shovel, Blocks.DIRT), "the shovel is no general digging tool: not dirt");
        check(!covers(helper, shovel, Blocks.SAND), "the shovel is no general digging tool: not sand");
        check(!covers(helper, shovel, BlockRegistry.SILVER_ORE.get()), "shovel must not cover ore");

        check(ExtractionToolPredicates.predicateFor(new ItemStack(
                        net.minecraft.world.item.Items.IRON_PICKAXE)) == null,
                "a vanilla pickaxe is in no extraction tag and earns nothing");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Break duration inputs                                              */
    /* ------------------------------------------------------------------ */

    /**
     * The timing architecture, asserted at its inputs: destroy time is per-block state, the
     * project tools contribute their configured speed, and vanilla's arithmetic turns the two
     * into genuinely different hold durations. Softer digs faster than harder; nothing
     * authorized computes an instant break.
     */
    @GameTest(template = TEMPLATE, batch = "adventure_mining", timeoutTicks = 60)
    public static void differentResourcesExposeDifferentRealBreakTimes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (Block block : List.of(Blocks.STONE, BlockRegistry.SILVER_ORE.get(),
                Blocks.DEEPSLATE_IRON_ORE, BlockRegistry.CLAY_DEPOSIT.get(),
                BlockRegistry.SILICA_SAND_DEPOSIT.get(), Blocks.OAK_LOG, Blocks.OAK_LEAVES)) {
            check(block.defaultDestroyTime() > 0.0f,
                    block + " must expose a non-zero destroy time");
        }
        check(Blocks.STONE.defaultDestroyTime() < BlockRegistry.SILVER_ORE.get().defaultDestroyTime(),
                "a managed ore must be harder than plain stone");
        check(BlockRegistry.SILVER_ORE.get().defaultDestroyTime()
                        < Blocks.DEEPSLATE_IRON_ORE.defaultDestroyTime(),
                "a deepslate ore must be harder than a standard ore");

        ServerPlayer miner = adventureMiner(level, "adventure-timing-miner", 100.0f);
        float stone = progressPerTick(helper, miner, Blocks.STONE);
        float silver = progressPerTick(helper, miner, BlockRegistry.SILVER_ORE.get());
        float deepslateIron = progressPerTick(helper, miner, Blocks.DEEPSLATE_IRON_ORE);
        check(stone > silver && silver > deepslateIron,
                "harder resources must progress more slowly per tick: stone=" + stone
                        + " silver=" + silver + " deepslateIron=" + deepslateIron);
        for (float progress : new float[] {stone, silver, deepslateIron}) {
            check(progress > 0.0f && progress < 1.0f,
                    "authorized digging must be real progress, never instant: " + progress);
        }
        helper.succeed();
    }

    private static float progressPerTick(GameTestHelper helper, ServerPlayer miner, Block block) {
        helper.setBlock(NODE, block);
        BlockPos absolute = helper.absolutePos(NODE);
        return helper.getLevel().getBlockState(absolute)
                .getDestroyProgress(miner, helper.getLevel(), absolute);
    }
}
