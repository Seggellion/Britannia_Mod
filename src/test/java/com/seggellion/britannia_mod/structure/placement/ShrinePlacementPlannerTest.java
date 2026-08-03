package com.seggellion.britannia_mod.structure.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.item.ShrineItem;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlock;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.multiblock.StructureCellRole;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShrinePlacementPlannerTest {
    private static ShrineItem item;
    private static LargeStructureAnchorBlock anchor;
    private static LargeStructurePartBlock part;

    @BeforeAll
    static void setup() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
        item = MilestoneTwoRegisteredTestContent.shrine();
        anchor = MilestoneTwoRegisteredTestContent.anchor();
        part = MilestoneTwoRegisteredTestContent.part();
    }

    @Test
    void allFourFacingsCreateOneAnchorThreePartsAndExactReverseOffsets() {
        BlockPos clicked = new BlockPos(10, 63, 20);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            FakeWorld world = new FakeWorld();
            ShrinePlacementPlanningResult result = plan(
                    new ItemStack(item), ShrineMonolithDefinitions.SHRINE,
                    new VariantId("honesty"), clicked, Direction.UP, facing, world);
            assertTrue(result.successful(), facing.toString());
            ShrinePlacementPlan plan = result.plan().orElseThrow();
            assertEquals(new BlockPos(10, 64, 20), plan.anchorPosition());
            assertEquals(4, plan.cells().size());
            assertEquals(1, plan.cells().stream()
                    .filter(cell -> cell.role() == StructureCellRole.ANCHOR).count());
            assertEquals(3, plan.cells().stream()
                    .filter(cell -> cell.role() == StructureCellRole.PART).count());
            assertEquals(facing, plan.placedStructure().facing());
            assertEquals(ShrineMonolithDefinitions.SHRINE, plan.placedStructure().familyId());
            assertEquals(new VariantId("honesty"), plan.placedStructure().variantId());
            assertEquals(ShrineMonolithDefinitions.catalogue()
                    .family(ShrineMonolithDefinitions.SHRINE).orElseThrow().footprint(),
                    plan.placedStructure().footprint());
            plan.cells().stream().filter(cell -> cell.role() == StructureCellRole.PART).forEach(cell -> {
                assertEquals(plan.anchorPosition(),
                        LargeStructurePartBlock.anchorPosition(cell.worldPosition(), cell.placedState()));
                assertEquals(cell.offset(), LargeStructurePartBlock.localOffset(cell.placedState()));
            });
            assertEquals(4, world.originalStateReads.size());
            assertEquals(0, world.mutations);
        }
    }

    @Test
    void itemFamilyVariantAndFloorFailuresAreTypedAndNonMutating() {
        FakeWorld world = new FakeWorld();
        assertEquals(ShrinePlacementFailure.INVALID_ITEM,
                plan(new ItemStack(Items.STICK), ShrineMonolithDefinitions.SHRINE,
                        new VariantId("honesty"), BlockPos.ZERO, Direction.UP, Direction.NORTH, world).failure());
        assertEquals(ShrinePlacementFailure.UNSUPPORTED_FAMILY,
                plan(new ItemStack(item), ShrineMonolithDefinitions.MONOLITH,
                        new VariantId("diagnostic_missing_content"), BlockPos.ZERO,
                        Direction.UP, Direction.NORTH, world).failure());
        assertEquals(ShrinePlacementFailure.VARIANT_MISSING,
                plan(new ItemStack(item), ShrineMonolithDefinitions.SHRINE,
                        new VariantId("missing"), BlockPos.ZERO, Direction.UP, Direction.NORTH, world).failure());
        assertEquals(ShrinePlacementFailure.INVALID_CLICKED_FACE,
                plan(new ItemStack(item), ShrineMonolithDefinitions.SHRINE,
                        new VariantId("honesty"), BlockPos.ZERO, Direction.NORTH, Direction.NORTH, world).failure());
        assertEquals(ShrinePlacementFailure.INVALID_FACING,
                plan(new ItemStack(item), ShrineMonolithDefinitions.SHRINE,
                        new VariantId("honesty"), BlockPos.ZERO, Direction.UP, Direction.UP, world).failure());
        assertEquals(0, world.mutations);
        assertTrue(world.originalStateReads.isEmpty());
    }

    @Test
    void everyWorldAndPreflightFailureChangesNothingAndConsumesNothing() {
        assertWorldFailure(world -> world.inBounds = false, ShrinePlacementFailure.WORLD_BOUND_FAILURE);
        assertWorldFailure(world -> world.loaded = false, ShrinePlacementFailure.REQUIRED_CHUNK_UNLOADED);
        assertWorldFailure(world -> world.unrelated = true, ShrinePlacementFailure.UNRELATED_STRUCTURE_CELL);
        assertWorldFailure(world -> world.replaceable = false, ShrinePlacementFailure.TARGET_OCCUPIED);
        assertWorldFailure(world -> world.allowed = false, ShrinePlacementFailure.PROTECTED_PLACEMENT);
        assertWorldFailure(world -> world.canCreate = false,
                ShrinePlacementFailure.BLOCK_ENTITY_CREATION_FAILURE);
        assertWorldFailure(world -> world.canEncode = false,
                ShrinePlacementFailure.PART_STATE_ENCODING_FAILURE);
    }

    @Test
    void onlyTheFourOccupiedCellsArePlannedAndAdjacentTargetsRemainUnreserved() {
        ShrinePlacementPlan plan = plan(new ItemStack(item), ShrineMonolithDefinitions.SHRINE,
                new VariantId("honesty"), new BlockPos(15, 69, 15), Direction.UP, Direction.NORTH,
                new FakeWorld()).plan().orElseThrow();
        Set<BlockPos> occupied = plan.cells().stream()
                .map(cell -> cell.worldPosition()).collect(java.util.stream.Collectors.toSet());
        assertEquals(4, occupied.size());
        for (BlockPos cell : Set.copyOf(occupied)) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = cell.relative(direction);
                if (!occupied.contains(neighbor)) {
                    assertFalse(plan.cells().stream()
                            .anyMatch(planned -> planned.worldPosition().equals(neighbor)));
                }
            }
        }
        assertEquals(Set.of(
                        LocalOffset.ANCHOR,
                        new LocalOffset(1, 0, 0),
                        new LocalOffset(0, 0, 1),
                        new LocalOffset(1, 0, 1)),
                new HashSet<>(plan.placedStructure().footprint()));
    }

    private static void assertWorldFailure(
            java.util.function.Consumer<FakeWorld> configure, ShrinePlacementFailure expected) {
        FakeWorld world = new FakeWorld();
        configure.accept(world);
        ItemStack stack = new ItemStack(item);
        assertEquals(expected, plan(stack, ShrineMonolithDefinitions.SHRINE,
                new VariantId("honesty"), BlockPos.ZERO, Direction.UP, Direction.NORTH, world).failure());
        assertEquals(1, stack.getCount());
        assertEquals(0, world.mutations);
    }

    private static ShrinePlacementPlanningResult plan(
            ItemStack stack,
            FamilyId familyId,
            VariantId variantId,
            BlockPos clicked,
            Direction face,
            Direction facing,
            FakeWorld world) {
        return ShrinePlacementPlanner.plan(
                item, stack, familyId, variantId, clicked, face, facing, anchor, part, world);
    }

    static final class FakeWorld implements ShrinePlacementWorld {
        boolean replaceable = true;
        boolean inBounds = true;
        boolean loaded = true;
        boolean unrelated;
        boolean allowed = true;
        boolean canCreate = true;
        boolean canEncode = true;
        int mutations;
        final Set<BlockPos> originalStateReads = new HashSet<>();

        @Override
        public BlockState blockState(BlockPos pos) {
            originalStateReads.add(pos.immutable());
            return Blocks.SHORT_GRASS.defaultBlockState();
        }

        @Override public boolean targetReplaceable(BlockPos pos) { return replaceable; }
        @Override public boolean inWorldBounds(BlockPos pos) { return inBounds; }
        @Override public boolean chunkLoaded(BlockPos pos) { return loaded; }
        @Override public boolean unrelatedLargeStructureCell(BlockPos pos) { return unrelated; }
        @Override public boolean placementAllowed(BlockPos pos, Direction facing, ItemStack stack) { return allowed; }
        @Override public boolean canCreateAnchorBlockEntity(BlockState state) { return canCreate; }
        @Override public boolean canEncodePart(BlockState state) { return canEncode; }
    }
}
