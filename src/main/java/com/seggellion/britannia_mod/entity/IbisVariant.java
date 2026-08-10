package com.seggellion.britannia_mod.entity;

/** Persistent visual variants for the single ibis entity type. */
public enum IbisVariant {
    WHITE(0, "white"),
    SCARLET(1, "scarlet");

    private final int id;
    private final String serializedName;

    IbisVariant(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int id() {
        return id;
    }

    public String serializedName() {
        return serializedName;
    }

    public static IbisVariant byId(int id) {
        return id == SCARLET.id ? SCARLET : WHITE;
    }
}
