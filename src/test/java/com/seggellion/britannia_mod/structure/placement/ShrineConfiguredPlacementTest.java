package com.seggellion.britannia_mod.structure.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.item.ShrineItem;
import com.seggellion.britannia_mod.structure.item.ShrineItemState;
import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShrineConfiguredPlacementTest {
    private static ShrineItem item;

    @BeforeAll
    static void setup() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
        item = MilestoneTwoRegisteredTestContent.shrine();
    }

    @Test
    void productionPlannerReadsConfiguredVariantInsteadOfHardCodedHonesty() {
        ItemStack stack = item.stateAccess().configuredStack(
                new ShrineItemState(1, new FamilyId("shrine"), new VariantId("justice")));
        CountingWorld world = new CountingWorld();
        var result = ShrinePlacementPlanner.plan(
                item, stack, BlockPos.ZERO, Direction.UP, Direction.NORTH,
                MilestoneTwoRegisteredTestContent.anchor(), MilestoneTwoRegisteredTestContent.part(), world);
        assertTrue(result.successful());
        assertEquals("justice", result.plan().orElseThrow().placedStructure().variantId().value());
        assertEquals(4, result.plan().orElseThrow().placedStructure().footprint().size());
        assertTrue(world.queries > 0);
    }

    @Test
    void rawDefaultStackStillPlansApprovedHonestyVariant() {
        var result = ShrinePlacementPlanner.plan(
                item, new ItemStack(item), BlockPos.ZERO, Direction.UP, Direction.EAST,
                MilestoneTwoRegisteredTestContent.anchor(), MilestoneTwoRegisteredTestContent.part(),
                new CountingWorld());
        assertTrue(result.successful());
        assertEquals("honesty", result.plan().orElseThrow().placedStructure().variantId().value());
    }

    @Test
    void missingVariantAndWrongFamilyFailBeforeAnyWorldMutationOrQuery() {
        CountingWorld missingWorld = new CountingWorld();
        ItemStack missing = item.stateAccess().configuredStack(
                new ShrineItemState(1, new FamilyId("shrine"), new VariantId("removed_variant")));
        assertEquals(ShrinePlacementFailure.VARIANT_MISSING,
                ShrinePlacementPlanner.plan(item, missing, BlockPos.ZERO, Direction.UP, Direction.NORTH,
                        MilestoneTwoRegisteredTestContent.anchor(), MilestoneTwoRegisteredTestContent.part(),
                        missingWorld).failure());
        assertEquals(0, missingWorld.queries);

        CountingWorld wrongWorld = new CountingWorld();
        ItemStack wrong = item.stateAccess().configuredStack(
                new ShrineItemState(1, new FamilyId("monolith"), new VariantId("honesty")));
        assertEquals(ShrinePlacementFailure.UNSUPPORTED_FAMILY,
                ShrinePlacementPlanner.plan(item, wrong, BlockPos.ZERO, Direction.UP, Direction.NORTH,
                        MilestoneTwoRegisteredTestContent.anchor(), MilestoneTwoRegisteredTestContent.part(),
                        wrongWorld).failure());
        assertEquals(0, wrongWorld.queries);
    }

    private static final class CountingWorld implements ShrinePlacementWorld {
        int queries;
        @Override public BlockState blockState(BlockPos pos) { queries++; return Blocks.AIR.defaultBlockState(); }
        @Override public boolean targetReplaceable(BlockPos pos) { queries++; return true; }
        @Override public boolean inWorldBounds(BlockPos pos) { queries++; return true; }
        @Override public boolean chunkLoaded(BlockPos pos) { queries++; return true; }
        @Override public boolean unrelatedLargeStructureCell(BlockPos pos) { queries++; return false; }
        @Override public boolean placementAllowed(BlockPos pos, Direction facing, ItemStack stack) {
            queries++; return true;
        }
        @Override public boolean canCreateAnchorBlockEntity(BlockState anchorState) { queries++; return true; }
        @Override public boolean canInitializeAnchor(BlockState anchorState, PlacedStructureState state) {
            queries++; return true;
        }
        @Override public boolean canEncodePart(BlockState partState) { queries++; return true; }
    }
}
