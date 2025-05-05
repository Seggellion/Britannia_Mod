package com.seggellion.britannia_mod.structure;

public enum HouseSize {

    SMALL ("small",  "small_wood_house",   9,  9,  8),
    MEDIUM("medium", "medium_house",  9,  7,  9),
    TOWER ("tower",  "tower_house",  13,  7, 13),
    CASTLE("castle", "castle_house", 33, 16, 33);

    private final String id;            // lowercase string → Rails & item id
    private final String structureStub; // without ".nbt"
    private final int width, height, depth;

    HouseSize(String id, String stub, int w, int h, int d) {
        this.id = id;
        this.structureStub = stub;
        this.width  = w;
        this.height = h;
        this.depth  = d;
    }

    public String id()               { return id; }
    public String structureFile()    { return structureStub + ".nbt"; }
    public int    getWidth()  { return width;  }
    public int    getHeight() { return height; }
    public int    getDepth()  { return depth;  }
}
