package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.block.TrellisBlock;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropEnvironmentRules;
import com.seggellion.britannia_mod.farming.CropGrowthContext;
import com.seggellion.britannia_mod.farming.CropQualityCalculator;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.FarmingClimate;
import com.seggellion.britannia_mod.farming.FarmingClimateResolver;
import com.seggellion.britannia_mod.farming.FarmingSkill;
import com.seggellion.britannia_mod.farming.FarmingSoilCare;
import com.seggellion.britannia_mod.farming.FlowerSoilSnapshot;
import com.seggellion.britannia_mod.farming.TallCropSupport;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.seggellion.britannia_mod.farming.FlowerProtectionService;
import com.seggellion.britannia_mod.farming.GrapeVarietyAgronomy;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class FarmingBlockEntity extends BlockEntity {
    public static final int MAX_HYDRATION = 5;
    public static final int MAX_FERTILE_HARVESTS = 5;
    public static final int UNTRACKED_FERTILE_HARVESTS = -1;
    public static final long COMMUNITY_SEED_WINDOW_TICKS = 1200L;
    private static final String OWNER_ID_KEY = "OwnerUUID";
    private static final String VISUAL_ROTATION_KEY = "VisualRotation";
    private static final String REMAINING_FERTILE_HARVESTS_KEY = "RemainingFertileHarvests";
    private static final int GROWTH_AGE_DATA_VERSION = 3;

    // Kept under the first-pass field names for safe world migration.
    private float nitrogen = 0.0f;
    private float phosphorus = 0.0f;
    private float potassium = 0.0f;
    private float organicMatter = 0.0f;

    private int hydration = 0;
    private String storedSeedVariety = "";
    private String plantedCropId = "";
    private float growthProgress = 0.0f;
    private int growthStage = 0;
    private int tickProgress = 0;
    private long rootEstablishedGameTime = -1L;
    private boolean mature = false;
    private boolean growthBlocked = false;
    private boolean communityPlot = false;
    private long seedableUntilGameTime = 0L;
    // Missing legacy NBT remains unlimited. Only canonical fertilized-dirt application tracks 5..0.
    private int remainingFertileHarvests = UNTRACKED_FERTILE_HARVESTS;

    /**
     * Whoever tilled this plot, or null for a plot nobody has claimed. Community plots ignore it
     * entirely. Stored as a UUID rather than a name so that a rename cannot transfer a farm, and
     * kept server-side only: the client is never told who owns a plot because nothing it renders
     * depends on that.
     */
    @Nullable
    private UUID ownerId = null;

    /**
     * Quarter turns clockwise applied to the crop's model, as set with the interior decorator tool.
     * Only the arbor is large and asymmetric enough for this to read, but it costs nothing to let
     * any crop carry it. Server-authoritative and synced like the rest of the plot's state.
     */
    private int visualRotationQuarters = 0;

    public FarmingBlockEntity(BlockPos pos, BlockState blockState) {
        this(BlockEntityRegistry.FARMING_BLOCK_BE.get(), pos, blockState);
    }

    protected FarmingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public void setStoredSeed(String variety) {
        this.storedSeedVariety = variety;
        if (communityPlot && variety != null && !variety.isBlank()) {
            this.seedableUntilGameTime = 0L;
        }
        setChangedAndSync();
    }

    public String getStoredSeed() {
        return storedSeedVariety;
    }

    public void clearStoredSeed() {
        this.storedSeedVariety = "";
        setChangedAndSync();
    }

    public boolean hasCrop() {
        return plantedCropId != null && !plantedCropId.isBlank();
    }

    public String getPlantedCropId() {
        return plantedCropId;
    }

    public int getGrowthStage() {
        return growthStage;
    }

    public float getGrowthProgress() {
        return growthProgress;
    }

    public boolean isMature() {
        return mature;
    }

    public boolean isGrowthBlocked() {
        return growthBlocked;
    }

    public long getRootEstablishedGameTime() {
        return rootEstablishedGameTime;
    }

    public int getRootAgeDays() {
        if (level == null || rootEstablishedGameTime < 0L) {
            return 0;
        }
        return Math.max(0, (int) ((level.getGameTime() - rootEstablishedGameTime) / 24000L));
    }

    public int getRootAgeQualityBonus(CropDefinition crop) {
        return CropQualityCalculator.rootAgeQualityBonus(crop, getRootAgeDays());
    }

    public int cropTier() {
        return CropRegistry.byId(plantedCropId).map(CropDefinition::cropTier).orElse(1);
    }

    public void plant(CropDefinition crop) {
        plant(crop, "");
    }

    public void plant(CropDefinition crop, String cropVariant) {
        this.plantedCropId = crop.id();
        this.storedSeedVariety = cropVariant == null ? "" : cropVariant;
        this.growthProgress = 0.0f;
        this.growthStage = 0;
        this.tickProgress = 0;
        this.mature = false;
        this.growthBlocked = false;
        this.rootEstablishedGameTime = crop.usesRootAgeQualityBonus() && level != null ? level.getGameTime() : -1L;
        if (communityPlot) {
            this.seedableUntilGameTime = 0L;
        }
        if (level != null && !level.isClientSide) {
            TallCropSupport.update(level, worldPosition, crop, growthStage);
        }
        setChangedAndSync();
    }

    public void clearCrop() {
        CropDefinition oldCrop = CropRegistry.byId(plantedCropId).orElse(null);
        if (level != null && !level.isClientSide && oldCrop != null) {
            TallCropSupport.clear(level, worldPosition, oldCrop);
        }
        this.plantedCropId = "";
        this.growthProgress = 0.0f;
        this.growthStage = 0;
        this.tickProgress = 0;
        this.rootEstablishedGameTime = -1L;
        this.mature = false;
        this.growthBlocked = false;
        setChangedAndSync();
    }

    /**
     * Seeds a plot from a plant that already existed in another form, preserving how far along it
     * was. Only migration uses this: ordinary planting always starts at stage zero, and letting
     * arbitrary callers choose a stage would make the crop's progress ambiguous.
     */
    public void plantMigratedCrop(CropDefinition crop, String cropVariant, int migratedGrowthStage) {
        plant(crop, cropVariant);
        int stage = Math.max(0, Math.min(crop.maxGrowthAge(), migratedGrowthStage));
        this.growthStage = stage;
        this.growthProgress = stage <= 0 ? 0.0f : Math.min(0.99f, stage / (float) crop.visualAgeCount());
        this.mature = crop.isMatureAge(stage) && stage >= crop.maxGrowthAge();
        if (this.mature) {
            this.growthProgress = 1.0f;
        }
        if (level != null && !level.isClientSide) {
            TallCropSupport.update(level, worldPosition, crop, this.growthStage);
        }
        setChangedAndSync();
    }

    public void regrowAfterHarvest(CropDefinition crop) {
        this.plantedCropId = crop.id();
        int regrowthAge = crop.clampedPostHarvestRegrowthAge();
        this.growthStage = regrowthAge;
        this.growthProgress = regrowthAge <= 0 ? 0.0f : Math.min(0.99f, regrowthAge / (float) crop.visualAgeCount());
        this.tickProgress = 0;
        this.mature = false;
        this.growthBlocked = false;
        if (crop.usesRootAgeQualityBonus() && rootEstablishedGameTime < 0L && level != null) {
            this.rootEstablishedGameTime = level.getGameTime();
        }
        if (level != null && !level.isClientSide) {
            TallCropSupport.update(level, worldPosition, crop, growthStage);
        }
        setChangedAndSync();
    }

    public boolean addNutrients(float boneMeal, float turquoise, float sulphurousAsh, float rottenFlesh) {
        float oldN = this.nitrogen;
        float oldP = this.phosphorus;
        float oldK = this.potassium;
        float oldOm = this.organicMatter;
        this.nitrogen = FarmingSoilCare.addNormalized(this.nitrogen, boneMeal);
        this.phosphorus = FarmingSoilCare.addNormalized(this.phosphorus, turquoise);
        this.potassium = FarmingSoilCare.addNormalized(this.potassium, sulphurousAsh);
        this.organicMatter = FarmingSoilCare.addNormalized(this.organicMatter, rottenFlesh);
        boolean changed = oldN != nitrogen || oldP != phosphorus || oldK != potassium || oldOm != organicMatter;
        if (changed) {
            setChangedAndSync();
        }
        return changed;
    }

    public float getBoneMealNutrient() {
        return nitrogen;
    }

    public float getTurquoiseNutrient() {
        return phosphorus;
    }

    public float getSulphurousAshNutrient() {
        return potassium;
    }

    public float getRottenFleshNutrient() {
        return organicMatter;
    }

    public float nutrientFit(CropDefinition crop) {
        return CropQualityCalculator.weightedNutrientFit(crop, nitrogen, phosphorus, potassium, organicMatter);
    }

    public void setHydration(int amount) {
        int old = this.hydration;
        this.hydration = Math.max(0, Math.min(MAX_HYDRATION, amount));
        if (old != this.hydration) {
            setChangedAndSync();
        }
    }

    public int getHydration() {
        return hydration;
    }

    public boolean water(int amount) {
        int old = hydration;
        setHydration(hydration + amount);
        return hydration > old;
    }

    /** Starts the finite lifecycle owned by one canonical fertilized-dirt application. */
    public void initializeFertileHarvests() {
        remainingFertileHarvests = MAX_FERTILE_HARVESTS;
        setChangedAndSync();
    }

    /**
     * Records exactly one already-confirmed server harvest. Untracked legacy plots remain unlimited,
     * and an exhausted plot can never underflow even if a stale interaction reaches this method.
     *
     * @return the remaining count, or {@link #UNTRACKED_FERTILE_HARVESTS} for a legacy plot
     */
    public int consumeSuccessfulFertileHarvest() {
        if (remainingFertileHarvests <= 0) {
            return remainingFertileHarvests;
        }
        remainingFertileHarvests--;
        setChangedAndSync();
        return remainingFertileHarvests;
    }

    public int getRemainingFertileHarvests() {
        return remainingFertileHarvests;
    }

    public boolean hasTrackedFertility() {
        return remainingFertileHarvests >= 0;
    }

    public boolean hasRemainingFertility() {
        return remainingFertileHarvests > 0;
    }

    public boolean isFertilityExhausted() {
        return remainingFertileHarvests == 0;
    }

    public void startCommunitySeedWindow(long deadlineGameTime) {
        this.communityPlot = true;
        this.ownerId = null;
        this.seedableUntilGameTime = deadlineGameTime;
        setChangedAndSync();
    }

    /** Turns the crop's model a quarter turn clockwise, wrapping back to its original heading. */
    public void rotateVisualClockwise() {
        this.visualRotationQuarters = Math.floorMod(visualRotationQuarters + 1, 4);
        setChangedAndSync();
    }

    public int getVisualRotationQuarters() {
        return visualRotationQuarters;
    }

    /**
     * The yaw the renderer should add, in the same sense as a blockstate's {@code y} rotation: that
     * form is negated about the Y axis, so a clockwise quarter turn is -90 degrees here.
     */
    public float visualRotationDegrees() {
        return -90.0f * visualRotationQuarters;
    }

    /** Records the tiller of a private plot. A community plot never takes an owner. */
    public void setOwner(@Nullable UUID owner) {
        this.ownerId = communityPlot ? null : owner;
        setChanged();
    }

    @Nullable
    public UUID getOwner() {
        return communityPlot ? null : ownerId;
    }

    public boolean hasOwner() {
        return getOwner() != null;
    }

    /**
     * Community plots are open to everyone, and an unclaimed private plot stays open so that plots
     * placed before ownership existed do not silently lock their owners out. Administrators are
     * authorized through the same path every other protected Britannia block uses.
     */
    public boolean mayPlant(@Nullable Player player) {
        if (communityPlot || ownerId == null) {
            return true;
        }
        if (player == null) {
            return false;
        }
        return ownerId.equals(player.getUUID()) || FlowerProtectionService.isAdministrator(player);
    }

    public boolean isCommunityPlot() {
        return communityPlot;
    }

    public long getSeedableUntilGameTime() {
        return seedableUntilGameTime;
    }

    public void clearCommunityPlotState() {
        this.communityPlot = false;
        this.seedableUntilGameTime = 0L;
        setChangedAndSync();
    }

    public boolean shouldReclaimCommunityPlot(Level level) {
        return communityPlot
                && seedableUntilGameTime > 0L
                && !hasCrop()
                && (storedSeedVariety == null || storedSeedVariety.isBlank())
                && level.getGameTime() >= seedableUntilGameTime;
    }

    /**
     * Captures every FarmingBlock/FarmingBlockEntity value before flower
     * replacement. Export is permitted only for genuinely empty soil.
     */
    public FlowerConversionSnapshot exportFlowerConversionSnapshot(BlockState farmingState) {
        Objects.requireNonNull(farmingState, "Farming blockstate is required for flower conversion");
        if (!(farmingState.getBlock() instanceof FarmingBlock)) {
            throw new IllegalArgumentException("Flower conversion snapshot requires an existing FarmingBlock");
        }
        if (farmingState.getValue(FarmingBlock.HAS_SEEDS)
                || hasCrop()
                || storedSeedVariety != null && !storedSeedVariety.isBlank()) {
            throw new IllegalStateException("Flower planting requires empty FarmingBlock soil");
        }

        int fertilizerLevel = farmingState.getValue(FarmingBlock.FERTILIZER);
        FlowerSoilSnapshot soil = communityPlot
                ? FlowerSoilSnapshot.communitySoil(
                        hydration, fertilizerLevel, nitrogen, phosphorus, potassium, organicMatter,
                        seedableUntilGameTime, remainingFertileHarvests
                )
                : FlowerSoilSnapshot.privateSoil(
                        hydration, fertilizerLevel, nitrogen, phosphorus, potassium, organicMatter,
                        remainingFertileHarvests
                );
        return new FlowerConversionSnapshot(
                farmingState,
                soil,
                storedSeedVariety == null ? "" : storedSeedVariety,
                plantedCropId == null ? "" : plantedCropId,
                growthProgress,
                growthStage,
                tickProgress,
                rootEstablishedGameTime,
                mature,
                growthBlocked,
                communityPlot,
                seedableUntilGameTime
        );
    }

    /** Restores a previously exported snapshot after a failed conversion. */
    public void restoreFlowerConversionSnapshot(FlowerConversionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Flower conversion rollback snapshot is required");
        FlowerSoilSnapshot soil = snapshot.soil();
        this.hydration = soil.hydration();
        this.nitrogen = soil.nitrogen();
        this.phosphorus = soil.phosphorus();
        this.potassium = soil.potassium();
        this.organicMatter = soil.organicMatter();
        this.storedSeedVariety = snapshot.storedSeedVariety();
        this.plantedCropId = snapshot.plantedCropId();
        this.growthProgress = snapshot.growthProgress();
        this.growthStage = snapshot.growthStage();
        this.tickProgress = snapshot.tickProgress();
        this.rootEstablishedGameTime = snapshot.rootEstablishedGameTime();
        this.mature = snapshot.mature();
        this.growthBlocked = snapshot.growthBlocked();
        this.communityPlot = snapshot.communityPlot();
        this.seedableUntilGameTime = snapshot.seedableUntilGameTime();
        this.remainingFertileHarvests = soil.remainingFertileHarvests();
        setChangedAndSync();
    }

    /** Initializes empty private soil restored from a permanently uprooted flower. */
    public void restoreUprootedFlowerSoil(FlowerSoilSnapshot soil) {
        Objects.requireNonNull(soil, "Flower soil restoration snapshot is required");
        if (soil.origin() != com.seggellion.britannia_mod.farming.FlowerSoilOrigin.PRIVATE_FARMING_BLOCK) {
            throw new IllegalArgumentException("Private FarmingBlock restoration cannot use community soil");
        }
        hydration = soil.hydration();
        nitrogen = soil.nitrogen();
        phosphorus = soil.phosphorus();
        potassium = soil.potassium();
        organicMatter = soil.organicMatter();
        storedSeedVariety = "";
        plantedCropId = "";
        growthProgress = 0.0f;
        growthStage = 0;
        tickProgress = 0;
        rootEstablishedGameTime = -1L;
        mature = false;
        growthBlocked = false;
        communityPlot = false;
        seedableUntilGameTime = 0L;
        remainingFertileHarvests = soil.remainingFertileHarvests();
        setChangedAndSync();
    }

    /** Restores finite community soil after a flower is permanently uprooted. */
    public void restoreUprootedCommunityFlowerSoil(FlowerSoilSnapshot soil) {
        Objects.requireNonNull(soil, "Flower soil restoration snapshot is required");
        if (soil.origin() != com.seggellion.britannia_mod.farming.FlowerSoilOrigin.COMMUNITY_PLOT) {
            throw new IllegalArgumentException("Community FarmingBlock restoration requires community soil");
        }
        hydration = soil.hydration();
        nitrogen = soil.nitrogen();
        phosphorus = soil.phosphorus();
        potassium = soil.potassium();
        organicMatter = soil.organicMatter();
        storedSeedVariety = "";
        plantedCropId = "";
        growthProgress = 0.0f;
        growthStage = 0;
        tickProgress = 0;
        rootEstablishedGameTime = -1L;
        mature = false;
        growthBlocked = false;
        communityPlot = true;
        seedableUntilGameTime = soil.communitySeedableUntilGameTime();
        remainingFertileHarvests = soil.remainingFertileHarvests();
        ownerId = null;
        setChangedAndSync();
    }

    public CropGrowthContext createGrowthContext(Level level, BlockPos pos, CropDefinition crop, @Nullable Player player) {
        return createGrowthContext(level, pos, crop, player, crop.requiresSupport() || crop.requiresLattice() ? hasRequiredSupport(level, pos, crop) : true);
    }

    public CropGrowthContext createGrowthContext(Level level, BlockPos pos, CropDefinition crop, @Nullable Player player, boolean latticeSatisfied) {
        // A grape plot is judged against the variety growing in it, not against grapes in general.
        // Resolved once here so every fit below - nutrients, hydration, climate, altitude - agrees on
        // the same requirements.
        crop = GrapeVarietyAgronomy.effectiveCrop(crop, this);
        float nutrientFit = nutrientFit(crop);
        float hydrationFit = CropQualityCalculator.hydrationFit(hydration / (float) MAX_HYDRATION, crop);
        FarmingClimate climate = FarmingClimateResolver.resolve(level, pos);
        boolean climateAllowed = crop.canGrowInClimate(climate);
        boolean altitudeAllowed = crop.canGrowAtAltitude(pos);
        float climateFit = climateFit(level, pos, crop, climate);
        boolean requiresDarknessOrUnderground = CropEnvironmentRules.requiresDarknessOrUnderground(crop);
        CropEnvironmentRules.NightshadeEnvironment nightshadeEnvironment = CropEnvironmentRules.nightshadeEnvironment(level, pos);
        boolean specialEnvironmentAllowed = !requiresDarknessOrUnderground || nightshadeEnvironment.allowed();
        float farmingSkill = player == null ? 0.0f : SkillManager.getSkill(player, FarmingSkill.SKILL_ID);
        boolean idealGrowth = nutrientFit >= 0.95f && hydrationFit >= 0.95f && climateFit >= 0.95f
                && climateAllowed && altitudeAllowed && specialEnvironmentAllowed && latticeSatisfied;
        return new CropGrowthContext(
                nutrientFit,
                hydrationFit,
                climateFit,
                climate,
                climateAllowed,
                altitudeAllowed,
                specialEnvironmentAllowed,
                requiresDarknessOrUnderground,
                nightshadeEnvironment.darkEnough(),
                nightshadeEnvironment.underground(),
                nightshadeEnvironment.skyLight(),
                nightshadeEnvironment.blockLight(),
                latticeSatisfied,
                idealGrowth,
                farmingSkill
        );
    }

    public void tickGrowth(Level level, BlockPos pos, BlockState state, RandomSource random) {
        if (level == null || level.isClientSide || !hasCrop()) {
            return;
        }

        CropDefinition crop = CropRegistry.byId(plantedCropId).orElse(null);
        if (crop == null) {
            markBlocked(true);
            return;
        }
        if (mature) {
            TallCropSupport.repairStructureIfPossible(level, pos, crop, growthStage);
            return;
        }

        CropGrowthContext context = createGrowthContext(level, pos, crop, null);
        float multiplier = CropQualityCalculator.growthMultiplier(context);
        if (multiplier <= 0.0f) {
            markBlocked(true);
            return;
        }

        float nextProgress = Math.min(1.0f, growthProgress + (multiplier / Math.max(1, crop.baseGrowthTicks())));
        int maxAge = crop.maxGrowthAge();
        int nextStage = progressToAge(nextProgress, crop);
        if (crop.tallCrop() && nextStage == maxAge && nextProgress < 1.0f) {
            nextStage = Math.max(0, maxAge - 1);
        }

        if (crop.tallCrop() && nextStage > growthStage && !TallCropSupport.canGrowToStage(level, pos, crop, nextStage)) {
            markBlocked(true);
            return;
        }

        growthBlocked = false;
        tickProgress++;
        growthProgress = nextProgress;
        if (nextStage != growthStage) {
            growthStage = nextStage;
            TallCropSupport.update(level, pos, crop, growthStage);
        }
        mature = growthProgress >= 1.0f && crop.isMatureAge(growthStage);
        if (growthProgress >= 1.0f && growthStage != maxAge) {
            growthStage = maxAge;
            TallCropSupport.update(level, pos, crop, growthStage);
            mature = true;
        }

        setChangedAndSync();
    }

    private float climateFit(Level level, BlockPos pos, CropDefinition crop, FarmingClimate climate) {
        float fit = crop.climateFit(climate);
        if (CropEnvironmentRules.requiresDarknessOrUnderground(crop)
                && CropEnvironmentRules.nightshadeEnvironment(level, pos).allowed()) {
            fit = 1.0f;
        }
        return clamp01(fit);
    }

    private boolean hasLatticeSupport(Level level, BlockPos pos) {
        return level.getBlockState(pos.above()).getBlock() instanceof TrellisBlock
                || level.getBlockState(pos.above(2)).getBlock() instanceof TrellisBlock
                || level.getBlockState(pos.above()).getBlock() instanceof com.seggellion.britannia_mod.block.GrapeVineBlock
                || level.getBlockState(pos.above(2)).getBlock() instanceof com.seggellion.britannia_mod.block.GrapeVineBlock;
    }

    public boolean hasRequiredSupport(Level level, BlockPos pos, CropDefinition crop) {
        return switch (crop.supportRequirement()) {
            case NONE -> true;
            case TRELLIS -> level.getBlockState(pos.above()).getBlock() instanceof TrellisBlock;
            case LATTICE -> hasLatticeSupport(level, pos);
            case TREE_STRUCTURE -> crop.treeCrop();
        };
    }

    public static int progressToAge(float progress, CropDefinition crop) {
        if (crop == null) {
            return 0;
        }
        int maxAge = crop.maxGrowthAge();
        int age = (int) Math.floor(clamp01(progress) * crop.visualAgeCount());
        return Math.max(0, Math.min(maxAge, age));
    }

    public int calculateQualityScore(@Nullable Player player) {
        CropDefinition crop = CropRegistry.byId(plantedCropId).orElse(null);
        if (crop == null || level == null) {
            return 0;
        }
        return CropQualityCalculator.calculateQuality(crop, createGrowthContext(level, worldPosition, crop, player), player, getRootAgeDays());
    }

    private void markBlocked(boolean blocked) {
        if (growthBlocked != blocked) {
            growthBlocked = blocked;
            setChangedAndSync();
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("Nitrogen", nitrogen);
        tag.putFloat("Phosphorus", phosphorus);
        tag.putFloat("Potassium", potassium);
        tag.putFloat("OrganicMatter", organicMatter);
        tag.putInt("Hydration", hydration);
        tag.putString("StoredSeed", storedSeedVariety);
        tag.putString("PlantedCropId", plantedCropId);
        tag.putFloat("GrowthProgress", growthProgress);
        tag.putInt("GrowthStage", growthStage);
        tag.putInt("GrowthAgeVersion", GROWTH_AGE_DATA_VERSION);
        tag.putInt("TickProgress", tickProgress);
        tag.putLong("RootEstablishedGameTime", rootEstablishedGameTime);
        tag.putBoolean("Mature", mature);
        tag.putBoolean("GrowthBlocked", growthBlocked);
        tag.putBoolean("CommunityPlot", communityPlot);
        tag.putLong("SeedableUntilGameTime", seedableUntilGameTime);
        if (hasTrackedFertility()) {
            tag.putInt(REMAINING_FERTILE_HARVESTS_KEY, remainingFertileHarvests);
        }
        if (ownerId != null) {
            tag.putUUID(OWNER_ID_KEY, ownerId);
        }
        tag.putInt(VISUAL_ROTATION_KEY, visualRotationQuarters);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.nitrogen = tag.getFloat("Nitrogen");
        this.phosphorus = tag.getFloat("Phosphorus");
        this.potassium = tag.getFloat("Potassium");
        this.organicMatter = tag.getFloat("OrganicMatter");
        this.hydration = tag.getInt("Hydration");
        this.storedSeedVariety = tag.getString("StoredSeed");
        this.plantedCropId = tag.getString("PlantedCropId");
        if ("sweet_potato".equals(this.plantedCropId)) {
            this.plantedCropId = "yam";
        } else if ("mandrake_root".equals(this.plantedCropId)) {
            this.plantedCropId = "mandrake";
        } else if ("strawberries".equals(this.plantedCropId)) {
            this.plantedCropId = "strawberry";
        } else if ("blueberries".equals(this.plantedCropId)) {
            this.plantedCropId = "blueberry";
        }
        this.growthProgress = tag.getFloat("GrowthProgress");
        this.growthStage = tag.getInt("GrowthStage");
        this.tickProgress = tag.getInt("TickProgress");
        this.rootEstablishedGameTime = tag.contains("RootEstablishedGameTime") ? tag.getLong("RootEstablishedGameTime") : -1L;
        this.mature = tag.getBoolean("Mature");
        this.growthBlocked = tag.getBoolean("GrowthBlocked");
        this.communityPlot = tag.getBoolean("CommunityPlot");
        this.seedableUntilGameTime = tag.getLong("SeedableUntilGameTime");
        this.remainingFertileHarvests = tag.contains(REMAINING_FERTILE_HARVESTS_KEY)
                ? Math.max(0, Math.min(MAX_FERTILE_HARVESTS, tag.getInt(REMAINING_FERTILE_HARVESTS_KEY)))
                : UNTRACKED_FERTILE_HARVESTS;
        this.ownerId = tag.hasUUID(OWNER_ID_KEY) ? tag.getUUID(OWNER_ID_KEY) : null;
        this.visualRotationQuarters = Math.floorMod(tag.getInt(VISUAL_ROTATION_KEY), 4);
        migrateLegacyGrowthStage(tag);
    }

    private void migrateLegacyGrowthStage(CompoundTag tag) {
        int growthAgeVersion = tag.getInt("GrowthAgeVersion");
        if (growthAgeVersion >= GROWTH_AGE_DATA_VERSION) {
            return;
        }
        CropDefinition crop = CropRegistry.byId(plantedCropId).orElse(null);
        if (crop == null) {
            return;
        }

        // Hops used four ages through data version 2. Expand existing plants onto
        // the corrected age 0..7 lifecycle, including old mature age-3 plants.
        if (growthAgeVersion < 3 && "hops".equals(crop.id())) {
            migrateLegacyFourStageCrop(crop);
            return;
        }

        if (growthAgeVersion >= 2) {
            return;
        }

        if (usesLegacyFourStageMigration(crop.id())) {
            migrateLegacyFourStageCrop(crop);
            return;
        }

        if (!usesLegacyFiveStageMigration(crop.id())) {
            return;
        }

        int oldStage = Math.max(0, Math.min(4, growthStage));
        int migratedAge = switch (oldStage) {
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 6;
            case 4 -> crop.maxGrowthAge();
            default -> 0;
        };

        this.growthStage = Math.max(0, Math.min(crop.maxGrowthAge(), migratedAge));
        if (crop.isMatureAge(this.growthStage) || mature) {
            this.growthProgress = 1.0f;
            this.mature = true;
        } else {
            this.growthProgress = Math.max(clamp01(this.growthProgress), this.growthStage / (float) crop.visualAgeCount());
            this.mature = false;
        }
    }

    private void migrateLegacyFourStageCrop(CropDefinition crop) {
        int oldStage = Math.max(0, Math.min(3, growthStage));
        int migratedAge = switch (oldStage) {
            case 1 -> 2;
            case 2 -> 5;
            case 3 -> crop.maxGrowthAge();
            default -> 0;
        };

        this.growthStage = Math.max(0, Math.min(crop.maxGrowthAge(), migratedAge));
        if (crop.isMatureAge(this.growthStage) || mature) {
            this.growthStage = crop.maxGrowthAge();
            this.growthProgress = 1.0f;
            this.mature = true;
        } else {
            this.growthProgress = Math.max(clamp01(this.growthProgress), this.growthStage / (float) crop.visualAgeCount());
            this.mature = false;
        }
    }

    private static boolean usesLegacyFourStageMigration(String cropId) {
        return switch (cropId) {
            case "strawberry", "blueberry" -> true;
            default -> false;
        };
    }

    private static boolean usesLegacyFiveStageMigration(String cropId) {
        return switch (cropId) {
            case "broccoli", "cauliflower", "lettuce", "rhubarb", "cabbage", "corn",
                    "squash", "yellow_onion", "green_onion", "garlic", "ginseng", "turnips",
                    "celery", "tobacco", "radish", "parsnip", "yam", "rutabaga" -> true;
            default -> false;
        };
    }

    private void setChangedAndSync() {
        setChanged();
        syncData();
    }

    private void syncData() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public record FlowerConversionSnapshot(
            BlockState blockState,
            FlowerSoilSnapshot soil,
            String storedSeedVariety,
            String plantedCropId,
            float growthProgress,
            int growthStage,
            int tickProgress,
            long rootEstablishedGameTime,
            boolean mature,
            boolean growthBlocked,
            boolean communityPlot,
            long seedableUntilGameTime
    ) {
        public FlowerConversionSnapshot {
            Objects.requireNonNull(blockState, "Rollback blockstate is required");
            Objects.requireNonNull(soil, "Rollback soil snapshot is required");
            storedSeedVariety = Objects.requireNonNull(storedSeedVariety, "Rollback seed variety is required");
            plantedCropId = Objects.requireNonNull(plantedCropId, "Rollback crop ID is required");
        }
    }
}
