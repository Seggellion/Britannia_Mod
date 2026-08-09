package com.seggellion.britannia_mod.structure.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.item.MonolithItem;
import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import com.seggellion.britannia_mod.structure.multiblock.StructureCell;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MonolithPlacementExecutorTest {
    private static MonolithItem item;
    private static ShrinePlacementPlan plan;

    @BeforeAll
    static void setup() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
        item = MilestoneTwoRegisteredTestContent.monolith();
        plan = ShrinePlacementPlanner.plan(
                item, new ItemStack(item), ShrineMonolithDefinitions.MONOLITH,
                new VariantId("diagnostic_missing_content"), new BlockPos(15, 69, 15),
                Direction.UP, Direction.WEST,
                MilestoneTwoRegisteredTestContent.anchor(),
                MilestoneTwoRegisteredTestContent.part(),
                new ShrinePlacementPlannerTest.FakeWorld()).plan().orElseThrow();
    }

    @Test
    void survivalAndCreativeSuccessMutateExactlyEighteenCellsInOrder() {
        ItemStack survival = new ItemStack(item);
        Mutation survivalMutation = new Mutation();
        assertEquals(ShrinePlacementFailure.NONE,
                ShrinePlacementExecutor.execute(plan, survivalMutation, survival, false));
        assertTrue(survival.isEmpty());
        assertEquals(java.util.stream.IntStream.range(0, 18).boxed().toList(),
                survivalMutation.placedIndexes);
        assertEquals(18, survivalMutation.changedCells);
        assertEquals(1, survivalMutation.synchronizations);
        assertEquals(1, survivalMutation.effects);

        ItemStack creative = new ItemStack(item);
        Mutation creativeMutation = new Mutation();
        assertEquals(ShrinePlacementFailure.NONE,
                ShrinePlacementExecutor.execute(plan, creativeMutation, creative, true));
        assertEquals(1, creative.getCount());
        assertEquals(18, creativeMutation.changedCells);
    }

    @Test
    void everyMutationAndVerificationBoundaryRollsBackWithoutConsumptionOrSuccess() {
        for (int index = 0; index < 18; index++) {
            ItemStack mutationStack = new ItemStack(item);
            Mutation mutation = new Mutation();
            mutation.failPlacementIndex = index;
            assertEquals(ShrinePlacementFailure.BLOCK_SET_FAILURE,
                    ShrinePlacementExecutor.execute(plan, mutation, mutationStack, false),
                    "mutation " + index);
            assertRolledBack(mutationStack, mutation);

            ItemStack verificationStack = new ItemStack(item);
            Mutation verification = new Mutation();
            verification.failVerificationIndex = index;
            assertEquals(ShrinePlacementFailure.FINAL_VERIFICATION_FAILURE,
                    ShrinePlacementExecutor.execute(plan, verification, verificationStack, false),
                    "verification " + index);
            assertRolledBack(verificationStack, verification);
        }
    }

    @Test
    void anchorEntityStateAndSynchronizationFailuresRollbackExplicitly() {
        for (int mode = 0; mode < 4; mode++) {
            ItemStack stack = new ItemStack(item);
            Mutation mutation = new Mutation();
            if (mode == 0) mutation.entityPresent = false;
            if (mode == 1) mutation.assign = false;
            if (mode == 2) mutation.reportMismatch = true;
            if (mode == 3) mutation.synchronize = false;
            assertTrue(ShrinePlacementExecutor.execute(plan, mutation, stack, false)
                    != ShrinePlacementFailure.NONE);
            assertRolledBack(stack, mutation);
        }

        ItemStack stack = new ItemStack(item);
        Mutation incomplete = new Mutation();
        incomplete.failPlacementIndex = 10;
        incomplete.rollback = false;
        assertEquals(ShrinePlacementFailure.UNEXPECTED_ROLLBACK_FAILURE,
                ShrinePlacementExecutor.execute(plan, incomplete, stack, false));
        assertEquals(1, stack.getCount());
        assertEquals(0, incomplete.effects);
    }

    private static void assertRolledBack(ItemStack stack, Mutation mutation) {
        assertEquals(1, stack.getCount());
        assertEquals(0, mutation.changedCells);
        assertEquals(Optional.empty(), mutation.storedState);
        assertEquals(1, mutation.rollbacks);
        assertEquals(0, mutation.effects);
    }

    private static final class Mutation implements ShrinePlacementMutation,
            ShrinePlacementMutation.StateTarget {
        int failPlacementIndex = -1;
        int failVerificationIndex = -1;
        boolean entityPresent = true;
        boolean assign = true;
        boolean reportMismatch;
        boolean synchronize = true;
        boolean rollback = true;
        int changedCells;
        int rollbacks;
        int synchronizations;
        int effects;
        final List<Integer> placedIndexes = new ArrayList<>();
        Optional<PlacedStructureState> storedState = Optional.empty();

        @Override public boolean placeCell(ShrinePlacementPlan ignored, StructureCell cell) {
            int index = plan.cells().indexOf(cell);
            if (index == failPlacementIndex) return false;
            placedIndexes.add(index);
            changedCells++;
            return true;
        }
        @Override public Optional<StateTarget> anchorBlockEntity(ShrinePlacementPlan ignored) {
            return entityPresent ? Optional.of(this) : Optional.empty();
        }
        @Override public boolean assign(PlacedStructureState state) {
            if (assign) storedState = Optional.of(state);
            return assign;
        }
        @Override public Optional<PlacedStructureState> currentState() {
            return reportMismatch ? Optional.of(new PlacedStructureState(
                    ShrineMonolithDefinitions.MONOLITH, new VariantId("mismatch"),
                    plan.placedStructure().facing(), plan.placedStructure().footprint())) : storedState;
        }
        @Override public boolean synchronize() {
            synchronizations++;
            return synchronize;
        }
        @Override public boolean verifyCell(ShrinePlacementPlan ignored, StructureCell cell) {
            return plan.cells().indexOf(cell) != failVerificationIndex;
        }
        @Override public boolean rollback(ShrinePlacementPlan ignored) {
            rollbacks++;
            if (rollback) {
                changedCells = 0;
                storedState = Optional.empty();
            }
            return rollback;
        }
        @Override public void afterSuccess(ShrinePlacementPlan ignored) { effects++; }
    }
}
