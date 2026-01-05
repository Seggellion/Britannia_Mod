package com.seggellion.britannia_mod.util;

import net.minecraft.util.StringRepresentable;

public enum LabelColor implements StringRepresentable {
    NONE("none"),
    RED("red"),
    GREEN("green"),
    ORANGE("orange"),
    YELLOW("yellow"),
    PINK("pink"),
    BLUE("blue"),
    GOLD("gold"),
    BROWN("brown"),
    WHITE("white"),
    SILVER("silver"),
    BLACK("black");

    private final String name;
    LabelColor(String name) { this.name = name; }
    @Override public String getSerializedName() { return name; }
}