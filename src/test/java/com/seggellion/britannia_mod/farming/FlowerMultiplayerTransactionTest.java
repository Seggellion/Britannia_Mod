package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.block.entity.FlowerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowerMultiplayerTransactionTest {
    private final FlowerRegistry registry = FlowerRegistry.initial();

    @Test
    void serverOrderMakesOnlyTheFirstCompetingPlantingTransactionWin() {
        FlowerDefinition poppy = registry.byId(FlowerRegistry.POPPY).orElseThrow();
        SharedPlot plot = new SharedPlot();
        ContendingAccess first = new ContendingAccess(plot, poppy.seedItemId(), 1L);
        ContendingAccess second = new ContendingAccess(plot, poppy.seedItemId(), 2L);
        AtomicInteger selectorCalls = new AtomicInteger();
        WeightedFlowerColorSelector delegate = new WeightedFlowerColorSelector(registry);
        FlowerColorSelector selector = (species, context, random, reason) -> {
            selectorCalls.incrementAndGet();
            return delegate.select(species, context, random, reason);
        };

        assertEquals(FlowerPlantingService.Outcome.PLANTED,
                FlowerPlantingService.execute(first, registry, selector));
        assertEquals(FlowerPlantingService.Outcome.REJECTED,
                FlowerPlantingService.execute(second, registry, selector));
        assertEquals(1, selectorCalls.get());
        assertEquals(1, first.consumedSeeds);
        assertEquals(0, second.consumedSeeds);
        assertEquals(1, first.feedbackCalls);
        assertEquals(0, second.feedbackCalls);
        assertTrue(plot.flowerState.isPresent());
        assertFalse(plot.farmingBlock);
        assertEquals(first.planterUuid(), plot.flowerState.orElseThrow().planterUuid());
    }

    @Test
    void twoObserversDeserializeIdenticalStateAcrossGrowthCareResetAndMastery() {
        FlowerDefinition poppy = registry.byId(FlowerRegistry.POPPY).orElseThrow();
        FlowerPersistentState state = new FlowerPersistentState(
                FlowerPersistentState.CURRENT_DATA_VERSION,
                poppy.id(), registry.fallbackColor(poppy), 6,
                FlowerPlantingOrigin.ADMIN, true,
                Optional.of(UUID.fromString("4ed8d511-952d-4682-af85-654a4836673a")),
                new FlowerRegionProvenance("Observer fixture", FarmingClimate.TEMPERATE),
                new FlowerQuality(77),
                FlowerSoilSnapshot.communitySoil(5, 2, 0.9F, 0.8F, 0.7F, 0.6F, 5_000L),
                new FlowerGrowthState(1.0F, 0, false)
        );

        for (FlowerPersistentState changed : new FlowerPersistentState[]{
                state,
                state.withSoil(state.soil().withHydration(3)),
                state.withGrowth(7, new FlowerGrowthState(1.0F, 0, false)),
                FlowerGrowthEvaluator.resetToStageOne(state)
        }) {
            CompoundTag packet = FlowerBlockEntity.clientStateTag(changed);
            FlowerPersistentState observerOne = FlowerPersistentState.fromTag(packet, registry);
            FlowerPersistentState observerTwo = FlowerPersistentState.fromTag(packet.copy(), registry);
            assertEquals(observerOne, observerTwo);
            assertEquals(changed.speciesId(), observerOne.speciesId());
            assertEquals(changed.color(), observerOne.color());
            assertEquals(changed.growthStage(), observerOne.growthStage());
            assertEquals(changed.protectedFlower(), observerOne.protectedFlower());
            assertTrue(observerOne.planterUuid().isEmpty());
        }
    }

    @Test
    void nearSimultaneousFlowerMutationsHaveOneWinnerInEitherArrivalOrder() {
        FlowerInteractionTransactionGate masteryFirst = new FlowerInteractionTransactionGate();
        assertTrue(masteryFirst.tryCommit(1_000L), "mastery packet wins");
        assertFalse(masteryFirst.tryCommit(1_005L), "harvest packet is a no-op inside the contention window");
        assertTrue(masteryFirst.tryCommit(1_010L), "later intentional interaction remains available");

        FlowerInteractionTransactionGate harvestFirst = new FlowerInteractionTransactionGate();
        assertTrue(harvestFirst.tryCommit(2_000L), "harvest packet wins");
        assertFalse(harvestFirst.tryCommit(2_001L), "mastery packet is a no-op inside the contention window");
    }

    @Test
    void uprootReplacementSuppressesOnlyTheContentionWindowAtThatPosition() {
        ResourceLocation dimension = ResourceLocation.fromNamespaceAndPath("britannia_mod", "m9_guard_test");
        long uprootedPos = 1234L;
        FlowerInteractionTransactionGate.markReplacement(dimension, uprootedPos, 3_000L);

        assertTrue(FlowerInteractionTransactionGate.suppressReplacementInteraction(dimension, uprootedPos, 3_005L));
        assertFalse(FlowerInteractionTransactionGate.suppressReplacementInteraction(dimension, uprootedPos + 1L, 3_005L));
        assertFalse(FlowerInteractionTransactionGate.suppressReplacementInteraction(dimension, uprootedPos, 3_010L));
    }

    private static final class SharedPlot {
        private boolean farmingBlock = true;
        private Optional<FlowerPersistentState> flowerState = Optional.empty();
    }

    private static final class ContendingAccess implements FlowerPlantingService.PlantingAccess {
        private final SharedPlot plot;
        private final ResourceLocation seed;
        private final long randomSeed;
        private final UUID planter;
        private int consumedSeeds;
        private int feedbackCalls;

        private ContendingAccess(SharedPlot plot, ResourceLocation seed, long randomSeed) {
            this.plot = plot;
            this.seed = seed;
            this.randomSeed = randomSeed;
            this.planter = new UUID(0x4D39504C414E544CL, randomSeed);
        }

        @Override public ResourceLocation heldItemId() { return seed; }
        @Override public boolean heldItemInFlowerSeedTag() { return true; }
        @Override public boolean logicalServer() { return true; }
        @Override public boolean targetIsFarmingBlock() { return plot.farmingBlock; }
        @Override public boolean targetOccupied() { return plot.flowerState.isPresent(); }
        @Override public FarmingCultivationGate.Subject cultivationSubject() {
            return FarmingCultivationGate.Subject.loadedPlayer(100.0F);
        }
        @Override public void applyCultivationDenial(FarmingCultivationGate.Evaluation evaluation) { }
        @Override public FlowerSoilSnapshot captureSoilSnapshot() {
            return FlowerSoilSnapshot.privateSoil(3, 1, 0.4F, 0.5F, 0.6F, 0.7F);
        }
        @Override public FlowerPlantingOrigin plantingOrigin() { return FlowerPlantingOrigin.PLAYER; }
        @Override public Optional<UUID> planterUuid() { return Optional.of(planter); }
        @Override public FlowerRegionProvenance regionProvenance() {
            return new FlowerRegionProvenance("Shared plot", FarmingClimate.TEMPERATE);
        }
        @Override public FarmingClimate currentClimate() { return FarmingClimate.TEMPERATE; }
        @Override public int altitude() { return 80; }
        @Override public RandomSource random() { return RandomSource.create(randomSeed); }
        @Override public boolean replaceWithFlower(FlowerSoilSnapshot soil) {
            if (!plot.farmingBlock || plot.flowerState.isPresent()) {
                return false;
            }
            plot.farmingBlock = false;
            return true;
        }
        @Override public boolean initializeFlower(FlowerPersistentState state) {
            if (plot.farmingBlock || plot.flowerState.isPresent()) {
                return false;
            }
            plot.flowerState = Optional.of(state);
            return true;
        }
        @Override public boolean verifyFlowerState(FlowerPersistentState state) {
            return plot.flowerState.filter(state::equals).isPresent();
        }
        @Override public void synchronizeFlower() { }
        @Override public void consumeOneSeed() { consumedSeeds++; }
        @Override public void applyPlantingFeedback() { feedbackCalls++; }
        @Override public boolean rollback() {
            plot.flowerState = Optional.empty();
            plot.farmingBlock = true;
            consumedSeeds = 0;
            feedbackCalls = 0;
            return true;
        }
        @Override public String targetDescription() { return "shared-plot"; }
    }
}
