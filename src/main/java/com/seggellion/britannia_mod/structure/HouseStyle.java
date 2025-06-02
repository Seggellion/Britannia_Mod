package com.seggellion.britannia_mod.structure;


public enum HouseStyle {

    SMALL_WOOD("wooden_house",   HouseSize.SMALL),
    SMALL_STONE("field_stone_house",   HouseSize.SMALL),
    SMALL_WOODPLASTER("wood_and_plaster_house",  HouseSize.SMALL),
    SMALL_COTTAGE("thatched_roof_cottage",    HouseSize.SMALL),
    SMALL_BRICK("small_brick_house",       HouseSize.SMALL),
    SMALL_STONEPLASTER("stone_and_plaster_house",       HouseSize.SMALL);


    private final String structureStub;
    private final HouseSize size;

    HouseStyle(String structureStub, HouseSize size) {
        this.structureStub = structureStub;
        this.size = size;
    }

    public String getStructureFile() {
        return structureStub + ".nbt";
    }

    public HouseSize getSize() {
        return size;
    }

    public int getWidth()  { return size.getWidth(); }
    public int getHeight() { return size.getHeight(); }
    public int getDepth()  { return size.getDepth(); }
}
