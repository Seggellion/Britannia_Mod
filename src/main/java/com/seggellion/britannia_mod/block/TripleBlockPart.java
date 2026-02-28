package com.seggellion.britannia_mod.block;


public enum TripleBlockPart implements net.minecraft.util.StringRepresentable {
    LOWER, MIDDLE, UPPER;

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase();
    }
}