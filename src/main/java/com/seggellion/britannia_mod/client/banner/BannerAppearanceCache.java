package com.seggellion.britannia_mod.client.banner;

/** Shared bounded layer-description cache used by both item and placed rendering. */
public final class BannerAppearanceCache {
    public static final int MAX_ENTRIES = 256;
    private static final BannerAppearanceEntryCache<BannerLayerPlan> ENTRIES =
            new BannerAppearanceEntryCache<>(MAX_ENTRIES);

    private BannerAppearanceCache() {
    }

    public static BannerLayerPlan plan(BannerAppearanceState appearance) {
        return ENTRIES.getOrCreate(appearance.key(), ignored -> BannerLayerPlan.from(appearance));
    }

    static void clear() {
        ENTRIES.clear();
    }

    public static int entryCount() {
        return ENTRIES.size();
    }
}
