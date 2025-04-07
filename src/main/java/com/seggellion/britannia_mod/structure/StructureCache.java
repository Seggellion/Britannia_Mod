package com.seggellion.britannia_mod.client.structure;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class StructureCache {
    private static StructureTemplate smallHouseTemplate;

    public static void setSmallHouseTemplate(StructureTemplate template) {
        smallHouseTemplate = template;
    }

    public static StructureTemplate getSmallHouseTemplate() {
        return smallHouseTemplate;
    }

    private static boolean ghostStructureLoaded = false;

    public static boolean hasLoadedGhostStructure() {
        return ghostStructureLoaded;
    }

    public static void setGhostStructureLoaded(boolean loaded) {
        ghostStructureLoaded = loaded;
    }

    public static final ResourceLocation SMALL_HOUSE_ID = ResourceLocation.fromNamespaceAndPath("britannia_mod", "small_house");
}
