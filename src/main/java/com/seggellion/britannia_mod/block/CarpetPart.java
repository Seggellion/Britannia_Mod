// CarpetPart.java
package com.seggellion.britannia_mod.block;

import net.minecraft.util.StringRepresentable;

public enum CarpetPart implements StringRepresentable {
    NORTH_WEST(0, 0, "nw"),
    NORTH(1, 0, "n"),
    NORTH_EAST(2, 0, "ne"),
    WEST(0, 1, "w"),
    CENTER(1, 1, "c"),
    EAST(2, 1, "e"),
    SOUTH_WEST(0, 2, "sw"),
    SOUTH(1, 2, "s"),
    SOUTH_EAST(2, 2, "se");

    public final int gridX, gridZ;     // 0..2
    private final String id;

    CarpetPart(int gx, int gz, String id) {
        this.gridX = gx;
        this.gridZ = gz;
        this.id    = id;
    }
    @Override public String getSerializedName() { return id; }

    public static CarpetPart fromGrid(int gx, int gz) {
        return values()[gz * 3 + gx];
    }
}
