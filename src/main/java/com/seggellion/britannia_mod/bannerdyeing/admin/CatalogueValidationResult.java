package com.seggellion.britannia_mod.bannerdyeing.admin;

import java.util.List;
import java.util.Objects;

public record CatalogueValidationResult(
        CatalogueValidationStatus status,
        int bannerDefinitions,
        int activeBanners,
        int disabledBanners,
        int materials,
        int palettes,
        int pigments,
        int mounts,
        int placeholders,
        int provisionalNames,
        int provisionalDimensions,
        int errors,
        int warnings,
        List<String> boundedIssues) {
    public CatalogueValidationResult {
        Objects.requireNonNull(status, "status");
        boundedIssues = List.copyOf(Objects.requireNonNull(boundedIssues, "boundedIssues"));
    }

    public boolean successful() {
        return status == CatalogueValidationStatus.VALID
                || status == CatalogueValidationStatus.VALID_WITH_WARNINGS;
    }
}
