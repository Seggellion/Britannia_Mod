package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.event.TreeKarmaHandler;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.TallCropSupport;
import com.seggellion.britannia_mod.item.GrapesItem;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.resource.extraction.ExtractionToolPredicates;
import com.seggellion.britannia_mod.util.AxeHarvestRules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Cutting a grape vine with the Britannia axe, end to end through the real break lifecycle.
 *
 * <h2>What was actually wrong</h2>
 * A grape vine could not be destroyed with an axe at all, and four independent gates each said no:
 * {@code TwoHandedAxeItem.getDestroySpeed} returned {@code 0.0F}, {@code ToolInteractionHandler}
 * cancelled the break-speed event, the adventure-mode {@code CAN_BREAK} predicate covered only wood
 * and leaves, and {@code WoodChopEventHandler} cancelled the break for any axe swing and then had no
 * branch that would remove the block. A fix that clears three of the four still leaves the vine
 * standing, which is why these tests drive {@code player.gameMode.destroyBlock} — the whole server
 * lifecycle — rather than any single gate in isolation.
 *
 * <h2>What a cut must and must not do</h2>
 * It destroys the plant and gives nothing: no grape, no {@code WeightedWoodItem}, no sapling, no
 * tree karma. The mature crop is deliberately lost. It also has to take the whole arbor with it and
 * reset the plot, because the segments are invisible solid blocks and an orphaned one is a wall a
 * player cannot see.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GrapeVineAxeGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BATCH = "grape_vine_axe";
    private static final BlockPos PLOT = new BlockPos(1, 1, 1);

    private GrapeVineAxeGameTests() {
    }

    /* ------------------------------------------------------------------ */
    /*  Every segment, in every mode a player can be in                    */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void adventureAxeSeversEverySegmentOfTheArbor(GameTestHelper helper) {
        for (int segment = 1; segment <= 3; segment++) {
            ServerLevel level = helper.getLevel();
            matureArbor(helper);
            ServerPlayer cutter = axeHolder(helper, GameType.ADVENTURE, "grape-adventure-" + segment);

            BlockPos target = helper.absolutePos(PLOT.above(segment));
            check(TallCropSupport.isGrapeSegment(level.getBlockState(target)),
                    "segment " + segment + " was not an arbor block before the cut");

            cutter.gameMode.destroyBlock(target);

            check(!TallCropSupport.isGrapeSegment(level.getBlockState(target)),
                    "segment " + segment + " survived an axe cut in adventure mode");
            check(cutter.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                    "cutting a vine moved the player out of adventure");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void survivalAndCreativeAxeBothSeverTheArbor(GameTestHelper helper) {
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
            ServerLevel level = helper.getLevel();
            matureArbor(helper);
            ServerPlayer cutter = axeHolder(helper, mode, "grape-" + mode.getName());

            BlockPos target = helper.absolutePos(PLOT.above(1));
            cutter.gameMode.destroyBlock(target);

            check(!TallCropSupport.isGrapeSegment(level.getBlockState(target)),
                    "the arbor survived an axe cut in " + mode.getName());
        }
        helper.succeed();
    }

    /** The retired standalone vine is still reachable in old saves, so it is severable too. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void theLegacyStandaloneVineIsAlsoSeverable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(3, 1, 3);
        helper.setBlock(relative, BlockRegistry.GRAPE_VINE_BLOCK.get().defaultBlockState());
        ServerPlayer cutter = axeHolder(helper, GameType.ADVENTURE, "legacy-vine-cutter");

        BlockPos target = helper.absolutePos(relative);
        cutter.gameMode.destroyBlock(target);

        check(!level.getBlockState(target).is(BlockRegistry.GRAPE_VINE_BLOCK.get()),
                "the legacy grape vine survived an axe cut");
        check(dropsNear(level, target).isEmpty(), "the legacy vine dropped something when cut");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  The whole plant, and nothing in return                             */
    /* ------------------------------------------------------------------ */

    /**
     * Cutting one segment takes the whole arbor and resets the plot. This is the assertion that
     * would fail if the destruction branch removed only the targeted block: the other two segments
     * would remain as invisible solid obstacles owned by nothing.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void cuttingOneSegmentTakesTheWholePlantAndResetsThePlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FarmingBlockEntity farmBe = matureArbor(helper);
        check(farmBe.hasCrop(), "the fixture did not plant a crop");
        ServerPlayer cutter = axeHolder(helper, GameType.ADVENTURE, "grape-whole-plant");

        // Deliberately the middle segment, not the base: the cascade has to run upwards and
        // downwards from whichever block was struck.
        cutter.gameMode.destroyBlock(helper.absolutePos(PLOT.above(2)));

        for (int offset = 1; offset <= 3; offset++) {
            BlockState left = level.getBlockState(helper.absolutePos(PLOT.above(offset)));
            check(!TallCropSupport.isGrapeSegment(left),
                    "segment " + offset + " was orphaned by the cut");
            check(left.getCollisionShape(level, helper.absolutePos(PLOT.above(offset))).isEmpty(),
                    "segment " + offset + " left invisible collision behind");
        }
        FarmingBlockEntity after = farmEntity(helper);
        check(!after.hasCrop(), "the plot kept its crop after the vine was cut down");
        helper.succeed();
    }

    /**
     * A cut vine is not a harvest and not a felled tree. Nothing drops at all, and in particular no
     * weighted wood: routing grapes through the log branch would have minted one.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void severingAVineDropsNothingAndMintsNoWood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        matureArbor(helper);
        ServerPlayer cutter = axeHolder(helper, GameType.SURVIVAL, "grape-no-drops");
        BlockPos target = helper.absolutePos(PLOT.above(1));

        TreeKarmaHandler.treeCutTimestamps.remove(cutter.getUUID());
        cutter.gameMode.destroyBlock(target);

        List<ItemEntity> drops = dropsNear(level, target);
        check(drops.isEmpty(), "cutting a grape vine dropped " + drops.size() + " item(s)");
        check(drops.stream().noneMatch(drop -> drop.getItem().getItem() instanceof WeightedWoodItem),
                "cutting a grape vine minted weighted wood");
        check(!TreeKarmaHandler.treeCutTimestamps.containsKey(cutter.getUUID()),
                "cutting a grape vine filed tree karma against the player");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Still not wood, and still not an authority                         */
    /* ------------------------------------------------------------------ */

    /**
     * The grape blocks are axe targets without being timber. If they ever satisfied a wood predicate
     * the wood handler would claim them again and start paying out weighted wood.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void grapeBlocksAreSeverableButAreNotWood(GameTestHelper helper) {
        for (BlockState state : List.of(
                BlockRegistry.GRAPE_ARBOR_BLOCK.get().defaultBlockState(),
                BlockRegistry.GRAPE_VINE_BLOCK.get().defaultBlockState())) {
            check(AxeHarvestRules.isAllowedAxeSeverablePlant(state), state + " is not severable");
            check(AxeHarvestRules.isAllowedAxeHarvestBlock(state), state + " is not an axe target");
            check(!AxeHarvestRules.isAllowedLogBlock(state), state + " was classified as a log");
            check(!AxeHarvestRules.isAllowedLeafBlock(state), state + " was classified as leaves");
            check(!AxeHarvestRules.isAllowedFruitBlock(state), state + " was classified as fruit");
            check(!AxeHarvestRules.isFruitTreeBlock(state), state + " was classified as fruit-tree wood");
            check(new ItemStack(ItemRegistry.TWO_HANDED_AXE.get())
                            .getItem().getDestroySpeed(ItemStack.EMPTY, state) > 0.0F,
                    state + " still has a zero destroy speed, so the dig can never complete");
        }
        helper.succeed();
    }

    /**
     * Severability is permission to attempt a cut, never permission to bypass the shared
     * authorization boundary. Automation is refused there, so it is refused here.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void automationCannotSeverAVine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        matureArbor(helper);

        FakePlayer machine = FakePlayerFactory.getMinecraft(level);
        machine.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.TWO_HANDED_AXE.get()));
        BlockPos target = helper.absolutePos(PLOT.above(1));

        com.seggellion.britannia_mod.event.WoodChopEventHandler.onBlockBreak(
                new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(
                        level, target, level.getBlockState(target), machine));

        check(TallCropSupport.isGrapeSegment(level.getBlockState(target)),
                "a fake player severed a grape vine through the axe path");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Fixtures                                                           */
    /* ------------------------------------------------------------------ */

    /** A fruiting arbor: the plot, plus its three occupancy segments. */
    private static FarmingBlockEntity matureArbor(GameTestHelper helper) {
        helper.setBlock(PLOT, BlockRegistry.FARMING_BLOCK.get().defaultBlockState());
        FarmingBlockEntity farmBe = farmEntity(helper);
        farmBe.plantMigratedCrop(grapeCrop(), GrapesItem.DEFAULT_VARIETY_ID, 7);
        for (int offset = 1; offset <= 3; offset++) {
            check(TallCropSupport.isGrapeSegment(
                            helper.getBlockState(PLOT.above(offset))),
                    "the arbor fixture did not fill the block " + offset + " above its plot");
        }
        return farmBe;
    }

    /** A player in {@code mode} holding an axe that carries its real adventure-mode grant. */
    private static ServerPlayer axeHolder(GameTestHelper helper, GameType mode, String name) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(helper.getLevel(), name);
        player.setGameMode(mode);
        ItemStack tool = new ItemStack(ItemRegistry.TWO_HANDED_AXE.get());
        AdventureModePredicate predicate = ExtractionToolPredicates.predicateFor(tool);
        check(predicate != null, "the two-handed axe must earn a predicate");
        tool.set(DataComponents.CAN_BREAK, predicate);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        return player;
    }

    private static FarmingBlockEntity farmEntity(GameTestHelper helper) {
        if (helper.getBlockEntity(PLOT) instanceof FarmingBlockEntity farmBe) {
            return farmBe;
        }
        throw new GameTestAssertException("no farming block entity at " + PLOT);
    }

    private static CropDefinition grapeCrop() {
        return CropRegistry.byId("grapes")
                .orElseThrow(() -> new GameTestAssertException("grapes crop is not registered"));
    }

    private static List<ItemEntity> dropsNear(ServerLevel level, BlockPos absolute) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3.0D));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
