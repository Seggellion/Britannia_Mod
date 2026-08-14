package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Live-world lifecycle checks for the shared decorative multiblock implementation. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class NewAssetsDecorativeMultiblockGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private NewAssetsDecorativeMultiblockGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void removingAnyFountainPartTearsDownTheWholeStructure(GameTestHelper helper) {
        DecorativeMultiblockBlock block = BlockRegistry.FOUNTAIN.get();
        Direction facing = Direction.NORTH;
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 3, 2));

        block.duringMutation(() -> {
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
            for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
                helper.getLevel().setBlock(
                        block.worldPosition(anchor, facing, cell), block.stateFor(facing, cell), flags);
            }
            return null;
        });
        for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
            check(helper.getLevel().getBlockState(block.worldPosition(anchor, facing, cell)).is(block),
                    "fountain placement did not create every occupied cell");
        }

        DecorativeMultiblockBlock.Cell child = block.cells().stream()
                .filter(cell -> !block.isRoot(block.stateFor(facing, cell)))
                .findFirst()
                .orElseThrow();
        helper.getLevel().setBlock(
                block.worldPosition(anchor, facing, child), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
            check(!helper.getLevel().getBlockState(block.worldPosition(anchor, facing, cell)).is(block),
                    "removing a child left an orphaned fountain cell");
        }
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
