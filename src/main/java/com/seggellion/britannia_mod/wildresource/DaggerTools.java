package com.seggellion.britannia_mod.wildresource;

import com.seggellion.britannia_mod.registry.WeaponRegistry;
import net.minecraft.world.item.ItemStack;

/** Exact project dagger identity; generic swords and QualitySwordItem instances do not qualify. */
public final class DaggerTools {
    private DaggerTools() {
    }

    public static boolean isDagger(ItemStack stack) {
        return stack.is(WeaponRegistry.DAGGER.get());
    }
}
