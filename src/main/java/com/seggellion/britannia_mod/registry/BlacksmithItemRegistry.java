package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.item.BlacksmithEquipmentItem;
import com.seggellion.britannia_mod.skill.crafting.CraftableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

/** Registers stable placeholder outputs only where an established item ID does not already exist. */
public final class BlacksmithItemRegistry {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, BritanniaMod.MODID);
    private static final Set<String> EXISTING_OUTPUTS = Set.of("dagger", "viking_sword", "two_handed_axe", "order_shield");
    private static final Set<String> LEGACY_OUTPUTS = Set.of("heavy_cannonball", "light_cannonball",
            "heavy_grapeshot", "light_grapeshot", "heavy_ship_cannon", "light_ship_cannon");
    private static boolean populated;
    private static final Map<String, ResourceLocation> INGREDIENT_IDS = new HashMap<>();

    private BlacksmithItemRegistry() {}

    private static void populate() {
        if (populated) return;
        populated = true;
        CraftableRegistry.getAll().stream()
                .filter(def -> !EXISTING_OUTPUTS.contains(def.resultItem().getPath()))
                .forEach(def -> ITEMS.register(def.resultItem().getPath(),
                        () -> new BlacksmithEquipmentItem(def,
                                outputProperties(def))));
        LEGACY_OUTPUTS.forEach(id -> ITEMS.register(id, () -> new Item(new Item.Properties())));

        INGREDIENT_IDS.put("board", ResourceLocation.withDefaultNamespace("oak_planks"));
        INGREDIENT_IDS.put("cloth", ResourceLocation.withDefaultNamespace("white_wool"));
        INGREDIENT_IDS.put("bone", ResourceLocation.withDefaultNamespace("bone"));
        INGREDIENT_IDS.put("boards_or_logs", ResourceLocation.withDefaultNamespace("oak_planks"));
        CraftableRegistry.getAll().stream().flatMap(def -> def.ingredients().stream())
                .map(req -> req.materialKey()).filter(key -> !key.equals("ingot"))
                .distinct().filter(key -> !INGREDIENT_IDS.containsKey(key))
                .forEach(key -> {
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, key);
                    INGREDIENT_IDS.put(key, id);
                    ITEMS.register(key, () -> new Item(new Item.Properties()));
                });
    }

    private static Item.Properties outputProperties(com.seggellion.britannia_mod.skill.crafting.CraftableDef def) {
        Item.Properties properties = new Item.Properties().stacksTo(def.outputCount() > 1 ? 64 : 1);
        var shield = com.seggellion.britannia_mod.skill.crafting.ShieldProfileRegistry.get(def.shieldProfileId());
        if (shield != null) return properties.durability(shield.maximumDurability());
        if (def.equipmentType().equals("weapon")) return properties.durability(250);
        if (def.equipmentType().equals("armor")) return properties.durability(300);
        return properties;
    }

    public static Item ingredientItem(String key) {
        ResourceLocation id = INGREDIENT_IDS.get(key);
        return id == null ? net.minecraft.world.item.Items.AIR : BuiltInRegistries.ITEM.get(id);
    }

    public static Item[] catalogueItems() {
        return ITEMS.getEntries().stream().map(holder -> holder.get())
                .filter(item -> item instanceof BlacksmithEquipmentItem).toArray(Item[]::new);
    }

    public static void register(IEventBus bus) {
        populate();
        ITEMS.register(bus);
    }
}
