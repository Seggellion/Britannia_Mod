package com.seggellion.britannia_mod.farming;

public enum FlowerMutationReason {
    PLANTING(false),
    CARE(false),
    HARVEST(false),
    SWORD_CUTBACK(false),
    PERMANENT_UPROOT(false),
    POPPY_STAGE_SEVEN(false),
    NORMAL_BREAK(false),
    EXPLOSION(false),
    FLUID(false),
    REPLACEMENT(false),
    PISTON(false),
    ADMIN_REMOVE(true),
    SYSTEM_MUTATION(true),
    ADMIN_COMMAND(true),
    WORLD_GENERATION(true);

    private final boolean systemAuthorized;

    FlowerMutationReason(boolean systemAuthorized) {
        this.systemAuthorized = systemAuthorized;
    }

    public boolean isSystemAuthorized() {
        return systemAuthorized;
    }
}
