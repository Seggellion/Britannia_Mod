package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;

/**
 * Maintains "Viking Swords" for different metals (IRON, VALORITE, etc.).
 */
public class SwordRegistry {
    public static final DeferredRegister<Item> SWORDS = DeferredRegister.create(
        net.minecraft.core.registries.Registries.ITEM,
        BritanniaMod.MODID
    );

    private static final Map<UOMetalToolMaterial, DeferredHolder<Item, QualitySwordItem>> VIKING_SWORDS = new HashMap<>();

    // Only ONE Viking Sword item
    public static final DeferredHolder<Item, QualitySwordItem> VIKING_SWORD = SWORDS.register(
        "viking_sword",
        () -> new QualitySwordItem(
            UOMetalToolMaterial.IRON.getTier(), // Default Tier (can override with custom model data)
            new Item.Properties().stacksTo(1)
        )
    );
    

   public static ItemStack createVikingSword(UOMetalToolMaterial material, int quality) {
        ItemStack stack = new ItemStack(VIKING_SWORD.get());
        QualitySwordItem.setQuality(stack, quality);
        QualitySwordItem.setMaterialModelData(stack, material);
        return stack;
    }


    public static void register(IEventBus eventBus) {
        SWORDS.register(eventBus);
    }
}
