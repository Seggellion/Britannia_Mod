package com.seggellion.britannia_mod.bannerdyeing.registry;

import com.seggellion.britannia_mod.banner.BannerFeature;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationIssue;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationPolicy;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationSeverity;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationSummary;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Server-data reload listener; parsing occurs during preparation and publication during apply. */
final class BannerDataReloadListener extends SimplePreparableReloadListener<PreparedRegistryData> {
    private final RegistryDataLoader loader = new RegistryDataLoader();
    private final ValidationPolicy policy;

    BannerDataReloadListener(ValidationPolicy policy) {
        this.policy = policy;
    }

    @Override
    protected PreparedRegistryData prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return loader.prepare(resourceManager);
    }

    @Override
    protected void apply(PreparedRegistryData prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        RegistrySnapshotPublisher candidatePublisher = new RegistrySnapshotPublisher(BannerDataRegistries.current());
        RegistryLoadResult result = loader.apply(prepared, policy, candidatePublisher);
        log(result);
        if (!result.published()) {
            throw new IllegalStateException("Banner data reload rejected: " + summaryText(result.report().summary()));
        }
        if (ProductionBannerCatalogue.isProductionCatalogue(result.snapshot())) {
            ProductionBannerCatalogue.requireComplete(result.snapshot());
            long placeholders = result.snapshot().banners().activeDefinitions().stream()
                    .filter(definition -> definition.contentStatus()
                            == com.seggellion.britannia_mod.banner.data.BannerContentStatus.PLACEHOLDER)
                    .count();
            BannerFeature.CONTENT_VALIDATION_LOGGER.info(
                    "Production banner catalogue active=33 disabled=0 placeholders={}", placeholders);
        }
        BannerDataRegistries.publisher().publish(result.snapshot());
    }

    private static void log(RegistryLoadResult result) {
        BannerFeature.CONTENT_VALIDATION_LOGGER.info("Banner data reload {}: {}",
                result.published() ? "published" : "rejected", summaryText(result.report().summary()));
        for (ValidationIssue issue : result.report().issues()) {
            String text = "[{}] {} id={} source={} related={} - {}".formatted(
                    issue.code(), issue.domain(), issue.definitionId().orElse("<unknown>"),
                    issue.sourceResource().map(Object::toString).orElse("<unknown>"),
                    issue.relatedId().orElse("<none>"), issue.message());
            if (issue.severity() == ValidationSeverity.ERROR) {
                BannerFeature.CONTENT_VALIDATION_LOGGER.error(text);
            } else if (issue.severity() == ValidationSeverity.WARNING) {
                BannerFeature.CONTENT_VALIDATION_LOGGER.warn(text);
            } else {
                BannerFeature.CONTENT_VALIDATION_LOGGER.info(text);
            }
        }
    }

    private static String summaryText(ValidationSummary summary) {
        return "discovered=%d decoded=%d active=%s disabled=%s errors=%d warnings=%d information=%d".formatted(
                summary.resourcesDiscovered(), summary.resourcesDecoded(), summary.activeEntries(),
                summary.disabledEntries(), summary.errors(), summary.warnings(), summary.information());
    }
}
