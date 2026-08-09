package com.seggellion.britannia_mod.dye.service;

import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Immutable diagnostic detail for tests and future developer tooling. */
public record DyeResolutionExplanation(
        Optional<PigmentId> pigmentId,
        FabricMaterialId materialId,
        Optional<ResourceLocation> paletteId,
        Optional<ResolvedColourId> selectedColourId,
        Optional<MatchType> matchType,
        Optional<Double> selectedDistance,
        boolean explicitMappingUsed,
        List<CandidateEvaluation> compatibleCandidates,
        List<RejectedPaletteEntry> rejectedEntries,
        FinalTieBreak finalTieBreak,
        Optional<DyeResolutionFailure> failure) {
    public DyeResolutionExplanation {
        pigmentId = Objects.requireNonNull(pigmentId, "pigmentId");
        Objects.requireNonNull(materialId, "materialId");
        paletteId = Objects.requireNonNull(paletteId, "paletteId");
        selectedColourId = Objects.requireNonNull(selectedColourId, "selectedColourId");
        matchType = Objects.requireNonNull(matchType, "matchType");
        selectedDistance = Objects.requireNonNull(selectedDistance, "selectedDistance");
        selectedDistance.ifPresent(value -> {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("selectedDistance must be finite and non-negative");
            }
        });
        compatibleCandidates = List.copyOf(Objects.requireNonNull(compatibleCandidates, "compatibleCandidates"));
        rejectedEntries = List.copyOf(Objects.requireNonNull(rejectedEntries, "rejectedEntries"));
        Objects.requireNonNull(finalTieBreak, "finalTieBreak");
        failure = Objects.requireNonNull(failure, "failure");
    }
}
