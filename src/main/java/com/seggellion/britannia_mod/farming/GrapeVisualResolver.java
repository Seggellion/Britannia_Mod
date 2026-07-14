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

    private GrapeVisualResolver() {
    }

    public static ResourceLocation modelLocation(CropDefinition crop, int growthAge, String varietyId) {
        int visualAge = crop == null ? 0 : Math.max(0, Math.min(crop.maxGrowthAge(), growthAge));
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "block/crops/" + modelName(visualAge, resolvedColor(varietyId)));
    }

    public static GrapeVisualInfo resolve(CropDefinition crop, int growthAge, String varietyId) {
        String safeVarietyId = varietyId == null || varietyId.isBlank() ? GrapesItem.DEFAULT_VARIETY_ID : varietyId;
        GrapeVariety variety = GrapeVarietyManager.getVariety(safeVarietyId);
        int visualAge = crop == null ? 0 : Math.max(0, Math.min(crop.maxGrowthAge(), growthAge));
        GrapeColor color = variety.colorType() == null ? FALLBACK_COLOR : variety.colorType();
        boolean fallbackVariety = !safeVarietyId.equals(variety.id());
        boolean fallbackColor = variety.colorType() == null;
        ResourceLocation model = ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "block/crops/" + modelName(visualAge, color));
        return new GrapeVisualInfo(safeVarietyId, variety.id(), variety.getFormattedName(), color, visualAge, model, fallbackVariety, fallbackColor);
    }

    public static List<ResourceLocation> allModelLocations() {
        List<ResourceLocation> models = new ArrayList<>();
        for (int stage = 1; stage <= 4; stage++) {
            models.add(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "block/crops/grape_vine_stage" + stage));
        }
        for (GrapeColor color : GrapeColor.values()) {
            String colorName = color.getSerializedName();
            models.add(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "block/crops/grape_" + colorName + "_trellis_5"));
            models.add(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "block/crops/grape_" + colorName + "_trellis_6"));
        }
        return models;
    }

    private static GrapeColor resolvedColor(String varietyId) {
        String safeVarietyId = varietyId == null || varietyId.isBlank() ? GrapesItem.DEFAULT_VARIETY_ID : varietyId;
        GrapeVariety variety = GrapeVarietyManager.getVariety(safeVarietyId);
        return variety.colorType() == null ? FALLBACK_COLOR : variety.colorType();
    }

    private static String modelName(int visualAge, GrapeColor color) {
        if (visualAge <= 1) {
            return "grape_vine_stage1";
        }
        if (visualAge == 2) {
            return "grape_vine_stage2";
        }
        if (visualAge == 3) {
            return "grape_vine_stage3";
        }
        if (visualAge == 4) {
            return "grape_vine_stage4";
        }

        String colorName = (color == null ? FALLBACK_COLOR : color).getSerializedName();
        int fruitStage = visualAge <= 5 ? 5 : 6;
        return "grape_" + colorName + "_trellis_" + fruitStage;
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
