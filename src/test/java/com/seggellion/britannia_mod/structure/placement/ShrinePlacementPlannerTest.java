package com.seggellion.britannia_mod.structure.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureCatalogue;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Family;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.item.ShrineItem;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlock;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.multiblock.StructureCellRole;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
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
            assertEquals(plan.cells().stream().map(cell -> cell.worldPosition()).toList(),
                    plan.authorizedPositions());
            assertEquals(4, world.placementChecks.size());
            assertTrue(plan.anchorInitializationValidated());
            assertEquals(0, world.mutations);
        }
    }

    @Test
    void planRetainsImmutableOrderedTargetsChunksAuthorizationAndInitializationEvidence() {
        ShrinePlacementPlan plan = plan(new ItemStack(item), ShrineMonolithDefinitions.SHRINE,
                new VariantId("honesty"), new BlockPos(15, 69, 15), Direction.UP, Direction.WEST,
                new FakeWorld()).plan().orElseThrow();

        assertEquals(4, plan.requiredChunks().size());
        assertEquals(plan.cells().stream().map(cell -> new ChunkPos(cell.worldPosition())).toList(),
                plan.requiredChunks());
        assertEquals(plan.cells().stream().map(cell -> cell.offset()).toList(),
                plan.placedStructure().footprint());
        assertEquals(4, new HashSet<>(plan.cells().stream()
                .map(cell -> cell.worldPosition()).toList()).size());
        assertThrows(UnsupportedOperationException.class, () -> plan.cells().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.requiredChunks().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.authorizedPositions().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.placedStructure().footprint().clear());
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
    void missingFamilyAndDisabledVariantAreRejectedByInjectedValidatedCatalogues() {
        StructureCatalogue empty = StructureCatalogue.build(List.of()).catalogue().orElseThrow();
        FakeWorld missingWorld = new FakeWorld();
        assertEquals(ShrinePlacementFailure.FAMILY_MISSING,
                ShrinePlacementPlanner.plan(
                        item, new ItemStack(item), ShrineMonolithDefinitions.SHRINE,
                        new VariantId("honesty"), BlockPos.ZERO, Direction.UP, Direction.NORTH,
                        anchor, part, missingWorld, empty).failure());
        assertTrue(missingWorld.originalStateReads.isEmpty());

        Family builtIn = ShrineMonolithDefinitions.catalogue()
                .family(ShrineMonolithDefinitions.SHRINE).orElseThrow();
        Variant enabled = builtIn.variants().getFirst();
        Variant source = builtIn.variants().get(1);
        Variant disabled = new Variant(
                source.id(), source.familyId(), source.displayName(), source.dimensions(),
                source.footprint(), source.placementMode(), source.collisionProfile(),
                source.renderOrigin(), source.renderOffsetVoxels(), source.model(), source.texture(),
                source.cyclePosition(), false, source.playerFacing(), source.contentStatus());
        Family family = new Family(
                builtIn.id(), builtIn.displayName(), builtIn.dimensions(), builtIn.footprint(),
                builtIn.anchorOffset(), builtIn.placementMode(), builtIn.collisionProfile(),
                builtIn.renderOrigin(), builtIn.renderOffsetVoxels(), builtIn.geometryMode(),
                builtIn.sharedGeometry(), builtIn.defaultVariant(), builtIn.contentStatus(),
                List.of(enabled, disabled));
        StructureCatalogue catalogue = StructureCatalogue.build(List.of(family))
                .catalogue().orElseThrow();
        FakeWorld disabledWorld = new FakeWorld();
        assertEquals(ShrinePlacementFailure.VARIANT_DISABLED,
                ShrinePlacementPlanner.plan(
                        item, new ItemStack(item), ShrineMonolithDefinitions.SHRINE,
                        disabled.id(), BlockPos.ZERO, Direction.UP, Direction.NORTH,
                        anchor, part, disabledWorld, catalogue).failure());
        assertTrue(disabledWorld.originalStateReads.isEmpty());
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
        assertWorldFailure(world -> world.canInitialize = false,
                ShrinePlacementFailure.ANCHOR_INITIALIZATION_FAILURE);
        assertWorldFailure(world -> world.canEncode = false,
                ShrinePlacementFailure.PART_STATE_ENCODING_FAILURE);
    }

    @Test
    void protectionChecksEveryTargetForAllowedAndDeniedPlacement() {
        FakeWorld allowed = new FakeWorld();
        ShrinePlacementPlan plan = plan(new ItemStack(item), ShrineMonolithDefinitions.SHRINE,
                new VariantId("honesty"), BlockPos.ZERO, Direction.UP, Direction.NORTH, allowed)
                .plan().orElseThrow();
        assertEquals(plan.authorizedPositions(), allowed.placementChecks);

        FakeWorld denied = new FakeWorld();
        denied.deniedAuthorizationCall = 2;
        ItemStack stack = new ItemStack(item);
        assertEquals(ShrinePlacementFailure.PROTECTED_PLACEMENT,
                plan(stack, ShrineMonolithDefinitions.SHRINE, new VariantId("honesty"),
                        BlockPos.ZERO, Direction.UP, Direction.NORTH, denied).failure());
        assertEquals(4, denied.placementChecks.size());
        assertEquals(1, stack.getCount());
        assertTrue(denied.originalStateReads.isEmpty());
        assertEquals(0, denied.mutations);
    }

    @Test
    void crossChunkPlanRejectsAnyUnloadedRequiredChunkWithoutMutationOrConsumption() {
        BlockPos clicked = new BlockPos(15, 69, 15);
        FakeWorld allLoaded = new FakeWorld();
        ShrinePlacementPlan plan = plan(new ItemStack(item), ShrineMonolithDefinitions.SHRINE,
                new VariantId("honesty"), clicked, Direction.UP, Direction.WEST, allLoaded)
                .plan().orElseThrow();
        assertEquals(4, plan.requiredChunks().size());

        FakeWorld oneUnloaded = new FakeWorld();
        oneUnloaded.unloadedChunks.add(plan.requiredChunks().getLast());
        ItemStack stack = new ItemStack(item);
        assertEquals(ShrinePlacementFailure.REQUIRED_CHUNK_UNLOADED,
                plan(stack, ShrineMonolithDefinitions.SHRINE, new VariantId("honesty"),
                        clicked, Direction.UP, Direction.WEST, oneUnloaded).failure());
        assertEquals(1, stack.getCount());
        assertTrue(oneUnloaded.originalStateReads.isEmpty());
        assertEquals(0, oneUnloaded.mutations);
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
        boolean canInitialize = true;
        boolean canEncode = true;
        int deniedAuthorizationCall = -1;
        int mutations;
        final Set<BlockPos> originalStateReads = new HashSet<>();
        final Set<ChunkPos> unloadedChunks = new HashSet<>();
        final List<BlockPos> placementChecks = new ArrayList<>();

        @Override
        public BlockState blockState(BlockPos pos) {
            originalStateReads.add(pos.immutable());
            return Blocks.SHORT_GRASS.defaultBlockState();
        }

        @Override public boolean targetReplaceable(BlockPos pos) { return replaceable; }
        @Override public boolean inWorldBounds(BlockPos pos) { return inBounds; }
        @Override public boolean chunkLoaded(BlockPos pos) {
            return loaded && !unloadedChunks.contains(new ChunkPos(pos));
        }
        @Override public boolean unrelatedLargeStructureCell(BlockPos pos) { return unrelated; }
        @Override public boolean placementAllowed(BlockPos pos, Direction facing, ItemStack stack) {
            placementChecks.add(pos.immutable());
            return allowed && placementChecks.size() != deniedAuthorizationCall;
        }
        @Override public boolean canCreateAnchorBlockEntity(BlockState state) { return canCreate; }
        @Override public boolean canInitializeAnchor(
                BlockState anchorState,
                com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState state) {
            return canInitialize;
        }
        @Override public boolean canEncodePart(BlockState state) { return canEncode; }
    }
}
