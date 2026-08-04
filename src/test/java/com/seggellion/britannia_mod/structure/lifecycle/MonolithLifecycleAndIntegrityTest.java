package com.seggellion.britannia_mod.structure.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.item.ShrineItemState;
import com.seggellion.britannia_mod.structure.placement.ShrinePlacementPlanner;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MonolithLifecycleAndIntegrityTest {
    private static final BlockPos ANCHOR = new BlockPos(15, 70, 15);

    @BeforeAll
    static void setup() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
    }

    @Test
    void everyCellResolvesPicksAndControlsSurvivalAndCreativeLifecycle() {
        var footprint = ShrineMonolithDefinitions.catalogue()
                .family(ShrineMonolithDefinitions.MONOLITH).orElseThrow().footprint();
        for (LocalOffset offset : footprint) {
            ShrineLifecycleTestWorld pickWorld = new ShrineLifecycleTestWorld();
            var pickAnchor = pickWorld.placeMonolith(ANCHOR, Direction.WEST);
            BlockPos source = ShrinePlacementPlanner.worldPosition(ANCHOR, Direction.WEST, offset);
            var resolution = ShrineLifecycleService.resolve(
                    pickWorld, source, pickWorld.blockState(source));
            assertTrue(resolution.valid(), offset.toString());
            var picked = ShrineLifecycleService.pick(pickWorld, source, pickWorld.blockState(source));
            assertEquals(MilestoneTwoRegisteredTestContent.monolith(), picked.getItem());
            ShrineItemState pickedState = picked.get(MilestoneTwoRegisteredTestContent.monolithComponent());
            assertEquals(pickAnchor.state().familyId(), pickedState.familyId());
            assertEquals(pickAnchor.state().variantId(), pickedState.variantId());
            assertTrue(pickWorld.writes.isEmpty());

            ShrineLifecycleTestWorld survival = new ShrineLifecycleTestWorld();
            survival.placeMonolith(ANCHOR, Direction.WEST);
            BlockPos survivalSource = ShrinePlacementPlanner.worldPosition(
                    ANCHOR, Direction.WEST, offset);
            var removed = ShrineLifecycleService.removeFrom(
                    survival, survivalSource, survival.blockState(survivalSource),
                    ShrineRemovalCause.SURVIVAL_PLAYER);
            assertTrue(removed.claimed());
            assertEquals(18, removed.removedCells());
            assertEquals(1, removed.drops());
            assertEquals(0, survival.shrineCellCount());
            assertEquals(1, survival.drops.size());
            assertEquals(MilestoneTwoRegisteredTestContent.monolith(),
                    survival.drops.getFirst().getItem());

            ShrineLifecycleTestWorld creative = new ShrineLifecycleTestWorld();
            creative.placeMonolith(ANCHOR, Direction.WEST);
            BlockPos creativeSource = ShrinePlacementPlanner.worldPosition(
                    ANCHOR, Direction.WEST, offset);
            var creativeRemoved = ShrineLifecycleService.removeFrom(
                    creative, creativeSource, creative.blockState(creativeSource),
                    ShrineRemovalCause.CREATIVE_PLAYER);
            assertEquals(18, creativeRemoved.removedCells());
            assertEquals(0, creativeRemoved.drops());
            assertTrue(creative.drops.isEmpty());
        }
    }

    @Test
    void everyMissingPartRepairsFromPersistedEighteenCellFootprintAcrossAllLayers() {
        for (int index = 1; index < 18; index++) {
            ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
            var anchor = world.placeMonolith(ANCHOR, Direction.SOUTH);
            LocalOffset offset = anchor.state().footprint().get(index);
            BlockPos missing = ShrinePlacementPlanner.worldPosition(ANCHOR, Direction.SOUTH, offset);
            world.replace(missing, Blocks.AIR.defaultBlockState());
            assertEquals(ShrineIntegrityService.Result.REPAIRED_MISSING_PARTS,
                    ShrineIntegrityService.checkAnchor(world, ANCHOR), offset.toString());
            assertTrue(ShrineLifecycleService.isExpectedCell(
                    world, world.blockState(missing), Direction.SOUTH, offset));
            assertEquals(18, world.shrineCellCount());
            assertEquals(1, world.synchronizations);
        }
    }

    @Test
    void unloadedChunksDeferAndObstructionsArePreservedWithoutDrops() {
        ShrineLifecycleTestWorld deferred = new ShrineLifecycleTestWorld();
        deferred.placeMonolith(ANCHOR, Direction.NORTH);
        LocalOffset top = new LocalOffset(2, 2, 1);
        BlockPos topPart = ShrinePlacementPlanner.worldPosition(ANCHOR, Direction.NORTH, top);
        deferred.unload(topPart);
        assertEquals(ShrineIntegrityService.Result.DEFERRED_UNLOADED_CHUNK,
                ShrineIntegrityService.checkAnchor(deferred, ANCHOR));
        assertTrue(deferred.writes.isEmpty());

        for (int y = 0; y < 3; y++) {
            ShrineLifecycleTestWorld obstructed = new ShrineLifecycleTestWorld();
            obstructed.placeMonolith(ANCHOR, Direction.EAST);
            LocalOffset offset = new LocalOffset(2, y, 1);
            BlockPos obstruction = ShrinePlacementPlanner.worldPosition(
                    ANCHOR, Direction.EAST, offset);
            obstructed.replace(obstruction, Blocks.OBSIDIAN.defaultBlockState());
            assertEquals(ShrineIntegrityService.Result.REMOVED_OBSTRUCTED_STRUCTURE,
                    ShrineIntegrityService.checkAnchor(obstructed, ANCHOR));
            assertEquals(Blocks.OBSIDIAN, obstructed.blockState(obstruction).getBlock());
            assertEquals(0, obstructed.shrineCellCount());
            assertTrue(obstructed.drops.isEmpty());
        }
    }

    @Test
    void definitiveTopLayerOrphanIsRemovedAndPartHasNoBlockEntityOrFluidState() {
        ShrineLifecycleTestWorld world = new ShrineLifecycleTestWorld();
        LocalOffset top = new LocalOffset(2, 2, 1);
        BlockPos partPos = ShrinePlacementPlanner.worldPosition(ANCHOR, Direction.NORTH, top);
        world.blocks.put(partPos, MilestoneTwoRegisteredTestContent.part().stateFor(Direction.NORTH, top));
        assertEquals(ShrineIntegrityService.Result.REMOVED_ORPHAN,
                ShrineIntegrityService.checkPart(world, partPos, world.blockState(partPos)));
        assertTrue(world.blockState(partPos).isAir());
        assertTrue(world.drops.isEmpty());
        assertFalse(net.minecraft.world.level.block.EntityBlock.class
                .isAssignableFrom(MilestoneTwoRegisteredTestContent.part().getClass()));
        assertTrue(MilestoneTwoRegisteredTestContent.part().defaultBlockState().getFluidState().isEmpty());
        assertTrue(MilestoneTwoRegisteredTestContent.anchor().defaultBlockState().getFluidState().isEmpty());
    }
}
