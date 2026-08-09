package com.seggellion.britannia_mod.dye.source;

import com.seggellion.britannia_mod.dye.api.PigmentId;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Immutable acquisition-neutral projection for future server integrations. */
public record PigmentSourceEntry(
        PigmentId pigmentId,
        String displayNameKey,
        ResourceLocation registeredItemId,
        String rarity,
        List<String> tags) {
    public PigmentSourceEntry {
        Objects.requireNonNull(pigmentId, "pigmentId");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(registeredItemId, "registeredItemId");
        Objects.requireNonNull(rarity, "rarity");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
    }
}
