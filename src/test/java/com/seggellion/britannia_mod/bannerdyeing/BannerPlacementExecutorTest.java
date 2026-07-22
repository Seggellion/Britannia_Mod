package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementExecutor;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementFailure;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementMutation;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementPlan;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.structure.BannerCellRole;
import com.seggellion.britannia_mod.banner.structure.BannerFootprint;
import com.seggellion.britannia_mod.banner.structure.BannerPlacedStructure;
import com.seggellion.britannia_mod.banner.structure.BannerStructureCell;
import com.seggellion.britannia_mod.banner.structure.BannerStructureTransform;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.CoreDataFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerPlacementExecutorTest {
    private static BannerPlacementPlan plan;

    @BeforeAll
    static void setup() {
        Milestone7RegisteredTestContent.ensureRegistered();
        BannerBlock anchor = new BannerBlock(BlockBehaviour.Properties.of());
        BannerPartBlock part = new BannerPartBlock(BlockBehaviour.Properties.of());
        BannerFootprint footprint = BannerFootprint.fromDimensions(new BannerDimensions(3, 2, true))
                .footprint();
        BlockPos anchorPos = new BlockPos(15, 70, 0);
        List<BannerStructureCell> cells = footprint.offsets().stream().map(offset -> {
            var placed = offset.isAnchor()
                    ? anchor.defaultBlockState().setValue(BannerBlock.FACING, Direction.NORTH)
                    : part.stateFor(Direction.NORTH, offset);
            return new BannerStructureCell(offset,
                    BannerStructureTransform.worldPosition(anchorPos, Direction.NORTH, offset),
                    offset.isAnchor() ? BannerCellRole.ANCHOR : BannerCellRole.PART,
                    Blocks.SHORT_GRASS.defaultBlockState(), placed);
        }).toList();
        plan = new BannerPlacementPlan(anchorPos, Direction.NORTH, BannerOrientation.WALL_PARALLEL,
                cells.getFirst().placedState(), CoreDataFixtures.dyedBannerState(),
                BannerPlacedStructure.fromFootprint(footprint), cells,
                cells.stream().filter(cell -> cell.offset().vertical() == 0)
                        .map(cell -> cell.worldPosition().south()).toList());
    }

    @Test
    void survivalSuccessPlacesSixCellsThenConsumesExactlyOne() {
        ItemStack stack = configuredStack();
        FakeMutation mutation = new FakeMutation();
        assertEquals(BannerPlacementFailure.NONE, BannerPlacementExecutor.execute(plan, mutation, stack, false));
        assertTrue(stack.isEmpty());
        assertEquals(plan.bannerState(), mutation.storedState.orElseThrow());
        assertEquals(plan.placedStructure(), mutation.storedStructure.orElseThrow());
        assertEquals(6, mutation.changedCells);
        assertEquals(List.of(0, 1, 2, 3, 4, 5), mutation.placedIndexes);
        assertEquals(1, mutation.effects);
        assertEquals(1, mutation.syncs);
    }

    @Test
    void creativeSuccessConsumesNothing() {
        ItemStack stack = configuredStack();
        FakeMutation mutation = new FakeMutation();
        assertEquals(BannerPlacementFailure.NONE, BannerPlacementExecutor.execute(plan, mutation, stack, true));
        assertEquals(1, stack.getCount());
        assertEquals(6, mutation.changedCells);
    }

    @Test
    void everyAnchorAndChildPlacementFailureRollsBackAllCellsWithoutConsumptionOrDrops() {
        for (int failureIndex = 0; failureIndex < plan.cells().size(); failureIndex++) {
            ItemStack stack = configuredStack();
            FakeMutation mutation = new FakeMutation();
            mutation.failPlacementIndex = failureIndex;
            assertEquals(BannerPlacementFailure.BLOCK_SET_FAILURE,
                    BannerPlacementExecutor.execute(plan, mutation, stack, false), "cell " + failureIndex);
            assertRolledBack(stack, mutation);
        }
    }

    @Test
    void missingEntityAndRejectedMismatchedOrThrowingStateAllRollBack() {
        for (int mode = 0; mode < 4; mode++) {
            ItemStack stack = configuredStack();
            FakeMutation mutation = new FakeMutation();
            if (mode == 0) mutation.entityPresent = false;
            if (mode == 1) mutation.assign = false;
            if (mode == 2) mutation.reportMismatch = true;
            if (mode == 3) mutation.throwOnAssign = true;
            BannerPlacementFailure result = BannerPlacementExecutor.execute(plan, mutation, stack, false);
            assertTrue(result == BannerPlacementFailure.BLOCK_ENTITY_CREATION_FAILURE
                    || result == BannerPlacementFailure.STATE_TRANSFER_FAILURE);
            assertRolledBack(stack, mutation);
        }
    }

    @Test
    void finalVerificationAndSynchronizationFailuresRollBack() {
        ItemStack verifyStack = configuredStack();
        FakeMutation verify = new FakeMutation();
        verify.failVerificationIndex = 5;
        assertEquals(BannerPlacementFailure.FINAL_VERIFICATION_FAILURE,
                BannerPlacementExecutor.execute(plan, verify, verifyStack, false));
        assertRolledBack(verifyStack, verify);

        ItemStack syncStack = configuredStack();
        FakeMutation sync = new FakeMutation();
        sync.synchronize = false;
        assertEquals(BannerPlacementFailure.SYNCHRONIZATION_FAILURE,
                BannerPlacementExecutor.execute(plan, sync, syncStack, false));
        assertRolledBack(syncStack, sync);
    }

    @Test
    void rollbackFailureIsExplicitAndStillNeverConsumes() {
        ItemStack stack = configuredStack();
        FakeMutation mutation = new FakeMutation();
        mutation.failPlacementIndex = 3;
        mutation.rollback = false;
        assertEquals(BannerPlacementFailure.UNEXPECTED_ROLLBACK_FAILURE,
                BannerPlacementExecutor.execute(plan, mutation, stack, false));
        assertEquals(1, stack.getCount());
        assertEquals(0, mutation.drops);
    }

    private static ItemStack configuredStack() {
        ItemStack stack = new ItemStack(Milestone7RegisteredTestContent.banner());
        stack.set(Milestone7RegisteredTestContent.component(), plan.bannerState());
        return stack;
    }

    private static void assertRolledBack(ItemStack stack, FakeMutation mutation) {
        assertEquals(1, stack.getCount());
        assertEquals(1, mutation.rollbacks);
        assertEquals(0, mutation.changedCells);
        assertEquals(0, mutation.drops);
        assertTrue(mutation.storedState.isEmpty());
        assertTrue(mutation.storedStructure.isEmpty());
        assertFalse(mutation.effects > 0);
    }

    private static final class FakeMutation implements BannerPlacementMutation, BannerPlacementMutation.StateTarget {
        boolean entityPresent = true;
        boolean assign = true;
        boolean reportMismatch;
        boolean throwOnAssign;
        boolean synchronize = true;
        boolean rollback = true;
        int failPlacementIndex = -1;
        int failVerificationIndex = -1;
        int changedCells;
        int rollbacks;
        int drops;
        int effects;
        int syncs;
        Optional<BannerInstanceState> storedState = Optional.empty();
        Optional<BannerPlacedStructure> storedStructure = Optional.empty();
        List<Integer> placedIndexes = new ArrayList<>();

        @Override
        public boolean placeCell(BannerPlacementPlan ignored, BannerStructureCell cell) {
            int index = plan.cells().indexOf(cell);
            if (index == failPlacementIndex) return false;
            placedIndexes.add(index);
            changedCells++;
            return true;
        }

        @Override
        public Optional<StateTarget> bannerBlockEntity(BannerPlacementPlan ignored) {
            return entityPresent ? Optional.of(this) : Optional.empty();
        }

        @Override
        public boolean assign(BannerInstanceState state, BannerPlacedStructure structure) {
            if (throwOnAssign) throw new IllegalStateException("fixture failure");
            if (assign) {
                storedState = Optional.of(state);
                storedStructure = Optional.of(structure);
            }
            return assign;
        }

        @Override public Optional<BannerInstanceState> currentState() {
            return reportMismatch ? Optional.of(CoreDataFixtures.naturalBannerState()) : storedState;
        }
        @Override public Optional<BannerPlacedStructure> currentStructure() { return storedStructure; }
        @Override public boolean synchronize() { syncs++; return synchronize; }
        @Override public boolean verifyCell(BannerPlacementPlan ignored, BannerStructureCell cell) {
            return plan.cells().indexOf(cell) != failVerificationIndex;
        }

        @Override
        public boolean rollback(BannerPlacementPlan ignored) {
            rollbacks++;
            if (rollback) {
                changedCells = 0;
                storedState = Optional.empty();
                storedStructure = Optional.empty();
            }
            return rollback;
        }

        @Override public void afterSuccess(BannerPlacementPlan ignored) { effects++; }
    }
}
