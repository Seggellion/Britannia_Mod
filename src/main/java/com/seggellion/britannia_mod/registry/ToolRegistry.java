package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.item.QualityToolItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ToolRegistry {
    public static final DeferredRegister<Item> TOOLS = DeferredRegister.create(
        Registries.ITEM, BritanniaMod.MODID
    );

    public static final DeferredHolder<Item, QualityToolItem> PICKAXE = TOOLS.register(
        "pickaxe",
        () -> new QualityToolItem(UOMetalToolMaterial.IRON.getTier(), new Item.Properties().stacksTo(1))
    );

    public static ItemStack createPickaxe(UOMetalToolMaterial material, int quality) {
        ItemStack stack = new ItemStack(PICKAXE.get());
        QualityToolItem.setQuality(stack, quality);
        QualityToolItem.setMaterialModelData(stack, material);
        return stack;
    }

    public static void register(IEventBus eventBus) {
        TOOLS.register(eventBus);
    }
}
