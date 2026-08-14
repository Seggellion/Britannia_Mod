package com.seggellion.britannia_mod.wildresource;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildResourceEntryTest {
    private static final ResourceLocation TEST_ID = ResourceLocation.fromNamespaceAndPath(
            "britannia_mod", "test_resource"
    );

    @Test
    void entriesRegisterAndUnknownIdsFailSafely() {
        WildResourceRegistry registry = new WildResourceRegistry();
        WildResourceEntry entry = entry(new WildResourceTuning(20, 40, 60, 80, 3, 2));

        assertEquals(entry, registry.register(entry));
        assertEquals(entry, registry.find(TEST_ID).orElseThrow());
        assertTrue(registry.find(ResourceLocation.fromNamespaceAndPath("britannia_mod", "missing")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> registry.register(entry));
    }

    @Test
    void centralizedTuningCanBeChangedWithoutChangingStrategies() {
        WildResourceTuning conservative = new WildResourceTuning(100, 100, 200, 200, 2, 4);
        WildResourceTuning generous = new WildResourceTuning(10, 10, 20, 20, 8, 1);

        WildResourceEntry first = entry(conservative);
        WildResourceEntry second = entry(generous);

        assertEquals(2, first.tuning().maxRandomProbes());
        assertEquals(8, second.tuning().maxRandomProbes());
        assertEquals(10, second.tuning().nextAttemptDelay(RandomSource.create(1L)));
    }

    @Test
    void entryDispatchesEachPlacementStrategyInOrderAndShortCircuits() {
        StringBuilder calls = new StringBuilder();
        WildResourceEntry entry = new WildResourceEntry(
                TEST_ID, 1, 1, new WildResourceTuning(20, 20, 40, 40, 1, 0),
                (level, chunk, random) -> BlockPos.ZERO,
                (level, position) -> calls.append('e') != null,
                (level, position) -> calls.append('s') != null,
                (level, position) -> false,
                (level, position) -> calls.append('n') != null,
                (level, position) -> true,
                WildResourceEntry.HarvestStrategy.DISABLED,
                WildResourceEntry.LootStrategy.NONE
        );

        assertFalse(entry.isValidPlacement(null, BlockPos.ZERO));
        assertEquals("es", calls.toString());
    }

    @Test
    void weightedSelectionDispatchesThroughRegisteredEntries() {
        WildResourceRegistry registry = new WildResourceRegistry();
        registry.register(entry(new WildResourceTuning(20, 20, 40, 40, 1, 0)));

        assertEquals(TEST_ID, registry.select(RandomSource.create(2L)).orElseThrow().id());
        assertTrue(new WildResourceRegistry().select(RandomSource.create(2L)).isEmpty());
    }

    @Test
    void weightedSelectionCanBeRestrictedToCurrentlyDueEntries() {
        WildResourceRegistry registry = new WildResourceRegistry();
        WildResourceEntry first = entry(new WildResourceTuning(20, 20, 40, 40, 1, 0));
        WildResourceEntry second = new WildResourceEntry(
                ResourceLocation.fromNamespaceAndPath("britannia_mod", "second"),
                50,
                1,
                first.tuning(),
                first.candidateGenerator(),
                first.environmentRule(),
                first.substrateRule(),
                first.biomeRule(),
                first.nearbyRule(),
                first.placementStrategy(),
                first.harvestStrategy(),
                first.lootStrategy()
        );
        registry.register(first);
        registry.register(second);

        assertEquals(second, registry.select(RandomSource.create(5L), List.of(second)).orElseThrow());
    }

    private static WildResourceEntry entry(WildResourceTuning tuning) {
        return new WildResourceEntry(
                TEST_ID, 3, 2, tuning,
                (level, chunk, random) -> BlockPos.ZERO,
                WildResourceEntry.PlacementRule.ALLOW,
                WildResourceEntry.PlacementRule.ALLOW,
                WildResourceEntry.PlacementRule.ALLOW,
                WildResourceEntry.PlacementRule.ALLOW,
                (level, position) -> true,
                WildResourceEntry.HarvestStrategy.DISABLED,
                (level, position, player) -> ItemStack.EMPTY
        );
    }
}
