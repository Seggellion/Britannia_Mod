package com.seggellion.britannia_mod.farming;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record FlowerColorDefinition(ResourceLocation id, FlowerColor color) {
    public FlowerColorDefinition {
        Objects.requireNonNull(id, "Flower color ID is required");
        Objects.requireNonNull(color, "Flower color tint is required");
    }
}
