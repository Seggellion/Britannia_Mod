package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dedicated-server persistence, menu, and anti-duplication checks for crate containers. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class NewAssetsCrateGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private NewAssetsCrateGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void largeCrateUsesOneMenuInventoryAndDropsItOnceFromAChildBreak(GameTestHelper helper) {
        CrateBlock block = BlockRegistry.LARGE_CRATE.get();
        Direction facing = Direction.NORTH;
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 3, 3));
        place(helper, block, anchor, facing);

        check(helper.getLevel().getBlockEntity(anchor) instanceof CrateBlockEntity,
                "large crate root did not create its inventory");
        CrateBlockEntity crate = (CrateBlockEntity) helper.getLevel().getBlockEntity(anchor);
        long blockEntities = block.cells().stream()
                .filter(cell -> helper.getLevel().getBlockEntity(block.worldPosition(anchor, facing, cell)) != null)
                .count();
        check(blockEntities == 1, "large crate created more than one authoritative inventory");
        crate.setItem(0, new ItemStack(Items.DIAMOND, 7));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.openMenu(crate);
        check(player.containerMenu instanceof ChestMenu, "large crate did not open a server chest menu");
        player.closeContainer();

        DecorativeMultiblockBlock.Cell child = block.cells().getLast();
        BlockPos childPos = block.worldPosition(anchor, facing, child);
        var childState = helper.getLevel().getBlockState(childPos);
        block.onDestroyedByPlayer(
                childState, helper.getLevel(), childPos, player, true, childState.getFluidState());

        for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
            check(!helper.getLevel().getBlockState(block.worldPosition(anchor, facing, cell)).is(block),
                    "child break left an orphaned large-crate cell");
        }

        AABB dropsArea = new AABB(anchor).inflate(4.0D);
        int diamonds = 0;
        int crateItems = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, dropsArea)) {
            ItemStack stack = entity.getItem();
            if (stack.is(Items.DIAMOND)) {
                diamonds += stack.getCount();
            } else if (stack.is(ItemRegistry.LARGE_CRATE_ITEM.get())) {
                crateItems += stack.getCount();
            }
        }
        check(diamonds == 7, "large crate contents were lost or duplicated");
        check(crateItems == 1, "large crate structure did not drop exactly one block item");
        helper.succeed();
    }

    private static void place(GameTestHelper helper, CrateBlock block, BlockPos anchor, Direction facing) {
        block.duringMutation(() -> {
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
            for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
                helper.getLevel().setBlock(
                        block.worldPosition(anchor, facing, cell), block.stateFor(facing, cell), flags);
            }
            return null;
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
