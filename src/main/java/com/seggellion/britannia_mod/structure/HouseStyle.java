package com.seggellion.britannia_mod.structure;


public enum HouseStyle {

    SMALL_WOOD("wooden_house",   HouseSize.SMALL, -2),
    SMALL_STONE("field_stone_house",   HouseSize.SMALL, -2),
    SMALL_WOODPLASTER("wood_and_plaster_house",  HouseSize.SMALL, -2),
    SMALL_COTTAGE("thatched_roof_cottage",    HouseSize.SMALL, -2),
    SMALL_BRICK("small_brick_house",       HouseSize.SMALL, -2),
    SMALL_STONEPLASTER("stone_and_plaster_house",       HouseSize.SMALL, -2),
    CASTLE("castle", HouseSize.CASTLE, -3);


    private final String structureStub;
    private final HouseSize size;
        private final int xOffset;


    HouseStyle(String structureStub, HouseSize size, int xOffset) {
        this.structureStub = structureStub;
        this.size = size;
        this.xOffset = xOffset;
    }

    public String getStructureFile() {
        return structureStub + ".nbt";
    }

    public HouseSize getSize() {
        return size;
    }

       public int getLotOffsetX() {
        return xOffset;
    }

    public int getWidth()  { return size.getWidth(); }
    public int getHeight() { return size.getHeight(); }
    public int getDepth()  { return size.getDepth(); }
}
