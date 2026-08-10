package com.seggellion.britannia_mod.structure.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DecorativeMultiblockBlockTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
    }

    @Test
    void cartFootprintEncodesTwentySevenUniqueCellsAndOneRenderedRoot() {
        DecorativeMultiblockBlock block = cart();

        assertEquals(27, block.cells().size());
        assertEquals(13, block.defaultBlockState().getValue(DecorativeMultiblockBlock.PART));
        assertEquals(RenderShape.MODEL, block.getRenderShape(block.defaultBlockState()));
        assertEquals(RenderShape.INVISIBLE,
                block.getRenderShape(block.stateFor(Direction.NORTH, block.cells().getFirst())));
    }

    @Test
    void everyPartRoundTripsToTheAnchorInEveryHorizontalDirection() {
        DecorativeMultiblockBlock block = cart();
        BlockPos anchor = new BlockPos(37, 80, -19);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            HashSet<BlockPos> occupied = new HashSet<>();
            for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
                BlockPos position = block.worldPosition(anchor, facing, cell);
                occupied.add(position);
                assertEquals(anchor,
                        block.anchorPosition(position, block.stateFor(facing, cell)));
            }
            assertEquals(27, occupied.size());
        }
    }

    @Test
    void selectedMinimumCornerMapsToTheMinimumLocalCellForEveryFacing() {
        DecorativeMultiblockBlock block = cart();
        BlockPos selected = new BlockPos(5, 64, 11);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos anchor = block.anchorForMinimumPosition(selected, facing);
            DecorativeMultiblockBlock.Cell minimumCell = block.cells().stream()
                    .filter(cell -> cell.x() == -1 && cell.y() == -1 && cell.z() == -1)
                    .findFirst()
                    .orElseThrow();
            assertEquals(selected, block.worldPosition(anchor, facing, minimumCell));
        }
    }

    @Test
    void footprintLargerThanPartEncodingIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DecorativeMultiblockBlock(
                BlockBehaviour.Properties.of(), -1, 1, -1, 2, -1, 1,
                (x, y, z) -> Block.box(0, 0, 0, 16, 16, 16)));
    }

    private static DecorativeMultiblockBlock cart() {
        return new DecorativeMultiblockBlock(
                BlockBehaviour.Properties.of(), -1, 1, -1, 1, -1, 1,
                (x, y, z) -> Block.box(1, 0, 1, 15, 16, 15));
    }
}
