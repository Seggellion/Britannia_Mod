package com.seggellion.britannia_mod.skill.crafting;

public record SkillRequirement(String skillKey, float minValue) {
    public SkillRequirement {
        if (skillKey == null || skillKey.isBlank()) {
            throw new IllegalArgumentException("Skill key cannot be blank");
        }
        if (!Float.isFinite(minValue) || minValue < 0.0f) {
            throw new IllegalArgumentException("Invalid minimum skill: " + minValue);
        }
        skillKey = normalize(skillKey);
    }

    /** The persisted skill table calls this skill blacksmithy, while old recipe data used blacksmith. */
    public static String normalize(String key) {
        String normalized = key.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("blacksmith") ? "blacksmithy" : normalized;
    }
}
