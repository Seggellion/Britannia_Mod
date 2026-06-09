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
        boolean exceptionalOnly
) {
}