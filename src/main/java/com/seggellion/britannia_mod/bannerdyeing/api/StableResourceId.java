package com.seggellion.britannia_mod.bannerdyeing.api;

import net.minecraft.resources.ResourceLocation;

/**
 * Common contract for stable, namespaced identifiers used by banner and dye data.
 */
public interface StableResourceId {
    ResourceLocation value();
}
