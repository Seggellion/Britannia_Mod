package com.seggellion.britannia_mod.structure.render;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ResourceId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlockEntity;
import java.util.Objects;

/** Pure, display-only selection of the shrine rim material from synchronized structure identity. */
public final class ShrineRimMaterialSelection {
    public static final ResourceId DEFAULT_GRANITE = new ResourceId(
            "britannia_mod", "textures/block/shrine/granite.png");
    public static final ResourceId CHAOS_GRANITE = new ResourceId(
            "britannia_mod", "textures/block/shrine/light_granite.png");
    private static final VariantId CHAOS = new VariantId("chaos");

    private ShrineRimMaterialSelection() {
    }

    public static ResourceId textureFor(LargeStructureAnchorBlockEntity anchor) {
        Objects.requireNonNull(anchor, "anchor");
        return anchor.placedState()
                .map(state -> textureFor(state.familyId(), state.variantId()))
                .orElse(DEFAULT_GRANITE);
    }

    public static ResourceId textureFor(FamilyId familyId, VariantId variantId) {
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(variantId, "variantId");
        return familyId.equals(ShrineMonolithDefinitions.SHRINE) && variantId.equals(CHAOS)
                ? CHAOS_GRANITE : DEFAULT_GRANITE;
    }
}
