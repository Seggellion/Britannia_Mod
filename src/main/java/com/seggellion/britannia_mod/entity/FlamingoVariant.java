package com.seggellion.britannia_mod.entity;

/** Synchronized/persistent visual colors for the shared Flamingo entity type. */
public enum FlamingoVariant {
    PINK(0, "pink"),
    ROSE(1, "rose"),
    WHITE(2, "white");

    private static final FlamingoVariant[] VALUES = values();
    private final int id;
    private final String serializedName;

    FlamingoVariant(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int id() {
        return id;
    }

    public String serializedName() {
        return serializedName;
    }

    public static int count() {
        return VALUES.length;
    }

    public static FlamingoVariant byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : PINK;
    }
}
