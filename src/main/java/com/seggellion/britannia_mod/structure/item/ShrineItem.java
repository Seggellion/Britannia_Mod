package com.seggellion.britannia_mod.structure.item;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.placement.ShrinePlacementService;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Family placement item configured to the approved diagnostic Honesty shrine. */
public final class ShrineItem extends Item {
    private static final VariantId DIAGNOSTIC_VARIANT = new VariantId("honesty");

    public ShrineItem(Properties properties) {
        super(properties);
    }

    public FamilyId familyId() {
        return ShrineMonolithDefinitions.SHRINE;
    }

    public VariantId variantId() {
        return DIAGNOSTIC_VARIANT;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return ShrinePlacementService.place(context);
    }
}
