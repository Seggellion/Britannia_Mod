package com.seggellion.britannia_mod.dye.item;

import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.Item;

public record DyeTubLoadPlan(
        DyeTubLoadResult result,
        DyeTubState originalState,
        Optional<DyeTubState> replacementState,
        Optional<Item> pigmentItem,
        Optional<PigmentId> pigmentId) {

    public DyeTubLoadPlan {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(originalState, "originalState");
        Objects.requireNonNull(replacementState, "replacementState");
        Objects.requireNonNull(pigmentItem, "pigmentItem");
        Objects.requireNonNull(pigmentId, "pigmentId");
        if (result.loadedSuccessfully()
                != (replacementState.isPresent() && pigmentItem.isPresent() && pigmentId.isPresent())) {
            throw new IllegalArgumentException("Only successful load plans may contain a replacement mutation");
        }
    }

    public static DyeTubLoadPlan failure(DyeTubLoadResult result, DyeTubState originalState) {
        return new DyeTubLoadPlan(result, originalState, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static DyeTubLoadPlan success(
            DyeTubLoadResult result,
            DyeTubState originalState,
            DyeTubState replacementState,
            Item pigmentItem,
            PigmentId pigmentId) {
        return new DyeTubLoadPlan(
                result,
                originalState,
                Optional.of(replacementState),
                Optional.of(pigmentItem),
                Optional.of(pigmentId));
    }
}
