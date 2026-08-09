package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.block.OrangeFruitBlock;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropGrowthContext;
import com.seggellion.britannia_mod.farming.CropQualityCalculator;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.FarmingClimate;
import com.seggellion.britannia_mod.farming.FarmingClimateResolver;
import com.seggellion.britannia_mod.farming.FruitTreeDefinition;
import com.seggellion.britannia_mod.farming.FruitTreeRegistry;
import com.seggellion.britannia_mod.farming.FruitProvenance;
import com.seggellion.britannia_mod.farming.OrangeTreeStructurePlanner;
import com.seggellion.britannia_mod.farming.OrangeTreeUtils;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class OrangeTreeRootBlockEntity extends BlockEntity {
    public static final String CROP_ID = "orange";
    private static final float GROWTH_PROGRESS_TARGET = 1.0f;
    private static final float BASE_PROGRESS_PER_RANDOM_TICK = 0.20f;
    private static final float STRUCTURAL_GROWTH_FLOOR = 0.35f;
    private static final float STRUCTURAL_FLOOR_MIN_HYDRATION_FIT = 0.35f;

    private String treeTypeId = FruitTreeRegistry.DEFAULT_TREE_ID;
    private long treeSeed = 0L;
    private int growthStep = 1;
    private float growthProgress = 0.0f;
    private int totalFruit = 0;
    private int harvestedFruit = 0;
    private float boneMealNutrient = 0.0f;
    private float turquoiseNutrient = 0.0f;
    private float sulphurousAshNutrient = 0.0f;
    private float rottenFleshNutrient = 0.0f;
    // Legacy fallback for older saves with a root but no readable FarmingBlockEntity below it.
    private float hydration = 0.0f;
    private long lastGrowthGameTime = 0L;
    private boolean generatedInitialSeed = false;
    private boolean growthBlocked = false;
    private String lastGrowthStatus = "No growth attempt recorded";
    private String lastGrowthFailureReason = "No random tick received";
    private long lastGrowthAttemptGameTime = 0L;
    private float lastGrowthMultiplier = 0.0f;
    private float lastFertilizerFit = 0.0f;
    private float lastHydrationFit = 0.0f;
    private float lastClimateFit = 0.0f;
    private boolean lastAltitudeAllowed = true;
    private boolean lastClimateAllowed = true;
    private int lastPlannedPlacements = 0;
    private int lastSuccessfulPlacements = 0;
    private int lastFruitCandidateCount = 0;
    private float lastFruitSaturation = 0.0f;
    private float lastConditionScore = 0.0f;
    private int lastExpectedFruitCount = 0;
    private int lastSoftSkippedPlacements = 0;
    private int lastHardBlockedPlacements = 0;
    private float lastPlacementSuccessRatio = 0.0f;
    private float lastPlacementStageThreshold = 0.0f;
    private boolean lastStageCanAdvance = true;
    private String lastSoftSkipReason = "none";
    private String lastHardBlockPlacementReason = "none";
    private String lastStageAdvanceReason = "No placement attempt recorded";
    private FruitTreeGrowthPhase lastGrowthPhase = FruitTreeGrowthPhase.STRUCTURE;
    private float lastBaseProgressPerTick = BASE_PROGRESS_PER_RANDOM_TICK;
    private float lastEffectiveProgressAdded = 0.0f;
    private int failedGrowthRolls = 0;
    private boolean lastGrowthFloorApplied = false;
    private String lastHardBlockReason = "none";

    public OrangeTreeRootBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.ORANGE_TREE_ROOT_BE.get(), pos, blockState);
    }

    public void initializeFromFarm(FarmingBlockEntity farmBlockEntity, RandomSource random) {
        initializeFromFarm(farmBlockEntity, random, farmBlockEntity.getPlantedCropId());
    }

    public void initializeFromFarm(FarmingBlockEntity farmBlockEntity, RandomSource random, String treeTypeId) {
        this.treeTypeId = FruitTreeRegistry.byIdOrDefault(treeTypeId).id();
        this.treeSeed = random.nextLong();
        this.generatedInitialSeed = true;
        this.growthStep = 1;
        this.growthProgress = 0.0f;
        this.totalFruit = 0;
        this.harvestedFruit = 0;
        this.failedGrowthRolls = 0;
        this.lastEffectiveProgressAdded = 0.0f;
        this.lastGrowthFloorApplied = false;
        this.lastHardBlockReason = "none";
        this.boneMealNutrient = farmBlockEntity.getBoneMealNutrient();
        this.turquoiseNutrient = farmBlockEntity.getTurquoiseNutrient();
        this.sulphurousAshNutrient = farmBlockEntity.getSulphurousAshNutrient();
        this.rottenFleshNutrient = farmBlockEntity.getRottenFleshNutrient();
        setChangedAndSync();
    }

    public void setTreeTypeIdIfUnset(String treeTypeId) {
        if ((this.treeTypeId == null || this.treeTypeId.isBlank() || FruitTreeRegistry.DEFAULT_TREE_ID.equals(this.treeTypeId))
                && treeTypeId != null && !treeTypeId.isBlank()) {
            this.treeTypeId = FruitTreeRegistry.byIdOrDefault(treeTypeId).id();
        }
    }

    public String getTreeTypeId() {
        return treeTypeId == null || treeTypeId.isBlank() ? FruitTreeRegistry.DEFAULT_TREE_ID : treeTypeId;
    }

    public FruitTreeDefinition definition() {
        return FruitTreeRegistry.byIdOrDefault(getTreeTypeId());
    }

    public void ensureSeed(RandomSource random) {
        if (!generatedInitialSeed) {
            this.treeSeed = random.nextLong();
            this.generatedInitialSeed = true;
            this.lastGrowthStatus = "Generated missing root seed";
            this.lastGrowthFailureReason = "";
            setChangedAndSync();
        }
    }

    public boolean addNutrients(float boneMeal, float turquoise, float sulphurousAsh, float rottenFlesh) {
        Optional<FarmingBlockEntity> soil = getSoilBlockEntity();
        if (soil.isPresent()) {
            boolean changed = soil.get().addNutrients(boneMeal, turquoise, sulphurousAsh, rottenFlesh);
            syncFromSoil(soil.get());
            return changed;
        }
        float oldBone = boneMealNutrient;
        float oldTurquoise = turquoiseNutrient;
        float oldAsh = sulphurousAshNutrient;
        float oldFlesh = rottenFleshNutrient;
        boneMealNutrient = clamp01(boneMealNutrient + boneMeal);
        turquoiseNutrient = clamp01(turquoiseNutrient + turquoise);
        sulphurousAshNutrient = clamp01(sulphurousAshNutrient + sulphurousAsh);
        rottenFleshNutrient = clamp01(rottenFleshNutrient + rottenFlesh);
        boolean changed = oldBone != boneMealNutrient || oldTurquoise != turquoiseNutrient
                || oldAsh != sulphurousAshNutrient || oldFlesh != rottenFleshNutrient;
        if (changed) {
            setChangedAndSync();
        }
        return changed;
    }

    public boolean water(int amount) {
        int old = getHydrationLevel();
        setHydrationLevel(old + amount);
        return getHydrationLevel() > old;
    }

    public void setHydrationLevel(int value) {
        Optional<FarmingBlockEntity> soil = getSoilBlockEntity();
        if (soil.isPresent()) {
            setSoilHydrationLevel(soil.get(), value);
            return;
        }
        float old = hydration;
        hydration = clamp01(Math.max(0, Math.min(FarmingBlockEntity.MAX_HYDRATION, value)) / (float) FarmingBlockEntity.MAX_HYDRATION);
        if (old != hydration) {
            setChangedAndSync();
        }
    }

    public int getHydrationLevel() {
        return getRawHydration();
    }

    public void tickGrowth(ServerLevel level, RandomSource random) {
        ensureSeed(random);
        lastGrowthAttemptGameTime = level.getGameTime();
        Optional<FarmingBlockEntity> soil = getSoilBlockEntity(level);
        if (soil.isEmpty()) {
            recordGrowthFailure("Missing FarmingBlock soil below root at " + getSoilPos().toShortString());
            return;
        }
        syncFromSoil(soil.get());
        FruitTreeDefinition definition = definition();
        if (growthStep >= definition.maxGrowthStep()) {
            recordGrowthStatus("Mature", "");
            return;
        }

        CropDefinition crop = crop();
        if (crop == null) {
            recordGrowthFailure("Missing crop definition for fruit tree type " + getTreeTypeId());
            return;
        }

        int nextStep = Math.min(definition.maxGrowthStep(), growthStep + 1);
        FruitTreeGrowthPhase phase = phaseForStep(nextStep);
        CropGrowthContext context = createGrowthContext(level, getSoilPos(), crop, null);
        GrowthMultiplierResult multiplierResult = growthMultiplierForPhase(context, phase);
        float multiplier = multiplierResult.multiplier();
        recordConditionSnapshot(context, multiplier, phase, multiplierResult.floorApplied());
        if (multiplier <= 0.0f) {
            recordGrowthFailure(hardBlockReason(crop, context));
            return;
        }

        float fruitSaturation = fruitSaturationFor(context);
        OrangeTreeStructurePlanner.Plan plan = OrangeTreeStructurePlanner.plan(definition, treeSeed, nextStep, worldPosition, fruitSaturation);
        recordFruitYieldPlan(plan, fruitSaturation);
        Set<BlockPos> requiredPlacements = requiredPlacementsForStep(nextStep, plan);
        lastPlannedPlacements = requiredPlacements.size();
        lastSuccessfulPlacements = 0;
        if (requiredPlacements.isEmpty()) {
            recordGrowthFailure("No planned placements for step " + nextStep);
            return;
        }

        PlacementResult previewResult = evaluatePlacements(level, plan, requiredPlacements, nextStep, false);
        recordPlacementResult(previewResult);
        if (!previewResult.canAdvance()) {
            recordGrowthFailure(previewResult.advanceReason());
            return;
        }

        float randomVariation = Mth.lerp(random.nextFloat(), 0.90f, 1.10f);
        lastBaseProgressPerTick = BASE_PROGRESS_PER_RANDOM_TICK;
        lastEffectiveProgressAdded = BASE_PROGRESS_PER_RANDOM_TICK * multiplier * randomVariation;
        growthProgress = Mth.clamp(growthProgress + lastEffectiveProgressAdded, 0.0f, GROWTH_PROGRESS_TARGET);
        if (growthProgress < GROWTH_PROGRESS_TARGET) {
            failedGrowthRolls = 0;
            recordGrowthStatus("Accumulating growth progress; no hard block.",
                    String.format("progress=%.3f/%.3f, phase=%s, multiplier=%.3f",
                            growthProgress, GROWTH_PROGRESS_TARGET, phase, multiplier));
            return;
        }

        growthProgress = 0.0f;
        failedGrowthRolls = 0;
        growthBlocked = false;
        lastGrowthGameTime = level.getGameTime();
        PlacementResult placementResult = evaluatePlacements(level, plan, requiredPlacements, nextStep, true);
        recordPlacementResult(placementResult);
        lastSuccessfulPlacements = placementResult.successful();
        if (!placementResult.canAdvance()) {
            recordGrowthFailure(placementResult.advanceReason());
            return;
        }
        growthStep = nextStep;
        recordGrowthStatus(
                placementResult.softSkipped() > 0
                        ? "Advanced to step " + growthStep + " with soft placement skips"
                        : "Advanced to step " + growthStep,
                placementResult.softSkipped() > 0 ? placementResult.advanceReason() : ""
        );
        setChangedAndSync();
    }

    public ItemStack createHarvestStack(@Nullable Player player) {
        return createHarvestStack(player, 1);
    }

    public ItemStack createHarvestStack(@Nullable Player player, int count) {
        CropDefinition crop = crop();
        ItemStack stack = new ItemStack(definition().fruitItem().get(), Math.max(1, count));
        if (crop != null && level != null) {
            int quality = CropQualityCalculator.calculateQuality(crop, createGrowthContext(level, getSoilPos(), crop, player), player);
            CropQualityCalculator.applyQuality(stack, crop, quality);
            FruitProvenance.setRegionName(stack, resolveRegionName());
        }
        return stack;
    }

    public void onFruitHarvested(BlockPos fruitPos) {
        harvestedFruit++;
        if (totalFruit > 0 && harvestedFruit / (float) totalFruit >= 0.80f && level instanceof ServerLevel serverLevel) {
            triggerRegression(serverLevel);
        } else {
            setChangedAndSync();
        }
    }

    public void triggerRegression(ServerLevel level) {
        BlockPos.betweenClosedStream(
                worldPosition.offset(-definition().canopyRadiusX() - 1, 1, -definition().canopyRadiusZ() - 1),
                worldPosition.offset(definition().canopyRadiusX() + 1, definition().trunkHeight() + definition().canopyRadiusY() + 2, definition().canopyRadiusZ() + 1)
        ).forEach(pos -> {
            BlockState state = level.getBlockState(pos);
            if (isOrangeCanopyBlock(state)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        });
        growthStep = 7;
        totalFruit = 0;
        harvestedFruit = 0;
        setChangedAndSync();
    }

    public void cleanupTree(ServerLevel level) {
        cleanupTree(level, null, false, false);
    }

    public void cleanupTree(ServerLevel level, @Nullable Player player, boolean dropFruit) {
        cleanupTree(level, player, dropFruit, true);
    }

    private void cleanupTree(ServerLevel level, @Nullable Player player, boolean dropFruit, boolean includeRoot) {
        BlockPos.betweenClosedStream(
                worldPosition.offset(-definition().canopyRadiusX() - 1, 0, -definition().canopyRadiusZ() - 1),
                worldPosition.offset(definition().canopyRadiusX() + 1, definition().trunkHeight() + definition().canopyRadiusY() + 2, definition().canopyRadiusZ() + 1)
        ).forEach(pos -> {
            BlockState state = level.getBlockState(pos);
            FruitTreeDefinition definition = definition();
            if (state.is(definition.fruitBlock().get())) {
                if (dropFruit) {
                    OrangeFruitBlock.dropFruitFromTree(level, pos, this, player, false);
                }
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            } else if (state.is(definition.rootBlock().get())
                    || state.is(definition.trunkBlock().get())
                    || state.is(definition.branchBlock().get())) {
                if (includeRoot || !pos.equals(worldPosition)) {
                    if (dropFruit) {
                        dropFruitTreeWood(level, pos, state);
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            } else if (state.is(definition.leafBlock().get())) {
                if (includeRoot || !pos.equals(worldPosition)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        });
        clearSoilCrop(level);
    }

    public CropGrowthContext createGrowthContext(Level level, BlockPos pos, CropDefinition crop, @Nullable Player player) {
        Optional<FarmingBlockEntity> soil = getSoilBlockEntity(level);
        float bone = soil.map(FarmingBlockEntity::getBoneMealNutrient).orElse(boneMealNutrient);
        float turquoise = soil.map(FarmingBlockEntity::getTurquoiseNutrient).orElse(turquoiseNutrient);
        float ash = soil.map(FarmingBlockEntity::getSulphurousAshNutrient).orElse(sulphurousAshNutrient);
        float flesh = soil.map(FarmingBlockEntity::getRottenFleshNutrient).orElse(rottenFleshNutrient);
        float hydrated = normalizedHydration(soil.map(FarmingBlockEntity::getHydration).orElse(getLegacyHydrationLevel()));
        float nutrientFit = CropQualityCalculator.weightedNutrientFit(crop, bone, turquoise, ash, flesh);
        float hydrationFit = treeHydrationFit(hydrated, crop);
        FarmingClimate climate = FarmingClimateResolver.resolve(level, pos);
        boolean climateAllowed = crop.canGrowInClimate(climate);
        boolean altitudeAllowed = crop.canGrowAtAltitude(pos);
        float climateFit = clamp01(crop.climateFit(climate));
        float farmingSkill = player == null ? 0.0f : SkillManager.getSkill(player, com.seggellion.britannia_mod.farming.FarmingSkill.SKILL_ID);
        boolean idealGrowth = nutrientFit >= 0.95f && hydrationFit >= 0.95f && climateFit >= 0.95f && climateAllowed && altitudeAllowed;
        return new CropGrowthContext(nutrientFit, hydrationFit, climateFit, climate, climateAllowed, altitudeAllowed, true, idealGrowth, farmingSkill);
    }

    private PlacementResult evaluatePlacements(ServerLevel level, OrangeTreeStructurePlanner.Plan plan, Set<BlockPos> requiredPlacements, int nextStep, boolean apply) {
        int planned = requiredPlacements.size();
        int successful = 0;
        int softSkipped = 0;
        int hardBlocked = 0;
        int placedFruit = 0;
        String firstSoftSkip = "none";
        String firstHardBlock = "none";

        for (BlockPos pos : orderedPlacementTargets(plan, requiredPlacements)) {
            PlacementKind kind = placementKindFor(pos, plan, nextStep);
            PlacementDecision decision = placementDecision(level, pos, kind, nextStep);
            if (decision.hardBlocked()) {
                hardBlocked++;
                if ("none".equals(firstHardBlock)) {
                    firstHardBlock = decision.reason();
                }
                continue;
            }
            if (!decision.placeable()) {
                softSkipped++;
                if ("none".equals(firstSoftSkip)) {
                    firstSoftSkip = decision.reason();
                }
                continue;
            }

            if (apply) {
                placePlannedBlock(level, pos, kind, nextStep);
            }
            successful++;
            if (kind == PlacementKind.FRUIT) {
                placedFruit++;
            }
        }

        float threshold = placementThreshold(nextStep);
        float successRatio = planned <= 0 ? 1.0f : successful / (float) planned;
        boolean enoughSuccess = threshold <= 0.0f || successRatio >= threshold || (nextStep <= 8 && successful > 0 && softSkipped > 0);
        boolean canAdvance = hardBlocked == 0 && enoughSuccess;
        String advanceReason;
        if (hardBlocked > 0) {
            advanceReason = firstHardBlock;
        } else if (!enoughSuccess) {
            advanceReason = String.format("Insufficient valid placements for step %d; successful=%d/%d threshold=%.2f first_soft_skip=%s",
                    nextStep, successful, planned, threshold, firstSoftSkip);
        } else if (softSkipped > 0) {
            advanceReason = "Enough valid placements despite soft conflicts";
        } else {
            advanceReason = "All required placements valid";
        }

        if (apply && nextStep >= 9) {
            totalFruit = placedFruit;
            if (nextStep == 9) {
                harvestedFruit = 0;
            }
        }

        return new PlacementResult(planned, successful, softSkipped, hardBlocked, successRatio, threshold, canAdvance, firstSoftSkip, firstHardBlock, advanceReason);
    }

    private Iterable<BlockPos> orderedPlacementTargets(OrangeTreeStructurePlanner.Plan plan, Set<BlockPos> requiredPlacements) {
        java.util.List<BlockPos> ordered = new java.util.ArrayList<>();
        addRequiredTargets(ordered, plan.trunks(), requiredPlacements);
        addRequiredTargets(ordered, plan.branches(), requiredPlacements);
        addRequiredTargets(ordered, plan.leaves(), requiredPlacements);
        addRequiredTargets(ordered, plan.fruit(), requiredPlacements);
        return ordered;
    }

    private static void addRequiredTargets(java.util.List<BlockPos> ordered, Set<BlockPos> candidates, Set<BlockPos> requiredPlacements) {
        candidates.stream()
                .filter(requiredPlacements::contains)
                .sorted((a, b) -> a.asLong() == b.asLong() ? 0 : Long.compare(a.asLong(), b.asLong()))
                .forEach(ordered::add);
    }

    private PlacementKind placementKindFor(BlockPos pos, OrangeTreeStructurePlanner.Plan plan, int nextStep) {
        if (plan.trunks().contains(pos)) {
            return PlacementKind.TRUNK;
        }
        if (plan.branches().contains(pos)) {
            int dy = pos.getY() - worldPosition.getY();
            int horizontalDistance = Math.abs(pos.getX() - worldPosition.getX()) + Math.abs(pos.getZ() - worldPosition.getZ());
            return dy == definition().trunkHeight() && horizontalDistance == 1 ? PlacementKind.PRIMARY_BRANCH : PlacementKind.SECONDARY_BRANCH;
        }
        if (plan.fruit().contains(pos) || nextStep >= 9 && plan.fruitCandidates().contains(pos)) {
            return PlacementKind.FRUIT;
        }
        return PlacementKind.LEAF;
    }

    private PlacementDecision placementDecision(ServerLevel level, BlockPos pos, PlacementKind kind, int nextStep) {
        if (level.isOutsideBuildHeight(pos)) {
            return kind.isCritical()
                    ? PlacementDecision.hard("Hard blocked " + kind.debugName() + " at " + pos.toShortString() + " outside build height")
                    : PlacementDecision.soft("Skipped " + kind.debugName() + " at " + pos.toShortString() + " outside build height");
        }

        BlockState state = level.getBlockState(pos);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (kind == PlacementKind.FRUIT && !canPlaceFruitAt(state, pos, nextStep)) {
            return PlacementDecision.soft("Skipped fruit at " + pos.toShortString() + " blocked by " + blockId);
        }
        if (isSafelyReplaceable(state, pos)) {
            return PlacementDecision.place();
        }
        if (level.getBlockEntity(pos) != null || isOtherFruitTreeRoot(state)) {
            return kind.isCritical()
                    ? PlacementDecision.hard("Hard blocked " + kind.debugName() + " at " + pos.toShortString() + " by " + blockId)
                    : PlacementDecision.soft("Skipped " + kind.debugName() + " at " + pos.toShortString() + " blocked by " + blockId);
        }
        if (isSoftConflict(state) || !kind.isCritical()) {
            return PlacementDecision.soft("Skipped " + kind.debugName() + " at " + pos.toShortString() + " blocked by " + blockId);
        }
        return PlacementDecision.hard("Hard blocked " + kind.debugName() + " at " + pos.toShortString() + " by " + blockId);
    }

    private boolean canPlaceFruitAt(BlockState state, BlockPos pos, int nextStep) {
        return nextStep >= 10
                ? isOwnTreeBlock(pos, state) && state.is(definition().fruitBlock().get())
                : isOwnTreeBlock(pos, state) && (state.is(definition().leafBlock().get()) || state.is(definition().fruitBlock().get()));
    }

    private void placePlannedBlock(ServerLevel level, BlockPos pos, PlacementKind kind, int nextStep) {
        switch (kind) {
            case TRUNK -> level.setBlock(pos, trunkState(), 3);
            case PRIMARY_BRANCH, SECONDARY_BRANCH -> level.setBlock(pos, branchStateFor(pos), 3);
            case LEAF -> level.setBlock(pos, definition().leafBlock().get().defaultBlockState(), 3);
            case FRUIT -> level.setBlock(pos, definition().fruitBlock().get().defaultBlockState().setValue(OrangeFruitBlock.RIPE, nextStep >= 10), 3);
        }
    }

    private Set<BlockPos> requiredPlacementsForStep(int nextStep, OrangeTreeStructurePlanner.Plan nextPlan) {
        Set<BlockPos> required = new HashSet<>();
        OrangeTreeStructurePlanner.Plan currentPlan = OrangeTreeStructurePlanner.plan(definition(), treeSeed, growthStep, worldPosition, lastFruitSaturation);
        if (nextStep <= 6) {
            required.addAll(nextPlan.trunks());
            required.removeAll(currentPlan.trunks());
            required.addAll(nextPlan.branches());
            required.removeAll(currentPlan.branches());
            required.remove(worldPosition);
        } else if (nextStep <= 8) {
            required.addAll(nextPlan.leaves());
            required.removeAll(currentPlan.leaves());
            required.removeAll(nextPlan.trunks());
            required.removeAll(nextPlan.branches());
        } else if (nextStep == 9) {
            required.addAll(nextPlan.leaves());
            required.removeAll(currentPlan.leaves());
            required.removeAll(nextPlan.trunks());
            required.removeAll(nextPlan.branches());
            required.addAll(nextPlan.fruit());
        } else {
            required.addAll(nextPlan.fruit());
        }
        return required;
    }

    private boolean isSafelyReplaceable(BlockState state, BlockPos pos) {
        return pos.equals(worldPosition)
                || state.isAir()
                || state.canBeReplaced()
                || isOwnTreeBlock(pos, state)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.VINE)
                || state.is(Blocks.SNOW);
    }

    private boolean isSoftConflict(BlockState state) {
        return OrangeTreeUtils.isOrangeTreeBody(state)
                || state.is(BlockTags.LEAVES)
                || state.canBeReplaced();
    }

    private boolean isOwnTreeBlock(BlockPos pos, BlockState state) {
        if (pos.equals(worldPosition)) {
            return true;
        }
        if (!OrangeTreeUtils.isOrangeTreeBody(state)) {
            return false;
        }
        return level != null
                && OrangeTreeUtils.findRoot(level, pos)
                .map(root -> root.getBlockPos().equals(worldPosition))
                .orElse(false);
    }

    private boolean isOtherFruitTreeRoot(BlockState state) {
        return FruitTreeRegistry.all().stream().anyMatch(definition -> state.is(definition.rootBlock().get()))
                && !state.is(definition().rootBlock().get());
    }

    private boolean isOrangeCanopyBlock(BlockState state) {
        return state.is(definition().leafBlock().get()) || state.is(definition().fruitBlock().get());
    }

    private void dropFruitTreeWood(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack woodStack = new ItemStack(ItemRegistry.WEIGHTED_WOOD_ITEM.get());
        WeightedWoodItem woodItem = (WeightedWoodItem) woodStack.getItem();
        woodItem.setWoodType(woodStack, definition().id());
        double minWeight = state.is(definition().branchBlock().get()) ? 1.0D : 3.0D;
        double maxWeight = state.is(definition().branchBlock().get()) ? 4.0D : 8.0D;
        woodItem.setWeight(woodStack, minWeight + level.getRandom().nextDouble() * (maxWeight - minWeight));
        level.addFreshEntity(new ItemEntity(
                level,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                woodStack
        ));
    }

    private BlockState branchStateFor(BlockPos pos) {
        Direction.Axis axis = Direction.Axis.Y;
        int dx = pos.getX() - worldPosition.getX();
        int dz = pos.getZ() - worldPosition.getZ();
        if (Math.abs(dx) >= Math.abs(dz) && dx != 0) {
            axis = Direction.Axis.X;
        } else if (dz != 0) {
            axis = Direction.Axis.Z;
        }
        return definition().branchBlock().get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private BlockState trunkState() {
        return definition().trunkBlock().get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    }

    private void markBlocked(boolean blocked) {
        if (growthBlocked != blocked) {
            growthBlocked = blocked;
            setChangedAndSync();
        }
    }

    private void recordConditionSnapshot(CropGrowthContext context, float multiplier, FruitTreeGrowthPhase phase, boolean floorApplied) {
        lastFertilizerFit = context.nutrientFit();
        lastHydrationFit = context.hydrationFit();
        lastClimateFit = context.climateFit();
        lastAltitudeAllowed = context.altitudeAllowed();
        lastClimateAllowed = context.climateAllowed();
        lastGrowthMultiplier = multiplier;
        lastGrowthPhase = phase;
        lastGrowthFloorApplied = floorApplied;
        lastConditionScore = conditionScore(context);
        lastHardBlockReason = "none";
    }

    private void recordFruitYieldPlan(OrangeTreeStructurePlanner.Plan plan, float fruitSaturation) {
        lastFruitCandidateCount = plan.fruitCandidates().size();
        lastFruitSaturation = fruitSaturation;
        lastExpectedFruitCount = plan.fruit().size();
    }

    private void recordPlacementResult(PlacementResult result) {
        lastPlannedPlacements = result.planned();
        lastSuccessfulPlacements = result.successful();
        lastSoftSkippedPlacements = result.softSkipped();
        lastHardBlockedPlacements = result.hardBlocked();
        lastPlacementSuccessRatio = result.successRatio();
        lastPlacementStageThreshold = result.stageThreshold();
        lastStageCanAdvance = result.canAdvance();
        lastSoftSkipReason = result.firstSoftSkip();
        lastHardBlockPlacementReason = result.firstHardBlock();
        lastStageAdvanceReason = result.advanceReason();
    }

    private void recordGrowthStatus(String status, String reason) {
        growthBlocked = false;
        lastGrowthStatus = status;
        lastGrowthFailureReason = reason == null ? "" : reason;
        lastHardBlockReason = "none";
        setChangedAndSync();
    }

    private void recordGrowthFailure(String reason) {
        growthBlocked = true;
        lastGrowthStatus = "Blocked";
        lastGrowthFailureReason = reason == null || reason.isBlank() ? "Unknown growth failure" : reason;
        lastHardBlockReason = lastGrowthFailureReason;
        lastEffectiveProgressAdded = 0.0f;
        lastSuccessfulPlacements = 0;
        setChangedAndSync();
    }

    private String hardBlockReason(CropDefinition crop, CropGrowthContext context) {
        if (!context.climateAllowed()) {
            return "Climate forbidden: " + context.climate();
        }
        if (!context.altitudeAllowed()) {
            return "Altitude invalid: y=" + getSoilPos().getY() + " allowed=" + crop.minAltitude() + "-" + crop.maxAltitude();
        }
        if (getRawHydration() <= 0) {
            return "Soil completely dry";
        }
        return "Growth multiplier is zero";
    }

    private GrowthMultiplierResult growthMultiplierForPhase(CropGrowthContext context, FruitTreeGrowthPhase phase) {
        if (!context.climateAllowed() || !context.altitudeAllowed() || getRawHydration() <= 0) {
            return new GrowthMultiplierResult(0.0f, false);
        }

        float multiplier = switch (phase) {
            case STRUCTURE -> context.hydrationFit() * 0.45f
                    + context.climateFit() * 0.35f
                    + context.nutrientFit() * 0.20f;
            case CANOPY -> context.hydrationFit() * 0.40f
                    + context.climateFit() * 0.35f
                    + context.nutrientFit() * 0.25f;
            case FRUITING, RIPENING -> context.nutrientFit() * 0.45f
                    + context.hydrationFit() * 0.30f
                    + context.climateFit() * 0.25f;
        };

        boolean floorApplies = (phase == FruitTreeGrowthPhase.STRUCTURE || phase == FruitTreeGrowthPhase.CANOPY)
                && context.climateAllowed()
                && context.altitudeAllowed()
                && context.hydrationFit() >= STRUCTURAL_FLOOR_MIN_HYDRATION_FIT
                && multiplier < STRUCTURAL_GROWTH_FLOOR;
        if (floorApplies) {
            multiplier = STRUCTURAL_GROWTH_FLOOR;
        }
        return new GrowthMultiplierResult(Mth.clamp(multiplier, 0.0f, 1.0f), floorApplies);
    }

    private float placementThreshold(int nextStep) {
        if (nextStep <= 4) {
            return 0.75f;
        }
        if (nextStep <= 6) {
            return 0.35f;
        }
        if (nextStep <= 8) {
            return 0.25f;
        }
        return 0.0f;
    }

    private static FruitTreeGrowthPhase phaseForStep(int step) {
        if (step <= 6) {
            return FruitTreeGrowthPhase.STRUCTURE;
        }
        if (step <= 8) {
            return FruitTreeGrowthPhase.CANOPY;
        }
        if (step == 9) {
            return FruitTreeGrowthPhase.FRUITING;
        }
        return FruitTreeGrowthPhase.RIPENING;
    }

    private float fruitSaturationFor(CropGrowthContext context) {
        float score = conditionScore(context);
        if (score <= 0.0f) {
            return 0.0f;
        }
        FruitTreeDefinition definition = definition();
        float adjusted = Mth.clamp((score - (1.0f - definition.inhospitableTolerance())) / Math.max(0.05f, definition.inhospitableTolerance()), 0.0f, 1.0f);
        return Mth.clamp(Mth.lerp(adjusted, definition.baseFruitDensity(), definition.maxFruitDensity()), 0.0f, definition.maxFruitDensity());
    }

    private float conditionScore(CropGrowthContext context) {
        if (!context.climateAllowed() || !context.altitudeAllowed()) {
            return 0.0f;
        }
        return Mth.clamp(
                context.nutrientFit() * 0.45f
                        + context.hydrationFit() * 0.30f
                        + context.climateFit() * 0.25f,
                0.0f,
                1.0f);
    }

    @Nullable
    private CropDefinition crop() {
        return CropRegistry.byId(getTreeTypeId()).orElse(null);
    }

    public long getTreeSeed() {
        return treeSeed;
    }

    public int getGrowthStep() {
        return growthStep;
    }

    public float getGrowthProgress() {
        return growthProgress;
    }

    public float getGrowthProgressTarget() {
        return GROWTH_PROGRESS_TARGET;
    }

    public int getTotalFruit() {
        return totalFruit;
    }

    public int getHarvestedFruit() {
        return harvestedFruit;
    }

    public float getBoneMealNutrient() {
        return getSoilBlockEntity().map(FarmingBlockEntity::getBoneMealNutrient).orElse(boneMealNutrient);
    }

    public float getTurquoiseNutrient() {
        return getSoilBlockEntity().map(FarmingBlockEntity::getTurquoiseNutrient).orElse(turquoiseNutrient);
    }

    public float getSulphurousAshNutrient() {
        return getSoilBlockEntity().map(FarmingBlockEntity::getSulphurousAshNutrient).orElse(sulphurousAshNutrient);
    }

    public float getRottenFleshNutrient() {
        return getSoilBlockEntity().map(FarmingBlockEntity::getRottenFleshNutrient).orElse(rottenFleshNutrient);
    }

    public float getHydration() {
        return normalizedHydration(getRawHydration());
    }

    public int getRawHydration() {
        return getSoilBlockEntity().map(FarmingBlockEntity::getHydration).orElse(getLegacyHydrationLevel());
    }

    public int getMaxHydration() {
        return FarmingBlockEntity.MAX_HYDRATION;
    }

    public boolean isUsingSoilHydration() {
        return getSoilBlockEntity().isPresent();
    }

    public long getLastGrowthGameTime() {
        return lastGrowthGameTime;
    }

    public boolean isGrowthBlocked() {
        return growthBlocked;
    }

    public String getLastGrowthStatus() {
        return lastGrowthStatus;
    }

    public String getLastGrowthFailureReason() {
        return lastGrowthFailureReason;
    }

    public long getLastGrowthAttemptGameTime() {
        return lastGrowthAttemptGameTime;
    }

    public float getLastGrowthMultiplier() {
        return lastGrowthMultiplier;
    }

    public String getLastGrowthPhase() {
        return lastGrowthPhase.name();
    }

    public float getLastBaseProgressPerTick() {
        return lastBaseProgressPerTick;
    }

    public float getLastEffectiveProgressAdded() {
        return lastEffectiveProgressAdded;
    }

    public int getFailedGrowthRolls() {
        return failedGrowthRolls;
    }

    public boolean wasLastGrowthFloorApplied() {
        return lastGrowthFloorApplied;
    }

    public String getLastHardBlockReason() {
        return lastHardBlockReason;
    }

    public float getLastFertilizerFit() {
        return lastFertilizerFit;
    }

    public float getLastHydrationFit() {
        return lastHydrationFit;
    }

    public float getLastClimateFit() {
        return lastClimateFit;
    }

    public boolean isLastAltitudeAllowed() {
        return lastAltitudeAllowed;
    }

    public boolean isLastClimateAllowed() {
        return lastClimateAllowed;
    }

    public int getLastPlannedPlacements() {
        return lastPlannedPlacements;
    }

    public int getLastSuccessfulPlacements() {
        return lastSuccessfulPlacements;
    }

    public int getLastSoftSkippedPlacements() {
        return lastSoftSkippedPlacements;
    }

    public int getLastHardBlockedPlacements() {
        return lastHardBlockedPlacements;
    }

    public float getLastPlacementSuccessRatio() {
        return lastPlacementSuccessRatio;
    }

    public float getLastPlacementStageThreshold() {
        return lastPlacementStageThreshold;
    }

    public boolean canLastStageAdvance() {
        return lastStageCanAdvance;
    }

    public String getLastSoftSkipReason() {
        return lastSoftSkipReason;
    }

    public String getLastHardBlockPlacementReason() {
        return lastHardBlockPlacementReason;
    }

    public String getLastStageAdvanceReason() {
        return lastStageAdvanceReason;
    }

    public int getCurrentPlannedPlacementCount() {
        return OrangeTreeStructurePlanner.plan(definition(), treeSeed, growthStep, worldPosition).placementCount();
    }

    public int getCurrentPlannedTrunkCount() {
        return OrangeTreeStructurePlanner.plan(definition(), treeSeed, growthStep, worldPosition).trunks().size();
    }

    public int getCurrentPlannedBranchCount() {
        return OrangeTreeStructurePlanner.plan(definition(), treeSeed, growthStep, worldPosition).branches().size();
    }

    public String getCurrentPlannedTrunkPositions() {
        return formatPositions(OrangeTreeStructurePlanner.plan(definition(), treeSeed, growthStep, worldPosition).trunks());
    }

    public String getCurrentPlannedBranchPositions() {
        return formatPositions(OrangeTreeStructurePlanner.plan(definition(), treeSeed, growthStep, worldPosition).branches());
    }

    public String resolveRegionName() {
        return level == null
                ? "Unknown"
                : FarmingClimateResolver.findRegionAt(level, getSoilPos()).map(region -> region.name).orElse("Unknown");
    }

    private static String formatPositions(Set<BlockPos> positions) {
        if (positions.isEmpty()) {
            return "<none>";
        }
        return positions.stream()
                .map(BlockPos::toShortString)
                .sorted()
                .collect(Collectors.joining(";"));
    }

    public int getLastFruitCandidateCount() {
        return lastFruitCandidateCount;
    }

    public float getLastFruitSaturation() {
        return lastFruitSaturation;
    }

    public float getLastConditionScore() {
        return lastConditionScore;
    }

    public int getLastExpectedFruitCount() {
        return lastExpectedFruitCount;
    }

    public BlockPos getSoilPos() {
        return worldPosition.below();
    }

    public Optional<FarmingBlockEntity> getSoilBlockEntity() {
        return level == null ? Optional.empty() : getSoilBlockEntity(level);
    }

    public Optional<FarmingBlockEntity> getSoilBlockEntity(Level level) {
        BlockEntity blockEntity = level.getBlockEntity(getSoilPos());
        return blockEntity instanceof FarmingBlockEntity farmBlockEntity ? Optional.of(farmBlockEntity) : Optional.empty();
    }

    private void syncFromSoil(FarmingBlockEntity soil) {
        this.boneMealNutrient = soil.getBoneMealNutrient();
        this.turquoiseNutrient = soil.getTurquoiseNutrient();
        this.sulphurousAshNutrient = soil.getSulphurousAshNutrient();
        this.rottenFleshNutrient = soil.getRottenFleshNutrient();
        setChangedAndSync();
    }

    private void setSoilHydrationLevel(FarmingBlockEntity soil, int value) {
        int clamped = Math.max(0, Math.min(FarmingBlockEntity.MAX_HYDRATION, value));
        soil.setHydration(clamped);
        if (level != null && !level.isClientSide) {
            BlockState soilState = level.getBlockState(getSoilPos());
            if (soilState.getBlock() instanceof FarmingBlock) {
                level.setBlock(getSoilPos(), soilState.setValue(FarmingBlock.HYDRATION, clamped), 3);
            }
        }
        syncFromSoil(soil);
    }

    private int getLegacyHydrationLevel() {
        return Math.max(0, Math.min(FarmingBlockEntity.MAX_HYDRATION, Math.round(hydration * FarmingBlockEntity.MAX_HYDRATION)));
    }

    private void clearSoilCrop(ServerLevel level) {
        BlockState soilState = level.getBlockState(getSoilPos());
        if (soilState.getBlock() instanceof FarmingBlock) {
            if (level.getBlockEntity(getSoilPos()) instanceof FarmingBlockEntity farmBlockEntity) {
                farmBlockEntity.clearCrop();
            }
            level.setBlock(getSoilPos(), soilState.setValue(FarmingBlock.HAS_SEEDS, false), 3);
        }
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float normalizedHydration(int rawHydration) {
        int clamped = Math.max(0, Math.min(FarmingBlockEntity.MAX_HYDRATION, rawHydration));
        return clamped / (float) FarmingBlockEntity.MAX_HYDRATION;
    }

    private static float treeHydrationFit(float actual, CropDefinition crop) {
        float hydration = clamp01(actual);
        if (hydration <= 0.0f) {
            return 0.0f;
        }

        float ideal = clamp01(crop.hydrationIdeal());
        if (hydration < ideal) {
            float distance = ideal - hydration;
            float penalty = Math.min(1.0f, distance / Math.max(0.01f, crop.hydrationUnderTolerance()));
            return Mth.clamp(1.0f - penalty * 0.65f, 0.20f, 1.0f);
        }

        if (hydration > ideal) {
            float distance = hydration - ideal;
            float penalty = Math.min(1.0f, distance / Math.max(0.01f, crop.hydrationOverTolerance()));
            return Mth.clamp(1.0f - penalty * 0.85f, 0.15f, 1.0f);
        }

        return 1.0f;
    }

    private static FruitTreeGrowthPhase parseGrowthPhase(String value) {
        try {
            return FruitTreeGrowthPhase.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return FruitTreeGrowthPhase.STRUCTURE;
        }
    }

    private enum FruitTreeGrowthPhase {
        STRUCTURE,
        CANOPY,
        FRUITING,
        RIPENING
    }

    private enum PlacementKind {
        TRUNK("trunk", true),
        PRIMARY_BRANCH("primary branch", true),
        SECONDARY_BRANCH("secondary branch", false),
        LEAF("leaf", false),
        FRUIT("fruit", false);

        private final String debugName;
        private final boolean critical;

        PlacementKind(String debugName, boolean critical) {
            this.debugName = debugName;
            this.critical = critical;
        }

        public String debugName() {
            return debugName;
        }

        public boolean isCritical() {
            return critical;
        }
    }

    private record PlacementDecision(boolean placeable, boolean hardBlocked, String reason) {
        static PlacementDecision place() {
            return new PlacementDecision(true, false, "");
        }

        static PlacementDecision soft(String reason) {
            return new PlacementDecision(false, false, reason);
        }

        static PlacementDecision hard(String reason) {
            return new PlacementDecision(false, true, reason);
        }
    }

    private record PlacementResult(
            int planned,
            int successful,
            int softSkipped,
            int hardBlocked,
            float successRatio,
            float stageThreshold,
            boolean canAdvance,
            String firstSoftSkip,
            String firstHardBlock,
            String advanceReason
    ) {
    }

    private record GrowthMultiplierResult(float multiplier, boolean floorApplied) {
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("TreeTypeId", getTreeTypeId());
        tag.putLong("TreeSeed", treeSeed);
        tag.putInt("GrowthStep", growthStep);
        tag.putFloat("GrowthProgress", growthProgress);
        tag.putInt("TotalFruit", totalFruit);
        tag.putInt("HarvestedFruit", harvestedFruit);
        tag.putFloat("BoneMealNutrient", boneMealNutrient);
        tag.putFloat("TurquoiseNutrient", turquoiseNutrient);
        tag.putFloat("SulphurousAshNutrient", sulphurousAshNutrient);
        tag.putFloat("RottenFleshNutrient", rottenFleshNutrient);
        tag.putLong("LastGrowthGameTime", lastGrowthGameTime);
        tag.putBoolean("GeneratedInitialSeed", generatedInitialSeed);
        tag.putBoolean("GrowthBlocked", growthBlocked);
        tag.putString("LastGrowthStatus", lastGrowthStatus);
        tag.putString("LastGrowthFailureReason", lastGrowthFailureReason);
        tag.putLong("LastGrowthAttemptGameTime", lastGrowthAttemptGameTime);
        tag.putFloat("LastGrowthMultiplier", lastGrowthMultiplier);
        tag.putFloat("LastFertilizerFit", lastFertilizerFit);
        tag.putFloat("LastHydrationFit", lastHydrationFit);
        tag.putFloat("LastClimateFit", lastClimateFit);
        tag.putBoolean("LastAltitudeAllowed", lastAltitudeAllowed);
        tag.putBoolean("LastClimateAllowed", lastClimateAllowed);
        tag.putInt("LastPlannedPlacements", lastPlannedPlacements);
        tag.putInt("LastSuccessfulPlacements", lastSuccessfulPlacements);
        tag.putInt("LastFruitCandidateCount", lastFruitCandidateCount);
        tag.putFloat("LastFruitSaturation", lastFruitSaturation);
        tag.putFloat("LastConditionScore", lastConditionScore);
        tag.putInt("LastExpectedFruitCount", lastExpectedFruitCount);
        tag.putInt("LastSoftSkippedPlacements", lastSoftSkippedPlacements);
        tag.putInt("LastHardBlockedPlacements", lastHardBlockedPlacements);
        tag.putFloat("LastPlacementSuccessRatio", lastPlacementSuccessRatio);
        tag.putFloat("LastPlacementStageThreshold", lastPlacementStageThreshold);
        tag.putBoolean("LastStageCanAdvance", lastStageCanAdvance);
        tag.putString("LastSoftSkipReason", lastSoftSkipReason);
        tag.putString("LastHardBlockPlacementReason", lastHardBlockPlacementReason);
        tag.putString("LastStageAdvanceReason", lastStageAdvanceReason);
        tag.putString("LastGrowthPhase", lastGrowthPhase.name());
        tag.putFloat("LastBaseProgressPerTick", lastBaseProgressPerTick);
        tag.putFloat("LastEffectiveProgressAdded", lastEffectiveProgressAdded);
        tag.putInt("FailedGrowthRolls", failedGrowthRolls);
        tag.putBoolean("LastGrowthFloorApplied", lastGrowthFloorApplied);
        tag.putString("LastHardBlockReason", lastHardBlockReason);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        treeTypeId = tag.contains("TreeTypeId") ? tag.getString("TreeTypeId") : FruitTreeRegistry.DEFAULT_TREE_ID;
        treeTypeId = FruitTreeRegistry.byIdOrDefault(treeTypeId).id();
        treeSeed = tag.getLong("TreeSeed");
        growthStep = Math.max(1, Math.min(definition().maxGrowthStep(), tag.getInt("GrowthStep")));
        growthProgress = tag.contains("GrowthProgress") ? Mth.clamp(tag.getFloat("GrowthProgress"), 0.0f, GROWTH_PROGRESS_TARGET) : 0.0f;
        totalFruit = tag.getInt("TotalFruit");
        harvestedFruit = tag.getInt("HarvestedFruit");
        boneMealNutrient = clamp01(tag.getFloat("BoneMealNutrient"));
        turquoiseNutrient = clamp01(tag.getFloat("TurquoiseNutrient"));
        sulphurousAshNutrient = clamp01(tag.getFloat("SulphurousAshNutrient"));
        rottenFleshNutrient = clamp01(tag.getFloat("RottenFleshNutrient"));
        hydration = clamp01(tag.getFloat("Hydration"));
        lastGrowthGameTime = tag.getLong("LastGrowthGameTime");
        generatedInitialSeed = tag.getBoolean("GeneratedInitialSeed");
        growthBlocked = tag.getBoolean("GrowthBlocked");
        lastGrowthStatus = tag.contains("LastGrowthStatus") ? tag.getString("LastGrowthStatus") : "No growth attempt recorded";
        lastGrowthFailureReason = tag.contains("LastGrowthFailureReason") ? tag.getString("LastGrowthFailureReason") : "No random tick received";
        lastGrowthAttemptGameTime = tag.getLong("LastGrowthAttemptGameTime");
        lastGrowthMultiplier = tag.getFloat("LastGrowthMultiplier");
        lastFertilizerFit = tag.getFloat("LastFertilizerFit");
        lastHydrationFit = tag.getFloat("LastHydrationFit");
        lastClimateFit = tag.getFloat("LastClimateFit");
        lastAltitudeAllowed = !tag.contains("LastAltitudeAllowed") || tag.getBoolean("LastAltitudeAllowed");
        lastClimateAllowed = !tag.contains("LastClimateAllowed") || tag.getBoolean("LastClimateAllowed");
        lastPlannedPlacements = tag.getInt("LastPlannedPlacements");
        lastSuccessfulPlacements = tag.getInt("LastSuccessfulPlacements");
        lastFruitCandidateCount = tag.getInt("LastFruitCandidateCount");
        lastFruitSaturation = tag.getFloat("LastFruitSaturation");
        lastConditionScore = tag.getFloat("LastConditionScore");
        lastExpectedFruitCount = tag.getInt("LastExpectedFruitCount");
        lastSoftSkippedPlacements = tag.getInt("LastSoftSkippedPlacements");
        lastHardBlockedPlacements = tag.getInt("LastHardBlockedPlacements");
        lastPlacementSuccessRatio = tag.getFloat("LastPlacementSuccessRatio");
        lastPlacementStageThreshold = tag.getFloat("LastPlacementStageThreshold");
        lastStageCanAdvance = !tag.contains("LastStageCanAdvance") || tag.getBoolean("LastStageCanAdvance");
        lastSoftSkipReason = tag.contains("LastSoftSkipReason") ? tag.getString("LastSoftSkipReason") : "none";
        lastHardBlockPlacementReason = tag.contains("LastHardBlockPlacementReason") ? tag.getString("LastHardBlockPlacementReason") : "none";
        lastStageAdvanceReason = tag.contains("LastStageAdvanceReason") ? tag.getString("LastStageAdvanceReason") : "No placement attempt recorded";
        lastGrowthPhase = tag.contains("LastGrowthPhase") ? parseGrowthPhase(tag.getString("LastGrowthPhase")) : phaseForStep(growthStep);
        lastBaseProgressPerTick = tag.contains("LastBaseProgressPerTick") ? tag.getFloat("LastBaseProgressPerTick") : BASE_PROGRESS_PER_RANDOM_TICK;
        lastEffectiveProgressAdded = tag.getFloat("LastEffectiveProgressAdded");
        failedGrowthRolls = tag.getInt("FailedGrowthRolls");
        lastGrowthFloorApplied = tag.getBoolean("LastGrowthFloorApplied");
        lastHardBlockReason = tag.contains("LastHardBlockReason") ? tag.getString("LastHardBlockReason") : (growthBlocked ? lastGrowthFailureReason : "none");
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
}
