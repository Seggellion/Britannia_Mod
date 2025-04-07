package com.seggellion.britannia_mod.house;

public enum HouseSize {
    SMALL(9, 9, 8),
    MEDIUM(9, 7, 9),
    LARGE(13, 7, 13),
    CASTLE(25, 15, 25);

    private final int width;
    private final int height;
    private final int depth;

    HouseSize(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepth() {
        return depth;
    }
}
