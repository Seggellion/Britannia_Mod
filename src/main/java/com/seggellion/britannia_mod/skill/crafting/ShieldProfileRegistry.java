package com.seggellion.britannia_mod.skill.crafting;

import java.util.Map;

public final class ShieldProfileRegistry {
    public record ShieldProfile(ResistanceProfile bonus, int minimumStrength, int minimumDurability, int maximumDurability,
                                boolean provisional) {
        public ShieldProfile(ResistanceProfile bonus, int minimumStrength, int minimumDurability, int maximumDurability) {
            this(bonus, minimumStrength, minimumDurability, maximumDurability, false);
        }
        public ShieldProfile {
            if (minimumStrength < 0 || minimumDurability <= 0 || maximumDurability < minimumDurability) {
                throw new IllegalArgumentException("Invalid shield profile");
            }
        }
    }

    private static ResistanceProfile bonus(int physical, int fire, int ice, int poison, int magic) {
        return new ResistanceProfile(physical, fire, ice, poison, magic, false);
    }

    private static final Map<String, ShieldProfile> PROFILES = Map.ofEntries(
            Map.entry("bronze_shield", new ShieldProfile(bonus(0,0,1,0,0),35,25,30)),
            Map.entry("buckler", new ShieldProfile(bonus(0,0,0,1,0),20,40,50)),
            Map.entry("chaos_shield", new ShieldProfile(bonus(1,0,0,0,0),95,100,125)),
            Map.entry("heater_shield", new ShieldProfile(bonus(0,1,0,0,0),90,50,65)),
            Map.entry("metal_shield", new ShieldProfile(bonus(0,1,0,0,0),45,50,65)),
            // Source art has no balance data; use the existing metal shield analogue.
            Map.entry("decorative_shield", new ShieldProfile(bonus(0,1,0,0,0),45,50,65,true)),
            Map.entry("metal_kite_shield", new ShieldProfile(bonus(0,0,0,0,1),45,45,60)),
            Map.entry("order_shield", new ShieldProfile(bonus(1,0,0,0,0),95,100,125)),
            Map.entry("tear_kite_shield", new ShieldProfile(bonus(0,0,0,0,1),20,50,65)),
            Map.entry("small_plate_shield", new ShieldProfile(bonus(0,0,1,0,0),20,30,31)),
            Map.entry("gargish_kite_shield", new ShieldProfile(bonus(0,0,0,0,1),45,58,70)),
            Map.entry("large_plate_shield", new ShieldProfile(bonus(0,1,0,0,0),90,60,78)),
            Map.entry("medium_plate_shield", new ShieldProfile(bonus(0,1,0,0,0),45,60,75)),
            Map.entry("gargish_chaos_shield", new ShieldProfile(bonus(1,0,0,0,0),95,112,128)),
            Map.entry("gargish_order_shield", new ShieldProfile(bonus(1,0,0,0,0),95,112,148)),
            Map.entry("large_stone_shield", new ShieldProfile(bonus(0,0,0,1,0),20,57,66)),
            Map.entry("shield_orb", new ShieldProfile(bonus(0,0,0,0,1),0,50,65,true))
    );

    private ShieldProfileRegistry() {}
    public static ShieldProfile get(String id) { return id == null ? null : PROFILES.get(id); }
    public static Map<String, ShieldProfile> all() { return PROFILES; }
}
