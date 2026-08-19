package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.item.GrapesItem;
import com.seggellion.britannia_mod.winery.GrapeColor;
import com.seggellion.britannia_mod.winery.GrapeVariety;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class GrapeVisualResolver {
    private static final GrapeColor FALLBACK_COLOR = GrapeColor.PURPLE;
    private static final String MODEL_DIRECTORY = "block/crops/grapes/";
    private static final String MODEL_PREFIX = "grape_vine_stage_";

    /**
     * Gameplay age (0..7) to authored vine stage. Ages 0 and 1 share the seedling stage, so the
     * table stays explicit: stage numbers are not interchangeable with ages.
     */
    private static final int[] AGE_TO_VISUAL_STAGE = {1, 1, 2, 3, 4, 5, 6, 7};

    /** Stages at or above this one carry visible fruit and therefore exist once per grape colour. */
    private static final int FIRST_FRUITING_STAGE = 6;
    private static final int LAST_VISUAL_STAGE = 7;

    private GrapeVisualResolver() {
    }

    public static ResourceLocation modelLocation(CropDefinition crop, int growthAge, String varietyId) {
        int visualAge = crop == null ? 0 : Math.max(0, Math.min(crop.maxGrowthAge(), growthAge));
        return model(modelName(visualAge, resolvedColor(varietyId)));
    }

    public static GrapeVisualInfo resolve(CropDefinition crop, int growthAge, String varietyId) {
        String safeVarietyId = varietyId == null || varietyId.isBlank() ? GrapesItem.DEFAULT_VARIETY_ID : varietyId;
        GrapeVariety variety = GrapeVarietyManager.getVariety(safeVarietyId);
        int visualAge = crop == null ? 0 : Math.max(0, Math.min(crop.maxGrowthAge(), growthAge));
        GrapeColor color = variety.colorType() == null ? FALLBACK_COLOR : variety.colorType();
        boolean fallbackVariety = !safeVarietyId.equals(variety.id());
        boolean fallbackColor = variety.colorType() == null;
        ResourceLocation model = model(modelName(visualAge, color));
        return new GrapeVisualInfo(safeVarietyId, variety.id(), variety.getFormattedName(), color, visualAge, model, fallbackVariety, fallbackColor);
    }

    public static List<ResourceLocation> allModelLocations() {
        List<ResourceLocation> models = new ArrayList<>();
        for (int stage = 1; stage < FIRST_FRUITING_STAGE; stage++) {
            models.add(model(MODEL_PREFIX + stage));
        }
        for (int stage = FIRST_FRUITING_STAGE; stage <= LAST_VISUAL_STAGE; stage++) {
            for (GrapeColor color : GrapeColor.values()) {
                models.add(model(MODEL_PREFIX + stage + "_" + color.getSerializedName()));
            }
        }
        return models;
    }

    private static ResourceLocation model(String modelName) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, MODEL_DIRECTORY + modelName);
    }

    private static GrapeColor resolvedColor(String varietyId) {
        String safeVarietyId = varietyId == null || varietyId.isBlank() ? GrapesItem.DEFAULT_VARIETY_ID : varietyId;
        GrapeVariety variety = GrapeVarietyManager.getVariety(safeVarietyId);
        return variety.colorType() == null ? FALLBACK_COLOR : variety.colorType();
    }

    private static String modelName(int visualAge, GrapeColor color) {
        int clampedAge = Math.max(0, Math.min(AGE_TO_VISUAL_STAGE.length - 1, visualAge));
        int stage = AGE_TO_VISUAL_STAGE[clampedAge];
        if (stage < FIRST_FRUITING_STAGE) {
            return MODEL_PREFIX + stage;
        }
        return MODEL_PREFIX + stage + "_" + (color == null ? FALLBACK_COLOR : color).getSerializedName();
    }

    public record GrapeVisualInfo(
            String requestedVarietyId,
            String resolvedVarietyId,
            String resolvedVarietyName,
            GrapeColor color,
            int visualAge,
            ResourceLocation model,
            boolean fallbackVariety,
            boolean fallbackColor
    ) {
    }
}
