package com.seggellion.britannia_mod.skill.crafting;

import java.util.Map;

public final class ArmorProfileRegistry {
    private static final Map<String, ResistanceProfile> PROFILES = Map.of(
            "ringmail", new ResistanceProfile(3, 3, 1, 5, 3, false),
            "chainmail", new ResistanceProfile(4, 4, 4, 1, 2, false),
            "platemail", new ResistanceProfile(5, 3, 2, 3, 2, false),
            "female_plate", new ResistanceProfile(5, 3, 2, 3, 2, false),
            "platemail_do", new ResistanceProfile(5, 3, 2, 3, 2, false),
            "dragon_armor", new ResistanceProfile(3, 3, 3, 3, 3, false),
            "gargish_platemail", new ResistanceProfile(8, 6, 5, 6, 5, false),
            "wyrmscale", new ResistanceProfile(4, 3, 4, 4, 3, true),
            "provisional_armor", new ResistanceProfile(1, 1, 1, 1, 1, true)
    );

    private ArmorProfileRegistry() {}

    public static ResistanceProfile get(String id) { return id == null ? null : PROFILES.get(id); }
    public static Map<String, ResistanceProfile> all() { return PROFILES; }
}
