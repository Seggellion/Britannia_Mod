package com.seggellion.britannia_mod.dye.service;

import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.List;
import java.util.Objects;

public record RejectedPaletteEntry(
        ResolvedColourId colourId,
        PaletteRejectionReason reason,
        List<String> relevantTags) {
    public RejectedPaletteEntry {
        Objects.requireNonNull(colourId, "colourId");
        Objects.requireNonNull(reason, "reason");
        relevantTags = List.copyOf(Objects.requireNonNull(relevantTags, "relevantTags"));
    }
}
