package com.seggellion.britannia_mod.dye.preview;

import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.service.DyeResult;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.Objects;
import java.util.Optional;

/** Complete non-mutating preview plan, ready to become one transient session. */
public record DyePreviewPlan(
        Optional<BannerInstanceState> bannerState,
        Optional<DyeTubState> tubState,
        Optional<PigmentId> pigmentId,
        Optional<DyeResult> result,
        Optional<DyePreviewDisplayData> displayData,
        DyePreviewFailure failure) {
    public DyePreviewPlan {
        bannerState = Objects.requireNonNull(bannerState, "bannerState");
        tubState = Objects.requireNonNull(tubState, "tubState");
        pigmentId = Objects.requireNonNull(pigmentId, "pigmentId");
        result = Objects.requireNonNull(result, "result");
        displayData = Objects.requireNonNull(displayData, "displayData");
        Objects.requireNonNull(failure, "failure");
    }

    public static DyePreviewPlan failure(DyePreviewFailure failure) {
        return new DyePreviewPlan(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), failure);
    }

    public static DyePreviewPlan success(
            BannerInstanceState bannerState, DyeTubState tubState, PigmentId pigmentId,
            DyeResult result, DyePreviewDisplayData displayData) {
        return new DyePreviewPlan(Optional.of(bannerState), Optional.of(tubState), Optional.of(pigmentId),
                Optional.of(result), Optional.of(displayData), DyePreviewFailure.NONE);
    }

    public boolean successful() {
        return failure == DyePreviewFailure.NONE;
    }
}
