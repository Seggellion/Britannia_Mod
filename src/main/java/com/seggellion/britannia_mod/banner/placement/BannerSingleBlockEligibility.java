package com.seggellion.britannia_mod.banner.placement;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;

/** Legacy one-cell classification, now evaluated against the caller's selected orientation. */
public final class BannerSingleBlockEligibility {
    public enum Result {
        ELIGIBLE,
        ORIENTATION_UNSUPPORTED,
        MULTI_BLOCK_DEFERRED
    }

    private BannerSingleBlockEligibility() {
    }

    public static Result evaluate(BannerDefinition definition) {
        return evaluate(definition, definition.supportedOrientations().getFirst());
    }

    public static Result evaluate(BannerDefinition definition, BannerOrientation orientation) {
        if (!definition.supportedOrientations().contains(orientation)) {
            return Result.ORIENTATION_UNSUPPORTED;
        }
        return definition.dimensions().widthBlocks() == 1 && definition.dimensions().heightBlocks() == 1
                ? Result.ELIGIBLE
                : Result.MULTI_BLOCK_DEFERRED;
    }

    public static boolean isEligible(BannerDefinition definition) {
        return evaluate(definition) == Result.ELIGIBLE;
    }

    public static boolean isEligible(BannerDefinition definition, BannerOrientation orientation) {
        return evaluate(definition, orientation) == Result.ELIGIBLE;
    }
}
