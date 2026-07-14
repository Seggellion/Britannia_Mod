package com.seggellion.britannia_mod.skill.crafting;

import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import java.util.EnumMap;
import java.util.Map;

/** One reviewable source for smithing material presentation and effective-stat modifiers. */
public final class MaterialProfileRegistry {
    public record MaterialProfile(int tint, double attackMultiplier, double defenseMultiplier,
                                  double durabilityMultiplier, double weightMultiplier,
                                  double craftingDifficultyModifier, boolean provisional) {}

    private static final Map<UOMetalToolMaterial, MaterialProfile> PROFILES = new EnumMap<>(UOMetalToolMaterial.class);
    static {
        PROFILES.put(UOMetalToolMaterial.IRON,        new MaterialProfile(0xD8D8D8, 1.00, 1.00, 1.00, 1.00, 0.00, false));
        PROFILES.put(UOMetalToolMaterial.GOLD,        new MaterialProfile(0xFFD700, 0.90, 0.90, 0.75, 1.15, 0.00, true));
        PROFILES.put(UOMetalToolMaterial.SHADOW_IRON, new MaterialProfile(0x4D535B, 1.04, 1.04, 1.10, 1.05, 2.50, true));
        PROFILES.put(UOMetalToolMaterial.COPPER,      new MaterialProfile(0xB87333, 0.96, 0.96, 0.90, 1.05, 0.00, true));
        PROFILES.put(UOMetalToolMaterial.TIN,         new MaterialProfile(0xC9D1D3, 0.92, 0.92, 0.80, 0.90, 0.00, true));
        PROFILES.put(UOMetalToolMaterial.SILVER,      new MaterialProfile(0xE8E8F0, 1.02, 1.00, 0.90, 1.00, 1.00, true));
        PROFILES.put(UOMetalToolMaterial.AGAPITE,     new MaterialProfile(0xD47A45, 1.08, 1.08, 1.35, 1.08, 5.00, true));
        PROFILES.put(UOMetalToolMaterial.VERITE,      new MaterialProfile(0x4F9B78, 1.12, 1.12, 1.50, 1.10, 7.50, true));
        PROFILES.put(UOMetalToolMaterial.VALORITE,    new MaterialProfile(0x5674C8, 1.16, 1.16, 1.70, 1.12, 10.00, true));
    }

    private MaterialProfileRegistry() {}
    public static MaterialProfile get(UOMetalToolMaterial material) { return PROFILES.get(material); }
    public static Map<UOMetalToolMaterial, MaterialProfile> all() { return Map.copyOf(PROFILES); }
}
