package com.seggellion.britannia_mod.structure;

public enum HouseSize {

    SMALL ("small",   9,  8,  9),
    MEDIUM("medium",  9,  7,  9),
    TOWER ("tower",  13,  7, 13),
    CASTLE("castle", 33, 16, 33);

    private final String id; // lowercase string → used for Rails, logic, etc.
    private final int width, height, depth;

    HouseSize(String id, int width, int height, int depth) {
        this.id = id;
        this.width  = width;
        this.height = height;
        this.depth  = depth;
    }

    public String id()         { return id; }
    public int getWidth()      { return width; }
    public int getHeight()     { return height; }
    public int getDepth()      { return depth; }
}
