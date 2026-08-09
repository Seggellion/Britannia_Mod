package com.seggellion.britannia_mod.skill.crafting;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record CraftableDef(
        String id,
        String category,
        String displayName,
        ResourceLocation resultItem,
        List<IngredientRequirement> ingredients,
        List<SkillRequirement> skillRequirements,
        boolean exceptionalOnly,
        int outputCount,
        boolean retainsMaterialColor,
        boolean makersMarkEligible,
        String learnedRecipeKey,
        String raceRestriction,
        String genderRestriction,
        String equipmentType,
        String weaponProfileId,
        String armorProfileId,
        String shieldProfileId,
        double baseWeight,
        boolean provisional,
        boolean batchCrafting,
        boolean recyclable
) {
    public CraftableDef {
        ingredients = List.copyOf(ingredients);
        skillRequirements = List.copyOf(skillRequirements);
        if (outputCount <= 0) throw new IllegalArgumentException("Output count must be positive");
        if (!Double.isFinite(baseWeight) || baseWeight < 0.0) throw new IllegalArgumentException("Invalid weight");
        learnedRecipeKey = emptyToNull(learnedRecipeKey);
        raceRestriction = emptyToNull(raceRestriction);
        genderRestriction = emptyToNull(genderRestriction);
        weaponProfileId = emptyToNull(weaponProfileId);
        armorProfileId = emptyToNull(armorProfileId);
        shieldProfileId = emptyToNull(shieldProfileId);
    }

    /** Compatibility constructor for the legacy catalogue while its source fields are imported. */
    public CraftableDef(String id, String category, String displayName, ResourceLocation resultItem,
                        List<IngredientRequirement> ingredients, List<SkillRequirement> skillRequirements,
                        boolean exceptionalOnly) {
        this(id, category, displayName, resultItem, ingredients, skillRequirements, exceptionalOnly,
                1, hasSelectableMetal(ingredients), false, null,
                inferRace(displayName), inferGender(displayName), inferEquipmentType(category),
                isWeaponCategory(category) ? id : null,
                inferArmorProfile(category, displayName),
                category.equalsIgnoreCase("Shields") ? id : null,
                inferWeight(category, displayName), true, false,
                isWeaponCategory(category) || category.equalsIgnoreCase("Armor")
                        || category.equalsIgnoreCase("Helmets") || category.equalsIgnoreCase("Shields"));
    }

    public static CraftableDef catalogue(String id, String category, String displayName, ResourceLocation resultItem,
                                         List<IngredientRequirement> ingredients, List<SkillRequirement> skills,
                                         int outputCount, boolean retainsColor, boolean makersMark, String learnedRecipe,
                                         String race, String gender, boolean batch, boolean recyclable) {
        String equipmentType = inferEquipmentType(category, displayName);
        return new CraftableDef(id, category, displayName, resultItem, ingredients, skills, false, outputCount,
                retainsColor, makersMark, learnedRecipe, race, gender, equipmentType,
                isWeaponCategory(category) ? id : null,
                equipmentType.equals("armor") ? inferArmorProfile(category, displayName) : null,
                equipmentType.equals("shield") ? id : null,
                inferWeight(category, displayName), false, batch, recyclable);
    }

    public float minimumBlacksmithy() {
        return skillRequirements.stream()
                .filter(r -> r.skillKey().equals("blacksmithy"))
                .map(SkillRequirement::minValue).findFirst().orElse(0.0f);
    }

    public boolean requiresLearnedRecipe() { return learnedRecipeKey != null; }

    public static boolean isWeaponCategory(String category) {
        return switch (category.toLowerCase(java.util.Locale.ROOT)) {
            case "axes", "bashing", "bladed", "polearms", "throwing" -> true;
            default -> false;
        };
    }

    private static boolean hasSelectableMetal(List<IngredientRequirement> ingredients) {
        return ingredients.stream().anyMatch(IngredientRequirement::selectableMetal);
    }

    private static String inferEquipmentType(String category) {
        return inferEquipmentType(category, "");
    }

    private static String inferEquipmentType(String category, String name) {
        if (isWeaponCategory(category)) return "weapon";
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("deed")) return "component";
        if (category.equalsIgnoreCase("Miscellaneous")
                && (n.startsWith("dragon ") || n.equals("gloves of feudal grip"))) return "armor";
        return switch (category.toLowerCase(java.util.Locale.ROOT)) {
            case "armor", "helmets" -> "armor";
            case "shields" -> "shield";
            default -> "component";
        };
    }

    private static String inferArmorProfile(String category, String name) {
        if (!(category.equalsIgnoreCase("Armor") || category.equalsIgnoreCase("Dragon Scale Armor")
                || category.equalsIgnoreCase("Helmets") || category.equalsIgnoreCase("Miscellaneous"))) return null;
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("dragon")) return "dragon_armor";
        if (n.contains("wyrmscale")) return "wyrmscale";
        if (n.contains("gargish platemail")) return "gargish_platemail";
        if (n.contains("ringmail")) return "ringmail";
        if (n.contains("chainmail")) return "chainmail";
        if (n.contains("female plate")) return "female_plate";
        if (n.contains("platemail do")) return "platemail_do";
        if (n.contains("plate") || n.contains("helmet") || n.contains("helm")) return "platemail";
        return "provisional_armor";
    }

    private static String inferRace(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).contains("gargish") ? "gargoyle" : null;
    }

    private static String inferGender(String name) {
        return name.equalsIgnoreCase("Female Plate") ? "female" : null;
    }

    private static double inferWeight(String category, String name) {
        String c = category.toLowerCase(java.util.Locale.ROOT);
        if (isWeaponCategory(category)) return 4.0;
        if (c.equals("shields")) return 6.0;
        if (c.equals("armor") || c.equals("dragon scale armor") || c.equals("helmets")) {
            String n = name.toLowerCase(java.util.Locale.ROOT);
            if (n.contains("glove") || n.contains("gorget") || n.contains("coif")) return 2.0;
            if (n.contains("arm") || n.contains("sleeve") || n.contains("helm") || n.contains("circlet")) return 4.0;
            if (n.contains("leg") || n.contains("kilt") || n.contains("haidate") || n.contains("suneate")) return 7.0;
            return 10.0;
        }
        return 0.0;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
