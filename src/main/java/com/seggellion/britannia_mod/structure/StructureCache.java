package com.seggellion.britannia_mod.client.structure;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class StructureCache {

    public static final boolean DEBUG = Boolean.getBoolean("britannia.ghostPreviewDebug");

    private static final Map<String, StructureTemplate> templates = new HashMap<>();
    private static final Set<String> warnedMissingKeys = new HashSet<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean ghostStructureLoaded = false;

    /* ------------- Public API ------------- */

    /** Adds a structure template to the cache by NBT filename (no .nbt extension) */
    public static void put(String structureName, StructureTemplate template) {
        String key = normalizeKey(structureName);
        templates.put(key, template);
        warnedMissingKeys.remove(key);

        if (DEBUG) {
            LOGGER.info("[GHOST_PREVIEW] cachePut key={} original={} size={}",
                    key, structureName, template != null ? template.getSize() : null);
        }
    }

    /** Retrieves a structure template from the cache */
    public static StructureTemplate get(String structureName) {
        String key = normalizeKey(structureName);
        StructureTemplate template = templates.get(key);

        if (template == null && warnedMissingKeys.add(key)) {
            LOGGER.warn("[GHOST_PREVIEW] No template cached for '{}' (normalized from '{}')", key, structureName);
        } else if (DEBUG) {
            LOGGER.info("[GHOST_PREVIEW] cacheGet key={} original={} found={} size={}",
                    key, structureName, template != null, template != null ? template.getSize() : null);
        }

        return template;
    }

    /** Returns true if a structure with that name is loaded */
    public static boolean contains(String structureName) {
        return templates.containsKey(normalizeKey(structureName));
    }

    public static String normalizeKey(String structureName) {
        if (structureName == null) {
            return "";
        }

        String key = structureName.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        int namespaceSeparator = key.indexOf(':');
        if (namespaceSeparator >= 0) {
            key = key.substring(namespaceSeparator + 1);
        }
        key = key.replaceFirst("^structures/", "");
        key = key.replaceFirst("\\.nbt$", "");
        return key;
    }

    public static boolean hasLoadedGhostStructure() {
        return ghostStructureLoaded;
    }

    public static void setGhostStructureLoaded(boolean loaded) {
        ghostStructureLoaded = loaded;
    }
}
