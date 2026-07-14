package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.seggellion.britannia_mod.block.CornStalkBlock;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.block.entity.OrangeTreeRootBlockEntity;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropGrowthContext;
import com.seggellion.britannia_mod.farming.CropQualityCalculator;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.CropVisualModels;
import com.seggellion.britannia_mod.farming.CropVisualRotation;
import com.seggellion.britannia_mod.farming.FarmingClimateResolver;
import com.seggellion.britannia_mod.farming.FruitTreeDefinition;
import com.seggellion.britannia_mod.farming.GrapeVisualResolver;
import com.seggellion.britannia_mod.farming.OrangeTreeUtils;
import com.seggellion.britannia_mod.farming.GrainHarvestTools;
import com.seggellion.britannia_mod.farming.RootCropShovelTools;
import com.seggellion.britannia_mod.farming.TallCropSupport;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class FarmingDebugCommand {
    private FarmingDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("farming")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug")
                        .executes(FarmingDebugCommand::debugLookedAtBlock)));
    }

    private static int debugLookedAtBlock(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception ex) {
            source.sendFailure(Component.literal("This command must be run by a player looking at a Farming Block."));
            return 0;
        }

        HitResult hitResult = player.pick(20.0D, 0.0F, false);
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at a Farming Block and run /farming debug again."));
            return 0;
        }

        Level level = player.level();
        BlockPos lookedAtPos = blockHitResult.getBlockPos();
        BlockState lookedAtState = level.getBlockState(lookedAtPos);
        OrangeTreeRootBlockEntity fruitTreeRoot = OrangeTreeUtils.findRoot(level, lookedAtPos).orElse(null);
        if (fruitTreeRoot != null) {
            debugOrangeTree(source, level, lookedAtPos, fruitTreeRoot, player);
            return 1;
        }

        if (lookedAtState.getBlock() instanceof FarmingBlock
                && level.getBlockEntity(lookedAtPos.above()) instanceof OrangeTreeRootBlockEntity rootAbove) {
            debugOrangeTree(source, level, lookedAtPos, rootAbove, player);
            return 1;
        }

        BlockPos debugPos = lookedAtPos;
        BlockState debugState = lookedAtState;
        String lookedAtSegment = "base";
        String lookedAtPart = integerPropertyValue(lookedAtState, "part");
        if (lookedAtState.getBlock() instanceof CornStalkBlock) {
            BlockPos anchor = TallCropSupport.findAnchor(level, lookedAtPos);
            if (anchor == null) {
                source.sendFailure(Component.literal("Target corn segment is not attached to a Farming Block anchor."));
                return 0;
            }
            debugPos = anchor;
            debugState = level.getBlockState(anchor);
            lookedAtSegment = TallCropSupport.segmentName(lookedAtState);
        }

        final BlockPos pos = debugPos;
        final BlockState state = debugState;
        final String targetSegment = lookedAtSegment;
        final String targetPart = lookedAtPart;
        final BlockPos targetPos = lookedAtPos;
        if (!(state.getBlock() instanceof FarmingBlock) || !(level.getBlockEntity(pos) instanceof FarmingBlockEntity farmBe)) {
            source.sendFailure(Component.literal("Target block is not a Farming Block with farming data."));
            return 0;
        }

        CropDefinition crop = CropRegistry.byId(farmBe.getPlantedCropId()).orElse(null);
        int stateHydration = state.getValue(FarmingBlock.HYDRATION);
        int blockEntityHydration = farmBe.getHydration();
        int syncedHydration = FarmingBlock.getSyncedHydration(level, pos, state);
        boolean harvestReady = crop != null
                && farmBe.isMature()
                && (!crop.tallCrop() || TallCropSupport.hasRequiredVisuals(level, pos, crop, crop.maxGrowthAge()));
        source.sendSuccess(() -> Component.literal("Farming debug at " + pos.toShortString()), false);
        source.sendSuccess(() -> Component.literal("has_seeds=" + state.getValue(FarmingBlock.HAS_SEEDS)
                + ", crop_id=" + (crop == null ? "<none>" : crop.id())
                + ", lifecycle=" + (crop == null ? "<none>" : crop.lifecycle())
                + ", growth_habit=" + (crop == null ? "<none>" : crop.growthHabit())
                + ", support_requirement=" + (crop == null ? "<none>" : crop.supportRequirement())
                + ", harvest_tool=" + (crop == null ? "<none>" : crop.harvestTool())
                + ", nutrient_preference=" + (crop == null ? "<none>" : crop.nutrientPreferenceMode())
                + ", maximum_nutrients_override=" + (crop != null && crop.prefersMaximumNutrients())), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "progress: value=%.3f, age=%d, maxAge=%s, mature=%s, harvest_ready=%s, blocked=%s",
                farmBe.getGrowthProgress(),
                farmBe.getGrowthStage(),
                crop == null ? "<none>" : crop.maxGrowthAge(),
                farmBe.isMature(),
                harvestReady,
                farmBe.isGrowthBlocked()
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "hydration: state=%d, block_entity=%d, synced=%d, normalized=%.3f, rain_hydrating=%s, outdoors=%s",
                stateHydration,
                blockEntityHydration,
                syncedHydration,
                syncedHydration / (float) FarmingBlockEntity.MAX_HYDRATION,
                FarmingBlock.isRainHydrating(level, pos),
                FarmingBlock.isOutdoorsForRain(level, pos)
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "nutrients: bone=%.3f, turquoise=%.3f, ash=%.3f, flesh=%.3f",
                farmBe.getBoneMealNutrient(),
                farmBe.getTurquoiseNutrient(),
                farmBe.getSulphurousAshNutrient(),
                farmBe.getRottenFleshNutrient()
        )), false);

        if (crop != null) {
            CropGrowthContext growthContext = farmBe.createGrowthContext(level, pos, crop, player);
            int quality = CropQualityCalculator.calculateQuality(crop, growthContext, player, farmBe.getRootAgeDays());
            float multiplier = CropQualityCalculator.growthMultiplier(growthContext);
            int growthAge = farmBe.getGrowthStage();
            int visualAge = CropVisualModels.visualModelStage(crop, growthAge);
            source.sendSuccess(() -> Component.literal(String.format(
                    "environment: climate=%s, altitude=%d, climate_allowed=%s, altitude_allowed=%s, special_environment_allowed=%s",
                    FarmingClimateResolver.resolveClimateName(level, pos),
                    pos.getY(),
                    growthContext.climateAllowed(),
                    growthContext.altitudeAllowed(),
                    growthContext.specialEnvironmentAllowed()
            )), false);
            source.sendSuccess(() -> Component.literal(String.format(
                    "nightshade_environment: required=%s, dark_enough=%s, underground=%s, sky_light=%d, block_light=%d",
                    growthContext.requiresDarknessOrUnderground(),
                    growthContext.darkEnough(),
                    growthContext.underground(),
                    growthContext.skyLight(),
                    growthContext.blockLight()
            )), false);
            source.sendSuccess(() -> Component.literal(
                    "region_resolver: " + FarmingClimateResolver.resolveRegionDebugReason(level, pos)
            ), false);
            source.sendSuccess(() -> Component.literal(String.format(
                    "fits: nutrient=%.3f, hydration=%.3f, climate=%.3f, nutrient_preference=%s, maximum_nutrients_override=%s, requires_lattice=%s, support_requirement=%s, support_satisfied=%s, ideal=%s, growth_multiplier=%.3f, quality_estimate=%d/100",
                    growthContext.nutrientFit(),
                    growthContext.hydrationFit(),
                    growthContext.climateFit(),
                    crop.nutrientPreferenceMode(),
                    crop.prefersMaximumNutrients(),
                    crop.requiresLattice(),
                    crop.supportRequirement(),
                    growthContext.latticeSatisfied(),
                    growthContext.idealGrowth(),
                    multiplier,
                    quality
            )), false);
            source.sendSuccess(() -> Component.literal(String.format(
                    "definition: visualAgeCount=%d, maxAge=%d, lifecycle=%s, habit=%s, harvest_tool=%s, held_can_harvest=%s, held_grain_blade=%s, held_root_shovel=%s, requires_lattice=%s, tall_crop=%s, max_height=%d, model_visible_height=%d, post_harvest_regrowth_age=%d, root_age_bonus_enabled=%s, yield=%d-%d",
                    crop.visualAgeCount(),
                    crop.maxGrowthAge(),
                    crop.lifecycle(),
                    crop.growthHabit(),
                    crop.harvestTool(),
                    heldItemCanHarvest(crop, player),
                    GrainHarvestTools.isGrainHarvestBlade(player.getMainHandItem()),
                    RootCropShovelTools.isRootCropShovel(player.getMainHandItem()),
                    crop.requiresLattice(),
                    crop.tallCrop(),
                    crop.maxHeight(),
                    CropVisualModels.modelVisibleHeight(crop, growthAge),
                    crop.clampedPostHarvestRegrowthAge(),
                    crop.usesRootAgeQualityBonus(),
                    crop.minYield(),
                    crop.maxYield()
            )), false);
            source.sendSuccess(() -> Component.literal(String.format(
                    "root_age: established_game_time=%d, minecraft_days=%d, quality_bonus=%d",
                    farmBe.getRootEstablishedGameTime(),
                    farmBe.getRootAgeDays(),
                    farmBe.getRootAgeQualityBonus(crop)
            )), false);
            if ("grapes".equals(crop.id())) {
                GrapeVisualResolver.GrapeVisualInfo grapeVisual = GrapeVisualResolver.resolve(crop, growthAge, farmBe.getStoredSeed());
                source.sendSuccess(() -> Component.literal(String.format(
                        "grape_visual: requested_variety=%s, resolved_variety=%s, name=%s, color=%s, visual_age=%d, model=%s, model_file_exists=%s, fallback_variety=%s, fallback_color=%s",
                        grapeVisual.requestedVarietyId(),
                        grapeVisual.resolvedVarietyId(),
                        grapeVisual.resolvedVarietyName(),
                        grapeVisual.color(),
                        grapeVisual.visualAge(),
                        grapeVisual.model(),
                        modelResourceExists(grapeVisual.model()),
                        grapeVisual.fallbackVariety(),
                        grapeVisual.fallbackColor()
                )), false);
            }
            source.sendSuccess(() -> Component.literal(String.format(
                    "visual: block_entity_age=%d, maxAge=%d, blockstate_age=%s, visual_age=%d, visualAgeCount=%d, model=%s, model_file_exists=%s",
                    growthAge,
                    crop.maxGrowthAge(),
                    integerPropertyValue(state, "age"),
                    visualAge,
                    CropVisualModels.visualModelStageCount(crop),
                    visualModelPath(crop, growthAge, farmBe.getStoredSeed()),
                    modelResourceExists(CropVisualModels.modelLocation(crop, growthAge, farmBe.getStoredSeed()))
            )), false);
            if (crop.tallCrop()) {
                int expectedHeight = TallCropSupport.heightForStage(crop, growthAge);
                int currentHeight = TallCropSupport.currentHeight(level, pos, crop, growthAge);
                int expectedUpperSegments = TallCropSupport.upperSegmentCountForStage(crop, growthAge);
                int currentUpperSegments = TallCropSupport.currentUpperSegmentCount(level, pos, crop, growthAge);
                boolean hasCurrentStructure = TallCropSupport.hasRequiredVisuals(level, pos, crop, growthAge);
                boolean fullStructure = hasCurrentStructure;
                boolean canGrowCurrent = TallCropSupport.canGrowToStage(level, pos, crop, growthAge);
                String missingSegment = TallCropSupport.missingSegmentReason(level, pos, crop, growthAge);
                ResourceLocation baseModel = CropVisualModels.modelLocation(crop, growthAge);
                BlockState aboveOneState = level.getBlockState(pos.above());
                BlockState aboveTwoState = level.getBlockState(pos.above(2));
                BlockState aboveThreeState = level.getBlockState(pos.above(3));
                String aboveOne = TallCropSupport.segmentStateSummary(aboveOneState)
                        + ", model=" + TallCropSupport.segmentModelPath(aboveOneState);
                String aboveTwo = TallCropSupport.segmentStateSummary(aboveTwoState)
                        + ", model=" + TallCropSupport.segmentModelPath(aboveTwoState);
                String aboveThree = TallCropSupport.segmentStateSummary(aboveThreeState)
                        + ", model=" + TallCropSupport.segmentModelPath(aboveThreeState);
                source.sendSuccess(() -> Component.literal(String.format(
                        "tall_structure: looked_at=%s, segment=%s, part=%s, base=%s, expected_shape=%s, base_model=%s, base_model_file_exists=%s, max_height_raw=%d, max_height_semantics=%s, expected_upper_segments=%d, current_upper_segments=%d, expected_total_visible_height=%d, current_total_visible_height=%d, can_grow_current=%s, has_required_current=%s, full_structure_valid=%s, missing=%s, harvest_ready=%s",
                        targetPos.toShortString(),
                        targetSegment,
                        targetPart,
                        pos.toShortString(),
                        tallExpectedShape(expectedUpperSegments),
                        baseModel,
                        modelResourceExists(baseModel),
                        crop.maxHeight(),
                        TallCropSupport.maxHeightSemantics(crop),
                        expectedUpperSegments,
                        currentUpperSegments,
                        expectedHeight,
                        currentHeight,
                        canGrowCurrent,
                        hasCurrentStructure,
                        fullStructure,
                        missingSegment,
                        harvestReady
                )), false);
                source.sendSuccess(() -> Component.literal("tall_segments: above1=" + aboveOne + ", above2=" + aboveTwo + ", above3=" + aboveThree), false);
            }
            source.sendSuccess(() -> Component.literal(String.format(
                    "visual_rotation: enabled=%s, yaw=%.1f, render_source=%s",
                    CropVisualRotation.isEnabledFor(crop),
                    CropVisualRotation.isEnabledFor(crop) ? CropVisualRotation.yawFor(pos, crop) : 0.0f,
                    visualRenderSource(crop)
            )), false);
        }

        return 1;
    }

    private static String visualModelPath(CropDefinition crop, int growthAge, String cropVariant) {
        return CropVisualModels.modelLocation(crop, growthAge, cropVariant).toString();
    }

    private static boolean modelResourceExists(ResourceLocation model) {
        String resourcePath = "assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json";
        ClassLoader classLoader = FarmingDebugCommand.class.getClassLoader();
        return classLoader != null && classLoader.getResource(resourcePath) != null;
    }

    private static String tallExpectedShape(int expectedUpperSegments) {
        return switch (expectedUpperSegments) {
            case 0 -> "base_only";
            case 2 -> "two_segment";
            default -> "base_plus_lower_middle_top";
        };
    }

    private static boolean heldItemCanHarvest(CropDefinition crop, ServerPlayer player) {
        return switch (crop.harvestTool()) {
            case HAND -> true;
            case BARE_HAND -> player.getMainHandItem().isEmpty();
            case SCISSORS -> player.getMainHandItem().is(ItemRegistry.SCISSORS.get());
            case GRAIN_BLADE -> GrainHarvestTools.isGrainHarvestBlade(player.getMainHandItem());
            case ROOT_SHOVEL -> RootCropShovelTools.isRootCropShovel(player.getMainHandItem());
        };
    }

    private static String integerPropertyValue(BlockState state, String propertyName) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(propertyName)) {
                return String.valueOf(state.getValue(property));
            }
        }
        return "<none>";
    }

    private static String visualRenderSource(CropDefinition crop) {
        if (crop.treeCrop()) {
            return "fruit_tree_blocks";
        }
        if ("grapes".equals(crop.id())) {
            return "farming_block_entity_variety_crop";
        }
        if (crop.supportRequirement().name().contains("TRELLIS")) {
            return "farming_block_entity_trellis_crop";
        }
        if (crop.requiresLattice()) {
            return "lattice_crop_blockstate";
        }
        if ("corn".equals(crop.id())) {
            return "farming_block_entity_anchor";
        }
        return "farming_block_entity_model";
    }

    private static void debugOrangeTree(CommandSourceStack source, Level level, BlockPos pos, OrangeTreeRootBlockEntity root, ServerPlayer player) {
        FruitTreeDefinition definition = root.definition();
        CropDefinition crop = CropRegistry.byId(root.getTreeTypeId()).orElse(null);
        BlockPos rootPos = root.getBlockPos();
        BlockPos soilPos = root.getSoilPos();
        BlockPos canopyMin = rootPos.offset(-definition.canopyRadiusX() - 1, 1, -definition.canopyRadiusZ() - 1);
        BlockPos canopyMax = rootPos.offset(definition.canopyRadiusX() + 1, definition.trunkHeight() + definition.canopyRadiusY() + 2, definition.canopyRadiusZ() + 1);
        source.sendSuccess(() -> Component.literal(definition.displayName() + " Tree Debug"), false);
        source.sendSuccess(() -> Component.literal("looked_at=" + pos.toShortString()
                + ", tree_type=" + root.getTreeTypeId()
                + ", soil_position=" + soilPos.toShortString()
                + ", root_position=" + rootPos.toShortString()), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "tree_seed=%d, growth_step=%d, max_step=%d, trunk_height=%d, total_fruit=%d, harvested_fruit=%d, blocked=%s, last_growth_time=%d",
                root.getTreeSeed(),
                root.getGrowthStep(),
                definition.maxGrowthStep(),
                definition.trunkHeight(),
                root.getTotalFruit(),
                root.getHarvestedFruit(),
                root.isGrowthBlocked(),
                root.getLastGrowthGameTime()
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "soil_hydration=%.3f (%d/%d), hydration_source=%s, rain_hydrating=%s, outdoors=%s, bone_meal=%.3f, turquoise=%.3f, sulphurous_ash=%.3f, rotten_flesh=%.3f",
                root.getHydration(),
                root.getHydrationLevel(),
                root.getMaxHydration(),
                root.isUsingSoilHydration() ? "FarmingBlockEntity" : "legacy_root_fallback",
                FarmingBlock.isRainHydrating(level, soilPos),
                FarmingBlock.isOutdoorsForRain(level, soilPos),
                root.getBoneMealNutrient(),
                root.getTurquoiseNutrient(),
                root.getSulphurousAshNutrient(),
                root.getRottenFleshNutrient()
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "last_attempt=%d, last_status=%s, last_failure=%s",
                root.getLastGrowthAttemptGameTime(),
                root.getLastGrowthStatus(),
                root.getLastGrowthFailureReason().isBlank() ? "<none>" : root.getLastGrowthFailureReason()
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "growth_phase=%s, growth_progress=%.3f/%.3f, base_progress_per_tick=%.3f, effective_progress_added=%.3f, failed_growth_rolls=%d, growth_floor_applied=%s, hard_block_reason=%s",
                root.getLastGrowthPhase(),
                root.getGrowthProgress(),
                root.getGrowthProgressTarget(),
                root.getLastBaseProgressPerTick(),
                root.getLastEffectiveProgressAdded(),
                root.getFailedGrowthRolls(),
                root.wasLastGrowthFloorApplied(),
                root.getLastHardBlockReason() == null || root.getLastHardBlockReason().isBlank() ? "none" : root.getLastHardBlockReason()
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "last_fits: fertilizer=%.3f, hydration_fit=%.3f, climate=%.3f, final_growth_multiplier=%.3f, climate_allowed=%s, altitude_allowed=%s",
                root.getLastFertilizerFit(),
                root.getLastHydrationFit(),
                root.getLastClimateFit(),
                root.getLastGrowthMultiplier(),
                root.isLastClimateAllowed(),
                root.isLastAltitudeAllowed()
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "placements: current_plan=%d, planned_trunks=%d, planned_branches=%d, last_planned=%d, last_successful=%d, canopy_bounds=%s to %s",
                root.getCurrentPlannedPlacementCount(),
                root.getCurrentPlannedTrunkCount(),
                root.getCurrentPlannedBranchCount(),
                root.getLastPlannedPlacements(),
                root.getLastSuccessfulPlacements(),
                canopyMin.toShortString(),
                canopyMax.toShortString()
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "placement_result: planned=%d, successful=%d, soft_skipped=%d, hard_blocked=%d, success_ratio=%.3f, stage_threshold=%.3f, stage_can_advance=%s",
                root.getLastPlannedPlacements(),
                root.getLastSuccessfulPlacements(),
                root.getLastSoftSkippedPlacements(),
                root.getLastHardBlockedPlacements(),
                root.getLastPlacementSuccessRatio(),
                root.getLastPlacementStageThreshold(),
                root.canLastStageAdvance()
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "placement_reasons: first_soft_skip=%s, first_hard_block=%s, advance_reason=%s",
                root.getLastSoftSkipReason() == null || root.getLastSoftSkipReason().isBlank() ? "none" : root.getLastSoftSkipReason(),
                root.getLastHardBlockPlacementReason() == null || root.getLastHardBlockPlacementReason().isBlank() ? "none" : root.getLastHardBlockPlacementReason(),
                root.getLastStageAdvanceReason() == null || root.getLastStageAdvanceReason().isBlank() ? "none" : root.getLastStageAdvanceReason()
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "fruit_yield: candidates=%d, saturation=%.3f, condition_score=%.3f, expected=%d, actual_totalFruit=%d",
                root.getLastFruitCandidateCount(),
                root.getLastFruitSaturation(),
                root.getLastConditionScore(),
                root.getLastExpectedFruitCount(),
                root.getTotalFruit()
        )), false);
        source.sendSuccess(() -> Component.literal("planned_trunks=" + root.getCurrentPlannedTrunkPositions()), false);
        source.sendSuccess(() -> Component.literal("planned_branches=" + root.getCurrentPlannedBranchPositions()), false);

        if (crop != null) {
            CropGrowthContext growthContext = root.createGrowthContext(level, soilPos, crop, player);
            int quality = CropQualityCalculator.calculateQuality(crop, growthContext, player);
            float fitMultiplier = CropQualityCalculator.growthMultiplier(growthContext);
            source.sendSuccess(() -> Component.literal(String.format(
                    "environment: region=%s, climate=%s, altitude=%d, climate_allowed=%s, altitude_allowed=%s",
                    root.resolveRegionName(),
                    FarmingClimateResolver.resolveClimateName(level, soilPos),
                    soilPos.getY(),
                    growthContext.climateAllowed(),
                    growthContext.altitudeAllowed()
            )), false);
            source.sendSuccess(() -> Component.literal(
                    "region_resolver: " + FarmingClimateResolver.resolveRegionDebugReason(level, soilPos)
            ), false);
            source.sendSuccess(() -> Component.literal(String.format(
                    "fits: fertilizer=%.3f, hydration_fit=%.3f, climate=%.3f, nutrient_preference=%s, maximum_nutrients_override=%s, fit_multiplier=%.3f, final_growth_multiplier=%.3f, hard_block=%s, Predicted Quality: %d/100",
                    growthContext.nutrientFit(),
                    growthContext.hydrationFit(),
                    growthContext.climateFit(),
                    crop.nutrientPreferenceMode(),
                    crop.prefersMaximumNutrients(),
                    fitMultiplier,
                    root.isGrowthBlocked() ? 0.0f : root.getLastGrowthMultiplier(),
                    root.isGrowthBlocked(),
                    quality
            )), false);
        }
    }
}
