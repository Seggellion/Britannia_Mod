package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.bannerdyeing.diagnostics.BoundedDiagnosticTracker;
import java.util.Objects;

/** De-duplicates missing-content diagnostics within one data/resource generation. */
public final class BannerMissingLogTracker {
    public static final int MAX_DIAGNOSTICS = 256;
    private final BoundedDiagnosticTracker<MissingDiagnostic> diagnostics =
            new BoundedDiagnosticTracker<>(MAX_DIAGNOSTICS);

    public synchronized boolean first(
            BannerRenderFailure failure, String stableId, long dataGeneration, long resourceGeneration) {
        return diagnostics.first(new MissingDiagnostic(
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
