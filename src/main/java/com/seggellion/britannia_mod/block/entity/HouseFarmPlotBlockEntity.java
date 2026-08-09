package com.seggellion.britannia_mod.block.entity;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.FarmingClimate;
import com.seggellion.britannia_mod.farming.FarmingClimateResolver;
import com.seggellion.britannia_mod.farming.FlowerDefinition;
import com.seggellion.britannia_mod.farming.FlowerPersistentState;
import com.seggellion.britannia_mod.farming.FlowerPlantingContext;
import com.seggellion.britannia_mod.farming.FlowerPlantingOrigin;
import com.seggellion.britannia_mod.farming.FlowerRegionProvenance;
import com.seggellion.britannia_mod.farming.FlowerRegistry;
import com.seggellion.britannia_mod.farming.FlowerSoilSnapshot;
import com.seggellion.britannia_mod.farming.HouseFarmPlotAssignment;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Adds only the house plot's durable assignment to the existing crop and flower state machines.
 * Harvest never clears this assignment; only FarmingHoeItem transitions it to CLEARED.
 */
public final class HouseFarmPlotBlockEntity extends FlowerBlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ASSIGNMENT_KEY = "HouseFarmPlotAssignment";

    private HouseFarmPlotAssignment assignment = HouseFarmPlotAssignment.uninitialized();

    public HouseFarmPlotBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.HOUSE_FARM_PLOT_BE.get(), pos, blockState);
    }

    public HouseFarmPlotAssignment assignment() {
        return assignment;
    }

    public boolean isCleared() {
        return assignment.state() == HouseFarmPlotAssignment.State.CLEARED;
    }

    public boolean isAssigned() {
        return assignment.state() == HouseFarmPlotAssignment.State.ASSIGNED;
    }

    public void assignCrop(CropDefinition crop) {
        if (crop == null || !hasCrop() || !crop.id().equals(getPlantedCropId())) {
            return;
        }
        assignment = HouseFarmPlotAssignment.assigned(
                HouseFarmPlotAssignment.Kind.CROP,
                canonicalCropId(crop)
        );
        setChangedAndSync();
    }

    public void assignFlower(ResourceLocation speciesId) {
        if (speciesId == null || flowerState().filter(state -> speciesId.equals(state.speciesId())).isEmpty()) {
            return;
        }
        assignment = HouseFarmPlotAssignment.assigned(HouseFarmPlotAssignment.Kind.FLOWER, speciesId);
        setChangedAndSync();
    }

    public boolean assignmentMatches(CropDefinition crop) {
        return crop != null && assignment.isAssignedTo(
                HouseFarmPlotAssignment.Kind.CROP,
                canonicalCropId(crop)
        );
    }

    /** Initializes new and legacy plots exactly once with the existing canonical poppy. */
    public boolean initializeDefaultPoppy(ServerLevel level) {
        if (level == null || assignment.state() != HouseFarmPlotAssignment.State.UNINITIALIZED
                || hasCrop() || isInitialized()) {
            return false;
        }
        FlowerRegistry registry = FlowerRegistry.initial();
        FlowerDefinition poppy = registry.byId(FlowerRegistry.POPPY).orElse(null);
        if (poppy == null) {
            assignment = HouseFarmPlotAssignment.cleared();
            setChangedAndSync();
            return false;
        }

        FlowerSoilSnapshot soil = exportFlowerConversionSnapshot(getBlockState()).soil();
        FarmingClimate climate = FarmingClimateResolver.resolve(level, worldPosition);
        FlowerPlantingContext context = new FlowerPlantingContext(
                soil,
                new FlowerRegionProvenance(
                        FarmingClimateResolver.findRegionAt(level, worldPosition)
                                .map(region -> region.name)
                                .orElse(FlowerRegionProvenance.UNKNOWN_REGION),
                        climate
                ),
                climate,
                worldPosition.getY(),
                FlowerPlantingOrigin.WORLD_GENERATION,
                true
        );
        FlowerPersistentState initial = FlowerPersistentState.newlyPlanted(
                poppy, registry.fallbackColor(poppy), context, Optional.empty(), registry
        );
        if (!initialize(initial)) {
            assignment = HouseFarmPlotAssignment.cleared();
            setChangedAndSync();
            return false;
        }
        assignFlower(poppy.id());
        return true;
    }

    /** Clears crop/flower presentation in place and preserves an intentional empty state. */
    public boolean clearPersistentAssignment(ServerLevel level) {
        boolean presentationAlreadyEmpty = !hasCrop() && !isInitialized()
                && (getStoredSeed() == null || getStoredSeed().isBlank());
        if (level == null || assignment.state() == HouseFarmPlotAssignment.State.CLEARED
                && presentationAlreadyEmpty) {
            return false;
        }

        FlowerSoilSnapshot flowerSoil = flowerState().map(FlowerPersistentState::soil).orElse(null);
        CropDefinition crop = CropRegistry.byId(getPlantedCropId()).orElse(null);
        cleanupTreeRoot(level, crop);
        if (isInitialized()) {
            clearFlowerState();
        }
        if (hasCrop()) {
            clearCrop();
        }
        if (getStoredSeed() != null && !getStoredSeed().isBlank()) {
            clearStoredSeed();
        }
        if (flowerSoil != null) {
            restoreUprootedFlowerSoil(flowerSoil);
        }

        assignment = HouseFarmPlotAssignment.cleared();
        BlockState state = getBlockState();
        int hydration = flowerSoil == null ? getHydration() : flowerSoil.hydration();
        int fertilizer = flowerSoil == null ? state.getValue(FarmingBlock.FERTILIZER) : flowerSoil.fertilizerLevel();
        level.setBlock(worldPosition, state
                .setValue(FarmingBlock.HAS_SEEDS, false)
                .setValue(FarmingBlock.HYDRATION, hydration)
                .setValue(FarmingBlock.FERTILIZER, fertilizer), 3);
        setChangedAndSync();
        return true;
    }

    public void rollbackFlowerPlanting(FlowerConversionSnapshot snapshot) {
        if (isInitialized()) {
            clearFlowerState();
        }
        restoreFlowerConversionSnapshot(snapshot);
        assignment = HouseFarmPlotAssignment.cleared();
        setChangedAndSync();
    }

    public void cleanupTreeRoot(ServerLevel level, CropDefinition crop) {
        if (level == null || crop == null || !crop.treeCrop()) {
            return;
        }
        BlockPos rootPos = worldPosition.above();
        BlockEntity candidate = level.getBlockEntity(rootPos);
        if (candidate instanceof OrangeTreeRootBlockEntity root
                && crop.id().equals(root.getTreeTypeId())
                && root.getSoilPos().equals(worldPosition)) {
            level.removeBlock(rootPos, false);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (assignment.state() == HouseFarmPlotAssignment.State.UNINITIALIZED) {
            initializeDefaultPoppy(serverLevel);
            return;
        }
        if (!loadedAssignmentIsConsistent()) {
            LOGGER.warn("[house farm plot] Invalid or incomplete assigned plant at {}; leaving plot cleared", worldPosition);
            clearPersistentAssignment(serverLevel);
        }
    }

    private boolean loadedAssignmentIsConsistent() {
        if (assignment.state() == HouseFarmPlotAssignment.State.CLEARED) {
            return !hasCrop() && !isInitialized()
                    && (getStoredSeed() == null || getStoredSeed().isBlank());
        }
        ResourceLocation plantId = assignment.plantId().orElse(null);
        HouseFarmPlotAssignment.Kind kind = assignment.kind().orElse(null);
        if (plantId == null || kind == null) {
            return false;
        }
        if (kind == HouseFarmPlotAssignment.Kind.FLOWER) {
            return FlowerRegistry.initial().byId(plantId).isPresent()
                    && flowerState().filter(state -> plantId.equals(state.speciesId())).isPresent()
                    && !hasCrop();
        }
        CropDefinition crop = CropRegistry.byId(plantId.getPath()).orElse(null);
        return BritanniaMod.MODID.equals(plantId.getNamespace())
                && crop != null
                && crop.id().equals(getPlantedCropId())
                && !isInitialized();
    }

    private static ResourceLocation canonicalCropId(CropDefinition crop) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, crop.id());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(ASSIGNMENT_KEY, assignment.toTag());
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        assignment = tag.contains(ASSIGNMENT_KEY)
                ? HouseFarmPlotAssignment.fromTag(tag.getCompound(ASSIGNMENT_KEY))
                : HouseFarmPlotAssignment.uninitialized();
    }
}
