package com.seggellion.britannia_mod.structure.item;

import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;

/** One configured monolith-family item defaulting to the original provisional variant. */
public final class MonolithItem extends ConfiguredStructureItem {
    public MonolithItem(Properties properties) {
        this(properties, DataComponentRegistry.MONOLITH_INSTANCE_STATE::get);
    }

    public MonolithItem(
            Properties properties, Supplier<DataComponentType<ShrineItemState>> componentType) {
        super(properties, componentType, ShrineMonolithDefinitions.MONOLITH,
                new VariantId("diagnostic_missing_content"), 18);
    }
}
