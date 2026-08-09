package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.item.QualityShovelItem;
import com.seggellion.britannia_mod.item.QualityToolItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;

import java.util.HashMap;
import java.util.Map;

public class ToolRegistry {
    public static final DeferredRegister<Item> TOOLS = DeferredRegister.create(
        Registries.ITEM, BritanniaMod.MODID
    );

    public static final DeferredHolder<Item, QualityToolItem> PICKAXE = TOOLS.register(
        "pickaxe",
        () -> new QualityToolItem(UOMetalToolMaterial.IRON.getTier(), new Item.Properties().stacksTo(1))
    );

    public static final DeferredHolder<Item, QualityShovelItem> SHOVEL = TOOLS.register(
        "britannia_shovel",
        () -> new QualityShovelItem(UOMetalToolMaterial.IRON.getTier(), new Item.Properties().stacksTo(1))
    );

    public static ItemStack createPickaxe(UOMetalToolMaterial material, int quality) {
        ItemStack stack = new ItemStack(PICKAXE.get());
        QualityToolItem.setQuality(stack, quality);
        
        // Updated this line to use the new string-based material system!
        QualityToolItem.setMaterial(stack, material); 
        
        return stack;
    }

    public static ItemStack createShovel(UOMetalToolMaterial material, int quality) {
        ItemStack stack = new ItemStack(SHOVEL.get());
        QualityShovelItem.setQuality(stack, quality);
        QualityShovelItem.setMaterial(stack, material);
        return stack;
    }

    public static void register(IEventBus eventBus) {
        TOOLS.register(eventBus);
    }
}
