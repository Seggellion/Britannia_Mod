package com.seggellion.britannia_mod.banner.renderdata;

import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Display-only material palette projection. Stable colour IDs remain authoritative. */
public record BannerRenderMaterial(
        FabricMaterialId id,
        ResolvedColourId naturalColourId,
        ResourceLocation paletteId,
        Map<ResolvedColourId, Integer> displaySrgbByColour) {
    public BannerRenderMaterial {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(naturalColourId, "naturalColourId");
        Objects.requireNonNull(paletteId, "paletteId");
        Objects.requireNonNull(displaySrgbByColour, "displaySrgbByColour");
        LinkedHashMap<ResolvedColourId, Integer> copy = new LinkedHashMap<>();
        displaySrgbByColour.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(Object::toString)))
                .forEach(entry -> copy.put(
                        Objects.requireNonNull(entry.getKey(), "colourId"), entry.getValue() & 0xFFFFFF));
        displaySrgbByColour = Collections.unmodifiableMap(copy);
        if (!displaySrgbByColour.containsKey(naturalColourId)) {
            throw new IllegalArgumentException("Natural colour must exist in the synchronized palette");
        }
    }
}
