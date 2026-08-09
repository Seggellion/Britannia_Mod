package com.seggellion.britannia_mod.structure.definition;

import com.seggellion.britannia_mod.structure.definition.DefinitionDiagnostic.Code;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Family;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable, explicitly ordered catalogue of validated shrine and monolith families. */
public final class StructureCatalogue {
    private final List<Family> families;
    private final Map<FamilyId, Family> familiesById;

    private StructureCatalogue(List<Family> families) {
        this.families = List.copyOf(families);
        Map<FamilyId, Family> indexed = new LinkedHashMap<>();
        families.forEach(family -> indexed.put(family.id(), family));
        this.familiesById = Map.copyOf(indexed);
    }

    public List<Family> families() {
        return families;
    }

    public Optional<Family> family(FamilyId id) {
        return Optional.ofNullable(familiesById.get(id));
    }

    public Optional<Variant> variant(FamilyId familyId, VariantId variantId) {
        return family(familyId).flatMap(family -> family.variants().stream()
                .filter(variant -> variant.id().equals(variantId) && variant.familyId().equals(familyId))
                .findFirst());
    }

    public static BuildResult build(List<Family> families) {
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
        Map<FamilyId, Family> unique = new LinkedHashMap<>();
        for (Family family : families) {
            if (unique.putIfAbsent(family.id(), family) != null) {
                diagnostics.add(new DefinitionDiagnostic(
                        Code.DUPLICATE_FAMILY_ID,
                        "family:" + family.id().value(),
                        "Duplicate family ID"));
            }
            diagnostics.addAll(StructureDefinitionValidator.validate(family).diagnostics());
        }
        return diagnostics.isEmpty()
                ? new BuildResult(Optional.of(new StructureCatalogue(families)), new DefinitionValidation(List.of()))
                : new BuildResult(Optional.empty(), new DefinitionValidation(diagnostics));
    }

    public record BuildResult(Optional<StructureCatalogue> catalogue, DefinitionValidation validation) {
        public BuildResult {
            catalogue = java.util.Objects.requireNonNull(catalogue, "catalogue");
            java.util.Objects.requireNonNull(validation, "validation");
        }
    }
}
