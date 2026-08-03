package com.seggellion.britannia_mod.structure.item;

import com.seggellion.britannia_mod.structure.placement.ShrinePlacementService;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** One shared shrine item; absent component state resolves to the approved Honesty default. */
public final class ShrineItem extends Item {
    private final Supplier<DataComponentType<ShrineItemState>> componentType;

    public ShrineItem(Properties properties) {
        this(properties, com.seggellion.britannia_mod.registry.DataComponentRegistry
                .SHRINE_INSTANCE_STATE::get);
    }

    public ShrineItem(Properties properties, Supplier<DataComponentType<ShrineItemState>> componentType) {
        super(properties);
        this.componentType = Objects.requireNonNull(componentType, "componentType");
    }

    public ShrineItemStateAccess stateAccess() {
        return new ShrineItemStateAccess(this, componentType.get());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return ShrinePlacementService.place(context);
    }
}
