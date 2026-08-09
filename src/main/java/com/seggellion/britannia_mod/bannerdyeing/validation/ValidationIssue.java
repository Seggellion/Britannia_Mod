package com.seggellion.britannia_mod.bannerdyeing.validation;

import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryDomain;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public record ValidationIssue(
        ValidationSeverity severity,
        ValidationStage stage,
        RegistryDomain domain,
        Optional<String> definitionId,
        Optional<ResourceLocation> sourceResource,
        String code,
        String message,
        Optional<String> relatedId) {

    public static final Comparator<ValidationIssue> ORDER = Comparator
            .comparing(ValidationIssue::severity)
            .thenComparing(ValidationIssue::stage)
            .thenComparing(issue -> issue.domain().name())
            .thenComparing(issue -> issue.definitionId().orElse(""))
            .thenComparing(issue -> issue.sourceResource().map(ResourceLocation::toString).orElse(""))
            .thenComparing(ValidationIssue::code)
            .thenComparing(ValidationIssue::message)
            .thenComparing(issue -> issue.relatedId().orElse(""));

    public ValidationIssue {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(domain, "domain");
        definitionId = Objects.requireNonNull(definitionId, "definitionId");
        sourceResource = Objects.requireNonNull(sourceResource, "sourceResource");
        code = requireText(code, "code");
        message = requireText(message, "message");
        relatedId = Objects.requireNonNull(relatedId, "relatedId");
    }

    public static ValidationIssue error(
            ValidationStage stage,
            RegistryDomain domain,
            String definitionId,
            ResourceLocation sourceResource,
            String code,
            String message,
            String relatedId) {
        return new ValidationIssue(ValidationSeverity.ERROR, stage, domain,
                Optional.ofNullable(definitionId), Optional.ofNullable(sourceResource), code, message,
                Optional.ofNullable(relatedId));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
