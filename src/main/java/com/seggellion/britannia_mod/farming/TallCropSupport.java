package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.block.CornStalkBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class TallCropSupport {
    private static final int CORN_MAX_UPPER_SEGMENTS = 3;
    private static final int CROP_KIND_CORN = 0;
    private static final int CROP_KIND_BANANA = 1;

    private TallCropSupport() {
    }

    public static int heightForStage(CropDefinition crop, int stage) {
        if (!crop.tallCrop()) {
            return 1;
        }
        if (usesSegmentedTallShape(crop)) {
            return 1 + upperSegmentCountForStage(crop, stage);
        }
        int maxStage = Math.max(1, crop.growthStages() - 1);
        float stageRatio = Math.max(0.0f, Math.min(1.0f, stage / (float) maxStage));
        return Math.max(1, Math.min(crop.maxHeight(), 1 + Math.round(stageRatio * (crop.maxHeight() - 1))));
    }

    public static int upperSegmentCountForStage(CropDefinition crop, int stage) {
        if (crop == null || !crop.tallCrop()) {
            return 0;
        }
        if (usesSegmentedTallShape(crop)) {
            if (stage >= 5) return Math.min(CORN_MAX_UPPER_SEGMENTS, Math.max(0, crop.maxHeight() - 1));
            if (stage >= 1) return Math.min(2, Math.max(0, crop.maxHeight() - 1));
            return 0;
        }
        return Math.max(0, heightForStage(crop, stage) - 1);
    }

    public static int maxUpperSegmentCount(CropDefinition crop) {
        if (crop == null || !crop.tallCrop()) {
            return 0;
        }
        if (usesSegmentedTallShape(crop)) {
            return CORN_MAX_UPPER_SEGMENTS;
        }
        return Math.max(0, crop.maxHeight() - 1);
    }

    public static String maxHeightSemantics(CropDefinition crop) {
        if (crop != null && usesSegmentedTallShape(crop)) {
            return "total_visible_height_including_base";
        }
        return "total_visible_height";
    }

    public static boolean canGrowToStage(Level level, BlockPos basePos, CropDefinition crop, int stage) {
        if (!crop.tallCrop()) {
            return true;
        }
        int upperSegments = upperSegmentCountForStage(crop, stage);
        for (int offset = 1; offset <= upperSegments; offset++) {
            BlockState state = level.getBlockState(basePos.above(offset));
            if (!(state.canBeReplaced() || state.getBlock() instanceof CornStalkBlock)) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasRequiredVisuals(Level level, BlockPos basePos, CropDefinition crop, int stage) {
        if (!crop.tallCrop()) {
            return true;
        }
        int upperSegments = upperSegmentCountForStage(crop, stage);
        for (int offset = 1; offset <= upperSegments; offset++) {
            if (!isExpectedSegment(level.getBlockState(basePos.above(offset)), crop, stage, offset)) {
                return false;
            }
        }
        return true;
    }

    public static int currentHeight(Level level, BlockPos basePos, CropDefinition crop, int stage) {
        if (level == null || crop == null || !crop.tallCrop()) {
            return 1;
        }
        return 1 + currentUpperSegmentCount(level, basePos, crop, stage);
    }

    public static int currentUpperSegmentCount(Level level, BlockPos basePos, CropDefinition crop, int stage) {
        if (level == null || crop == null || !crop.tallCrop()) {
            return 0;
        }
        int expectedUpperSegments = upperSegmentCountForStage(crop, stage);
        int upperSegments = 0;
        for (int offset = 1; offset <= expectedUpperSegments; offset++) {
            if (!isExpectedSegment(level.getBlockState(basePos.above(offset)), crop, stage, offset)) {
                break;
            }
            upperSegments++;
        }
        return upperSegments;
    }

    public static String missingSegmentReason(Level level, BlockPos basePos, CropDefinition crop, int stage) {
        if (level == null || crop == null || !crop.tallCrop()) {
            return "none";
        }
        int upperSegments = upperSegmentCountForStage(crop, stage);
        for (int offset = 1; offset <= upperSegments; offset++) {
            BlockState state = level.getBlockState(basePos.above(offset));
            if (!isExpectedSegment(state, crop, stage, offset)) {
                return "offset=" + offset
                        + ", expected_age=" + Math.max(0, Math.min(crop.maxGrowthAge(), stage))
                        + ", expected_part=" + expectedPartForOffset(crop, stage, offset)
                        + ", actual=" + segmentStateSummary(state);
            }
        }
        return "none";
    }

    public static boolean repairStructureIfPossible(Level level, BlockPos basePos, CropDefinition crop, int stage) {
        if (level == null || level.isClientSide || crop == null || !crop.tallCrop()) {
            return true;
        }
        if (hasRequiredVisuals(level, basePos, crop, stage)) {
            return true;
        }
        if (!canGrowToStage(level, basePos, crop, stage)) {
            return false;
        }
        update(level, basePos, crop, stage);
        return hasRequiredVisuals(level, basePos, crop, stage);
    }

    public static void update(Level level, BlockPos basePos, CropDefinition crop, int stage) {
        if (level == null || level.isClientSide || !crop.tallCrop()) {
            return;
        }

        int upperSegments = upperSegmentCountForStage(crop, stage);
        int maxUpperSegments = maxUpperSegmentCount(crop);
        for (int offset = 1; offset <= maxUpperSegments; offset++) {
            BlockPos pos = basePos.above(offset);
            BlockState current = level.getBlockState(pos);
            if (offset <= upperSegments) {
                if (current.canBeReplaced() || current.getBlock() instanceof CornStalkBlock) {
                    level.setBlock(pos, BlockRegistry.CORN_STALK_BLOCK.get().defaultBlockState()
                            .setValue(CornStalkBlock.AGE, Math.max(0, Math.min(crop.maxGrowthAge(), stage)))
                            .setValue(CornStalkBlock.PART, expectedPartForOffset(crop, stage, offset))
                            .setValue(CornStalkBlock.CROP_KIND, cropKind(crop)), 3);
                }
            } else if (current.getBlock() instanceof CornStalkBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    public static void clear(Level level, BlockPos basePos, CropDefinition crop) {
        if (level == null || level.isClientSide || crop == null || !crop.tallCrop()) {
            return;
        }
        for (int offset = 1; offset <= maxUpperSegmentCount(crop); offset++) {
            BlockPos pos = basePos.above(offset);
            if (level.getBlockState(pos).getBlock() instanceof CornStalkBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    public static BlockPos findAnchor(Level level, BlockPos pos) {
        for (int offset = 1; offset <= 3; offset++) {
            BlockPos candidate = pos.below(offset);
            if (level.getBlockEntity(candidate) instanceof FarmingBlockEntity farmBe) {
                CropDefinition crop = CropRegistry.byId(farmBe.getPlantedCropId()).orElse(null);
                if (crop != null && crop.tallCrop()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    public static String segmentName(BlockState state) {
        if (!(state.getBlock() instanceof CornStalkBlock)) {
            return "base";
        }
        return switch (state.getValue(CornStalkBlock.PART)) {
            case 1 -> "lower";
            case 2 -> "middle";
            case 3 -> "top";
            default -> "legacy_part_0";
        };
    }

    public static String segmentStateSummary(BlockState state) {
        if (!(state.getBlock() instanceof CornStalkBlock)) {
            return state.getBlock().builtInRegistryHolder().key().location().toString();
        }
        return state.getBlock().builtInRegistryHolder().key().location()
                + "[age=" + state.getValue(CornStalkBlock.AGE)
                + ",part=" + state.getValue(CornStalkBlock.PART)
                + ",crop_kind=" + state.getValue(CornStalkBlock.CROP_KIND)
                + "]";
    }

    public static String segmentModelPath(BlockState state) {
        if (!(state.getBlock() instanceof CornStalkBlock)) {
            return "<none>";
        }
        int age = state.getValue(CornStalkBlock.AGE);
        int part = state.getValue(CornStalkBlock.PART);
        String cropPath = state.getValue(CornStalkBlock.CROP_KIND) == CROP_KIND_BANANA ? "banana" : "corn";
        if (part == 3 && age >= 1) {
            return "britannia_mod:block/crops/" + cropPath + "/" + cropPath + "_top_age_" + age;
        }
        if (part == 2 && age >= 5) {
            return "britannia_mod:block/crops/" + cropPath + "/" + cropPath + "_middle_age_" + age;
        }
        if (part == 1 && age >= 1) {
            return "britannia_mod:block/crops/" + cropPath + "/" + cropPath + "_lower_age_" + age;
        }
        return "britannia_mod:block/crops/" + cropPath + "/" + cropPath + "_stalk_invisible";
    }

    private static boolean isExpectedSegment(BlockState state, CropDefinition crop, int stage, int offset) {
        if (!(state.getBlock() instanceof CornStalkBlock)) {
            return false;
        }
        int expectedAge = Math.max(0, Math.min(crop.maxGrowthAge(), stage));
        int actualPart = state.getValue(CornStalkBlock.PART);
        return state.getValue(CornStalkBlock.AGE) == expectedAge
                && actualPart == expectedPartForOffset(crop, stage, offset)
                && state.getValue(CornStalkBlock.CROP_KIND) == cropKind(crop);
    }

    private static int expectedPartForOffset(CropDefinition crop, int stage, int offset) {
        if (usesSegmentedTallShape(crop)) {
            return expectedCornPartForOffsetAndAge(offset, stage);
        }
        return Math.max(0, Math.min(3, offset));
    }

    private static int expectedCornPartForOffsetAndAge(int offset, int age) {
        if (age >= 5) {
            return Math.max(1, Math.min(3, offset));
        }
        if (age >= 1) {
            return offset == 1 ? 1 : 3;
        }
        return 0;
    }

    private static boolean usesSegmentedTallShape(CropDefinition crop) {
        return crop != null && ("corn".equals(crop.id()) || "banana".equals(crop.id()));
    }

    private static int cropKind(CropDefinition crop) {
        return crop != null && "banana".equals(crop.id()) ? CROP_KIND_BANANA : CROP_KIND_CORN;
    }
}
