package com.seggellion.britannia_mod.structure.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.item.MonolithItem;
import com.seggellion.britannia_mod.structure.item.ShrineItemState;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.multiblock.StructureCellRole;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MonolithPlacementPlannerTest {
    private static final VariantId VARIANT = new VariantId("diagnostic_missing_content");
    private static MonolithItem item;

    @BeforeAll
    static void setup() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
        item = MilestoneTwoRegisteredTestContent.monolith();
    }

    @Test
    void rawItemAndConfiguredItemPlanExactEighteenCellsForEveryFacing() {
        BlockPos clicked = new BlockPos(10, 63, 20);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            ShrinePlacementPlannerTest.FakeWorld world = new ShrinePlacementPlannerTest.FakeWorld();
            ItemStack raw = new ItemStack(item);
            ShrinePlacementPlan plan = plan(raw, clicked, Direction.UP, facing, world).plan().orElseThrow();
            assertEquals(18, plan.cells().size());
            assertEquals(1, plan.cells().stream()
                    .filter(cell -> cell.role() == StructureCellRole.ANCHOR).count());
            assertEquals(17, plan.cells().stream()
                    .filter(cell -> cell.role() == StructureCellRole.PART).count());
            assertEquals(18, new HashSet<>(plan.cells().stream().map(cell -> cell.offset()).toList()).size());
            assertEquals(18, new HashSet<>(plan.cells().stream()
                    .map(cell -> cell.worldPosition()).toList()).size());
            assertEquals(new BlockPos(10, 64, 20), plan.anchorPosition());
            assertEquals(ShrineMonolithDefinitions.MONOLITH, plan.placedStructure().familyId());
            assertEquals(VARIANT, plan.placedStructure().variantId());
            assertEquals(expectedOffsets(), plan.placedStructure().footprint());
            assertEquals(expectedPositions(plan.anchorPosition(), facing),
                    plan.cells().stream().map(cell -> cell.worldPosition()).toList());
            plan.cells().stream().filter(cell -> cell.role() == StructureCellRole.PART).forEach(cell -> {
                assertEquals(plan.anchorPosition(),
                        LargeStructurePartBlock.anchorPosition(cell.worldPosition(), cell.placedState()));
                assertEquals(cell.offset(), LargeStructurePartBlock.localOffset(cell.placedState()));
            });
            assertEquals(18, world.placementChecks.size());
            assertEquals(18, world.originalStateReads.size());
            assertEquals(1, raw.getCount());
        }
    }

    @Test
    void shrineItemCannotPlaceMonolithAndMalformedOrWrongFamilyStateFailsBeforeWorldReads() {
        ShrinePlacementPlannerTest.FakeWorld wrongItemWorld = new ShrinePlacementPlannerTest.FakeWorld();
        assertEquals(ShrinePlacementFailure.INVALID_ITEM,
                ShrinePlacementPlanner.plan(
                        item,
                        new ItemStack(MilestoneTwoRegisteredTestContent.shrine()),
                        ShrineMonolithDefinitions.MONOLITH,
                        VARIANT,
                        BlockPos.ZERO, Direction.UP, Direction.NORTH,
                        MilestoneTwoRegisteredTestContent.anchor(),
                        MilestoneTwoRegisteredTestContent.part(), wrongItemWorld).failure());
        assertTrue(wrongItemWorld.originalStateReads.isEmpty());

        ItemStack wrongFamily = new ItemStack(item);
        wrongFamily.set(MilestoneTwoRegisteredTestContent.monolithComponent(), new ShrineItemState(
                ShrineItemState.CURRENT_SCHEMA_VERSION,
                ShrineMonolithDefinitions.SHRINE, new VariantId("honesty")));
        ShrinePlacementPlannerTest.FakeWorld wrongFamilyWorld = new ShrinePlacementPlannerTest.FakeWorld();
        assertEquals(ShrinePlacementFailure.UNSUPPORTED_FAMILY,
                ShrinePlacementPlanner.plan(
                        item, wrongFamily, BlockPos.ZERO, Direction.UP, Direction.NORTH,
                        MilestoneTwoRegisteredTestContent.anchor(),
                        MilestoneTwoRegisteredTestContent.part(), wrongFamilyWorld).failure());
        assertTrue(wrongFamilyWorld.originalStateReads.isEmpty());
    }

    @Test
    void everyPreflightBoundaryIsAtomicAndRenderOffsetNeverChangesTargets() {
        assertFailure(world -> world.inBounds = false, ShrinePlacementFailure.WORLD_BOUND_FAILURE);
        assertFailure(world -> world.loaded = false, ShrinePlacementFailure.REQUIRED_CHUNK_UNLOADED);
        assertFailure(world -> world.replaceable = false, ShrinePlacementFailure.TARGET_OCCUPIED);
        assertFailure(world -> world.unrelated = true, ShrinePlacementFailure.UNRELATED_STRUCTURE_CELL);
        assertFailure(world -> world.allowed = false, ShrinePlacementFailure.PROTECTED_PLACEMENT);
        assertFailure(world -> world.canEncode = false, ShrinePlacementFailure.PART_STATE_ENCODING_FAILURE);
        assertFailure(world -> world.canCreate = false,
                ShrinePlacementFailure.BLOCK_ENTITY_CREATION_FAILURE);
        assertFailure(world -> world.canInitialize = false,
                ShrinePlacementFailure.ANCHOR_INITIALIZATION_FAILURE);

        ShrinePlacementPlan before = plan(new ItemStack(item), BlockPos.ZERO,
                Direction.UP, Direction.SOUTH, new ShrinePlacementPlannerTest.FakeWorld())
                .plan().orElseThrow();
        ShrineMonolithDefinitions.MONOLITH_RENDER_OFFSET.toBlockUnits();
        ShrinePlacementPlan after = plan(new ItemStack(item), BlockPos.ZERO,
                Direction.UP, Direction.SOUTH, new ShrinePlacementPlannerTest.FakeWorld())
                .plan().orElseThrow();
        assertEquals(before.cells().stream().map(cell -> cell.worldPosition()).toList(),
                after.cells().stream().map(cell -> cell.worldPosition()).toList());
        assertFalse(before.cells().stream().anyMatch(cell ->
                cell.worldPosition().equals(cell.worldPosition().above())));
    }

    private static void assertFailure(
            java.util.function.Consumer<ShrinePlacementPlannerTest.FakeWorld> configure,
            ShrinePlacementFailure expected) {
        ShrinePlacementPlannerTest.FakeWorld world = new ShrinePlacementPlannerTest.FakeWorld();
        configure.accept(world);
        ItemStack stack = new ItemStack(item);
        assertEquals(expected, plan(stack, BlockPos.ZERO, Direction.UP, Direction.NORTH, world).failure());
        assertEquals(1, stack.getCount());
        assertEquals(0, world.mutations);
    }

    private static ShrinePlacementPlanningResult plan(
            ItemStack stack, BlockPos clicked, Direction face, Direction facing,
            ShrinePlacementPlannerTest.FakeWorld world) {
        return ShrinePlacementPlanner.plan(
                item, stack, ShrineMonolithDefinitions.MONOLITH, VARIANT,
                clicked, face, facing,
                MilestoneTwoRegisteredTestContent.anchor(),
                MilestoneTwoRegisteredTestContent.part(), world);
    }

    private static List<LocalOffset> expectedOffsets() {
        return List.of(
                new LocalOffset(0, 0, 0), new LocalOffset(1, 0, 0), new LocalOffset(2, 0, 0),
                new LocalOffset(0, 0, 1), new LocalOffset(1, 0, 1), new LocalOffset(2, 0, 1),
                new LocalOffset(0, 1, 0), new LocalOffset(1, 1, 0), new LocalOffset(2, 1, 0),
                new LocalOffset(0, 1, 1), new LocalOffset(1, 1, 1), new LocalOffset(2, 1, 1),
                new LocalOffset(0, 2, 0), new LocalOffset(1, 2, 0), new LocalOffset(2, 2, 0),
                new LocalOffset(0, 2, 1), new LocalOffset(1, 2, 1), new LocalOffset(2, 2, 1));
    }

    private static List<BlockPos> expectedPositions(BlockPos anchor, Direction facing) {
        return expectedOffsets().stream().map(offset -> switch (facing) {
            case NORTH -> anchor.offset(-offset.x(), offset.y(), offset.z());
            case EAST -> anchor.offset(-offset.z(), offset.y(), -offset.x());
            case SOUTH -> anchor.offset(offset.x(), offset.y(), -offset.z());
            case WEST -> anchor.offset(offset.z(), offset.y(), offset.x());
            default -> throw new IllegalArgumentException("horizontal facing required");
        }).toList();
    }
}
