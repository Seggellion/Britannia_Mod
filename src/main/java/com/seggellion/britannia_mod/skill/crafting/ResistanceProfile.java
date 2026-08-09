package com.seggellion.britannia_mod.skill.crafting;

public record ResistanceProfile(int physical, int fire, int ice, int poison, int magic, boolean provisional) {
    public ResistanceProfile {
        if (physical < 0 || fire < 0 || ice < 0 || poison < 0 || magic < 0) {
            throw new IllegalArgumentException("Resistance values cannot be negative");
        }
    }
}
