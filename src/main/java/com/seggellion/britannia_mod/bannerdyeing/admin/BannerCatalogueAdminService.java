package com.seggellion.britannia_mod.bannerdyeing.admin;

import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionBannerCatalogue;
import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionDyeContent;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationIssue;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationSeverity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Read-only projections over the immutable published snapshot and its publication diagnostics. */
public final class BannerCatalogueAdminService {
    public static final int PLACEHOLDER_PAGE_SIZE = 8;
    public static final int MAX_REPORTED_ISSUES = 5;

    public CatalogueValidationResult validate(
            RegistrySnapshot snapshot, boolean registryAvailable, List<ValidationIssue> publicationIssues) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(publicationIssues, "publicationIssues");
        Counts counts = counts(snapshot);
        if (!registryAvailable) {
            return result(CatalogueValidationStatus.REGISTRY_UNAVAILABLE, counts, 0, 0, List.of());
        }

        List<String> issues = new ArrayList<>();
        int errors = (int) publicationIssues.stream()
                .filter(issue -> issue.severity() == ValidationSeverity.ERROR).count();
        int warnings = (int) publicationIssues.stream()
                .filter(issue -> issue.severity() == ValidationSeverity.WARNING).count();
        publicationIssues.stream()
                .filter(issue -> issue.severity() != ValidationSeverity.INFORMATION)
                .limit(MAX_REPORTED_ISSUES)
                .map(issue -> issue.code() + ": " + issue.message())
                .forEach(issues::add);
        try {
            ProductionBannerCatalogue.requireComplete(snapshot);
            ProductionDyeContent.requireComplete(snapshot);
        } catch (IllegalStateException exception) {
            errors++;
            if (issues.size() < MAX_REPORTED_ISSUES) {
                issues.add(exception.getMessage());
            }
        } catch (RuntimeException exception) {
            return result(CatalogueValidationStatus.UNEXPECTED_FAILURE, counts, errors + 1, warnings,
                    List.of(exception.getClass().getSimpleName()));
        }
        CatalogueValidationStatus status = errors > 0 ? CatalogueValidationStatus.INVALID
                : warnings > 0 ? CatalogueValidationStatus.VALID_WITH_WARNINGS
                : CatalogueValidationStatus.VALID;
        return result(status, counts, errors, warnings, issues);
    }

    public PlaceholderPage placeholders(RegistrySnapshot snapshot, int requestedPage) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<PlaceholderEntry> all = snapshot.banners().activeDefinitions().stream()
                .filter(definition -> definition.contentStatus() != BannerContentStatus.COMPLETE)
                .map(definition -> new PlaceholderEntry(
                        definition.id(), definition.displayNameKey(),
                        definition.dimensions().widthBlocks(), definition.dimensions().heightBlocks(),
                        definition.contentStatus(), definition.sourceReference().sourceLabel().isEmpty(),
                        definition.dimensions().provisional()))
                .sorted(java.util.Comparator.comparing(entry -> entry.definitionId().toString()))
                .toList();
        int pageCount = Math.max(1, (all.size() + PLACEHOLDER_PAGE_SIZE - 1) / PLACEHOLDER_PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pageCount));
        int from = Math.min((page - 1) * PLACEHOLDER_PAGE_SIZE, all.size());
        int to = Math.min(from + PLACEHOLDER_PAGE_SIZE, all.size());
        return new PlaceholderPage(all.subList(from, to), requestedPage, page, pageCount, all.size(),
                PLACEHOLDER_PAGE_SIZE);
    }

    private static Counts counts(RegistrySnapshot snapshot) {
        int placeholders = (int) snapshot.banners().activeDefinitions().stream()
                .filter(definition -> definition.contentStatus() != BannerContentStatus.COMPLETE).count();
        int provisionalNames = (int) snapshot.banners().activeDefinitions().stream()
                .filter(definition -> definition.sourceReference().sourceLabel().isEmpty()).count();
        int provisionalDimensions = (int) snapshot.banners().activeDefinitions().stream()
                .filter(definition -> definition.dimensions().provisional()).count();
        return new Counts(snapshot.banners().activeCount() + snapshot.banners().disabledCount(),
                snapshot.banners().activeCount(), snapshot.banners().disabledCount(),
                snapshot.fabricMaterials().activeCount(), snapshot.materialPalettes().activeCount(),
                snapshot.pigments().activeCount(), snapshot.mounts().activeCount(),
                placeholders, provisionalNames, provisionalDimensions);
    }

    private static CatalogueValidationResult result(
            CatalogueValidationStatus status, Counts counts, int errors, int warnings, List<String> issues) {
        return new CatalogueValidationResult(status, counts.bannerDefinitions, counts.activeBanners,
                counts.disabledBanners, counts.materials, counts.palettes, counts.pigments, counts.mounts,
                counts.placeholders, counts.provisionalNames, counts.provisionalDimensions,
                errors, warnings, issues);
    }

    private record Counts(
            int bannerDefinitions, int activeBanners, int disabledBanners, int materials, int palettes,
            int pigments, int mounts, int placeholders, int provisionalNames, int provisionalDimensions) {
    }
}
