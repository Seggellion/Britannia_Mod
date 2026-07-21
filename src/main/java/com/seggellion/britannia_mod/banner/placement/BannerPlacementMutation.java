package com.seggellion.britannia_mod.banner.placement;

import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import java.util.Optional;

/** Narrow transactional boundary shared by the live level and rollback tests. */
public interface BannerPlacementMutation {
    interface StateTarget {
        boolean assign(BannerInstanceState state);
        Optional<BannerInstanceState> currentState();
    }

    boolean placeBanner(BannerPlacementPlan plan);
    Optional<StateTarget> bannerBlockEntity(BannerPlacementPlan plan);
    boolean rollback(BannerPlacementPlan plan);
    void afterSuccess(BannerPlacementPlan plan);
}
