package com.seggellion.britannia_mod.banner.placement;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;

/** Eligibility is deliberately based on authored dimensions/orientation, never catalogue IDs or groups. */
public final class BannerSingleBlockEligibility {
    public enum Result {
        ELIGIBLE,
        WALL_PARALLEL_UNSUPPORTED,
        MULTI_BLOCK_DEFERRED
    }

    private BannerSingleBlockEligibility() {
    }

    public static Result evaluate(BannerDefinition definition) {
        if (!definition.supportedOrientations().contains(BannerOrientation.WALL_PARALLEL)) {
            return Result.WALL_PARALLEL_UNSUPPORTED;
        }
        return definition.dimensions().widthBlocks() == 1 && definition.dimensions().heightBlocks() == 1
                ? Result.ELIGIBLE
                : Result.MULTI_BLOCK_DEFERRED;
    }

    public static boolean isEligible(BannerDefinition definition) {
        return evaluate(definition) == Result.ELIGIBLE;
    }
}
