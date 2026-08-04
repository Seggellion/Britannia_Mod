package com.seggellion.britannia_mod.structure.item;

import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import net.minecraft.world.item.ItemStack;

/** Sole placed-shrine to configured-family-item conversion path. */
public final class ShrineItemTransfer {
    private ShrineItemTransfer() {
    }

    public static ItemStack fromPlacedState(ConfiguredStructureItem item, PlacedStructureState state) {
        return item.stateAccess().configuredStack(new ShrineItemState(
                state.schemaVersion(), state.familyId(), state.variantId()));
    }
}
