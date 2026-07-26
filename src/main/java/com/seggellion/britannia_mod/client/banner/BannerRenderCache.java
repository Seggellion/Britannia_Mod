package com.seggellion.britannia_mod.client.banner;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.registry.BannerItemRegistry;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/** Bounded client-only baked-pass cache. It never retains mutable gameplay or screen objects. */
public final class BannerRenderCache {
    public static final int MAX_ENTRIES = 256;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BannerRenderEntryCache<BakedModel> MODELS = new BannerRenderEntryCache<>(MAX_ENTRIES);
    private static final BannerMissingLogTracker LOGGED_MISSING = new BannerMissingLogTracker();

    private BannerRenderCache() {
    }

    static BannerItemRenderState extract(ItemStack stack) {
        return BannerRenderStateExtractor.extract(stack, BannerItemRegistry.BANNER.get(),
                DataComponentRegistry.BANNER_INSTANCE_STATE.get(), ClientBannerRenderData.current(),
                BannerModelRepository.availability(), BannerModelRepository.generation());
    }

    static BakedModel resolve(ItemStack stack) {
        BannerItemRenderState state = extract(stack);
        BannerRenderKey key = state.key(BannerModelRepository.generation());
        return MODELS.getOrCreate(key, ignored -> {
            BannerAppearanceCache.plan(state.appearance());
            BakedModel model = BannerModelRepository.compose(state);
            if (state.fallback() && LOGGED_MISSING.first(
                    state.failure(), state.diagnosticId(), state.dataGeneration(), BannerModelRepository.generation())) {
                LOGGER.warn("Banner item render fallback: reason={}, stable_id={}",
                        state.failure(), state.diagnosticId());
            }
            return model;
        });
    }

    static synchronized void onModelsReloaded() {
        clear();
    }

    static synchronized void onClientDataReplaced() {
        clear();
    }

    private static synchronized void clear() {
        MODELS.clear();
        BannerAppearanceCache.clear();
        BannerPlacedRenderCache.clear();
        LOGGED_MISSING.clear();
    }

    public static int entryCount() {
        return MODELS.size();
    }

    public static int missingDiagnosticCount() {
        return LOGGED_MISSING.size();
    }
}
