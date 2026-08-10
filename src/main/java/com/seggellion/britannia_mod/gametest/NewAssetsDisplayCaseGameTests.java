package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.block.DisplayCaseBlock;
import com.seggellion.britannia_mod.block.DisplayCaseBlock.ConnectionForm;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dedicated-server topology and lifecycle checks for Milestone 9 display cases. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class NewAssetsDisplayCaseGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private NewAssetsDisplayCaseGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void displayCasesConnectWithoutCreatingInventories(GameTestHelper helper) {
        DisplayCaseBlock block = BlockRegistry.DISPLAY_CASE.get();
        BlockPos west = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos middle = helper.absolutePos(new BlockPos(3, 3, 2));
        BlockPos east = helper.absolutePos(new BlockPos(4, 3, 2));

        place(helper, block, west);
        check(form(helper, block, west) == ConnectionForm.INDEPENDENT,
                "a lone display case was not independent");
        place(helper, block, middle);
        check(form(helper, block, west) == ConnectionForm.END
                        && form(helper, block, middle) == ConnectionForm.END,
                "two display cases did not become matching end pieces");
        place(helper, block, east);
        check(form(helper, block, west) == ConnectionForm.END
                        && form(helper, block, middle) == ConnectionForm.MIDDLE
                        && form(helper, block, east) == ConnectionForm.END,
                "three display cases did not form end/middle/end");
        check(helper.getLevel().getBlockState(middle).getValue(DisplayCaseBlock.WEST)
                        && helper.getLevel().getBlockState(middle).getValue(DisplayCaseBlock.EAST),
                "middle display case did not open both shared faces");

        BlockPos corner = helper.absolutePos(new BlockPos(7, 3, 5));
        BlockPos cornerEast = corner.east();
        BlockPos cornerSouth = corner.south();
        place(helper, block, corner);
        place(helper, block, cornerEast);
        place(helper, block, cornerSouth);
        check(form(helper, block, corner) == ConnectionForm.CORNER,
                "adjacent display-case neighbours did not form a corner");
        check(helper.getLevel().getBlockState(corner).getValue(DisplayCaseBlock.EAST)
                        && helper.getLevel().getBlockState(corner).getValue(DisplayCaseBlock.SOUTH),
                "corner display case did not open the two shared faces");

        for (BlockPos anchor : List.of(west, middle, east, corner, cornerEast, cornerSouth)) {
            check(helper.getLevel().getBlockEntity(anchor) == null
                            && helper.getLevel().getBlockEntity(anchor.above()) == null,
                    "decorative display case created a storage/display block entity");
        }

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos middleTop = middle.above();
        var topState = helper.getLevel().getBlockState(middleTop);
        block.onDestroyedByPlayer(
                topState, helper.getLevel(), middleTop, player, true, topState.getFluidState());

        check(!helper.getLevel().getBlockState(middle).is(block)
                        && !helper.getLevel().getBlockState(middleTop).is(block),
                "breaking the upper cell left an orphaned display case");
        check(form(helper, block, west) == ConnectionForm.INDEPENDENT
                        && form(helper, block, east) == ConnectionForm.INDEPENDENT,
                "neighbour display cases did not disconnect after teardown");
        int drops = helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class, new AABB(middle).inflate(2.0D)).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(ItemRegistry.DISPLAY_CASE_ITEM.get()))
                .mapToInt(ItemStack::getCount)
                .sum();
        check(drops == 1, "display-case teardown did not drop exactly one item");
        helper.succeed();
    }

    private static void place(GameTestHelper helper, DisplayCaseBlock block, BlockPos anchor) {
        Direction facing = Direction.NORTH;
        block.duringMutation(() -> {
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
            for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
                helper.getLevel().setBlock(
                        block.worldPosition(anchor, facing, cell), block.stateFor(facing, cell), flags);
            }
            return null;
        });
        for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
            helper.getLevel().updateNeighborsAt(block.worldPosition(anchor, facing, cell), block);
        }
    }

    private static ConnectionForm form(GameTestHelper helper, DisplayCaseBlock block, BlockPos anchor) {
        var state = helper.getLevel().getBlockState(anchor);
        check(state.is(block) && block.isRoot(state), "expected a display-case root");
        return block.connectionForm(state);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
