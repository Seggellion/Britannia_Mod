package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementExecutor;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementFailure;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementMutation;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementPlan;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
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
        BannerBlock block = new BannerBlock(BlockBehaviour.Properties.of());
        plan = new BannerPlacementPlan(BlockPos.ZERO, new BlockPos(0, 0, -1), Direction.NORTH,
                Blocks.SHORT_GRASS.defaultBlockState(),
                block.defaultBlockState().setValue(BannerBlock.FACING, Direction.NORTH),
                CoreDataFixtures.dyedBannerState());
    }

    @Test
    void survivalSuccessTransfersExactStateThenConsumesExactlyOneAndEmitsEffects() {
        ItemStack stack = configuredStack();
        FakeMutation mutation = new FakeMutation();
        assertEquals(BannerPlacementFailure.NONE,
                BannerPlacementExecutor.execute(plan, mutation, stack, false));
        assertTrue(stack.isEmpty());
        assertEquals(plan.bannerState(), mutation.stored.orElseThrow());
        assertEquals(List.of("place", "assign", "effects"), mutation.events);
        assertEquals(1, mutation.changedCells);
        assertEquals(0, mutation.rollbacks);
        assertEquals(0, mutation.drops);
    }

    @Test
    void creativeSuccessTransfersExactStateWithoutConsumption() {
        ItemStack stack = configuredStack();
        FakeMutation mutation = new FakeMutation();
        assertEquals(BannerPlacementFailure.NONE,
                BannerPlacementExecutor.execute(plan, mutation, stack, true));
        assertEquals(1, stack.getCount());
        assertEquals(plan.bannerState(), mutation.stored.orElseThrow());
        assertEquals(1, mutation.changedCells);
    }

    @Test
    void blockSetFailureChangesNothingAndConsumesNothing() {
        ItemStack stack = configuredStack();
        FakeMutation mutation = new FakeMutation();
        mutation.place = false;
        assertEquals(BannerPlacementFailure.BLOCK_SET_FAILURE,
                BannerPlacementExecutor.execute(plan, mutation, stack, false));
        assertEquals(1, stack.getCount());
        assertEquals(0, mutation.changedCells);
        assertEquals(0, mutation.rollbacks);
        assertTrue(mutation.stored.isEmpty());
    }

    @Test
    void missingBlockEntityRollsBackWithoutDropOrConsumption() {
        ItemStack stack = configuredStack();
        FakeMutation mutation = new FakeMutation();
        mutation.entityPresent = false;
        assertEquals(BannerPlacementFailure.BLOCK_ENTITY_CREATION_FAILURE,
                BannerPlacementExecutor.execute(plan, mutation, stack, false));
        assertRolledBack(stack, mutation);
    }

    @Test
    void rejectedOrMismatchedStateWriteRollsBackWithoutOrphan() {
        ItemStack rejectedStack = configuredStack();
        FakeMutation rejected = new FakeMutation();
        rejected.assign = false;
        assertEquals(BannerPlacementFailure.STATE_TRANSFER_FAILURE,
                BannerPlacementExecutor.execute(plan, rejected, rejectedStack, false));
        assertRolledBack(rejectedStack, rejected);

        ItemStack mismatchStack = configuredStack();
        FakeMutation mismatch = new FakeMutation();
        mismatch.reportMismatch = true;
        assertEquals(BannerPlacementFailure.STATE_TRANSFER_FAILURE,
                BannerPlacementExecutor.execute(plan, mismatch, mismatchStack, false));
        assertRolledBack(mismatchStack, mismatch);
    }

    @Test
    void unexpectedStateWriteExceptionUsesSameRollbackBoundary() {
        ItemStack stack = configuredStack();
        FakeMutation mutation = new FakeMutation();
        mutation.throwOnAssign = true;
        assertEquals(BannerPlacementFailure.STATE_TRANSFER_FAILURE,
                BannerPlacementExecutor.execute(plan, mutation, stack, false));
        assertRolledBack(stack, mutation);
    }

    @Test
    void rollbackFailureIsExplicitAndStillDoesNotConsume() {
        ItemStack stack = configuredStack();
        FakeMutation mutation = new FakeMutation();
        mutation.entityPresent = false;
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
        assertFalse(mutation.orphan);
        assertFalse(mutation.effects);
    }

    private static final class FakeMutation implements BannerPlacementMutation, BannerPlacementMutation.StateTarget {
        boolean place = true;
        boolean entityPresent = true;
        boolean assign = true;
        boolean reportMismatch;
        boolean throwOnAssign;
        boolean rollback = true;
        boolean orphan;
        boolean effects;
        int changedCells;
        int rollbacks;
        int drops;
        Optional<BannerInstanceState> stored = Optional.empty();
        List<String> events = new ArrayList<>();

        @Override
        public boolean placeBanner(BannerPlacementPlan ignored) {
            events.add("place");
            if (place) changedCells = 1;
            return place;
        }

        @Override
        public Optional<StateTarget> bannerBlockEntity(BannerPlacementPlan ignored) {
            return entityPresent ? Optional.of(this) : Optional.empty();
        }

        @Override
        public boolean assign(BannerInstanceState state) {
            events.add("assign");
            if (throwOnAssign) throw new IllegalStateException("fixture failure");
            if (assign) stored = Optional.of(state);
            return assign;
        }

        @Override
        public Optional<BannerInstanceState> currentState() {
            return reportMismatch ? Optional.of(CoreDataFixtures.naturalBannerState()) : stored;
        }

        @Override
        public boolean rollback(BannerPlacementPlan ignored) {
            rollbacks++;
            if (rollback) {
                changedCells = 0;
                stored = Optional.empty();
                orphan = false;
            }
            return rollback;
        }

        @Override
        public void afterSuccess(BannerPlacementPlan ignored) {
            effects = true;
            events.add("effects");
        }
    }
}
