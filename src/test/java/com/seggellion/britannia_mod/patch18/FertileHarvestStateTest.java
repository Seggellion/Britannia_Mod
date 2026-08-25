package com.seggellion.britannia_mod.patch18;

import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.farming.FlowerSoilSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FertileHarvestStateTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void legacyPlotsRemainUntrackedWhileFreshApplicationsCountExactlyFiveSuccesses() {
        TestFarmingBlockEntity soil = new TestFarmingBlockEntity();

        assertEquals(FarmingBlockEntity.UNTRACKED_FERTILE_HARVESTS,
                soil.getRemainingFertileHarvests());
        assertEquals(FarmingBlockEntity.UNTRACKED_FERTILE_HARVESTS,
                soil.consumeSuccessfulFertileHarvest());
        assertFalse(soil.hasTrackedFertility());

        soil.initializeFertileHarvests();
        assertEquals(5, soil.getRemainingFertileHarvests());
        for (int expected = 4; expected >= 0; expected--) {
            assertEquals(expected, soil.consumeSuccessfulFertileHarvest());
        }
        assertTrue(soil.isFertilityExhausted());
        assertEquals(0, soil.consumeSuccessfulFertileHarvest(), "exhausted state underflowed");
    }

    @Test
    void diskAndClientTagsRoundTripTheAuthoritativeCountAndLegacyAbsence() {
        TestFarmingBlockEntity source = new TestFarmingBlockEntity();
        source.initializeFertileHarvests();
        source.consumeSuccessfulFertileHarvest();
        source.consumeSuccessfulFertileHarvest();

        CompoundTag disk = source.saveWithoutMetadata(RegistryAccess.EMPTY);
        TestFarmingBlockEntity fromDisk = new TestFarmingBlockEntity();
        fromDisk.loadWithComponents(disk, RegistryAccess.EMPTY);
        assertEquals(3, fromDisk.getRemainingFertileHarvests());

        TestFarmingBlockEntity fromUpdate = new TestFarmingBlockEntity();
        fromUpdate.handleUpdateTag(source.getUpdateTag(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertEquals(3, fromUpdate.getRemainingFertileHarvests());

        CompoundTag legacy = disk.copy();
        legacy.remove("RemainingFertileHarvests");
        TestFarmingBlockEntity fromLegacy = new TestFarmingBlockEntity();
        fromLegacy.loadWithComponents(legacy, RegistryAccess.EMPTY);
        assertEquals(FarmingBlockEntity.UNTRACKED_FERTILE_HARVESTS,
                fromLegacy.getRemainingFertileHarvests());
    }

    @Test
    void flowerSoilCarriesFiniteFertilityWithoutChangingLegacySnapshots() {
        FlowerSoilSnapshot tracked = FlowerSoilSnapshot.privateSoil(
                3, 1, 0.2F, 0.4F, 0.6F, 0.8F, 4);
        FlowerSoilSnapshot decoded = FlowerSoilSnapshot.fromTag(tracked.toTag());

        assertEquals(4, decoded.remainingFertileHarvests());
        assertEquals(4, decoded.withHydration(5).remainingFertileHarvests());
        assertEquals(4, decoded.withFertilizerLevel(2).remainingFertileHarvests());

        FlowerSoilSnapshot legacy = FlowerSoilSnapshot.privateSoil(
                3, 1, 0.2F, 0.4F, 0.6F, 0.8F);
        assertEquals(FarmingBlockEntity.UNTRACKED_FERTILE_HARVESTS,
                FlowerSoilSnapshot.fromTag(legacy.toTag()).remainingFertileHarvests());

        assertThrows(IllegalArgumentException.class, () -> FlowerSoilSnapshot.privateSoil(
                3, 1, 0.2F, 0.4F, 0.6F, 0.8F, 6));
    }

    private static final class TestFarmingBlockEntity extends FarmingBlockEntity {
        private TestFarmingBlockEntity() {
            super(BlockEntityType.FURNACE, BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
        }
    }
}
