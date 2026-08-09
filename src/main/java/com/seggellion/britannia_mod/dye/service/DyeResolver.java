package com.seggellion.britannia_mod.dye.service;

import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.colour.ColourMath;
import com.seggellion.britannia_mod.dye.colour.OklabColour;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.dye.palette.MaterialPaletteEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Registry-backed, side-effect-free, deterministic material colour resolution. */
public final class DyeResolver {
    public DyeResolutionOutcome resolve(
            PigmentId pigmentId, FabricMaterialId materialId, RegistrySnapshot snapshot) {
        return resolveInternal(pigmentId, materialId, snapshot, false).outcome();
    }

    public ExplainedDyeResolution explain(
            PigmentId pigmentId, FabricMaterialId materialId, RegistrySnapshot snapshot) {
        return resolveInternal(pigmentId, materialId, snapshot, true);
    }

    public DyeResolutionOutcome resolveNatural(FabricMaterialId materialId, RegistrySnapshot snapshot) {
        return resolveNaturalInternal(materialId, snapshot, false).outcome();
    }

    public ExplainedDyeResolution explainNatural(FabricMaterialId materialId, RegistrySnapshot snapshot) {
        return resolveNaturalInternal(materialId, snapshot, true);
    }

    private ExplainedDyeResolution resolveInternal(
            PigmentId pigmentId, FabricMaterialId materialId, RegistrySnapshot snapshot, boolean explain) {
        Objects.requireNonNull(pigmentId, "pigmentId");
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(snapshot, "snapshot");
        PigmentDefinition pigment = snapshot.pigments().find(pigmentId).orElse(null);
        if (pigment == null) {
            return failure(pigmentId, materialId, Optional.empty(), DyeResolutionFailure.MISSING_PIGMENT,
                    "No active pigment definition for " + pigmentId);
        }
        FabricMaterialDefinition material = snapshot.fabricMaterials().find(materialId).orElse(null);
        if (material == null) {
            return failure(pigmentId, materialId, Optional.empty(), DyeResolutionFailure.MISSING_MATERIAL,
                    "No active fabric material definition for " + materialId);
        }
        MaterialPalette palette = snapshot.materialPalettes().find(material.paletteId()).orElse(null);
        if (palette == null) {
            return failure(pigmentId, materialId, Optional.of(material.paletteId()),
                    DyeResolutionFailure.MISSING_PALETTE,
                    "Material palette does not exist: " + material.paletteId());
        }
        if (!palette.materialId().equals(materialId)) {
            return failure(pigmentId, materialId, Optional.of(palette.id()),
                    DyeResolutionFailure.PALETTE_OWNER_MISMATCH,
                    "Palette " + palette.id() + " belongs to " + palette.materialId());
        }
        if (palette.entries().isEmpty()) {
            return failure(pigmentId, materialId, Optional.of(palette.id()), DyeResolutionFailure.EMPTY_PALETTE,
                    "Palette contains no entries: " + palette.id());
        }

        OklabColour pigmentColour = ColourMath.toOklab(pigment.referenceSrgb());
        ResolvedColourId override = palette.pigmentOverrides().get(pigmentId);
        if (override != null) {
            MaterialPaletteEntry target = palette.entries().stream()
                    .filter(entry -> entry.id().equals(override)).findFirst().orElse(null);
            if (target == null) {
                return failure(pigmentId, materialId, Optional.of(palette.id()),
                        DyeResolutionFailure.MALFORMED_EXPLICIT_MAPPING,
                        "Explicit mapping target does not exist: " + override);
            }
            double distance = ColourMath.distance(pigmentColour, ColourMath.toOklab(target.displaySrgb()));
            DyeResult result = new DyeResult(target.id(), MatchType.EXPLICIT_MAPPING, distance);
            CandidateEvaluation candidate = candidate(target, distance, pigment.tags(), true);
            DyeResolutionExplanation detail = explanation(pigmentId, materialId, palette, result, true,
                    explain ? List.of(candidate) : List.of(), List.of(), FinalTieBreak.EXPLICIT_MAPPING);
            return new ExplainedDyeResolution(DyeResolutionOutcome.success(result), detail);
        }

        List<RankedCandidate> candidates = new ArrayList<>();
        List<RejectedPaletteEntry> rejected = new ArrayList<>();
        for (MaterialPaletteEntry entry : palette.entries().stream()
                .sorted(Comparator.comparing(candidate -> candidate.id().toString())).toList()) {
            List<String> excluded = intersection(pigment.tags(), entry.excludedPigmentTags());
            if (!excluded.isEmpty()) {
                rejected.add(new RejectedPaletteEntry(entry.id(), PaletteRejectionReason.EXCLUDED_PIGMENT_TAG,
                        excluded));
                continue;
            }
            List<String> allowed = intersection(pigment.tags(), entry.allowedPigmentTags());
            if (!entry.allowedPigmentTags().isEmpty() && allowed.isEmpty()) {
                rejected.add(new RejectedPaletteEntry(entry.id(), PaletteRejectionReason.NO_ALLOWED_PIGMENT_TAG,
                        entry.allowedPigmentTags().stream().sorted().toList()));
                continue;
            }
            double distance = ColourMath.distance(pigmentColour, ColourMath.toOklab(entry.displaySrgb()));
            candidates.add(new RankedCandidate(entry, distance, ColourFamilyTags.shared(pigment.tags(), entry.tags())));
        }
        if (candidates.isEmpty()) {
            return failure(pigmentId, materialId, Optional.of(palette.id()),
                    DyeResolutionFailure.NO_COMPATIBLE_COLOUR,
                    "No compatible palette entry remains after tag filtering", rejected);
        }

        Selection selection = select(candidates);
        DyeResult result = new DyeResult(selection.candidate.entry.id(), MatchType.NEAREST_COLOUR,
                selection.candidate.distance);
        List<CandidateEvaluation> evaluated = explain ? orderedEvaluations(candidates, selection.candidate) : List.of();
        DyeResolutionExplanation detail = explanation(pigmentId, materialId, palette, result, false,
                evaluated, explain ? rejected : List.of(), selection.tieBreak);
        return new ExplainedDyeResolution(DyeResolutionOutcome.success(result), detail);
    }

    private ExplainedDyeResolution resolveNaturalInternal(
            FabricMaterialId materialId, RegistrySnapshot snapshot, boolean explain) {
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(snapshot, "snapshot");
        FabricMaterialDefinition material = snapshot.fabricMaterials().find(materialId).orElse(null);
        if (material == null) {
            return failure(Optional.empty(), materialId, Optional.empty(), DyeResolutionFailure.MISSING_MATERIAL,
                    "No active fabric material definition for " + materialId);
        }
        MaterialPalette palette = snapshot.materialPalettes().find(material.paletteId()).orElse(null);
        if (palette == null) {
            return failure(Optional.empty(), materialId, Optional.of(material.paletteId()),
                    DyeResolutionFailure.MISSING_PALETTE, "Material palette does not exist: " + material.paletteId());
        }
        if (!palette.materialId().equals(materialId)) {
            return failure(Optional.empty(), materialId, Optional.of(palette.id()),
                    DyeResolutionFailure.PALETTE_OWNER_MISMATCH,
                    "Palette " + palette.id() + " belongs to " + palette.materialId());
        }
        MaterialPaletteEntry natural = palette.entries().stream()
                .filter(entry -> entry.id().equals(material.naturalColourId())).findFirst().orElse(null);
        if (natural == null) {
            return failure(Optional.empty(), materialId, Optional.of(palette.id()),
                    DyeResolutionFailure.MISSING_NATURAL_COLOUR,
                    "Natural colour does not exist in palette: " + material.naturalColourId());
        }
        DyeResult result = new DyeResult(natural.id(), MatchType.NATURAL, 0.0);
        List<CandidateEvaluation> candidates = explain
                ? List.of(candidate(natural, 0.0, List.of(), true)) : List.of();
        DyeResolutionExplanation detail = explanation(Optional.empty(), materialId, palette, result, false,
                candidates, List.of(), FinalTieBreak.NATURAL);
        return new ExplainedDyeResolution(DyeResolutionOutcome.success(result), detail);
    }

    private static Selection select(List<RankedCandidate> candidates) {
        double minimum = candidates.stream().mapToDouble(candidate -> candidate.distance).min().orElseThrow();
        List<RankedCandidate> tied = candidates.stream()
                .filter(candidate -> candidate.distance <= minimum + ColourMath.TIE_EPSILON).toList();
        if (tied.size() == 1) {
            return new Selection(tied.getFirst(), candidates.size() == 1
                    ? FinalTieBreak.ONLY_COMPATIBLE_CANDIDATE : FinalTieBreak.LOWEST_DISTANCE);
        }
        List<RankedCandidate> family = tied.stream().filter(candidate -> !candidate.sharedFamilies.isEmpty()).toList();
        if (!family.isEmpty() && family.size() < tied.size()) {
            tied = family;
            if (tied.size() == 1) {
                return new Selection(tied.getFirst(), FinalTieBreak.SHARED_COLOUR_FAMILY_TAG);
            }
        }
        int highestPriority = tied.stream().mapToInt(candidate -> candidate.entry.priority()).max().orElseThrow();
        List<RankedCandidate> priority = tied.stream()
                .filter(candidate -> candidate.entry.priority() == highestPriority).toList();
        if (priority.size() < tied.size()) {
            tied = priority;
            if (tied.size() == 1) {
                return new Selection(tied.getFirst(), FinalTieBreak.HIGHER_PRIORITY);
            }
        }
        RankedCandidate lexical = tied.stream()
                .min(Comparator.comparing(candidate -> candidate.entry.id().toString())).orElseThrow();
        return new Selection(lexical, FinalTieBreak.LEXICOGRAPHIC_ID);
    }

    private static List<CandidateEvaluation> orderedEvaluations(
            List<RankedCandidate> candidates, RankedCandidate selected) {
        return candidates.stream().map(candidate -> new CandidateEvaluation(candidate.entry.id(), candidate.distance,
                        candidate.sharedFamilies, candidate.entry.priority(), candidate == selected))
                .sorted(Comparator.comparing(CandidateEvaluation::selected).reversed()
                        .thenComparingDouble(CandidateEvaluation::distance)
                        .thenComparing(CandidateEvaluation::sharesColourFamilyTag, Comparator.reverseOrder())
                        .thenComparing(CandidateEvaluation::priority, Comparator.reverseOrder())
                        .thenComparing(candidate -> candidate.colourId().toString()))
                .toList();
    }

    private static CandidateEvaluation candidate(
            MaterialPaletteEntry entry, double distance, List<String> pigmentTags, boolean selected) {
        return new CandidateEvaluation(entry.id(), distance, ColourFamilyTags.shared(pigmentTags, entry.tags()),
                entry.priority(), selected);
    }

    private static List<String> intersection(List<String> first, List<String> second) {
        return first.stream().filter(second::contains).distinct().sorted().toList();
    }

    private static DyeResolutionExplanation explanation(
            PigmentId pigmentId, FabricMaterialId materialId, MaterialPalette palette, DyeResult result,
            boolean explicit, List<CandidateEvaluation> candidates, List<RejectedPaletteEntry> rejected,
            FinalTieBreak tieBreak) {
        return explanation(Optional.of(pigmentId), materialId, palette, result, explicit, candidates, rejected,
                tieBreak);
    }

    private static DyeResolutionExplanation explanation(
            Optional<PigmentId> pigmentId, FabricMaterialId materialId, MaterialPalette palette, DyeResult result,
            boolean explicit, List<CandidateEvaluation> candidates, List<RejectedPaletteEntry> rejected,
            FinalTieBreak tieBreak) {
        return new DyeResolutionExplanation(pigmentId, materialId, Optional.of(palette.id()),
                Optional.of(result.resolvedColourId()), Optional.of(result.matchType()),
                Optional.of(result.perceptualDistance()), explicit, candidates, rejected, tieBreak, Optional.empty());
    }

    private static ExplainedDyeResolution failure(
            PigmentId pigmentId, FabricMaterialId materialId, Optional<net.minecraft.resources.ResourceLocation> palette,
            DyeResolutionFailure failure, String message) {
        return failure(Optional.of(pigmentId), materialId, palette, failure, message, List.of());
    }

    private static ExplainedDyeResolution failure(
            PigmentId pigmentId, FabricMaterialId materialId, Optional<net.minecraft.resources.ResourceLocation> palette,
            DyeResolutionFailure failure, String message, List<RejectedPaletteEntry> rejected) {
        return failure(Optional.of(pigmentId), materialId, palette, failure, message, rejected);
    }

    private static ExplainedDyeResolution failure(
            Optional<PigmentId> pigmentId, FabricMaterialId materialId,
            Optional<net.minecraft.resources.ResourceLocation> palette,
            DyeResolutionFailure failure, String message) {
        return failure(pigmentId, materialId, palette, failure, message, List.of());
    }

    private static ExplainedDyeResolution failure(
            Optional<PigmentId> pigmentId, FabricMaterialId materialId,
            Optional<net.minecraft.resources.ResourceLocation> palette,
            DyeResolutionFailure failure, String message, List<RejectedPaletteEntry> rejected) {
        DyeResolutionOutcome outcome = DyeResolutionOutcome.failure(failure, message);
        DyeResolutionExplanation explanation = new DyeResolutionExplanation(pigmentId, materialId, palette,
                Optional.empty(), Optional.empty(), Optional.empty(), false, List.of(), rejected,
                FinalTieBreak.FAILURE, Optional.of(failure));
        return new ExplainedDyeResolution(outcome, explanation);
    }

    private record RankedCandidate(
            MaterialPaletteEntry entry, double distance, List<String> sharedFamilies) {
    }

    private record Selection(RankedCandidate candidate, FinalTieBreak tieBreak) {
    }
}
