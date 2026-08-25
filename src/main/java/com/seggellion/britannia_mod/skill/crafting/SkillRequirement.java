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

    /**
     * The persisted skill table calls this skill blacksmithy, while old recipe data used
     * blacksmith. Delegates to {@link com.seggellion.britannia_mod.skill.SkillKeys}, the single
     * canonical resolver, so recipe requirements can never spell a skill differently from the
     * skill tables they are compared against.
     */
    public static String normalize(String key) {
        return com.seggellion.britannia_mod.skill.SkillKeys.canonical(key);
    }
}
