package com.seggellion.britannia_mod.item;

import net.minecraft.world.item.Item;
import com.seggellion.britannia_mod.structure.HouseSize;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.seggellion.britannia_mod.registry.CreativeTabRegistry; // <- adjust package path


public final class DeedItemFactory {

    private DeedItemFactory() {}

    /** Registers one deed and returns the holder you can stash for later use. */
    public static DeferredHolder<Item, Item> register(DeferredRegister<Item> reg,
                                                      HouseSize size) {

       return reg.register(size.id() + "_house_deed",
            () -> new AbstractHouseDeedItem(size,
                   new Item.Properties()
                       .stacksTo(1)) {});
    }
}
