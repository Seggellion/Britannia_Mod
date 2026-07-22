package com.seggellion.britannia_mod.banner.placement;

import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.structure.BannerPlacedStructure;
import com.seggellion.britannia_mod.banner.structure.BannerStructureCell;
import java.util.Optional;

/** Narrow transactional boundary shared by the live level and rollback tests. */
public interface BannerPlacementMutation {
    interface StateTarget {
        boolean assign(BannerInstanceState state, BannerPlacedStructure structure);
        Optional<BannerInstanceState> currentState();
        Optional<BannerPlacedStructure> currentStructure();
        boolean synchronize();
    }

    boolean placeCell(BannerPlacementPlan plan, BannerStructureCell cell);
    Optional<StateTarget> bannerBlockEntity(BannerPlacementPlan plan);
    boolean verifyCell(BannerPlacementPlan plan, BannerStructureCell cell);
    boolean rollback(BannerPlacementPlan plan);
    void afterSuccess(BannerPlacementPlan plan);
}
