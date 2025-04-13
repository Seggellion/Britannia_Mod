package com.seggellion.britannia_mod.client.structure;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class StructureCache {
    private static StructureTemplate smallWoodHouseTemplate;

    public static void setSmallWoodHouseTemplate(StructureTemplate template) {
        smallWoodHouseTemplate = template;
    }

    public static StructureTemplate getSmallWoodHouseTemplate() {
        return smallWoodHouseTemplate;
    }

    private static boolean ghostStructureLoaded = false;

    public static boolean hasLoadedGhostStructure() {
        return ghostStructureLoaded;
    }

    public static void setGhostStructureLoaded(boolean loaded) {
        ghostStructureLoaded = loaded;
    }

    public static final ResourceLocation SMALL_WOOD_HOUSE_ID = ResourceLocation.fromNamespaceAndPath("britannia_mod", "small_wood_house");
}
