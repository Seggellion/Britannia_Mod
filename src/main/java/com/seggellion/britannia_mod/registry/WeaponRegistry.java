package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import com.seggellion.britannia_mod.item.DecorativeShieldItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;

import com.seggellion.britannia_mod.item.BlacksmithItemData;
import com.seggellion.britannia_mod.skill.crafting.CraftableDef;
import com.seggellion.britannia_mod.skill.crafting.CraftableRegistry;
import java.util.LinkedHashMap;
import java.util.Map;

public class WeaponRegistry {
    private static final Map<String, WeaponProfile> PROFILES = new LinkedHashMap<>();
    private static final Map<String, String> VARIANT_BASES = Map.ofEntries(
            Map.entry("true_leafblade", "leafblade"),
            Map.entry("leafblade_of_ease", "leafblade"),
            Map.entry("magekiller_leafblade", "leafblade"),
            Map.entry("charged_assassin_spike", "assassin_spike"),
            Map.entry("true_assassin_spike", "assassin_spike"),
            Map.entry("wounding_assassin_spike", "assassin_spike"),
            Map.entry("magekiller_assassin_spike", "assassin_spike"),
            Map.entry("fiery_spellblade", "elven_spellblade"),
            Map.entry("icy_spellblade", "elven_spellblade"),
            Map.entry("true_spellblade", "elven_spellblade"),
            Map.entry("rune_blade_of_knowledge", "rune_blade"),
            Map.entry("mages_rune_blade", "rune_blade"),
            Map.entry("true_radiant_scimitar", "radiant_scimitar"),
            Map.entry("true_war_cleaver", "war_cleaver"),
            Map.entry("butchers_war_cleaver", "war_cleaver"),
            Map.entry("knights_war_cleaver", "war_cleaver"),
            Map.entry("serrated_war_cleaver", "war_cleaver")
    );
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

    // Promote existing catalogue IDs without changing saved item identities.
    public static final DeferredHolder<Item, QualitySwordItem> KATANA = registerBlade("katana");
    public static final DeferredHolder<Item, QualitySwordItem> RAPIER = registerBlade("rapier");
    public static final DeferredHolder<Item, QualitySwordItem> HALBERD = registerBlade("halberd");
    public static final DeferredHolder<Item, DecorativeShieldItem> DECORATIVE_SHIELD = WEAPONS.register(
            "decorative_shield", () -> new DecorativeShieldItem(new Item.Properties().durability(
                    com.seggellion.britannia_mod.skill.crafting.ShieldProfileRegistry
                            .get("decorative_shield").maximumDurability())));

    private static DeferredHolder<Item, QualitySwordItem> registerBlade(String id) {
        return WEAPONS.register(id, () -> new QualitySwordItem(
                UOMetalToolMaterial.IRON.getTier(), new Item.Properties().stacksTo(1)));
    }

    public static Item[] implementedItems() {
        return new Item[] {DAGGER.get(), VIKING_SWORD.get(), KATANA.get(), RAPIER.get(),
                HALBERD.get(), DECORATIVE_SHIELD.get()};
    }

    // Generic factory method that works for ANY weapon in this registry
    public static ItemStack createWeapon(Item weaponItem, UOMetalToolMaterial material, int quality) {
        ItemStack stack = new ItemStack(weaponItem);
        if (weaponItem instanceof QualitySwordItem) {
            QualitySwordItem.setQuality(stack, quality);
            QualitySwordItem.setMaterial(stack, material);
        }
        BlacksmithItemData.apply(stack, material, quality, "unknown", "Unknown");
        return stack;
    }

    public static void initializeProfiles() {
        if (!PROFILES.isEmpty()) return;
        CraftableRegistry.getAll().stream()
                .filter(def -> CraftableDef.isWeaponCategory(def.category()))
                .forEach(def -> {
                    // Exact source statistics are imported here when supplied. Until then every fallback is explicit.
                    int minDamage = def.category().equalsIgnoreCase("Throwing") ? 8 : 10;
                    int maxDamage = def.category().equalsIgnoreCase("Bashing") ? 18 : 16;
                    boolean twoHanded = def.displayName().toLowerCase().contains("two handed")
                            || def.displayName().toLowerCase().contains("staff")
                            || def.displayName().toLowerCase().contains("halberd")
                            || def.displayName().toLowerCase().contains("bardiche");
                    String base = VARIANT_BASES.get(def.id());
                    PROFILES.put(def.id(), new WeaponProfile(def.id(), 0, minDamage, maxDamage, 2.5,
                            twoHanded, def.category().toLowerCase(), null, base, true));
                });
    }

    public static WeaponProfile getProfile(String id) {
        initializeProfiles();
        return id == null ? null : PROFILES.get(id);
    }

    public static Map<String, WeaponProfile> profiles() {
        initializeProfiles();
        return Map.copyOf(PROFILES);
    }

    public static Map<String, String> variantMappings() { return VARIANT_BASES; }

    public static void register(IEventBus eventBus) {
        initializeProfiles();
        WEAPONS.register(eventBus);
    }
}
