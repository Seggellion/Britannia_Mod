package com.seggellion.britannia_mod.dye.item;

import com.seggellion.britannia_mod.dye.state.DyeTubState;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import java.util.Objects;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

/** Normalizes legacy component absence to the canonical empty value without mutating the stack. */
public final class DyeTubStateAccess {
    private DyeTubStateAccess() {
    }

    public static DyeTubState read(ItemStack stack) {
        return read(stack, DataComponentRegistry.DYE_TUB_STATE.get());
    }

    public static DyeTubState read(ItemStack stack, DataComponentType<DyeTubState> componentType) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(componentType, "componentType");
        return stack.getOrDefault(componentType, DyeTubState.empty());
    }

    public static void write(ItemStack stack, DyeTubState state) {
        write(stack, DataComponentRegistry.DYE_TUB_STATE.get(), state);
    }

    public static void write(
            ItemStack stack,
            DataComponentType<DyeTubState> componentType,
            DyeTubState state) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(componentType, "componentType");
        stack.set(componentType, Objects.requireNonNull(state, "state"));
    }
}
