package com.seggellion.britannia_mod.banner.migration;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bannerdyeing.diagnostics.BoundedDiagnosticTracker;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Immutable release migration authority for definition IDs.
 *
 * <p>Every persistent and stream codec, item, block entity, placed structure transfer, and administrator command
 * enters through {@code BannerDefinitionId}, so canonicalization stays server-capable and cannot diverge by caller.
 * Unknown IDs are deliberately preserved unchanged for typed missing-content recovery.</p>
 */
public final class BannerDefinitionMigrations {
    public static final int MAX_LOGGED_ALIASES = 64;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, ResourceLocation> ALIASES = buildAliases();
    private static final BoundedDiagnosticTracker<ResourceLocation> LOGGED_ALIASES =
            new BoundedDiagnosticTracker<>(MAX_LOGGED_ALIASES);

    private BannerDefinitionMigrations() {
    }

    public static ResourceLocation canonicalize(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        ResourceLocation canonical = ALIASES.get(id);
        if (canonical == null) {
            return id;
        }
        if (LOGGED_ALIASES.first(id)) {
            LOGGER.info("Canonicalized legacy banner definition ID {} to {}", id, canonical);
        }
        return canonical;
    }

    public static Map<ResourceLocation, ResourceLocation> aliases() {
        return ALIASES;
    }

    public static int loggedAliasCount() {
        return LOGGED_ALIASES.size();
    }

    private static Map<ResourceLocation, ResourceLocation> buildAliases() {
        LinkedHashMap<ResourceLocation, ResourceLocation> aliases = new LinkedHashMap<>();
        add(aliases, "x_small_unnamed_01", "small_curtain");
        add(aliases, "end_01", "star_standard");
        add(aliases, "end_02", "ship_standard");
        add(aliases, "medium_wall_01", "verdant_grape_pennon");
        add(aliases, "medium_wall_02", "silver_rosette_pennon");
        add(aliases, "medium_wall_03", "four_seals_pennon");
        add(aliases, "medium_wall_04", "twin_spades_pennon");
        add(aliases, "medium_wall_05", "ankh_pennon");
        add(aliases, "large_01", "tournament_curtain");
        add(aliases, "large_02", "threefold_chain_standard");
        add(aliases, "large_03", "iron_serpent_standard");
        add(aliases, "large_04", "silver_fleur_curtain");
        add(aliases, "large_05", "gilded_trellis_curtain");
        add(aliases, "large_06", "gilded_chevron_curtain");
        return Map.copyOf(aliases);
    }

    private static void add(
            Map<ResourceLocation, ResourceLocation> aliases, String historicalPath, String canonicalPath) {
        ResourceLocation historical = ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, historicalPath);
        ResourceLocation canonical = ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, canonicalPath);
        if (aliases.put(historical, canonical) != null) {
            throw new IllegalStateException("Duplicate banner definition migration " + historical);
        }
    }
}
