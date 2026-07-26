package com.seggellion.britannia_mod.client.banner;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;

/** Bounded placed-plan cache and generation-scoped diagnostic de-duplication. */
public final class BannerPlacedRenderCache {
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_DIAGNOSTICS = 256;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BannerPlacedRenderEntryCache<BannerPlacedRenderPlan> ENTRIES =
            new BannerPlacedRenderEntryCache<>(MAX_ENTRIES);
    private static final Set<Diagnostic> LOGGED_MISSING = new LinkedHashSet<>();

    private BannerPlacedRenderCache() {
    }

    public static BannerPlacedRenderPlan resolve(BannerPlacedRenderState state) {
        BannerPlacedRenderPlan plan = ENTRIES.getOrCreate(
                state.key(), ignored -> BannerPlacedRenderPlan.from(state));
        if (state.fallback() && firstDiagnostic(state)) {
            LOGGER.warn("Placed banner render fallback: reason={}, stable_id={}, appearance_reason={}",
                    state.failure(), state.diagnosticId(), state.appearance().failure());
        }
        return plan;
    }

    static synchronized void clear() {
        ENTRIES.clear();
        LOGGED_MISSING.clear();
    }

    private static synchronized boolean firstDiagnostic(BannerPlacedRenderState state) {
        Diagnostic diagnostic = new Diagnostic(state.failure(), state.diagnosticId(),
                state.appearance().dataGeneration(), state.resourceGeneration());
        if (LOGGED_MISSING.contains(diagnostic)) {
            return false;
        }
        if (LOGGED_MISSING.size() >= MAX_DIAGNOSTICS) {
            var oldest = LOGGED_MISSING.iterator();
            if (oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
        }
        return LOGGED_MISSING.add(diagnostic);
    }

    public static int entryCount() {
        return ENTRIES.size();
    }

    public static synchronized int missingDiagnosticCount() {
        return LOGGED_MISSING.size();
    }

    private record Diagnostic(
            BannerPlacedRenderFailure failure,
            String stableId,
            long dataGeneration,
            long resourceGeneration) {
    }
}
