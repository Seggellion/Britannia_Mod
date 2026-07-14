package com.seggellion.britannia_mod.skill.crafting;

public record IngredientRequirement(String materialKey, int amount) {
    public IngredientRequirement {
        if (materialKey == null || materialKey.isBlank()) {
            throw new IllegalArgumentException("Ingredient key cannot be blank");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Ingredient amount must be positive");
        }
        materialKey = materialKey.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean selectableMetal() {
        return materialKey.equals("ingot");
    }
}
