package com.seggellion.britannia_mod.winery;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GrapeVarietyManager {
    private static final Map<String, GrapeVariety> VARIETIES = new HashMap<>();

    /**
     * Variety id to grape colour, as published by the shard and synced to clients.
     *
     * <p>{@link #VARIETIES} is only ever filled on the logical server -- the bootstrap fetch that
     * populates it needs a {@code ServerPlayer}. A dedicated client therefore knows the two
     * compiled-in Concords and nothing else, so every shard variety used to resolve to the Concord
     * fallback and render green. This map is the narrow slice of the catalogue the client actually
     * needs to draw a vine and an item stack: an id and a colour, and deliberately none of the
     * agronomy, altitude, pricing or naming the server keeps to itself.
     *
     * <p>It is consulted only when {@link #VARIETIES} has no record, so a real record always wins
     * and a server never reads colour-only data in place of its own catalogue.
     */
    private static final Map<String, GrapeColor> SYNCED_COLORS = new HashMap<>();
    /**
     * The variety a grape falls back to whenever the shard has published none, mirroring the
     * catalogue's own Concord records. Concord is the mass-market grape: forgiving chemistry, a wide
     * altitude band and a cool climate, so a fallback plant behaves like something a player could
     * reasonably have grown rather than like a placeholder.
     */
    private static final GrapeVariety CONCORD_GREEN = new GrapeVariety(
        "concord_green", "Concord Green", 3,
        0.22f, 0.13f, 0.25f, 0.56f,
        "Cool", 25, 625,
        0xA8B85A, 4, GrapeColor.GREEN
    );

    private static final GrapeVariety CONCORD_RED = new GrapeVariety(
        "concord_red", "Concord Red", 3,
        0.22f, 0.13f, 0.25f, 0.56f,
        "Cool", 25, 625,
        0x87364F, 4, GrapeColor.RED
    );

    private static final GrapeVariety FALLBACK_VARIETY = CONCORD_GREEN;

    /** Both Concords, so an offline shard still offers more than a single colour. */
    private static final List<GrapeVariety> BUILT_IN_VARIETIES = List.of(CONCORD_GREEN, CONCORD_RED);

    // Called on Mod startup - seeds the built-in Concords so grapes work before any shard data lands
    public static void init() {
        BUILT_IN_VARIETIES.forEach(GrapeVarietyManager::register);
    }

    // === NEW: Called by WorldBootstrapAPI ===
    public static void loadFromBootstrap(List<GrapeVariety> newVarieties) {
        VARIETIES.clear();
        for (GrapeVariety v : newVarieties) {
            register(v);
        }
        // A shard that publishes its own Concords wins; otherwise the built-ins stay available so
        // there is always something for an unknown or missing variety to resolve to.
        for (GrapeVariety builtIn : BUILT_IN_VARIETIES) {
            if (!VARIETIES.containsKey(builtIn.id())) {
                register(builtIn);
            }
        }
    }

    private static void register(GrapeVariety variety) {
        VARIETIES.put(variety.id(), variety);
    }

    /**
     * Discards every shard-supplied variety, leaving only the compiled-in Concords.
     *
     * <p>Mirrors the state a process has before any bootstrap lands. The client lifecycle needs it
     * on disconnect so a catalogue never outlives the shard that published it.
     */
    public static void resetToBuiltIns() {
        VARIETIES.clear();
        SYNCED_COLORS.clear();
        init();
    }

    /**
     * The id-to-colour pairs a client needs to render what this server's catalogue describes.
     *
     * <p>Varieties without a colour are omitted rather than sent as a guess: the client already
     * falls back safely, and sending a made-up colour would look authoritative.
     */
    public static Map<String, GrapeColor> colorCatalogue() {
        Map<String, GrapeColor> catalogue = new LinkedHashMap<>();
        for (GrapeVariety variety : VARIETIES.values()) {
            if (variety == null || variety.id() == null || variety.id().isBlank() || variety.colorType() == null) {
                continue;
            }
            catalogue.put(variety.id(), variety.colorType());
        }
        return catalogue;
    }

    /**
     * Installs a shard's colour catalogue on the client, replacing whatever the previous connection
     * left behind.
     *
     * <p>Replacement rather than merge: reconnecting to a different shard must not leave the
     * previous one's varieties resolvable, and a variety the shard has retired must stop resolving
     * to its old colour. Entries the id or colour of which are missing are dropped -- consistent
     * with the bootstrap parser, which skips an id-less grape rather than inventing one.
     */
    public static void replaceSyncedColors(Map<String, GrapeColor> catalogue) {
        SYNCED_COLORS.clear();
        if (catalogue == null) {
            return;
        }
        catalogue.forEach((id, color) -> {
            if (id == null || id.isBlank() || color == null) {
                return;
            }
            SYNCED_COLORS.put(id.trim(), color);
        });
    }

    /** Forgets the synced catalogue, for a client leaving a server. */
    public static void clearSyncedColors() {
        SYNCED_COLORS.clear();
    }

    public static int syncedColorCount() {
        return SYNCED_COLORS.size();
    }

    /**
     * The colour to draw a grape of {@code id} in, and where that answer came from.
     *
     * <p>Order matters. A full variety record is authoritative, so the server -- and a
     * single-player client, which shares this map in-process -- never consults the synced table.
     * The synced table then covers a dedicated client, which has no records of its own. Only an id
     * neither knows is a genuine fallback, which stays benign because retired ids are expected to
     * reach here.
     */
    public static GrapeColorResolution resolveColor(String id) {
        if (id != null && !id.isBlank()) {
            String trimmed = id.trim();
            GrapeVariety variety = VARIETIES.get(trimmed);
            if (variety != null) {
                return new GrapeColorResolution(variety.id(), variety.getFormattedName(), variety.colorType(), false);
            }
            GrapeColor syncedColor = SYNCED_COLORS.get(trimmed);
            if (syncedColor != null) {
                return new GrapeColorResolution(trimmed, trimmed, syncedColor, false);
            }
        }
        return new GrapeColorResolution(
            FALLBACK_VARIETY.id(), FALLBACK_VARIETY.getFormattedName(), FALLBACK_VARIETY.colorType(), true);
    }

    /**
     * A resolved grape colour.
     *
     * @param varietyId   the id the colour belongs to, or the fallback variety's id
     * @param displayName the variety's published name, or the raw id when only a colour is known
     * @param color       the colour, or {@code null} when the matched record carries none
     * @param fallback    {@code true} when no record and no synced colour matched the requested id
     */
    public record GrapeColorResolution(String varietyId, String displayName, GrapeColor color, boolean fallback) {
    }
    
    public static GrapeVariety getVariety(String id) {
        return VARIETIES.getOrDefault(id, FALLBACK_VARIETY);
    }

    public static GrapeVariety getVarietyOrNull(String id) {
        return VARIETIES.get(id);
    }

    public static String getDefaultVarietyIdForColor(GrapeColor color) {
        if (color == null) {
            return FALLBACK_VARIETY.id();
        }

        for (GrapeVariety variety : VARIETIES.values()) {
            if (variety.colorType() == color) {
                return variety.id();
            }
        }

        if (isDarkGrapeColor(color)) {
            for (GrapeVariety variety : VARIETIES.values()) {
                if (isDarkGrapeColor(variety.colorType())) {
                    return variety.id();
                }
            }
        }

        return FALLBACK_VARIETY.id();
    }

    /**
     * Whether a variety should wear the dark grape art rather than the green.
     *
     * <p>The {@code britannia_mod:grape_type} item property's whole decision, kept here so it is
     * reachable without the client classes -- and so it reads the same catalogue the vine does.
     */
    public static boolean isDarkGrapeVariety(String varietyId) {
        return isDarkGrapeColor(resolveColor(varietyId).color());
    }

    public static boolean isDarkGrapeColor(GrapeColor color) {
        if (color == null) {
            return false;
        }
        return switch (color) {
            case BLUE, DARK_PURPLE, PURPLE, RED -> true;
            default -> false;
        };
    }

    public static Collection<GrapeVariety> getAllVarieties() {
        return VARIETIES.values();
    }
}
