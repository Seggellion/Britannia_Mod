package com.seggellion.britannia_mod.skill;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Exists only on the physical client; refreshed each login. */
public final class ClientSkillTable {
    private static final Map<String, Float> SKILLS = new HashMap<>();

    public static void overwrite(Map<String, Float> fromServer) {
        SKILLS.clear();
        SKILLS.putAll(fromServer);
    }

    public static float get(String skill) {
        return SKILLS.getOrDefault(skill, 0f);
    }

    public static Map<String, Float> snapshot() {     // read‑only copy for UI
        return Collections.unmodifiableMap(new HashMap<>(SKILLS));
    }

    /** called when the client disconnects from any world */
    public static void clear() {
        SKILLS.clear();
    }
}
