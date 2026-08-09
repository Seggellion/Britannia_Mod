package com.seggellion.britannia_mod.bannerdyeing.registry;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** A decoded definition together with the effective data resource that supplied it. */
public record DefinitionEntry<I, T>(I id, T definition, ResourceLocation sourceResource) {
    public DefinitionEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(sourceResource, "sourceResource");
    }
}
