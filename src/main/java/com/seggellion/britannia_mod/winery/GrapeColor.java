package com.seggellion.britannia_mod.winery;

import net.minecraft.util.StringRepresentable;

public enum GrapeColor implements StringRepresentable {
    BLUE("blue"),
    DARK_GREEN("dark_green"),
    DARK_PURPLE("dark_purple"),
    GREEN("green"),
    LIGHT_GREEN("light_green"),
    PURPLE("purple"),
    RED("red"),
    YELLOW("yellow");

    private final String name;

    GrapeColor(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}