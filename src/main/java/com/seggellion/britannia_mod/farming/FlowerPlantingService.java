package com.seggellion.britannia_mod.farming;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.FlowerBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.block.entity.FlowerBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Owns the complete validated FarmingBlock-to-FlowerBlock transaction. */
public final class FlowerPlantingService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FlowerRegistry REGISTRY = FlowerRegistry.initial();
    private static final FlowerColorSelector COLOR_SELECTOR = new WeightedFlowerColorSelector(REGISTRY);

    private FlowerPlantingService() {
    }

    public enum Outcome {
        NOT_A_FLOWER_SEED,
        REJECTED,
        PLANTED
    }

    public static Outcome tryPlant(
            Level level,
            BlockPos pos,
            BlockState farmingState,
            Player player,
            ItemStack heldStack
    ) {
        return execute(new LevelPlantingAccess(level, pos, farmingState, player, heldStack), REGISTRY, COLOR_SELECTOR);
    }

    static Outcome execute(PlantingAccess access, FlowerRegistry registry, FlowerColorSelector selector) {
        Objects.requireNonNull(access, "Flower planting access is required");
        Objects.requireNonNull(registry, "Flower registry is required");
        Objects.requireNonNull(selector, "Flower color selector is required");

        FlowerDefinition definition = registry.bySeedItemId(access.heldItemId()).orElse(null);
        if (definition == null) {
            return access.heldItemInFlowerSeedTag() ? Outcome.REJECTED : Outcome.NOT_A_FLOWER_SEED;
        }
        if (!access.logicalServer()) {
            return Outcome.REJECTED;
        }

        boolean replacementAttempted = false;
        try {
            if (!access.targetIsFarmingBlock() || access.targetOccupied()) {
                return Outcome.REJECTED;
            }
            validateDefinitionForPlanting(definition, access.heldItemId(), registry);
            FarmingCultivationGate.Evaluation eligibility = FarmingCultivationGate.evaluateResolved(
                    new FarmingSkillRequirementResolver.ResolvedRequirement(
                            definition.id().toString(), definition
                    ),
                    access.cultivationSubject()
            );
            if (!eligibility.permitsPlanting()) {
                access.applyCultivationDenial(eligibility);
                return Outcome.REJECTED;
            }

            FlowerSoilSnapshot soil = access.captureSoilSnapshot();
            FlowerPlantingOrigin origin = Objects.requireNonNull(access.plantingOrigin(), "Planting origin is required");
            Optional<UUID> planterUuid = Objects.requireNonNull(access.planterUuid(), "Planter UUID optional is required");
            FlowerRegionProvenance provenance = Objects.requireNonNull(access.regionProvenance(), "Planting provenance is required");
            FarmingClimate climate = Objects.requireNonNull(access.currentClimate(), "Current planting climate is required");
            FlowerPlantingContext context = new FlowerPlantingContext(
                    soil,
                    provenance,
                    climate,
                    access.altitude(),
                    origin,
                    true
            );

            // This is the only random call in the transaction, after every
            // non-random validation and immediately before persistent state creation.
            FlowerColor selectedColor = selector.select(
                    definition,
                    context,
                    access.random(),
                    FlowerColorSelectionReason.SUCCESSFUL_SERVER_PLANTING
            );
            FlowerPersistentState proposedState = FlowerPersistentState.newlyPlanted(
                    definition,
                    selectedColor,
                    context,
                    planterUuid,
                    registry
            );
            validateInitializedState(proposedState, definition, registry);

            replacementAttempted = true;
            if (!access.replaceWithFlower(soil)
                    || !access.initializeFlower(proposedState)
                    || !access.verifyFlowerState(proposedState)) {
                rollbackOrWarn(access);
                return Outcome.REJECTED;
            }

            access.synchronizeFlower();
            access.consumeOneSeed();
            access.applyPlantingFeedback();
            LOGGER.debug("[flower planting] planted species={} tint={} origin={} protected={} target={}",
                    proposedState.speciesId(), proposedState.color().hex(), proposedState.plantingOrigin(),
                    proposedState.protectedFlower(), access.targetDescription());
            return Outcome.PLANTED;
        } catch (RuntimeException exception) {
            if (replacementAttempted) {
                rollbackOrWarn(access);
            }
            LOGGER.warn("[flower planting] Rejected unexpected transaction failure at {}: {}: {}",
                    access.targetDescription(), exception.getClass().getSimpleName(), exception.getMessage());
            return Outcome.REJECTED;
        }
    }

    private static void validateDefinitionForPlanting(
            FlowerDefinition definition,
            ResourceLocation heldItemId,
            FlowerRegistry registry
    ) {
        if (!definition.seedItemId().equals(heldItemId)) {
            throw new IllegalArgumentException("Flower seed mapping does not match the held item");
        }
        if (definition.naturalMaximumStage() < 1
                || definition.absoluteMaximumStage() < definition.naturalMaximumStage()
                || definition.absoluteMaximumStage() > 7
                || definition.palette().isEmpty()) {
            throw new IllegalArgumentException("Flower species has invalid stage or palette contracts: " + definition.id());
        }
        boolean fallbackPresent = false;
        for (FlowerPaletteEntry entry : definition.palette()) {
            if (entry.weight() <= 0 || registry.color(entry.colorId()).isEmpty()) {
                throw new IllegalArgumentException("Flower species has an invalid palette entry: " + definition.id());
            }
            fallbackPresent |= entry.colorId().equals(definition.fallbackColorId());
        }
        if (!fallbackPresent || registry.color(definition.fallbackColorId()).isEmpty()) {
            throw new IllegalArgumentException("Flower species has an invalid fallback color: " + definition.id());
        }
    }

    private static void validateInitializedState(
            FlowerPersistentState state,
            FlowerDefinition definition,
            FlowerRegistry registry
    ) {
        if (!state.speciesId().equals(definition.id())
                || state.growthStage() != 1
                || !registry.isAllowedColor(definition, state.color())
                || state.protectedFlower() != (state.plantingOrigin() == FlowerPlantingOrigin.ADMIN)) {
            throw new IllegalArgumentException("Complete initialized flower state failed validation");
        }
    }

    private static void rollbackOrWarn(PlantingAccess access) {
        if (!access.rollback()) {
            LOGGER.error("[flower planting] Failed to restore FarmingBlock transaction state at {}",
                    access.targetDescription());
        }
    }

    interface PlantingAccess {
        ResourceLocation heldItemId();

        boolean heldItemInFlowerSeedTag();

        boolean logicalServer();

        boolean targetIsFarmingBlock();

        boolean targetOccupied();

        FarmingCultivationGate.Subject cultivationSubject();

        void applyCultivationDenial(FarmingCultivationGate.Evaluation evaluation);

        FlowerSoilSnapshot captureSoilSnapshot();

        FlowerPlantingOrigin plantingOrigin();

        Optional<UUID> planterUuid();

        FlowerRegionProvenance regionProvenance();

        FarmingClimate currentClimate();

        int altitude();

        RandomSource random();

        boolean replaceWithFlower(FlowerSoilSnapshot soil);

        boolean initializeFlower(FlowerPersistentState state);

        boolean verifyFlowerState(FlowerPersistentState state);

        void synchronizeFlower();

        void consumeOneSeed();

        void applyPlantingFeedback();

        boolean rollback();

        String targetDescription();
    }

    private static final class LevelPlantingAccess implements PlantingAccess {
        private final Level level;
        private final BlockPos pos;
        private final BlockState originalState;
        private final Player player;
        private final ItemStack heldStack;
        private final int originalStackCount;
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private Optional<FarmingBlockEntity.FlowerConversionSnapshot> rollbackSnapshot = Optional.empty();

        private LevelPlantingAccess(Level level, BlockPos pos, BlockState originalState, Player player, ItemStack heldStack) {
            this.level = Objects.requireNonNull(level, "Flower planting level is required");
            this.pos = Objects.requireNonNull(pos, "Flower planting position is required");
            this.originalState = Objects.requireNonNull(originalState, "Flower planting blockstate is required");
            this.player = Objects.requireNonNull(player, "Flower planting player is required");
            this.heldStack = Objects.requireNonNull(heldStack, "Flower planting item stack is required");
            this.originalStackCount = heldStack.getCount();
        }

        @Override
        public ResourceLocation heldItemId() {
            return BuiltInRegistries.ITEM.getKey(heldStack.getItem());
        }

        @Override
        public boolean heldItemInFlowerSeedTag() {
            return heldStack.is(ModTags.Items.FLOWER_SEEDS);
        }

        @Override
        public boolean logicalServer() {
            return !level.isClientSide;
        }

        @Override
        public boolean targetIsFarmingBlock() {
            return originalState.getBlock() == BlockRegistry.FARMING_BLOCK.get()
                    && level.getBlockState(pos).getBlock() == BlockRegistry.FARMING_BLOCK.get()
                    && level.getBlockEntity(pos) instanceof FarmingBlockEntity;
        }

        @Override
        public boolean targetOccupied() {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            return originalState.getValue(FarmingBlock.HAS_SEEDS)
                    || !(blockEntity instanceof FarmingBlockEntity farming)
                    || farming.hasCrop()
                    || farming.getStoredSeed() != null && !farming.getStoredSeed().isBlank();
        }

        @Override
        public FarmingCultivationGate.Subject cultivationSubject() {
            return FarmingCultivationGate.subject(player);
        }

        @Override
        public void applyCultivationDenial(FarmingCultivationGate.Evaluation evaluation) {
            FarmingCultivationGate.sendDenialFeedback(player, evaluation);
            FarmingCultivationGate.synchronizeDeniedInteraction(player, level, pos);
        }

        @Override
        public FlowerSoilSnapshot captureSoilSnapshot() {
            if (!(level.getBlockEntity(pos) instanceof FarmingBlockEntity farming)) {
                throw new IllegalStateException("FarmingBlockEntity disappeared before flower snapshot");
            }
            FarmingBlockEntity.FlowerConversionSnapshot snapshot = farming.exportFlowerConversionSnapshot(originalState);
            rollbackSnapshot = Optional.of(snapshot);
            return snapshot.soil();
        }

        @Override
        public FlowerPlantingOrigin plantingOrigin() {
            return FlowerProtectionService.isAdministrator(player)
                    ? FlowerPlantingOrigin.ADMIN
                    : FlowerPlantingOrigin.PLAYER;
        }

        @Override
        public Optional<UUID> planterUuid() {
            return Optional.of(player.getUUID());
        }

        @Override
        public FlowerRegionProvenance regionProvenance() {
            FarmingClimate climate = currentClimate();
            String regionName = FarmingClimateResolver.findRegionAt(level, pos)
                    .map(region -> region.name)
                    .orElse(FlowerRegionProvenance.UNKNOWN_REGION);
            return new FlowerRegionProvenance(regionName, climate);
        }

        @Override
        public FarmingClimate currentClimate() {
            return FarmingClimateResolver.resolve(level, pos);
        }

        @Override
        public int altitude() {
            return pos.getY();
        }

        @Override
        public RandomSource random() {
            return level.getRandom();
        }

        @Override
        public boolean replaceWithFlower(FlowerSoilSnapshot soil) {
            BlockState flowerState = BlockRegistry.FLOWER_BLOCK.get().defaultBlockState()
                    .setValue(FlowerBlock.HYDRATION, soil.hydration())
                    .setValue(FlowerBlock.FERTILIZER, soil.fertilizerLevel());
            return level.setBlock(pos, flowerState, 3);
        }

        @Override
        public boolean initializeFlower(FlowerPersistentState state) {
            return level.getBlockEntity(pos) instanceof FlowerBlockEntity flower
                    && flower.initialize(state);
        }

        @Override
        public boolean verifyFlowerState(FlowerPersistentState state) {
            return level.getBlockEntity(pos) instanceof FlowerBlockEntity flower
                    && flower.flowerState().filter(state::equals).isPresent();
        }

        @Override
        public void synchronizeFlower() {
            if (!(level.getBlockEntity(pos) instanceof FlowerBlockEntity flower)) {
                throw new IllegalStateException("FlowerBlockEntity disappeared before synchronization");
            }
            flower.setChangedAndSync();
        }

        @Override
        public void consumeOneSeed() {
            if (!player.getAbilities().instabuild) {
                heldStack.shrink(1);
            }
        }

        @Override
        public void applyPlantingFeedback() {
            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
        }

        @Override
        public boolean rollback() {
            heldStack.setCount(originalStackCount);
            FarmingBlockEntity.FlowerConversionSnapshot snapshot = rollbackSnapshot.orElse(null);
            if (snapshot == null) {
                return false;
            }
            if (!level.getBlockState(pos).equals(snapshot.blockState())
                    && !level.setBlock(pos, snapshot.blockState(), 3)) {
                return false;
            }
            if (!(level.getBlockEntity(pos) instanceof FarmingBlockEntity restored)) {
                return false;
            }
            restored.restoreFlowerConversionSnapshot(snapshot);
            return true;
        }

        @Override
        public String targetDescription() {
            return pos.toShortString();
        }
    }
}
