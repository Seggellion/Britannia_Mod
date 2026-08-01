package com.seggellion.britannia_mod.farming;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.block.entity.FlowerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowerLifecycleTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));
    private final FlowerRegistry registry = FlowerRegistry.initial();

    @Test
    void onlyRegisteredFlowerSeedsRequestConversion() {
        FakeAccess ordinaryItem = new FakeAccess(ResourceLocation.parse("minecraft:stick"));
        assertEquals(FlowerPlantingService.Outcome.NOT_A_FLOWER_SEED,
                execute(ordinaryItem, new AtomicInteger()));
        assertFalse(ordinaryItem.mutated());

        FakeAccess unknownTaggedSeed = new FakeAccess(ResourceLocation.parse("britannia_mod:unknown_flower_seeds"));
        unknownTaggedSeed.taggedFlowerSeed = true;
        assertEquals(FlowerPlantingService.Outcome.REJECTED,
                execute(unknownTaggedSeed, new AtomicInteger()));
        assertFalse(unknownTaggedSeed.mutated());
    }

    @Test
    void allSevenSpeciesPlantAtStageOneWithOneAllowedColorSelection() {
        for (FlowerDefinition definition : registry.definitions().values()) {
            FakeAccess access = new FakeAccess(definition.seedItemId());
            AtomicInteger selectorCalls = new AtomicInteger();

            assertEquals(FlowerPlantingService.Outcome.PLANTED, execute(access, selectorCalls), definition.id().toString());
            FlowerPersistentState planted = access.flowerState.orElseThrow();
            assertEquals(1, selectorCalls.get());
            assertEquals(definition.id(), planted.speciesId());
            assertEquals(1, planted.growthStage());
            assertTrue(registry.isAllowedColor(definition, planted.color()));
            assertEquals(access.soil, planted.soil());
            assertEquals(access.provenance, planted.regionProvenance());
            assertEquals(FlowerQuality.DEFAULT, planted.quality());
            assertEquals(access.planterUuid, planted.planterUuid());
            assertTrue(access.replaced);
            assertTrue(access.initialized);
            assertTrue(access.synchronizedState);
            assertEquals(1, access.consumedSeeds);
            assertEquals(1, access.feedbackCalls);
        }
    }

    @Test
    void invalidTargetsFailBeforeColorSelectionAndMutateNothing() {
        ResourceLocation seed = registry.byId(FlowerRegistry.POPPY).orElseThrow().seedItemId();
        for (InvalidTarget invalid : InvalidTarget.values()) {
            FakeAccess access = new FakeAccess(seed);
            if (invalid == InvalidTarget.CLIENT) {
                access.logicalServer = false;
            } else if (invalid == InvalidTarget.WRONG_BLOCK_OR_MISSING_ENTITY) {
                access.validTarget = false;
            } else if (invalid == InvalidTarget.OCCUPIED) {
                access.occupied = true;
            } else {
                access.failure = FailurePoint.CAPTURE;
            }
            AtomicInteger selectorCalls = new AtomicInteger();

            assertEquals(FlowerPlantingService.Outcome.REJECTED, execute(access, selectorCalls), invalid.name());
            assertEquals(0, selectorCalls.get(), invalid.name());
            assertFalse(access.mutated(), invalid.name());
        }
    }

    @Test
    void postReplacementFailuresRestoreWorldSoilCommunityAndHeldSeed() {
        ResourceLocation seed = registry.byId(FlowerRegistry.ORFLUER).orElseThrow().seedItemId();
        for (FailurePoint failure : new FailurePoint[]{
                FailurePoint.REPLACE_FALSE,
                FailurePoint.INIT_FALSE,
                FailurePoint.VERIFY_FALSE,
                FailurePoint.INIT,
                FailurePoint.SYNC,
                FailurePoint.CONSUME
        }) {
            FakeAccess access = new FakeAccess(seed);
            access.soil = FlowerSoilSnapshot.communitySoil(
                    5, 2, 0.77f, 0.66f, 0.55f, 0.44f, 987654L
            );
            FlowerSoilSnapshot originalSoil = access.soil;
            access.failure = failure;

            assertEquals(FlowerPlantingService.Outcome.REJECTED,
                    execute(access, new AtomicInteger()), failure.name());
            assertEquals(1, access.rollbackCalls, failure.name());
            assertFalse(access.replaced, failure.name());
            assertFalse(access.initialized, failure.name());
            assertEquals(0, access.consumedSeeds, failure.name());
            assertEquals(0, access.feedbackCalls, failure.name());
            assertEquals(originalSoil, access.soil, failure.name());
            assertTrue(access.flowerState.isEmpty(), failure.name());
        }
    }

    @Test
    void planterPrivilegesAloneDeterminePersistedOriginAndProtection() {
        FlowerDefinition flower = registry.byId(FlowerRegistry.LILY).orElseThrow();

        FakeAccess ordinary = new FakeAccess(flower.seedItemId());
        ordinary.origin = FlowerPlantingOrigin.PLAYER;
        assertEquals(FlowerPlantingService.Outcome.PLANTED, execute(ordinary, new AtomicInteger()));
        assertEquals(FlowerPlantingOrigin.PLAYER, ordinary.flowerState.orElseThrow().plantingOrigin());
        assertFalse(ordinary.flowerState.orElseThrow().protectedFlower());

        FakeAccess creativeOrOperator = new FakeAccess(flower.seedItemId());
        creativeOrOperator.origin = FlowerPlantingOrigin.ADMIN;
        assertEquals(FlowerPlantingService.Outcome.PLANTED, execute(creativeOrOperator, new AtomicInteger()));
        assertEquals(FlowerPlantingOrigin.ADMIN, creativeOrOperator.flowerState.orElseThrow().plantingOrigin());
        assertTrue(creativeOrOperator.flowerState.orElseThrow().protectedFlower());

        // Soil has no preparer identity, so an ordinary planter remains ordinary
        // even when the soil was prepared by an administrator outside this transaction.
        FakeAccess adminPreparedSoilOrdinaryPlanter = new FakeAccess(flower.seedItemId());
        adminPreparedSoilOrdinaryPlanter.origin = FlowerPlantingOrigin.PLAYER;
        assertEquals(FlowerPlantingService.Outcome.PLANTED,
                execute(adminPreparedSoilOrdinaryPlanter, new AtomicInteger()));
        assertFalse(adminPreparedSoilOrdinaryPlanter.flowerState.orElseThrow().protectedFlower());
    }

    @Test
    void persistenceAndClientSynchronizationPreserveExactIdentityWithoutUuidExposure() {
        FlowerDefinition flower = registry.byId(FlowerRegistry.HYACINTH).orElseThrow();
        FakeAccess access = new FakeAccess(flower.seedItemId());
        access.soil = FlowerSoilSnapshot.communitySoil(4, 1, 0.4f, 0.5f, 0.6f, 0.7f, 12345L);
        assertEquals(FlowerPlantingService.Outcome.PLANTED, execute(access, new AtomicInteger()));
        FlowerPersistentState planted = access.flowerState.orElseThrow();
        FlowerPersistentState original = new FlowerPersistentState(
                planted.dataVersion(), planted.speciesId(), planted.color(), 4,
                planted.plantingOrigin(), planted.protectedFlower(), planted.planterUuid(),
                planted.regionProvenance(), new FlowerQuality(83), planted.soil(),
                new FlowerGrowthState(0.42f, 19, true)
        );

        FlowerPersistentState saved = FlowerPersistentState.fromTag(original.toTag(), registry);
        assertEquals(original, saved);
        assertEquals(original.color().tintValue(), saved.color().tintValue());
        assertEquals(FlowerSoilOrigin.COMMUNITY_PLOT, saved.soil().origin());
        assertEquals(12345L, saved.soil().communitySeedableUntilGameTime());
        assertEquals(FlowerCommunityRestoration.currentRepositoryState(),
                saved.soil().communityRestoration().orElseThrow());
        assertEquals(83, saved.quality().value());
        assertEquals(0.42f, saved.growthState().progress());
        assertEquals(19, saved.growthState().tickProgress());
        assertTrue(saved.growthState().blocked());

        CompoundTag clientTag = FlowerBlockEntity.clientStateTag(original);
        assertFalse(clientTag.hasUUID("PlanterUuid"));
        FlowerPersistentState observerOne = FlowerPersistentState.fromTag(clientTag, registry);
        FlowerPersistentState observerTwo = FlowerPersistentState.fromTag(clientTag.copy(), registry);
        assertEquals(observerOne, observerTwo);
        assertEquals(original.speciesId(), observerOne.speciesId());
        assertEquals(original.color(), observerOne.color());
        assertEquals(original.growthStage(), observerOne.growthStage());
        assertEquals(original.protectedFlower(), observerOne.protectedFlower());
        assertTrue(observerOne.planterUuid().isEmpty());
    }

    @Test
    void legacyAndUnknownIdentityLoadDeterministicallyWithoutRerolling() {
        FlowerDefinition poppy = registry.byId(FlowerRegistry.POPPY).orElseThrow();
        FakeAccess access = new FakeAccess(poppy.seedItemId());
        assertEquals(FlowerPlantingService.Outcome.PLANTED, execute(access, new AtomicInteger()));
        CompoundTag original = access.flowerState.orElseThrow().toTag();

        CompoundTag legacy = original.copy();
        legacy.remove("PlantingOrigin");
        legacy.remove("Protected");
        FlowerPersistentState legacyLoaded = FlowerPersistentState.fromTag(legacy, registry);
        assertEquals(FlowerPlantingOrigin.PLAYER, legacyLoaded.plantingOrigin());
        assertFalse(legacyLoaded.protectedFlower());

        CompoundTag unknownColor = original.copy();
        unknownColor.putInt("ColorTint", 0x123456);
        FlowerPersistentState colorLoaded = FlowerPersistentState.fromTag(unknownColor, registry);
        assertEquals(0x123456, colorLoaded.color().tintValue());
        assertNotEquals(colorLoaded.color(), colorLoaded.visualColor(registry));
        assertEquals(registry.fallbackColor(poppy), colorLoaded.visualColor(registry));

        CompoundTag unknownSpecies = original.copy();
        ResourceLocation missing = ResourceLocation.parse("britannia_mod:missing_flower");
        unknownSpecies.putString("SpeciesId", missing.toString());
        unknownSpecies.putInt("GrowthStage", 99);
        FlowerPersistentState missingLoaded = FlowerPersistentState.fromTag(unknownSpecies, registry);
        assertEquals(missing, missingLoaded.speciesId());
        assertEquals(7, missingLoaded.growthStage());
        assertEquals(0xFFFFFF, missingLoaded.visualColor(registry).tintValue());
    }

    @Test
    void impossibleCommunityMetadataFailsSafely() {
        FlowerDefinition flower = registry.byId(FlowerRegistry.CAMPION).orElseThrow();
        FakeAccess access = new FakeAccess(flower.seedItemId());
        assertEquals(FlowerPlantingService.Outcome.PLANTED, execute(access, new AtomicInteger()));
        CompoundTag corrupt = access.flowerState.orElseThrow().toTag();
        CompoundTag soil = corrupt.getCompound("Soil");
        soil.putString("Origin", FlowerSoilOrigin.PRIVATE_FARMING_BLOCK.name());
        soil.put("CommunityRestoration", FlowerCommunityRestoration.currentRepositoryState().toTag());
        assertThrows(IllegalArgumentException.class, () -> FlowerPersistentState.fromTag(corrupt, registry));
    }

    @Test
    void registrationIntegrationAndTemporaryResourcesRemainCompatibleWithGrowthMilestone() throws IOException {
        String farmingBlock = source("block/FarmingBlock.java");
        int care = farmingBlock.indexOf("applyCareItem(");
        int flowers = farmingBlock.indexOf("FlowerPlantingService.tryPlant(");
        int grapes = farmingBlock.indexOf("stack.getItem() instanceof GrapeSeedsItem", flowers);
        int crops = farmingBlock.indexOf("CropRegistry.bySeed", flowers);
        assertTrue(care >= 0 && care < flowers && flowers < grapes && grapes < crops);

        String blockRegistry = source("registry/BlockRegistry.java");
        String entityRegistry = source("registry/BlockEntityRegistry.java");
        String itemRegistry = source("registry/ItemRegistry.java");
        assertTrue(blockRegistry.contains("FLOWER_BLOCK = BLOCKS.register("));
        assertTrue(blockRegistry.contains("\"flower_block\""));
        assertTrue(entityRegistry.contains("FLOWER_BLOCK_BE"));
        assertTrue(entityRegistry.contains("\"flower_block_be\""));
        assertFalse(itemRegistry.contains("flowerContentItem(\"flower_block\")"));
        String blockEntity = source("block/entity/FlowerBlockEntity.java");
        assertTrue(blockEntity.contains("getUpdateTag("));
        assertTrue(blockEntity.contains("getUpdatePacket("));
        assertTrue(blockEntity.contains("ClientboundBlockEntityDataPacket.create(this)"));

        Path blockstatePath = PROJECT.resolve("src/main/resources/assets/britannia_mod/blockstates/flower_block.json");
        JsonObject blockstate = JsonParser.parseString(Files.readString(blockstatePath)).getAsJsonObject();
        assertEquals(6, blockstate.getAsJsonObject("variants").size());
        for (int hydration = 0; hydration <= 5; hydration++) {
            Path model = PROJECT.resolve("src/main/resources/assets/britannia_mod/models/block/flower_block_"
                    + hydration + ".json");
            JsonObject modelJson = JsonParser.parseString(Files.readString(model)).getAsJsonObject();
            assertEquals("britannia_mod:block/crops/farming_block_" + hydration,
                    modelJson.get("parent").getAsString());
        }

        String flowerBlockSource = source("block/FlowerBlock.java");
        assertTrue(flowerBlockSource.contains("randomTick("));
        assertFalse(flowerBlockSource.contains("tryHarvest"));
        assertFalse(flowerBlockSource.contains("SWORD_CUTBACK"));
        assertFalse(Files.exists(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/client/renderer/FlowerBlockEntityRenderer.java")));
    }

    private FlowerPlantingService.Outcome execute(FakeAccess access, AtomicInteger selectorCalls) {
        WeightedFlowerColorSelector delegate = new WeightedFlowerColorSelector(registry);
        FlowerColorSelector countingSelector = (species, context, random, reason) -> {
            selectorCalls.incrementAndGet();
            return delegate.select(species, context, random, reason);
        };
        return FlowerPlantingService.execute(access, registry, countingSelector);
    }

    private static String source(String relative) throws IOException {
        return Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod").resolve(relative),
                StandardCharsets.UTF_8
        );
    }

    private enum InvalidTarget {
        CLIENT,
        WRONG_BLOCK_OR_MISSING_ENTITY,
        OCCUPIED,
        INVALID_SOIL_SNAPSHOT
    }

    private enum FailurePoint {
        NONE,
        CAPTURE,
        REPLACE_FALSE,
        INIT_FALSE,
        VERIFY_FALSE,
        INIT,
        SYNC,
        CONSUME
    }

    private static final class FakeAccess implements FlowerPlantingService.PlantingAccess {
        private final ResourceLocation heldItemId;
        private boolean taggedFlowerSeed;
        private boolean logicalServer = true;
        private boolean validTarget = true;
        private boolean occupied;
        private FlowerSoilSnapshot soil = FlowerSoilSnapshot.privateSoil(3, 1, 0.2f, 0.4f, 0.6f, 0.8f);
        private FlowerPlantingOrigin origin = FlowerPlantingOrigin.PLAYER;
        private final Optional<UUID> planterUuid = Optional.of(
                UUID.fromString("4eaf6428-3fb5-4099-9dc7-c4a5eb619132")
        );
        private final FlowerRegionProvenance provenance =
                new FlowerRegionProvenance("Britain", FarmingClimate.TEMPERATE);
        private FailurePoint failure = FailurePoint.NONE;
        private boolean replaced;
        private boolean initialized;
        private boolean synchronizedState;
        private int consumedSeeds;
        private int feedbackCalls;
        private int rollbackCalls;
        private Optional<FlowerPersistentState> flowerState = Optional.empty();

        private FakeAccess(ResourceLocation heldItemId) {
            this.heldItemId = heldItemId;
        }

        private boolean mutated() {
            return replaced || initialized || synchronizedState || consumedSeeds != 0 || feedbackCalls != 0;
        }

        @Override
        public ResourceLocation heldItemId() {
            return heldItemId;
        }

        @Override
        public boolean heldItemInFlowerSeedTag() {
            return taggedFlowerSeed;
        }

        @Override
        public boolean logicalServer() {
            return logicalServer;
        }

        @Override
        public boolean targetIsFarmingBlock() {
            return validTarget;
        }

        @Override
        public boolean targetOccupied() {
            return occupied;
        }

        @Override
        public FlowerSoilSnapshot captureSoilSnapshot() {
            fail(FailurePoint.CAPTURE);
            return soil;
        }

        @Override
        public FlowerPlantingOrigin plantingOrigin() {
            return origin;
        }

        @Override
        public Optional<UUID> planterUuid() {
            return planterUuid;
        }

        @Override
        public FlowerRegionProvenance regionProvenance() {
            return provenance;
        }

        @Override
        public FarmingClimate currentClimate() {
            return FarmingClimate.TEMPERATE;
        }

        @Override
        public int altitude() {
            return 70;
        }

        @Override
        public RandomSource random() {
            return RandomSource.create(123456L);
        }

        @Override
        public boolean replaceWithFlower(FlowerSoilSnapshot soil) {
            if (failure == FailurePoint.REPLACE_FALSE) {
                return false;
            }
            replaced = true;
            return true;
        }

        @Override
        public boolean initializeFlower(FlowerPersistentState state) {
            if (failure == FailurePoint.INIT_FALSE) {
                return false;
            }
            fail(FailurePoint.INIT);
            initialized = true;
            flowerState = Optional.of(state);
            return true;
        }

        @Override
        public boolean verifyFlowerState(FlowerPersistentState state) {
            return failure != FailurePoint.VERIFY_FALSE && flowerState.filter(state::equals).isPresent();
        }

        @Override
        public void synchronizeFlower() {
            fail(FailurePoint.SYNC);
            synchronizedState = true;
        }

        @Override
        public void consumeOneSeed() {
            fail(FailurePoint.CONSUME);
            consumedSeeds++;
        }

        @Override
        public void applyPlantingFeedback() {
            feedbackCalls++;
        }

        @Override
        public boolean rollback() {
            rollbackCalls++;
            replaced = false;
            initialized = false;
            synchronizedState = false;
            consumedSeeds = 0;
            feedbackCalls = 0;
            flowerState = Optional.empty();
            return true;
        }

        @Override
        public String targetDescription() {
            return "fake-farm";
        }

        private void fail(FailurePoint point) {
            if (failure == point) {
                throw new IllegalStateException("simulated " + point);
            }
        }
    }
}
