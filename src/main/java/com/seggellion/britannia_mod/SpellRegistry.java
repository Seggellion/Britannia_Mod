package com.seggellion.britannia_mod.magic;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class SpellRegistry {
    private static final Map<ItemStack, Spell> SPELLS = new HashMap<>();

    static {
        // Register your spells here
        SPELLS.put(new ItemStack(BritanniaMod.NIGHT_SIGHT_ITEM.get()), new NightSightSpell());
        SPELLS.put(new ItemStack(BritanniaMod.HEAL_ITEM.get()), new HealSpell());
        SPELLS.put(new ItemStack(BritanniaMod.MAGIC_ARROW_ITEM.get()), new MagicArrowSpell());
        SPELLS.put(new ItemStack(BritanniaMod.CLUMSY_ITEM.get()), new ClumsySpell());
        SPELLS.put(new ItemStack(BritanniaMod.CREATE_FOOD_ITEM.get()), new CreateFoodSpell());
        SPELLS.put(new ItemStack(BritanniaMod.WEAKNESS_ITEM.get()), new WeaknessSpell());
        SPELLS.put(new ItemStack(BritanniaMod.REACTIVE_ARMOR_ITEM.get()), new ReactiveArmorSpell());
        SPELLS.put(new ItemStack(BritanniaMod.FEEBLEMIND_ITEM.get()), new FeeblemindSpell());
        // Add more spells as needed
    }

    public static Spell getSpell(ItemStack itemStack) {
        for (Map.Entry<ItemStack, Spell> entry : SPELLS.entrySet()) {
            if (itemStack.getItem() == entry.getKey().getItem()) {
                return entry.getValue();
            }
        }
        return null;
    }
}
