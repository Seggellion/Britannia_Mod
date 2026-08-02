package com.seggellion.britannia_mod.structure.definition;

import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Family;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Deterministic same-family selection over enabled, compatible variants. */
public final class StructureVariantCycler {
    private StructureVariantCycler() {
    }

    public static List<Variant> enabledVariants(Family family) {
        return family.variants().stream()
                .filter(Variant::enabled)
                .filter(variant -> StructureDefinitionValidator.compatible(family, variant))
                .sorted(Comparator.comparingInt(Variant::cyclePosition))
                .toList();
    }

    /**
     * Missing IDs return empty and never substitute another identity. A known disabled current
     * variant advances to the next enabled explicit cycle position, wrapping when required.
     */
    public static Optional<Variant> next(Family family, VariantId currentId) {
        Optional<Variant> current = family.variants().stream()
                .filter(variant -> variant.id().equals(currentId))
                .findFirst();
        if (current.isEmpty()) {
            return Optional.empty();
        }
        List<Variant> enabled = enabledVariants(family);
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        for (Variant candidate : enabled) {
            if (candidate.cyclePosition() > current.orElseThrow().cyclePosition()) {
                return Optional.of(candidate);
            }
        }
        return Optional.of(enabled.getFirst());
    }
}
