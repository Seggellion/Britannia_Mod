package com.seggellion.britannia_mod.client.banner;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** De-duplicates missing-content diagnostics within one data/resource generation. */
public final class BannerMissingLogTracker {
    private final Set<MissingDiagnostic> diagnostics = new LinkedHashSet<>();

    public synchronized boolean first(
            BannerRenderFailure failure, String stableId, long dataGeneration, long resourceGeneration) {
        return diagnostics.add(new MissingDiagnostic(
                Objects.requireNonNull(failure, "failure"), Objects.requireNonNull(stableId, "stableId"),
                dataGeneration, resourceGeneration));
    }

    public synchronized void clear() {
        diagnostics.clear();
    }

    public synchronized int size() {
        return diagnostics.size();
    }

    private record MissingDiagnostic(
            BannerRenderFailure failure, String stableId, long dataGeneration, long resourceGeneration) {
    }
}
