package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.crate.CrateFoundation;
import com.seggellion.britannia_mod.crate.CrateStackLayout;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * One large crate standing on another.
 *
 * <h2>How they meet</h2>
 *
 * <p>A large crate is nineteen voxels tall across two world cells, so the first cell a second one may
 * occupy begins thirteen voxels above the lid it should be resting on. The upper crate is therefore
 * built where ordinary placement puts it - two cells up - and told how far below those cells it is
 * really drawn. The cells underneath, which belong to the lower crate, draw and collide with the part
 * of it that hangs into them.
 *
 * <p>Nothing is merged. Both crates keep their own block entity and their own fifty-four slots; one of
 * them simply knows it is standing on the other.
 *
 * <h2>Breaking is deliberately absent</h2>
 *
 * <p>Taking a stacked pair apart has to decide what becomes of the other crate's contents, and that
 * transaction has not been written. Until it is, breaking either one does nothing at all - which is
 * the only answer that cannot lose an inventory, and is what the last tests here hold it to.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrateLargeStackGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** The lid a second large crate rests on. */
    private static final int LID = 1900;

    /** How far above its own origin a large crate's visible bottom - its base rim - sits. */
    private static final int VISIBLE_BASE = 100;

    /**
     * Where a crate standing on that lid has to start.
     *
     * <p>The lid it rests on, less the floor of the first cell it may occupy, less the height of its
     * own base rim above its origin: 1900 - 3200 - 100. Leaving out that last term is what left the
     * crate hovering a voxel above the lid.
     */
    private static final int ORIGIN = LID
            - CrateFoundation.ROOT_CELL_ABOVE_ANCHOR * CrateStackLayout.CELL_HUNDREDTHS
            - VISIBLE_BASE;

    private CrateLargeStackGameTests() {
    }

    /* ─── standing one on another ────────────────────────────── */

    /** A large crate clicked onto a large crate's lid stands on it rather than floating above it. */
    @GameTest(template = TEMPLATE)
    public static void aLargeCrateStandsOnALargeCrate(GameTestHelper helper) {
        Fixture lower = largeCrate(helper, Direction.NORTH);
        lower.crate().setItem(0, new ItemStack(Items.EMERALD, 5));

        lower.player().setGameMode(GameType.SURVIVAL);
        ItemStack hand = new ItemStack(large(), 8);
        click(lower, hand, lidHit(lower.anchor()));

        BlockPos upperAnchor = CrateFoundation.columnRootFor(lower.anchor());
        CrateBlockEntity upper = upperCrate(helper, upperAnchor);

        check(upper.originHundredths() == ORIGIN,
                "the upper crate sits at " + upper.originHundredths()
                        + " rather than on the lower crate's lid");
        check(upper.getContainerSize() == 54, "the upper crate is not a large crate");
        check(lower.crate().getContainerSize() == 54, "the lower crate stopped being a large crate");
        check(hand.getCount() == 7, "the placement consumed " + (8 - hand.getCount()) + " crates");
        check(lower.crate().getItem(0).is(Items.EMERALD),
                "the lower crate's own contents were disturbed");
        check(upper.getItem(0).isEmpty(), "the two crates are sharing an inventory");
        finish(helper, lower);
    }

    /** Each crate keeps its own fifty-four slots, and its own contents. */
    @GameTest(template = TEMPLATE)
    public static void stackedLargeCratesKeepSeparateInventories(GameTestHelper helper) {
        Fixture lower = largeCrate(helper, Direction.NORTH);
        click(lower, new ItemStack(large(), 8), lidHit(lower.anchor()));
        CrateBlockEntity upper =
                upperCrate(helper, CrateFoundation.columnRootFor(lower.anchor()));

        lower.crate().setItem(0, new ItemStack(Items.EMERALD, 5));
        upper.setItem(0, new ItemStack(Items.DIAMOND, 3));

        check(lower.crate().getItem(0).is(Items.EMERALD) && lower.crate().getItem(0).getCount() == 5,
                "the lower crate's contents changed when the upper one was filled");
        check(upper.getItem(0).is(Items.DIAMOND) && upper.getItem(0).getCount() == 3,
                "the upper crate did not keep its own contents");
        finish(helper, lower);
    }

    /** The crate lands in the same place whichever part of the lid was clicked. */
    @GameTest(template = TEMPLATE)
    public static void everyLidCellGivesTheSameUpperCrate(GameTestHelper helper) {
        Set<BlockPos> anchors = new HashSet<>();
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                Fixture lower = largeCrate(helper, Direction.NORTH);
                BlockPos lidCell = lower.anchor().offset(x, 1, z);
                Vec3 at = new Vec3(
                        lidCell.getX() + 0.5D,
                        lower.anchor().getY() + LID / (double) CrateStackLayout.CELL_HUNDREDTHS,
                        lidCell.getZ() + 0.5D);
                click(lower, new ItemStack(large(), 8),
                        new BlockHitResult(at, Direction.UP, lidCell, false));

                BlockPos expected = CrateFoundation.columnRootFor(lower.anchor());
                check(helper.getLevel().getBlockEntity(expected) instanceof CrateBlockEntity,
                        "clicking lid cell (" + x + "," + z + ") built nothing at " + expected);
                anchors.add(expected.subtract(lower.anchor()));
                clear(helper, lower);
            }
        }
        check(anchors.size() == 1,
                "the upper crate landed in different places depending on where the lid was "
                        + "clicked: " + anchors);
        helper.succeed();
    }

    /** Every facing stacks, and each crate keeps its own orientation. */
    @GameTest(template = TEMPLATE)
    public static void everyFacingStacks(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Fixture lower = largeCrate(helper, facing);
            click(lower, new ItemStack(large(), 8), lidHit(lower.anchor()));

            BlockPos upperAnchor = CrateFoundation.columnRootFor(lower.anchor());
            CrateBlockEntity upper = upperCrate(helper, upperAnchor);
            check(upper.originHundredths() == ORIGIN,
                    "a crate on a " + facing + " crate did not rest on its lid");
            check(helper.getLevel().getBlockState(upperAnchor).getValue(CrateBlock.FACING)
                            .getAxis().isHorizontal(),
                    "the upper crate has no sensible facing");
            clear(helper, lower);
        }
        helper.succeed();
    }

    /* ─── refusals ───────────────────────────────────────────── */

    /** With the space above taken, nothing happens at all. */
    @GameTest(template = TEMPLATE)
    public static void anObstructedLargeStackChangesNothing(GameTestHelper helper) {
        Fixture lower = largeCrate(helper, Direction.NORTH);
        lower.crate().setItem(0, new ItemStack(Items.EMERALD, 5));
        BlockPos blocked = CrateFoundation.columnRootFor(lower.anchor());
        helper.getLevel().setBlock(blocked, Blocks.STONE.defaultBlockState(), 3);

        lower.player().setGameMode(GameType.SURVIVAL);
        ItemStack hand = new ItemStack(large(), 8);
        click(lower, hand, lidHit(lower.anchor()));

        check(helper.getLevel().getBlockState(blocked).is(Blocks.STONE),
                "the obstruction was overwritten");
        check(hand.getCount() == 8, "a refused placement still consumed a crate");
        check(lower.crate().getItem(0).is(Items.EMERALD),
                "a refused placement disturbed the lower crate");
        finish(helper, lower);
    }

    /**
     * A compact crate will not stand on a large crate that is itself standing on one.
     *
     * <p>Its lid is thirteen voxels lower than its cells suggest, so a column resting there would
     * begin two cells below its own root rather than one - which the column geometry does not carry.
     * Refusing costs nothing and is honest; supporting it is separate work.
     */
    @GameTest(template = TEMPLATE)
    public static void aCompactCrateWillNotStandOnAStackedLargeCrate(GameTestHelper helper) {
        Fixture lower = largeCrate(helper, Direction.NORTH);
        click(lower, new ItemStack(large(), 8), lidHit(lower.anchor()));
        BlockPos upperAnchor = CrateFoundation.columnRootFor(lower.anchor());
        upperCrate(helper, upperAnchor);

        lower.player().setGameMode(GameType.SURVIVAL);
        ItemStack hand = new ItemStack(small(), 8);
        click(lower, hand, lidHit(upperAnchor));

        check(hand.getCount() == 8, "a refused placement still consumed a crate");
        check(!(helper.getLevel().getBlockState(upperAnchor.above(2)).getBlock()
                        instanceof CrateBlock),
                "a crate was placed above the stacked large crate");
        finish(helper, lower);
    }

    /* ─── breaking is not implemented yet ────────────────────── */

    /** Neither crate of a stacked pair can be broken, so neither inventory can be lost. */
    @GameTest(template = TEMPLATE)
    public static void neitherStackedLargeCrateCanBeBroken(GameTestHelper helper) {
        Fixture lower = largeCrate(helper, Direction.NORTH);
        click(lower, new ItemStack(large(), 8), lidHit(lower.anchor()));
        BlockPos upperAnchor = CrateFoundation.columnRootFor(lower.anchor());
        CrateBlockEntity upper = upperCrate(helper, upperAnchor);

        lower.crate().setItem(0, new ItemStack(Items.EMERALD, 5));
        upper.setItem(0, new ItemStack(Items.DIAMOND, 3));

        lower.player().gameMode.destroyBlock(lower.anchor());
        lower.player().gameMode.destroyBlock(upperAnchor);

        check(helper.getLevel().getBlockState(lower.anchor()).getBlock() instanceof CrateBlock,
                "the lower crate was destroyed while something was standing on it");
        check(helper.getLevel().getBlockState(upperAnchor).getBlock() instanceof CrateBlock,
                "the upper crate was destroyed before its removal was written");
        check(lower.crate().getItem(0).is(Items.EMERALD), "the lower crate lost its contents");
        check(upperCrate(helper, upperAnchor).getItem(0).is(Items.DIAMOND),
                "the upper crate lost its contents");
        finish(helper, lower);
    }

    /* ─── persistence ────────────────────────────────────────── */

    /** The offset is saved, and is never carried away by the item when a crate is picked up. */
    @GameTest(template = TEMPLATE)
    public static void theOffsetIsSavedButNeverTravels(GameTestHelper helper) {
        Fixture lower = largeCrate(helper, Direction.NORTH);
        click(lower, new ItemStack(large(), 8), lidHit(lower.anchor()));
        CrateBlockEntity upper =
                upperCrate(helper, CrateFoundation.columnRootFor(lower.anchor()));
        upper.setItem(0, new ItemStack(Items.DIAMOND, 3));

        CompoundTag saved = upper.saveWithFullMetadata(helper.getLevel().registryAccess());
        check(saved.getInt("OriginOffset") == ORIGIN,
                "the offset was not saved, so the crate would float after a reload");

        ItemStack carried = new ItemStack(large());
        upper.writePortableState(carried, helper.getLevel().registryAccess());
        CompoundTag portable = carried
                .getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                        net.minecraft.world.item.component.CustomData.EMPTY)
                .copyTag();
        check(!portable.contains("OriginOffset"),
                "a carried crate remembered where it used to be standing, and would be drawn sunk "
                        + "into the ground wherever it was put down next");
        finish(helper, lower);
    }

    /* ─── fixtures ───────────────────────────────────────────── */

    private record Fixture(
            GameTestHelper helper, ServerPlayer player, BlockPos anchor, CrateBlockEntity crate) {
    }

    private static Fixture largeCrate(GameTestHelper helper, Direction facing) {
        for (int x = 1; x <= 4; x++) {
            for (int z = 1; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().mayBuild = true;
        player.getAbilities().instabuild = true;
        player.onUpdateAbilities();
        player.absMoveTo(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D);
        player.setYRot(facing.getOpposite().toYRot());
        player.setYHeadRot(facing.getOpposite().toYRot());
        player.setXRot(0.0F);
        player.setOldPosAndRot();

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(large(), 8));
        Vec3 at = new Vec3(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, new BlockHitResult(at, Direction.UP, floor, false));

        BlockPos anchor = floor.above();
        BlockState placed = helper.getLevel().getBlockState(anchor);
        check(placed.getBlock() instanceof CrateBlock && CrateFoundation.isFoundation(placed),
                "the lower large crate was not placed at " + anchor);
        check(helper.getLevel().getBlockEntity(anchor) instanceof CrateBlockEntity,
                "the lower large crate has no inventory");
        return new Fixture(helper, player, anchor,
                (CrateBlockEntity) helper.getLevel().getBlockEntity(anchor));
    }

    /** A click on the very top of the lid of the crate anchored here. */
    private static BlockHitResult lidHit(BlockPos anchor) {
        double y = anchor.getY() + LID / (double) CrateStackLayout.CELL_HUNDREDTHS;
        Vec3 at = new Vec3(anchor.getX() + 0.5D, y, anchor.getZ() + 0.5D);
        return new BlockHitResult(at, Direction.UP, anchor.above(), false);
    }

    private static void click(Fixture fixture, ItemStack hand, BlockHitResult hit) {
        fixture.player().setItemInHand(InteractionHand.MAIN_HAND, hand);
        fixture.player().gameMode.useItemOn(fixture.player(), fixture.helper().getLevel(),
                fixture.player().getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static CrateBlockEntity upperCrate(GameTestHelper helper, BlockPos anchor) {
        BlockState state = helper.getLevel().getBlockState(anchor);
        check(state.getBlock() instanceof CrateBlock,
                "no crate stands at " + anchor + "; found " + state.getBlock());
        check(helper.getLevel().getBlockEntity(anchor) instanceof CrateBlockEntity,
                "the crate at " + anchor + " has no inventory");
        return (CrateBlockEntity) helper.getLevel().getBlockEntity(anchor);
    }

    private static void clear(GameTestHelper helper, Fixture fixture) {
        helper.getLevel().getServer().getPlayerList().remove(fixture.player());
        for (int y = 0; y <= 6; y++) {
            for (int x = -1; x <= 2; x++) {
                for (int z = -1; z <= 2; z++) {
                    helper.getLevel().removeBlock(fixture.anchor().offset(x, y, z), false);
                }
            }
        }
    }

    private static void finish(GameTestHelper helper, Fixture fixture) {
        helper.getLevel().getServer().getPlayerList().remove(fixture.player());
        helper.succeed();
    }

    private static Item large() {
        return ItemRegistry.LARGE_CRATE_ITEM.get();
    }

    private static Item small() {
        return ItemRegistry.SMALL_CRATE_ITEM.get();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
