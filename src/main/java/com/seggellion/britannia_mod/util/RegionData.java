package com.seggellion.britannia_mod.util;

import net.minecraft.core.BlockPos;

import java.util.List;

public class RegionData {
    public final String name;
    public final int minX, maxX, minY, maxY, minZ, maxZ;
    public final List<RegionItemData> items;

    public RegionData(String name,
                      int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                      List<RegionItemData> items) {
        this.name = name;
        this.minX = minX; this.maxX = maxX;
        this.minY = minY; this.maxY = maxY;
        this.minZ = minZ; this.maxZ = maxZ;
        this.items = items;
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX &&
               pos.getY() >= minY && pos.getY() <= maxY &&
               pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }
}
