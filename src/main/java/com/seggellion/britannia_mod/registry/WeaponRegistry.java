package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;

public class WeaponRegistry {
    // Renamed from SWORDS to WEAPONS
    public static final DeferredRegister<Item> WEAPONS = DeferredRegister.create(
        net.minecraft.core.registries.Registries.ITEM,
        BritanniaMod.MODID
    );

    // --- SWORDS ---
    public static final DeferredHolder<Item, QualitySwordItem> VIKING_SWORD = WEAPONS.register(
        "viking_sword",
        () -> new QualitySwordItem(
            UOMetalToolMaterial.IRON.getTier(), 
            new Item.Properties().stacksTo(1)
        )
    );

    // --- BLADED ---
    public static final DeferredHolder<Item, QualitySwordItem> DAGGER = WEAPONS.register(
        "dagger",
        () -> new QualitySwordItem(
            UOMetalToolMaterial.IRON.getTier(), 
            new Item.Properties().stacksTo(1)
        )
    );

    // Generic factory method that works for ANY weapon in this registry
    public static ItemStack createWeapon(Item weaponItem, UOMetalToolMaterial material, int quality) {
        ItemStack stack = new ItemStack(weaponItem);
        // Applies your custom UO properties to the stack
        QualitySwordItem.setQuality(stack, quality);
        QualitySwordItem.setMaterial(stack, material); 
        return stack;
    }

    public static void register(IEventBus eventBus) {
        WEAPONS.register(eventBus);
    }
}