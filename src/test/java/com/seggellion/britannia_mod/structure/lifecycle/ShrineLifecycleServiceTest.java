package com.seggellion.britannia_mod.structure.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.item.ShrineItemState;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.placement.ShrinePlacementPlanner;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShrineLifecycleServiceTest {
    private static final BlockPos ANCHOR = new BlockPos(15, 70, 15);

    @BeforeAll
    static void setup() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
    }

    @Test
    void survivalAnchorAndEveryValidPartRemoveFourCellsAndDropExactlyOneConfiguredItem() {
        for (int sourceIndex = 0; sourceIndex < 4; sourceIndex++) {
            ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
            var anchor = world.placeShrine(ANCHOR, Direction.NORTH, "justice");
            LocalOffset offset = anchor.state().footprint().get(sourceIndex);
            BlockPos source = ShrinePlacementPlanner.worldPosition(ANCHOR, Direction.NORTH, offset);
            var result = ShrineLifecycleService.removeFrom(
                    world, source, world.blockState(source), ShrineRemovalCause.SURVIVAL_PLAYER);
            assertTrue(result.claimed());
            assertEquals(4, result.removedCells());
            assertEquals(1, result.drops());
            assertEquals(0, world.shrineCellCount());
            assertEquals(1, world.drops.size());
            ShrineItemState recovered = MilestoneTwoRegisteredTestContent.shrine()
                    .stateAccess().read(world.drops.getFirst()).orElseThrow();
            assertEquals(1, recovered.schemaVersion());
            assertEquals("shrine", recovered.familyId().value());
            assertEquals("justice", recovered.variantId().value());
        }
    }

    @Test
    void creativeAnchorAndPartsRemoveCompleteStructureWithoutAnyDrop() {
        for (LocalOffset offset : new ArrayList<>(new ShrineLifecycleTestWorld()
                .placeShrine(ANCHOR, Direction.EAST, "honesty").state().footprint())) {
            ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
            world.placeShrine(ANCHOR, Direction.EAST, "honesty");
            BlockPos source = ShrinePlacementPlanner.worldPosition(ANCHOR, Direction.EAST, offset);
            var result = ShrineLifecycleService.removeFrom(
                    world, source, world.blockState(source), ShrineRemovalCause.CREATIVE_PLAYER);
            assertEquals(0, result.drops());
            assertTrue(world.drops.isEmpty());
            assertEquals(0, world.shrineCellCount());
        }
    }

    @Test
    void anchorAndEveryPartPickReturnEqualConfiguredStateWithoutMutationOrForceLoad() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        var anchor = world.placeShrine(ANCHOR, Direction.SOUTH, "missing_current_definition");
        ItemStack expected = ShrineLifecycleService.pick(world, ANCHOR, world.blockState(ANCHOR));
        for (LocalOffset offset : anchor.state().footprint()) {
            BlockPos source = ShrinePlacementPlanner.worldPosition(ANCHOR, Direction.SOUTH, offset);
            ItemStack picked = ShrineLifecycleService.pick(world, source, world.blockState(source));
            assertEquals(MilestoneTwoRegisteredTestContent.shrine().stateAccess().read(expected),
                    MilestoneTwoRegisteredTestContent.shrine().stateAccess().read(picked));
        }
        assertEquals(4, world.shrineCellCount());
        assertTrue(world.writes.isEmpty());
        assertEquals("missing_current_definition", MilestoneTwoRegisteredTestContent.shrine()
                .stateAccess().read(expected).orElseThrow().variantId().value());
    }

    @Test
    void invalidPartCannotFabricateHonestyOrAConfiguredItem() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        var bad = MilestoneTwoRegisteredTestContent.part().stateFor(
                Direction.NORTH, new LocalOffset(1, 0, 0));
        BlockPos position = new BlockPos(2, 70, 2);
        world.blocks.put(position, bad);
        assertTrue(ShrineLifecycleService.pick(world, position, bad).isEmpty());
        assertTrue(world.drops.isEmpty());
    }

    @Test
    void invalidSourceBreakRemovesOnlyThatSourceAndNeverDropsAnItem() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        var bad = MilestoneTwoRegisteredTestContent.part().stateFor(
                Direction.NORTH, new LocalOffset(1, 0, 0));
        BlockPos position = new BlockPos(2, 70, 2);
        world.blocks.put(position, bad);
        var result = ShrineLifecycleService.removeFrom(
                world, position, bad, ShrineRemovalCause.SURVIVAL_PLAYER);
        assertTrue(result.claimed());
        assertEquals(1, result.removedCells());
        assertEquals(ShrineRemovalCause.INVALID_ANCHOR, result.cause());
        assertTrue(world.blockState(position).isAir());
        assertTrue(world.drops.isEmpty());
    }

    @Test
    void explosionCleansCompleteFootprintOnceAndDropsNothing() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        var anchor = world.placeShrine(ANCHOR, Direction.WEST, "valor");
        BlockPos source = ShrinePlacementPlanner.worldPosition(
                ANCHOR, Direction.WEST, anchor.state().footprint().getLast());
        var first = ShrineLifecycleService.removeFrom(
                world, source, world.blockState(source), ShrineRemovalCause.EXPLOSION);
        var second = ShrineLifecycleService.removeFrom(
                world, source, world.blockState(source), ShrineRemovalCause.EXPLOSION);
        assertEquals(4, first.removedCells());
        assertEquals(0, first.drops());
        assertFalse(second.claimed());
        assertTrue(world.drops.isEmpty());
        assertEquals(0, world.shrineCellCount());
    }

    @Test
    void everyExternalReplacementIsPreservedWhileRemainingKnownCellsAreCleanedWithoutDrop() {
        for (int replacementIndex = 0; replacementIndex < 4; replacementIndex++) {
            ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
            var anchor = world.placeShrine(ANCHOR, Direction.NORTH, "honesty");
            LocalOffset offset = anchor.state().footprint().get(replacementIndex);
            BlockPos replaced = ShrinePlacementPlanner.worldPosition(ANCHOR, Direction.NORTH, offset);
            var oldState = world.blockState(replaced);
            world.replace(replaced, Blocks.STONE.defaultBlockState());
            var result = offset.equals(LocalOffset.ANCHOR)
                    ? ShrineLifecycleService.removeAnchor(
                            world, anchor, ShrineRemovalCause.EXTERNAL_REPLACEMENT, replaced)
                    : ShrineLifecycleService.removeExternalPart(world, replaced, oldState);
            assertTrue(result.claimed());
            assertEquals(Blocks.STONE, world.blockState(replaced).getBlock());
            assertEquals(0, world.shrineCellCount());
            assertTrue(world.drops.isEmpty());
        }
    }

    @Test
    void alreadyMissingAndUnrelatedCellsAreNeverOverwrittenDuringCleanup() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        var anchor = world.placeShrine(ANCHOR, Direction.NORTH, "honesty");
        BlockPos missing = ShrinePlacementPlanner.worldPosition(
                ANCHOR, Direction.NORTH, anchor.state().footprint().get(1));
        BlockPos unrelated = ShrinePlacementPlanner.worldPosition(
                ANCHOR, Direction.NORTH, anchor.state().footprint().get(2));
        world.replace(missing, Blocks.AIR.defaultBlockState());
        world.replace(unrelated, Blocks.DIAMOND_BLOCK.defaultBlockState());
        var result = ShrineLifecycleService.removeFrom(
                world, ANCHOR, world.blockState(ANCHOR), ShrineRemovalCause.SURVIVAL_PLAYER);
        assertEquals(2, result.removedCells());
        assertEquals(Blocks.DIAMOND_BLOCK, world.blockState(unrelated).getBlock());
        assertTrue(world.blockState(missing).isAir());
        assertEquals(1, result.drops());
    }

    @Test
    void reentrancySuppressesRecursiveRemovalAndDuplicateDropsAndGuardReleasesAfterSuccess() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        world.placeShrine(ANCHOR, Direction.NORTH, "honesty");
        world.reenterOnRemove = true;
        var result = ShrineLifecycleService.removeFrom(
                world, ANCHOR, world.blockState(ANCHOR), ShrineRemovalCause.SURVIVAL_PLAYER);
        assertEquals(1, result.drops());
        assertFalse(world.recursiveResult.claimed());
        assertEquals(1, world.drops.size());
        assertFalse(ShrineLifecycleService.isGuarded(world.identity, ANCHOR));
    }

    @Test
    void guardReleasesAfterInjectedFailure() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        var anchor = world.placeShrine(ANCHOR, Direction.NORTH, "honesty");
        world.throwOnRemove = ShrinePlacementPlanner.worldPosition(
                ANCHOR, Direction.NORTH, anchor.state().footprint().getLast());
        var result = ShrineLifecycleService.removeFrom(
                world, ANCHOR, world.blockState(ANCHOR), ShrineRemovalCause.SURVIVAL_PLAYER);
        assertFalse(result.claimed());
        assertEquals(0, result.drops());
        assertFalse(ShrineLifecycleService.isGuarded(world.identity, ANCHOR));
    }

    @Test
    void membershipRejectsWrongFacingOffsetAndUnrelatedAnchor() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        world.placeShrine(ANCHOR, Direction.NORTH, "honesty");
        LocalOffset offset = new LocalOffset(1, 0, 0);
        BlockPos correct = ShrinePlacementPlanner.worldPosition(ANCHOR, Direction.NORTH, offset);
        var wrongFacing = MilestoneTwoRegisteredTestContent.part().stateFor(Direction.SOUTH, offset);
        assertNotEquals(ShrineLifecycleService.ResolutionStatus.VALID,
                ShrineLifecycleService.resolve(world, correct, wrongFacing).status());
        var wrongOffset = MilestoneTwoRegisteredTestContent.part().stateFor(
                Direction.NORTH, new LocalOffset(0, 0, 1));
        assertNotEquals(ShrineLifecycleService.ResolutionStatus.VALID,
                ShrineLifecycleService.resolve(world, correct, wrongOffset).status());
    }

    @Test
    void anchorAndThreePartsResolveForEveryHorizontalFacingWithoutForceLoading() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
            var anchor = world.placeShrine(ANCHOR, facing, "honesty");
            for (LocalOffset offset : anchor.state().footprint()) {
                BlockPos source = ShrinePlacementPlanner.worldPosition(ANCHOR, facing, offset);
                var resolution = ShrineLifecycleService.resolve(world, source, world.blockState(source));
                assertEquals(ShrineLifecycleService.ResolutionStatus.VALID, resolution.status());
                assertEquals(ANCHOR, resolution.anchorPosition());
                assertEquals(offset, resolution.sourceOffset());
            }
            assertTrue(world.writes.isEmpty());
        }
    }

    @Test
    void unloadedCandidateAnchorChunkIsReportedWithoutLoadOrMutation() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        var anchor = world.placeShrine(ANCHOR, Direction.NORTH, "honesty");
        LocalOffset offset = anchor.state().footprint().getLast();
        BlockPos part = ShrinePlacementPlanner.worldPosition(ANCHOR, Direction.NORTH, offset);
        world.unload(ANCHOR);
        assertEquals(ShrineLifecycleService.ResolutionStatus.ANCHOR_CHUNK_UNLOADED,
                ShrineLifecycleService.resolve(world, part, world.blockState(part)).status());
        assertTrue(world.writes.isEmpty());
        assertFalse(world.chunkLoaded(ANCHOR));
    }
}
