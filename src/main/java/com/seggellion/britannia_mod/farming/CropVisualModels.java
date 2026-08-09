package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.resources.ResourceLocation;

public final class CropVisualModels {
    private CropVisualModels() {
    }

    public static ResourceLocation modelLocation(CropDefinition crop, int growthAge) {
        return modelLocation(crop, growthAge, "");
    }

    public static ResourceLocation modelLocation(CropDefinition crop, int growthAge, String cropVariant) {
        if ("grapes".equals(crop.id())) {
            return GrapeVisualResolver.modelLocation(crop, growthAge, cropVariant);
        }
        if ("corn".equals(crop.id())) {
            return ResourceLocation.fromNamespaceAndPath(
                    BritanniaMod.MODID,
                    "block/crops/corn/corn_base_age_" + visualAge(crop, growthAge)
            );
        }
        if (usesWheatVisuals(crop.id())) {
            return ResourceLocation.fromNamespaceAndPath(
                    BritanniaMod.MODID,
                    "block/crops/" + crop.id() + "/" + crop.id() + "_age_" + visualAge(crop, growthAge)
            );
        }
        if ("beans".equals(crop.id())) {
            return ResourceLocation.fromNamespaceAndPath(
                    BritanniaMod.MODID,
                    "block/crops/beans/beans_age_" + visualAge(crop, growthAge)
            );
        }

        String assetId = assetCropId(crop.id());
        String modelName = usesAgeAliasModels(crop.id())
                ? ageAliasModelName(assetId, visualAge(crop, growthAge))
                : "om_" + assetId + "_" + Math.max(1, Math.min(crop.visualAgeCount(), growthAge + 1));
        return ResourceLocation.fromNamespaceAndPath(
                BritanniaMod.MODID,
                "block/crops/" + assetId + "/" + modelName
        );
    }

    public static int visualModelStage(CropDefinition crop, int growthAge) {
        if (crop == null) {
            return 0;
        }
        if ("corn".equals(crop.id())) {
            return visualAge(crop, growthAge);
        }
        if (usesAgeAliasModels(crop.id())) {
            return visualAge(crop, growthAge);
        }
        if ("beans".equals(crop.id())) {
            return visualAge(crop, growthAge);
        }
        if (usesWheatVisuals(crop.id())) {
            return visualAge(crop, growthAge);
        }
        return Math.max(1, Math.min(crop.visualAgeCount(), growthAge + 1));
    }

    public static int visualModelStageCount(CropDefinition crop) {
        if (crop == null) {
            return 0;
        }
        if ("corn".equals(crop.id())) {
            return crop.visualAgeCount();
        }
        if (usesAgeAliasModels(crop.id())) {
            return crop.visualAgeCount();
        }
        if ("beans".equals(crop.id())) {
            return crop.visualAgeCount();
        }
        if (usesWheatVisuals(crop.id())) {
            return 8;
        }
        return crop.visualAgeCount();
    }

    private static boolean usesWheatVisuals(String cropId) {
        return switch (cropId) {
            case "wheat", "rye", "barley", "oats", "mustard" -> true;
            default -> false;
        };
    }

    public static int modelVisibleHeight(CropDefinition crop, int growthAge) {
        if (crop == null) {
            return 0;
        }
        if ("hops".equals(crop.id())) {
            return visualAge(crop, growthAge) >= 4 ? 2 : 1;
        }
        return 1;
    }

    private static boolean usesAgeAliasModels(String cropId) {
        return switch (cropId) {
            case "broccoli", "cauliflower", "lettuce", "rhubarb", "cabbage",
                    "squash", "yellow_onion", "green_onion", "garlic", "ginseng", "turnips",
                    "celery", "tobacco", "radish", "parsnip", "yam", "rutabaga",
                    "pumpkin", "carrot", "potato", "watermelon", "snow_peas","hops", "cotton",
                    "flax", "hemp", "mandrake", "nightshade", "pineapple", "strawberry",
                    "blueberry", "raspberry", "cranberry", "blackberry", "huckleberry",
                    "mulberry", "elderberry", "tomato", "bell_peppers", "cucumbers",
                    "honeydew", "cantaloupe", "banana" -> true;
            default -> false;
        };
    }

    private static String assetCropId(String cropId) {
        return switch (cropId) {
            case "turnips" -> "turnip";
            case "bell_peppers" -> "bell_pepper";
            case "cucumbers" -> "cucumber";
            default -> cropId;
        };
    }

    private static String ageAliasModelName(String assetId, int visualAge) {
        return assetId + "_age_" + visualAge;
    }

    private static int visualAge(CropDefinition crop, int growthAge) {
        return Math.max(0, Math.min(crop.maxGrowthAge(), growthAge));
    }

    private static int vanillaStage(int growthAge, int growthStages, int maxVanillaStage) {
        int maxGrowthAge = Math.max(1, growthStages - 1);
        float ratio = Math.max(0.0F, Math.min(1.0F, growthAge / (float) maxGrowthAge));
        return Math.max(0, Math.min(maxVanillaStage, Math.round(ratio * maxVanillaStage)));
    }
}
