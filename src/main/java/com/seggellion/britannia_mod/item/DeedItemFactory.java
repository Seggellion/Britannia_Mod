package com.seggellion.britannia_mod.item;

import net.minecraft.world.item.Item;
import com.seggellion.britannia_mod.structure.HouseStyle;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.seggellion.britannia_mod.registry.CreativeTabRegistry; // <- adjust package path


public final class DeedItemFactory {

    private DeedItemFactory() {}

    /** Registers one deed and returns the holder you can stash for later use. */
    public static DeferredHolder<Item, Item> register(DeferredRegister<Item> reg, HouseStyle style) {
        String itemId = style.getStructureFile().replace(".nbt", "") + "_deed"; // ✅ "wooden_house_house_deed"
        return reg.register(itemId, () -> new AbstractHouseDeedItem(style, new Item.Properties().stacksTo(1)) {});
    }
}
