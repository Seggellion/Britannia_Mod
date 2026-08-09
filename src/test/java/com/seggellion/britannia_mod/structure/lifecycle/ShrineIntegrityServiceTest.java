package com.seggellion.britannia_mod.structure.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.placement.ShrinePlacementPlanner;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShrineIntegrityServiceTest {
    private static final BlockPos ANCHOR = new BlockPos(15, 70, 15);

    @BeforeAll
    static void setup() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
    }

    @Test
    void unavailableAnchorChunkDefersPartWithoutRemovingOrForceLoadingIt() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        var anchor = world.placeShrine(ANCHOR, Direction.NORTH, "honesty");
        LocalOffset crossChunkOffset = new LocalOffset(0, 0, 1);
        BlockPos part = ShrinePlacementPlanner.worldPosition(ANCHOR, Direction.NORTH, crossChunkOffset);
        world.unload(ANCHOR);
        assertEquals(ShrineIntegrityService.Result.DEFERRED_UNLOADED_CHUNK,
                ShrineIntegrityService.checkPart(world, part, world.blockState(part)));
        assertTrue(world.isPart(world.blockState(part)));
        assertTrue(world.writes.isEmpty());
        assertEquals(4, anchor.state().footprint().size());
    }

    @Test
    void laterAnchorLoadDiscoversValidMembershipAndCancelsOrphanRemoval() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        world.placeShrine(ANCHOR, Direction.NORTH, "honesty");
        BlockPos part = ShrinePlacementPlanner.worldPosition(
                ANCHOR, Direction.NORTH, new LocalOffset(0, 0, 1));
        world.unload(ANCHOR);
        assertEquals(ShrineIntegrityService.Result.DEFERRED_UNLOADED_CHUNK,
                ShrineIntegrityService.checkPart(world, part, world.blockState(part)));
        world.load(ANCHOR);
        assertEquals(ShrineIntegrityService.Result.VALID,
                ShrineIntegrityService.checkPart(world, part, world.blockState(part)));
        assertEquals(4, world.shrineCellCount());
    }

    @Test
    void definitiveOrphanIsRemovedWithoutDropWhenCandidateAnchorChunkIsLoaded() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        LocalOffset offset = new LocalOffset(0, 0, 1);
        BlockPos part = new BlockPos(15, 70, 16);
        world.blocks.put(part, MilestoneTwoRegisteredTestContent.part().stateFor(Direction.NORTH, offset));
        assertEquals(ShrineIntegrityService.Result.REMOVED_ORPHAN,
                ShrineIntegrityService.checkPart(world, part, world.blockState(part)));
        assertTrue(world.blockState(part).isAir());
        assertTrue(world.drops.isEmpty());
    }

    @Test
    void everyMissingReplaceablePartRepairsFromPersistedOffsetWithoutStateMutationOrPartEntity() {
        for (int index = 1; index < 4; index++) {
            ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
            var anchor = world.placeShrine(ANCHOR, Direction.WEST, "sacrifice");
            LocalOffset missingOffset = anchor.state().footprint().get(index);
            BlockPos missing = ShrinePlacementPlanner.worldPosition(ANCHOR, Direction.WEST, missingOffset);
            world.replace(missing, Blocks.AIR.defaultBlockState());
            assertEquals(ShrineIntegrityService.Result.REPAIRED_MISSING_PARTS,
                    ShrineIntegrityService.checkAnchor(world, ANCHOR));
            assertTrue(ShrineLifecycleService.isExpectedCell(
                    world, world.blockState(missing), Direction.WEST, missingOffset));
            assertEquals("sacrifice", world.anchor(ANCHOR).orElseThrow().state().variantId().value());
            assertEquals(1, world.synchronizations);
            assertTrue(world.drops.isEmpty());
            assertFalse(net.minecraft.world.level.block.EntityBlock.class
                    .isAssignableFrom(MilestoneTwoRegisteredTestContent.part().getClass()));
        }
    }

    @Test
    void unloadedMissingPartChunkDefersRepairAndDoesNotWrite() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        var anchor = world.placeShrine(ANCHOR, Direction.NORTH, "honesty");
        LocalOffset missingOffset = new LocalOffset(0, 0, 1);
        BlockPos missing = ShrinePlacementPlanner.worldPosition(ANCHOR, Direction.NORTH, missingOffset);
        world.replace(missing, Blocks.AIR.defaultBlockState());
        world.unload(missing);
        assertEquals(ShrineIntegrityService.Result.DEFERRED_UNLOADED_CHUNK,
                ShrineIntegrityService.checkAnchor(world, ANCHOR));
        assertTrue(world.blockState(missing).isAir());
        assertTrue(world.writes.isEmpty());
        assertEquals(4, anchor.state().footprint().size());
    }

    @Test
    void obstructionAtEveryPartIsPreservedAndRemovesRemainingShrineWithoutDrop() {
        for (int index = 1; index < 4; index++) {
            ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
            var anchor = world.placeShrine(ANCHOR, Direction.SOUTH, "honor");
            BlockPos obstruction = ShrinePlacementPlanner.worldPosition(
                    ANCHOR, Direction.SOUTH, anchor.state().footprint().get(index));
            world.replace(obstruction, Blocks.OBSIDIAN.defaultBlockState());
            assertEquals(ShrineIntegrityService.Result.REMOVED_OBSTRUCTED_STRUCTURE,
                    ShrineIntegrityService.checkAnchor(world, ANCHOR));
            assertEquals(Blocks.OBSIDIAN, world.blockState(obstruction).getBlock());
            assertEquals(0, world.shrineCellCount());
            assertTrue(world.drops.isEmpty());
        }
    }

    @Test
    void failedRepairCleansPersistedCellsAfterPlacementGuardReleases() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        var anchor = world.placeShrine(ANCHOR, Direction.SOUTH, "honor");
        BlockPos missing = ShrinePlacementPlanner.worldPosition(
                ANCHOR, Direction.SOUTH, anchor.state().footprint().get(1));
        world.replace(missing, Blocks.AIR.defaultBlockState());
        world.failPartPlacement = true;
        assertEquals(ShrineIntegrityService.Result.REMOVED_OBSTRUCTED_STRUCTURE,
                ShrineIntegrityService.checkAnchor(world, ANCHOR));
        assertEquals(0, world.shrineCellCount());
        assertTrue(world.drops.isEmpty());
        assertFalse(ShrineLifecycleService.isGuarded(world.identity, ANCHOR));
    }

    @Test
    void integrityDefersWhilePlacementOrRemovalOwnsTheStructure() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        var anchor = world.placeShrine(ANCHOR, Direction.NORTH, "honesty");
        BlockPos part = ShrinePlacementPlanner.worldPosition(
                ANCHOR, Direction.NORTH, anchor.state().footprint().get(1));
        ShrineLifecycleService.duringPlacement(world.identity, ANCHOR, () -> {
            assertEquals(ShrineIntegrityService.Result.DEFERRED_ACTIVE_MUTATION,
                    ShrineIntegrityService.checkAnchor(world, ANCHOR));
            assertEquals(ShrineIntegrityService.Result.DEFERRED_ACTIVE_MUTATION,
                    ShrineIntegrityService.checkPart(world, part, world.blockState(part)));
            return true;
        });
        assertTrue(world.writes.isEmpty());
        assertFalse(ShrineLifecycleService.isGuarded(world.identity, ANCHOR));
    }

    @Test
    void changedCurrentDefinitionCannotAddMoveOrResizePersistedRepairTargets() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        var anchor = world.placeShrine(ANCHOR, Direction.NORTH, "honesty");
        LocalOffset storedMissing = anchor.state().footprint().getLast();
        BlockPos storedPosition = ShrinePlacementPlanner.worldPosition(ANCHOR, Direction.NORTH, storedMissing);
        BlockPos hypotheticalNewDefinitionCell = ShrinePlacementPlanner.worldPosition(
                ANCHOR, Direction.NORTH, new LocalOffset(2, 0, 0));
        world.replace(storedPosition, Blocks.AIR.defaultBlockState());
        world.replace(hypotheticalNewDefinitionCell, Blocks.AIR.defaultBlockState());
        assertEquals(ShrineIntegrityService.Result.REPAIRED_MISSING_PARTS,
                ShrineIntegrityService.checkAnchor(world, ANCHOR));
        assertTrue(world.isPart(world.blockState(storedPosition)));
        assertTrue(world.blockState(hypotheticalNewDefinitionCell).isAir());
        assertEquals(1, world.writes.size());
    }

    @Test
    void repeatedObstructionDiagnosticIsBoundedForSameStructure() {
        int before = ShrineIntegrityService.diagnosticCount();
        for (int attempt = 0; attempt < 3; attempt++) {
            ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
            var anchor = world.placeShrine(ANCHOR, Direction.EAST, "humility");
            BlockPos obstruction = ShrinePlacementPlanner.worldPosition(
                    ANCHOR, Direction.EAST, anchor.state().footprint().get(1));
            world.replace(obstruction, Blocks.BEDROCK.defaultBlockState());
            ShrineIntegrityService.checkAnchor(world, ANCHOR);
        }
        assertTrue(ShrineIntegrityService.diagnosticCount() <= before + 1);
    }
}
