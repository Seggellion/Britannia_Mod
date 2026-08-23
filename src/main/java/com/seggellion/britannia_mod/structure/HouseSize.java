package com.seggellion.britannia_mod.structure;

public enum HouseSize {

    SMALL ("small",   9,  8,  9),
    MEDIUM("medium",  9,  7,  9),
    TOWER ("tower",  13,  7, 13),
    // Measured from the shipped structures rather than taken from the plan, which predated the
    // authoring: the villa is 13x13 and not 11x11. The keep's 2026-08 rebuild squared its
    // footprint at 26x26 and raised it to twelve high; it shipped 26 wide by 25 deep and ten
    // high before that.
    VILLA ("villa",  13, 10, 13),
    PATIO ("patio",  18,  8, 18),
    KEEP  ("keep",   26, 12, 26),
    CASTLE("castle", 34, 20, 35);

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
