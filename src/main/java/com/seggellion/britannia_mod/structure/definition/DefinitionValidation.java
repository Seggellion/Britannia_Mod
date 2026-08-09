package com.seggellion.britannia_mod.structure.definition;

import java.util.List;

/** Immutable validation result used by definitions and catalogue construction. */
public record DefinitionValidation(List<DefinitionDiagnostic> diagnostics) {
    public DefinitionValidation {
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean valid() {
        return diagnostics.isEmpty();
    }

    public boolean has(DefinitionDiagnostic.Code code) {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.code() == code);
    }
}
