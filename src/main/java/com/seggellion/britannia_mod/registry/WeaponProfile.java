package com.seggellion.britannia_mod.registry;

public record WeaponProfile(String id, int minimumStrength, int minimumDamage, int maximumDamage,
                            double speed, boolean twoHanded, String combatFamily,
                            String specialMove, String baseProfileId, boolean provisional) {
    public WeaponProfile {
        if (id == null || id.isBlank() || minimumStrength < 0 || minimumDamage < 0
                || maximumDamage < minimumDamage || speed <= 0.0) {
            throw new IllegalArgumentException("Invalid weapon profile: " + id);
        }
    }
}
