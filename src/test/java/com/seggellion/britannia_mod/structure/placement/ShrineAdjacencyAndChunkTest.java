package com.seggellion.britannia_mod.structure.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShrineAdjacencyAndChunkTest {
    @BeforeAll
    static void setup() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
    }

    @Test
    void actualRegisteredStairsCanOccupyEveryPerimeterCellWithoutMutatingAnyShrineCell() {
        assertInstanceOf(StairBlock.class, Blocks.OAK_STAIRS);
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState();
        assertFalse(stair.canBeReplaced());

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            ShrinePlacementPlan plan = ShrinePlacementPlanner.plan(
                    MilestoneTwoRegisteredTestContent.shrine(),
                    new ItemStack(MilestoneTwoRegisteredTestContent.shrine()),
                    ShrineMonolithDefinitions.SHRINE,
                    new VariantId("honesty"),
                    new BlockPos(31, 69, 31),
                    Direction.UP,
                    facing,
                    MilestoneTwoRegisteredTestContent.anchor(),
                    MilestoneTwoRegisteredTestContent.part(),
                    new ShrinePlacementPlannerTest.FakeWorld()).plan().orElseThrow();

            Map<BlockPos, BlockState> simulatedWorld = new HashMap<>();
            plan.cells().forEach(cell -> simulatedWorld.put(cell.worldPosition(), cell.placedState()));
            Map<BlockPos, BlockState> shrineSnapshot = Map.copyOf(simulatedWorld);
            Set<BlockPos> occupied = shrineSnapshot.keySet();
            Set<BlockPos> perimeter = new HashSet<>();
            for (BlockPos cell : occupied) {
                assertFalse(simulatedWorld.get(cell).canBeReplaced());
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos target = cell.relative(direction);
                    if (!occupied.contains(target)) {
                        perimeter.add(target.immutable());
                    }
                }
            }
            assertEquals(8, perimeter.size());
            for (BlockPos target : perimeter) {
                simulatedWorld.put(target, Blocks.SHORT_GRASS.defaultBlockState());
                assertTrue(simulatedWorld.get(target).canBeReplaced());
                simulatedWorld.put(target, stair);
                assertEquals(stair, simulatedWorld.get(target));
                shrineSnapshot.forEach((position, state) ->
                        assertEquals(state, simulatedWorld.get(position)));
            }
            assertEquals(4, plan.cells().size());
        }
    }
}
