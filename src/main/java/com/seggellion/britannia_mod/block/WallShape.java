package com.seggellion.britannia_mod.block;

import net.minecraft.util.StringRepresentable;

public enum WallShape implements StringRepresentable {
    STRAIGHT("straight"),
    CORNER("corner"),
    T_JUNCTION("t_junction");

    private final String name;

    WallShape(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
