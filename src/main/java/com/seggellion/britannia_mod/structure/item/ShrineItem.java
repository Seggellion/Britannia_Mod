package com.seggellion.britannia_mod.structure.item;

import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;

/** One shared shrine item; absent component state resolves to the approved Honesty default. */
public final class ShrineItem extends ConfiguredStructureItem {
    public ShrineItem(Properties properties) {
        this(properties, DataComponentRegistry.SHRINE_INSTANCE_STATE::get);
    }

    public ShrineItem(Properties properties, Supplier<DataComponentType<ShrineItemState>> componentType) {
        super(properties, componentType, ShrineMonolithDefinitions.SHRINE, new VariantId("honesty"), 4);
    }
}
