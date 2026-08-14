package com.seggellion.britannia_mod.training;

/** Canonical training-dummy combat skill keys used by the existing skill service. */
public enum TrainingWeaponSkill {
    WRESTLING("wrestling"),
    SWORDSMANSHIP("swordsmanship"),
    MACE_FIGHTING("mace_fighting"),
    FENCING("fencing");

    private final String skillSlug;

    TrainingWeaponSkill(String skillSlug) {
        this.skillSlug = skillSlug;
    }

    public String skillSlug() {
        return skillSlug;
    }
}
