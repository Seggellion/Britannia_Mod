package com.seggellion.britannia_mod.structure.definition;

import java.util.Objects;
import java.util.Optional;

/** Stable structure identities kept separate from optional client resource locations. */
public final class StructureIdentity {
    private StructureIdentity() {
    }

    public record FamilyId(String value) {
        public FamilyId {
            Objects.requireNonNull(value, "value");
            if (value.isBlank()) {
                throw new IllegalArgumentException("Family ID must not be blank");
            }
        }
    }

    public record VariantId(String value) {
        public VariantId {
            Objects.requireNonNull(value, "value");
            if (value.isBlank()) {
                throw new IllegalArgumentException("Variant ID must not be blank");
            }
        }
    }

    public enum DisplayResolution {
        RESOLVED,
        UNRESOLVED
    }

    public record DisplayName(
            String logicalIdentity,
            Optional<String> translationKey,
            String fallbackLabel,
            DisplayResolution resolution) {
        public DisplayName {
            Objects.requireNonNull(logicalIdentity, "logicalIdentity");
            translationKey = Objects.requireNonNull(translationKey, "translationKey");
            Objects.requireNonNull(fallbackLabel, "fallbackLabel");
            Objects.requireNonNull(resolution, "resolution");
        }

        public static DisplayName unresolved(String logicalIdentity, String fallbackLabel) {
            return new DisplayName(logicalIdentity, Optional.empty(), fallbackLabel, DisplayResolution.UNRESOLVED);
        }
    }

    public record ResourceId(String namespace, String path) {
        public ResourceId {
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(path, "path");
        }
    }

    public enum ResourceAvailability {
        AVAILABLE,
        UNAVAILABLE
    }

    /**
     * A logical resource identity survives even when no concrete client path is known or available.
     */
    public record ClientResource(
            String logicalIdentity,
            Optional<ResourceId> location,
            ResourceAvailability availability,
            Optional<String> unavailableReason) {
        public ClientResource {
            Objects.requireNonNull(logicalIdentity, "logicalIdentity");
            if (logicalIdentity.isBlank()) {
                throw new IllegalArgumentException("Logical resource identity must not be blank");
            }
            location = Objects.requireNonNull(location, "location");
            Objects.requireNonNull(availability, "availability");
            unavailableReason = Objects.requireNonNull(unavailableReason, "unavailableReason");
        }

        public static ClientResource available(String logicalIdentity, String namespace, String path) {
            return new ClientResource(
                    logicalIdentity,
                    Optional.of(new ResourceId(namespace, path)),
                    ResourceAvailability.AVAILABLE,
                    Optional.empty());
        }

        public static ClientResource unavailable(String logicalIdentity, String reason) {
            return new ClientResource(
                    logicalIdentity,
                    Optional.empty(),
                    ResourceAvailability.UNAVAILABLE,
                    Optional.of(reason));
        }
    }

    public enum ContentStatus {
        APPROVED,
        PROVISIONAL
    }
}
