package com.seggellion.britannia_mod.client.structure;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class StructureCache {

    // ✅ instead of just one hardcoded template
    private static final Map<String, StructureTemplate> templates = new HashMap<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean ghostStructureLoaded = false;

    /* ------------- Public API ------------- */

    /** Adds a structure template to the cache by NBT filename (no .nbt extension) */
    public static void put(String structureName, StructureTemplate template) {
        templates.put(structureName, template);
    }

    /** Retrieves a structure template from the cache */
    public static StructureTemplate get(String structureName) {
            StructureTemplate template = templates.get(structureName);
    if (template == null) {
        LOGGER.warn("⚠️ No template cached for '{}'", structureName);
    }
        return templates.get(structureName);
    }

    /** Returns true if a structure with that name is loaded */
    public static boolean contains(String structureName) {
        return templates.containsKey(structureName);
    }

    public static boolean hasLoadedGhostStructure() {
        return ghostStructureLoaded;
    }

    public static void setGhostStructureLoaded(boolean loaded) {
        ghostStructureLoaded = loaded;
    }
}