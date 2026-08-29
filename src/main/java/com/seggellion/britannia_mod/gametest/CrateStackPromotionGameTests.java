package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateStackPromotion;
import com.seggellion.britannia_mod.crate.CrateVariant;
import com.seggellion.britannia_mod.crate.LogicalCrateContainer;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Turning a legacy crate into a one-crate column, in a world where the block's own teardown runs.
 *
 * <h2>Why this cannot be a unit test</h2>
 *
 * <p>The whole risk in promotion is a world callback. A crate is a {@code DecorativeMultiblockBlock},
 * and replacing one normally triggers {@code onRemove} -> {@code dismantle} -> {@code dropContents},
 * which would leave the crate's items on the floor while the new column also holds them. Only a real
 * level runs that path, so only a GameTest can show it did not happen. The assertions below therefore
 * look as hard at the ground as they do at the column.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrateStackPromotionGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private CrateStackPromotionGameTests() {
    }

    /**
     * The acceptance case: contents cross into the column exactly once, and nothing hits the floor.
     */
    @GameTest(template = TEMPLATE)
    public static void promotingASmallCrateCarriesItsContentsAndDropsNothing(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        placeLegacy(helper, BlockRegistry.SMALL_CRATE.get(), pos, Direction.WEST);

        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Bane of Crates"));
        CrateBlockEntity legacy = (CrateBlockEntity) helper.getLevel().getBlockEntity(pos);
        legacy.setItem(0, named);
        legacy.setItem(8, new ItemStack(Items.GOLD_INGOT, 11));

        CrateStackPromotion.Result result = CrateStackPromotion.promote(helper.getLevel(), pos);

        check(result.succeeded(), "promotion refused: " + result.refusal());
        check(helper.getLevel().getBlockState(pos).is(BlockRegistry.CRATE_STACK.get()),
                "the position is not a crate column");
        CrateStackBlockEntity stack = result.promoted().orElseThrow().stack();
        int crateId = result.promoted().orElseThrow().crateId();

        check(stack.crateCount() == 1, "a promoted crate should arrive as one logical crate");
        check(stack.crateById(crateId).variant() == CrateVariant.SMALL, "variant was not carried");
        check(stack.crateById(crateId).facing() == Direction.WEST, "facing was not carried");

        LogicalCrateContainer carried = stack.containerFor(crateId);
        check(carried.getItem(0).is(Items.DIAMOND_SWORD), "the contents did not arrive");
        check(Component.literal("Bane of Crates")
                        .equals(carried.getItem(0).get(DataComponents.CUSTOM_NAME)),
                "item components did not survive promotion");
        check(carried.getItem(8).getCount() == 11, "a second slot did not arrive intact");

        // The whole point: the old block entity's teardown must not have spilled anything, and the
        // crate itself must not have dropped as an item either.
        check(countDropped(helper, pos, Items.DIAMOND_SWORD) == 0,
                "promotion spilled the crate's contents onto the floor");
        check(countDropped(helper, pos, Items.GOLD_INGOT) == 0,
                "promotion spilled the crate's contents onto the floor");
        check(helper.getLevel()
                        .getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(5.0D))
                        .isEmpty(),
                "promotion dropped something it should not have");
        helper.succeed();
    }

    /** The same guarantee for the three-row crate, whose inventory is a different size. */
    @GameTest(template = TEMPLATE)
    public static void promotingAMediumCrateKeepsAllTwentySevenSlots(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        placeLegacy(helper, BlockRegistry.MEDIUM_CRATE.get(), pos, Direction.NORTH);

        CrateBlockEntity legacy = (CrateBlockEntity) helper.getLevel().getBlockEntity(pos);
        legacy.setItem(26, new ItemStack(Items.EMERALD, 5));

        CrateStackPromotion.Result result = CrateStackPromotion.promote(helper.getLevel(), pos);

        check(result.succeeded(), "promotion refused: " + result.refusal());
        CrateStackBlockEntity stack = result.promoted().orElseThrow().stack();
        int crateId = result.promoted().orElseThrow().crateId();
        check(stack.containerFor(crateId).getContainerSize() == 27, "the medium crate lost slots");
        check(stack.containerFor(crateId).getItem(26).getCount() == 5, "the last slot did not arrive");
        check(helper.getLevel()
                        .getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(5.0D))
                        .isEmpty(),
                "promotion dropped something it should not have");
        helper.succeed();
    }

    /**
     * The large crate is a 2x2x2 multiblock and has no single cell to convert.
     *
     * <p>Refusal has to leave it exactly as it was — a half-converted eight-cell structure would be
     * far worse than never starting.
     */
    @GameTest(template = TEMPLATE)
    public static void promotingALargeCrateIsRefusedAndChangesNothing(GameTestHelper helper) {
        CrateBlock large = BlockRegistry.LARGE_CRATE.get();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        placeLegacy(helper, large, anchor, Direction.NORTH);
        CrateBlockEntity legacy = (CrateBlockEntity) helper.getLevel().getBlockEntity(anchor);
        legacy.setItem(0, new ItemStack(Items.DIAMOND, 4));

        CrateStackPromotion.Result result = CrateStackPromotion.promote(helper.getLevel(), anchor);

        check(!result.succeeded(), "the large crate must not become a compact column");
        check(result.refusal().orElseThrow() == CrateStackPromotion.Refusal.VARIANT_NOT_SUPPORTED,
                "refused for the wrong reason: " + result.refusal());
        check(helper.getLevel().getBlockState(anchor).is(large), "the large crate was disturbed");
        for (DecorativeMultiblockBlock.Cell cell : large.cells()) {
            check(helper.getLevel().getBlockState(large.worldPosition(anchor, Direction.NORTH, cell))
                            .is(large),
                    "a refused promotion removed one of the large crate's cells");
        }
        CrateBlockEntity after = (CrateBlockEntity) helper.getLevel().getBlockEntity(anchor);
        check(after.getItem(0).getCount() == 4, "a refused promotion disturbed the contents");
        check(helper.getLevel()
                        .getEntitiesOfClass(ItemEntity.class, new AABB(anchor).inflate(5.0D))
                        .isEmpty(),
                "a refused promotion dropped something");
        helper.succeed();
    }

    /**
     * A column that has fallen back to one crate stays a column.
     *
     * <p>Demotion would put a second inventory-carrying conversion into ordinary play, every time a
     * stack was emptied to its last crate. Promoting only, and only once, keeps that count at one for
     * the lifetime of the column.
     */
    @GameTest(template = TEMPLATE)
    public static void aColumnDownToOneCrateDoesNotTurnBackIntoACrateBlock(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        placeLegacy(helper, BlockRegistry.SMALL_CRATE.get(), pos, Direction.NORTH);

        CrateStackPromotion.Result result = CrateStackPromotion.promote(helper.getLevel(), pos);
        check(result.succeeded(), "promotion refused: " + result.refusal());
        CrateStackBlockEntity stack = result.promoted().orElseThrow().stack();
        int first = result.promoted().orElseThrow().crateId();
        int second = stack.appendCrate(CrateVariant.SMALL, Direction.NORTH).orElseThrow();

        stack.removeCrate(second);

        check(stack.crateCount() == 1, "the column should be back to one crate");
        check(helper.getLevel().getBlockState(pos).is(BlockRegistry.CRATE_STACK.get()),
                "a one-crate column must remain a column");
        check(stack.crateById(first) != null, "the surviving crate lost its identity");
        helper.succeed();
    }

    /**
     * A container view is tied to its crate, and to a live column at a live position.
     *
     * <p>Needs a real level and a real player, which is why it sits here rather than in the domain
     * suite: the validity rules are about the world the column is in as much as about the column.
     */
    @GameTest(template = TEMPLATE)
    public static void aContainerViewSurvivesItsNeighbourAndDiesWithItsColumn(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        placeLegacy(helper, BlockRegistry.SMALL_CRATE.get(), pos, Direction.NORTH);
        CrateStackPromotion.Result result = CrateStackPromotion.promote(helper.getLevel(), pos);
        check(result.succeeded(), "promotion refused: " + result.refusal());

        CrateStackBlockEntity stack = result.promoted().orElseThrow().stack();
        int lower = result.promoted().orElseThrow().crateId();
        int upper = stack.appendCrate(CrateVariant.SMALL, Direction.NORTH).orElseThrow();

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);

        LogicalCrateContainer upperView = stack.containerFor(upper);
        check(upperView.stillValid(player), "a view of a live crate should be usable");

        stack.removeCrate(lower);
        check(upperView.stillValid(player),
                "removing the crate below must not invalidate the crate above");

        stack.removeCrate(upper);
        check(!upperView.stillValid(player), "a view of a departed crate must stop being usable");

        helper.getLevel().removeBlock(pos, false);
        check(!stack.containerFor(lower).stillValid(player),
                "a view must not outlive the column's block");

        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /* ─── helpers ────────────────────────────────────────────── */

    /** Places a legacy crate structure the way the multiblock family builds itself. */
    private static void placeLegacy(
            GameTestHelper helper, CrateBlock block, BlockPos anchor, Direction facing) {
        block.duringMutation(() -> {
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
            for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
                helper.getLevel().setBlock(
                        block.worldPosition(anchor, facing, cell), block.stateFor(facing, cell), flags);
            }
            return null;
        });
    }

    private static int countDropped(GameTestHelper helper, BlockPos around, Item item) {
        int found = 0;
        for (ItemEntity entity : helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(5.0D))) {
            if (entity.getItem().is(item)) {
                found += entity.getItem().getCount();
            }
        }
        return found;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
