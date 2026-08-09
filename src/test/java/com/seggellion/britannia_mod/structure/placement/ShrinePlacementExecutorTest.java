package com.seggellion.britannia_mod.structure.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.item.ShrineItem;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlock;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import com.seggellion.britannia_mod.structure.multiblock.StructureCell;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShrinePlacementExecutorTest {
    private static ShrineItem item;
    private static ShrinePlacementPlan plan;

    @BeforeAll
    static void setup() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
        item = MilestoneTwoRegisteredTestContent.shrine();
        var anchor = MilestoneTwoRegisteredTestContent.anchor();
        var part = MilestoneTwoRegisteredTestContent.part();
        plan = ShrinePlacementPlanner.plan(
                item,
                new ItemStack(item),
                ShrineMonolithDefinitions.SHRINE,
                new VariantId("honesty"),
                new BlockPos(15, 69, 15),
                Direction.UP,
                Direction.WEST,
                anchor,
                part,
                new ShrinePlacementPlannerTest.FakeWorld()).plan().orElseThrow();
    }

    @Test
    void survivalSuccessChangesFourCellsThenConsumesExactlyOne() {
        ItemStack stack = new ItemStack(item);
        FakeMutation mutation = new FakeMutation();
        assertEquals(ShrinePlacementFailure.NONE,
                ShrinePlacementExecutor.execute(plan, mutation, stack, false));
        assertTrue(stack.isEmpty());
        assertEquals(List.of(0, 1, 2, 3), mutation.placedIndexes);
        assertEquals(4, mutation.changedCells);
        assertEquals(Optional.of(plan.placedStructure()), mutation.storedState);
        assertEquals(1, mutation.synchronizations);
        assertEquals(1, mutation.effects);
        assertEquals(0, mutation.drops);
        assertEquals(4, plan.requiredChunks().size());
    }

    @Test
    void creativeSuccessChangesFourCellsAndConsumesNothing() {
        ItemStack stack = new ItemStack(item);
        FakeMutation mutation = new FakeMutation();
        assertEquals(ShrinePlacementFailure.NONE,
                ShrinePlacementExecutor.execute(plan, mutation, stack, true));
        assertEquals(1, stack.getCount());
        assertEquals(4, mutation.changedCells);
    }

    @Test
    void failureAtEveryCellMutationRollsBackWithoutConsumptionEffectsOrDrops() {
        for (int index = 0; index < plan.cells().size(); index++) {
            ItemStack stack = new ItemStack(item);
            FakeMutation mutation = new FakeMutation();
            mutation.failPlacementIndex = index;
            assertEquals(ShrinePlacementFailure.BLOCK_SET_FAILURE,
                    ShrinePlacementExecutor.execute(plan, mutation, stack, false), "cell " + index);
            assertRolledBack(stack, mutation);
        }
    }

    @Test
    void missingEntityRejectedStateMismatchedStateAndThrownAssignmentRollBack() {
        for (int mode = 0; mode < 4; mode++) {
            ItemStack stack = new ItemStack(item);
            FakeMutation mutation = new FakeMutation();
            if (mode == 0) mutation.entityPresent = false;
            if (mode == 1) mutation.assign = false;
            if (mode == 2) mutation.reportMismatch = true;
            if (mode == 3) mutation.throwOnAssign = true;
            ShrinePlacementFailure result = ShrinePlacementExecutor.execute(plan, mutation, stack, false);
            assertTrue(result == ShrinePlacementFailure.BLOCK_ENTITY_CREATION_FAILURE
                    || result == ShrinePlacementFailure.STATE_TRANSFER_FAILURE);
            assertRolledBack(stack, mutation);
        }
    }

    @Test
    void failureAtEveryFinalVerificationAndSynchronizationRollsBack() {
        for (int index = 0; index < plan.cells().size(); index++) {
            ItemStack stack = new ItemStack(item);
            FakeMutation mutation = new FakeMutation();
            mutation.failVerificationIndex = index;
            assertEquals(ShrinePlacementFailure.FINAL_VERIFICATION_FAILURE,
                    ShrinePlacementExecutor.execute(plan, mutation, stack, false));
            assertRolledBack(stack, mutation);
        }
        ItemStack stack = new ItemStack(item);
        FakeMutation mutation = new FakeMutation();
        mutation.synchronize = false;
        assertEquals(ShrinePlacementFailure.SYNCHRONIZATION_FAILURE,
                ShrinePlacementExecutor.execute(plan, mutation, stack, false));
        assertRolledBack(stack, mutation);
    }

    @Test
    void reverseAnchorResolutionFailureRollsBackBeforeConsumptionAndSuccessEffects() {
        ItemStack stack = new ItemStack(item);
        FakeMutation mutation = new FakeMutation();
        mutation.failReverseResolutionIndex = 3;
        assertEquals(ShrinePlacementFailure.FINAL_VERIFICATION_FAILURE,
                ShrinePlacementExecutor.execute(plan, mutation, stack, false));
        assertRolledBack(stack, mutation);
        assertEquals(1, mutation.reverseResolutionChecks);
    }

    @Test
    void rollbackFailureIsExplicitAndStillDoesNotConsumeOrPlaySuccessEffects() {
        ItemStack stack = new ItemStack(item);
        FakeMutation mutation = new FakeMutation();
        mutation.failPlacementIndex = 2;
        mutation.rollback = false;
        assertEquals(ShrinePlacementFailure.UNEXPECTED_ROLLBACK_FAILURE,
                ShrinePlacementExecutor.execute(plan, mutation, stack, false));
        assertEquals(1, stack.getCount());
        assertEquals(0, mutation.effects);
        assertEquals(0, mutation.drops);
    }

    private static void assertRolledBack(ItemStack stack, FakeMutation mutation) {
        assertEquals(1, stack.getCount());
        assertEquals(0, mutation.changedCells);
        assertEquals(Optional.empty(), mutation.storedState);
        assertEquals(1, mutation.rollbacks);
        assertEquals(0, mutation.effects);
        assertEquals(0, mutation.drops);
    }

    private static final class FakeMutation
            implements ShrinePlacementMutation, ShrinePlacementMutation.StateTarget {
        int failPlacementIndex = -1;
        int failVerificationIndex = -1;
        int failReverseResolutionIndex = -1;
        boolean entityPresent = true;
        boolean assign = true;
        boolean reportMismatch;
        boolean throwOnAssign;
        boolean synchronize = true;
        boolean rollback = true;
        int changedCells;
        int rollbacks;
        int synchronizations;
        int effects;
        int drops;
        int reverseResolutionChecks;
        final List<Integer> placedIndexes = new ArrayList<>();
        Optional<PlacedStructureState> storedState = Optional.empty();

        @Override
        public boolean placeCell(ShrinePlacementPlan ignored, StructureCell cell) {
            int index = plan.cells().indexOf(cell);
            if (index == failPlacementIndex) return false;
            placedIndexes.add(index);
            changedCells++;
            return true;
        }

        @Override
        public Optional<StateTarget> anchorBlockEntity(ShrinePlacementPlan ignored) {
            return entityPresent ? Optional.of(this) : Optional.empty();
        }

        @Override
        public boolean assign(PlacedStructureState state) {
            if (throwOnAssign) throw new IllegalStateException("injected");
            if (assign) storedState = Optional.of(state);
            return assign;
        }

        @Override
        public Optional<PlacedStructureState> currentState() {
            return reportMismatch
                    ? Optional.of(new PlacedStructureState(
                            ShrineMonolithDefinitions.SHRINE,
                            new VariantId("chaos"),
                            plan.placedStructure().facing(),
                            plan.placedStructure().footprint()))
                    : storedState;
        }

        @Override
        public boolean synchronize() {
            synchronizations++;
            return synchronize;
        }

        @Override
        public boolean verifyCell(ShrinePlacementPlan ignored, StructureCell cell) {
            int index = plan.cells().indexOf(cell);
            if (index == failReverseResolutionIndex) {
                reverseResolutionChecks++;
                return LargeStructurePartBlock.anchorPosition(
                        cell.worldPosition().relative(Direction.EAST), cell.placedState())
                        .equals(plan.anchorPosition());
            }
            return index != failVerificationIndex;
        }

        @Override
        public boolean rollback(ShrinePlacementPlan ignored) {
            rollbacks++;
            if (rollback) {
                changedCells = 0;
                storedState = Optional.empty();
            }
            return rollback;
        }

        @Override
        public void afterSuccess(ShrinePlacementPlan ignored) {
            effects++;
        }
    }
}
