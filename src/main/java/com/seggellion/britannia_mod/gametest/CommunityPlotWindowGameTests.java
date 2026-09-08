package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CommunityHoedFarmBlock;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.CommunityFarmBlockEntity;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.farming.CommunityPlotWindow;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.wildresource.WildResourceEntries;
import com.seggellion.britannia_mod.wildresource.WildResourceQuestScheduling;
import com.seggellion.britannia_mod.wildresource.WildResourceSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.seggellion.britannia_mod.gametest.QuestActionTestSupport.check;
import static com.seggellion.britannia_mod.gametest.QuestActionTestSupport.checkCount;

/**
 * Rowan farming questline M9 items 1, 3 and 5, in a world.
 *
 * <p>Four things only a running level can prove:
 * <ul>
 *   <li>the fertilized plot's window expires on its deadline with {@code randomTickSpeed} at 0 --
 *       which is the whole of discovery defect D6. Before this milestone the only thing that read
 *       the window was {@code FarmingBlock#randomTick}, so at 0 the plot never expired at all, and
 *       at the default 3 it expired at a different moment every run;</li>
 *   <li>it expires at the same moment at the ordinary speed, so nothing was traded away for that;</li>
 *   <li>neither fertilized dirt nor a seed is taken from a player who arrives after the deadline;</li>
 *   <li>accepting quest 1 brings the dung schedule forward as a normal tracked attempt, and places
 *       nothing itself.</li>
 * </ul>
 *
 * <p>The whole class shares a batch. The two timing tests change a world gamerule, which is
 * process-wide state, and each of them sets the value it needs at its own start and restores the
 * vanilla default at its end -- so a failure in one cannot leave a later test running under a
 * rule it did not choose.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CommunityPlotWindowGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 3);
    private static final BlockPos PLOT = new BlockPos(2, 2, 2);

    /** Long enough to be unmistakably shorter than a random tick, short enough to run quickly. */
    private static final int WINDOW_TICKS = 40;

    /** Vanilla's default, restored at the end of the two tests that change it. */
    private static final int DEFAULT_RANDOM_TICK_SPEED = 3;

    private CommunityPlotWindowGameTests() {}

    // ------------------------------------------------- item 1: expiry without a random tick

    @GameTest(template = TEMPLATE, batch = "community_plot_window", timeoutTicks = 200)
    public static void theSeedWindowExpiresOnItsDeadlineWithRandomTicksTurnedOff(GameTestHelper helper) {
        expiryRunsOnItsDeadline(helper, 0);
    }

    @GameTest(template = TEMPLATE, batch = "community_plot_window", timeoutTicks = 200)
    public static void theSeedWindowExpiresOnTheSameDeadlineAtNormalRandomTickSpeed(GameTestHelper helper) {
        expiryRunsOnItsDeadline(helper, DEFAULT_RANDOM_TICK_SPEED);
    }

    /**
     * The plot survives to one tick before its deadline and is gone two ticks after it, at
     * whatever {@code randomTickSpeed} the caller names. A window this short cannot be closed by a
     * random tick at any speed -- the expected wait for one is hundreds of ticks -- so passing at
     * both speeds is the proof that the scheduled tick is doing the work and the gamerule is not.
     */
    private static void expiryRunsOnItsDeadline(GameTestHelper helper, int randomTickSpeed) {
        ServerLevel level = helper.getLevel();
        setRandomTickSpeed(level, randomTickSpeed);
        BlockPos plot = helper.absolutePos(PLOT);
        openSeedWindow(level, plot, level.getGameTime() + WINDOW_TICKS);

        check(level.getBlockState(plot).getBlock() instanceof FarmingBlock,
            "the plot did not start as a fertilized farming block");

        helper.startSequence()
            .thenIdle(WINDOW_TICKS - 1)
            .thenExecute(() -> check(level.getBlockState(plot).getBlock() instanceof FarmingBlock,
                "the plot was reclaimed before its deadline at randomTickSpeed " + randomTickSpeed))
            .thenIdle(3)
            .thenExecute(() -> {
                setRandomTickSpeed(level, DEFAULT_RANDOM_TICK_SPEED);
                check(level.getBlockState(plot).is(BlockRegistry.COMMUNITY_FARM_BLOCK.get()),
                    "the fertilized plot did not go back to grass on its deadline at randomTickSpeed "
                        + randomTickSpeed);
            })
            .thenSucceed();
    }

    // ------------------------------------------------- item 3: nothing is consumed after expiry

    @GameTest(template = TEMPLATE, batch = "community_plot_window", timeoutTicks = 200)
    public static void anExpiredSeedWindowRefusesToTakeTheSeed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos plot = helper.absolutePos(PLOT);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "m9-seed", STAND);

        // A window that ran out while nothing was looking at it: no scheduled tick has fired yet,
        // which is exactly the gap the old code let a player plant into.
        openSeedWindowWithoutScheduling(level, plot, level.getGameTime() - 1L);

        // The questline's own crop, and its real seed item -- tryPlantSeed resolves the crop from
        // the seed before anything else, so a stack that is not a registered seed would fall out
        // of the method long before the check this test is about.
        CropDefinition carrot = CropRegistry.byId("carrot").orElseThrow();
        ItemStack seeds = new ItemStack(carrot.seedItem().get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, seeds);
        FarmingBlock.tryPlantSeed(level, plot, level.getBlockState(plot), player, seeds,
            false, "m9_gametest");

        checkCount(3, seeds.getCount(), "an expired plot took the player's seed");
        check(level.getBlockState(plot).is(BlockRegistry.COMMUNITY_FARM_BLOCK.get()),
            "planting on an expired plot did not reclaim it");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "community_plot_window", timeoutTicks = 200)
    public static void anExpiredHoeingRefusesToTakeTheFertilizedDirt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos plot = helper.absolutePos(PLOT);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "m9-dirt", STAND);

        level.setBlock(plot, BlockRegistry.COMMUNITY_HOED_FARM_BLOCK.get().defaultBlockState(), 3);
        check(level.getBlockEntity(plot) instanceof CommunityFarmBlockEntity,
            "the hoed plot has no community block entity");
        CommunityFarmBlockEntity hoed = (CommunityFarmBlockEntity) level.getBlockEntity(plot);
        // markPrepared always books a full window, so wind the clock rather than the deadline:
        // this is a plot that was hoed a very long time ago.
        hoed.markPrepared(level.getGameTime() - CommunityPlotWindow.HOE_TO_FERTILIZE_TICKS - 1L);
        check(hoed.preparationExpired(level), "the hoeing should already have run out");

        ItemStack dirt = new ItemStack(ItemRegistry.FERTILIZED_DIRT.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, dirt);
        ItemInteractionResult result =
            CommunityHoedFarmBlock.fertilizeCommunityPlot(level, plot, player, dirt);

        check(result != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
            "the expired plot passed the interaction through");
        checkCount(2, dirt.getCount(), "an expired hoeing took the player's fertilized dirt");
        check(level.getBlockState(plot).is(BlockRegistry.COMMUNITY_FARM_BLOCK.get()),
            "fertilizing an expired plot did not put it back to grass");
        helper.succeed();
    }

    // ------------------------------------------------- item 3: the countdown speaks on the way

    @GameTest(template = TEMPLATE, batch = "community_plot_window", timeoutTicks = 300)
    public static void theSeedWindowAnnouncesEachMarkOnlyOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos plot = helper.absolutePos(PLOT);
        // 210 ticks passes the 10 s mark (200 ticks out) and no other.
        openSeedWindow(level, plot, level.getGameTime() + 210L);
        check(level.getBlockEntity(plot) instanceof FarmingBlockEntity,
            "the fertilized plot has no farming block entity");
        FarmingBlockEntity farmBe = (FarmingBlockEntity) level.getBlockEntity(plot);

        helper.startSequence()
            .thenIdle(12)
            .thenExecute(() -> {
                // The scheduled tick at the 10 s mark has already claimed it, so claiming the same
                // mark again is refused: a repeated tick cannot repeat the sentence.
                check(!farmBe.claimSeedWindowWarning(10L),
                    "the 10 s countdown mark was not announced when the window passed it");
                check(farmBe.claimSeedWindowWarning(30L),
                    "a mark the window has not reached should still be claimable");
            })
            .thenSucceed();
    }

    // ------------------------------------------------- item 5: dung scheduling, not placing

    @GameTest(template = TEMPLATE, batch = "community_plot_window", timeoutTicks = 200)
    public static void acceptingTheDungQuestBringsTheScheduleForwardWithoutPlacingAnything(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(PLOT);
        WildResourceSavedData data = WildResourceSavedData.get(level);
        ChunkPos chunk = new ChunkPos(centre);
        long now = level.getGameTime();

        // Every chunk in the search area, and one just outside it, put a long way off: the
        // situation the quest can send a player into, and the reason the quest needs to say
        // anything about the schedule at all.
        int radius = WildResourceQuestScheduling.DEFAULT_CHUNK_RADIUS;
        long faraway = now + 20L * 60L * 12L;
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                data.scheduleAttempt(new ChunkPos(chunk.x + offsetX, chunk.z + offsetZ),
                    WildResourceEntries.DUNG, faraway);
            }
        }
        ChunkPos outside = new ChunkPos(chunk.x + radius + 1, chunk.z);
        data.scheduleAttempt(outside, WildResourceEntries.DUNG, faraway);
        int nodesBefore = data.countInChunk(chunk, WildResourceEntries.DUNG);

        int expedited = WildResourceQuestScheduling.expediteDungNear(level, centre);

        int side = radius * 2 + 1;
        checkCount(side * side, expedited, "the bounded area is not the documented 5x5 chunks");
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                ChunkPos neighbour = new ChunkPos(chunk.x + offsetX, chunk.z + offsetZ);
                check(data.nextAttempt(neighbour, WildResourceEntries.DUNG) <= now,
                    "chunk " + neighbour + " in the search area is still not due");
            }
        }
        check(data.nextAttempt(outside, WildResourceEntries.DUNG) == faraway,
            "a chunk outside the bounded area was expedited");
        // Scheduled, not placed. The attempt that follows is the ordinary one, on the ordinary
        // tick, with the ordinary chunk cap, spacing, substrate rule and randomness -- so the pile
        // it eventually makes is a tracked node like any other, which is what lets it be harvested
        // and reported. Nothing was command-placed here.
        checkCount(nodesBefore, data.countInChunk(chunk, WildResourceEntries.DUNG),
            "expediting the schedule placed a pile itself instead of letting the world do it");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "community_plot_window", timeoutTicks = 200)
    public static void expeditingNeverDelaysAnAttemptThatWasAlreadyDue(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(PLOT);
        WildResourceSavedData data = WildResourceSavedData.get(level);
        ChunkPos chunk = new ChunkPos(centre);
        long overdue = level.getGameTime() - 500L;

        data.scheduleAttempt(chunk, WildResourceEntries.DUNG, overdue);
        WildResourceQuestScheduling.expediteDungNear(level, centre);

        check(data.nextAttempt(chunk, WildResourceEntries.DUNG) == overdue,
            "an already-overdue attempt was pushed back by expediting");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "community_plot_window", timeoutTicks = 200)
    public static void theExpeditedRadiusStaysBoundedHoweverLargeTheCallerAsks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(PLOT);
        int side = WildResourceQuestScheduling.MAX_CHUNK_RADIUS * 2 + 1;

        int expedited = WildResourceQuestScheduling.expediteNear(
            level, centre, WildResourceEntries.DUNG, 10_000);

        check(expedited <= side * side,
            "a caller asking for a huge radius escaped the bound (" + expedited + " chunks)");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static void openSeedWindow(ServerLevel level, BlockPos plot, long deadline) {
        openSeedWindowWithoutScheduling(level, plot, deadline);
        FarmingBlock.scheduleCommunitySeedWindow(level, plot, deadline);
    }

    private static void openSeedWindowWithoutScheduling(ServerLevel level, BlockPos plot, long deadline) {
        level.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState()
            .setValue(FarmingBlock.HYDRATION, 1)
            .setValue(FarmingBlock.HAS_SEEDS, false), 3);
        check(level.getBlockEntity(plot) instanceof FarmingBlockEntity,
            "the fertilized plot has no farming block entity");
        FarmingBlockEntity farmBe = (FarmingBlockEntity) level.getBlockEntity(plot);
        farmBe.setHydration(1);
        farmBe.startCommunitySeedWindow(deadline);
        farmBe.initializeFertileHarvests();
    }

    private static void setRandomTickSpeed(ServerLevel level, int speed) {
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(speed, level.getServer());
    }
}
