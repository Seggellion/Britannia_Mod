package com.seggellion.britannia_mod.skill;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.Style;

import com.seggellion.britannia_mod.network.SkillSyncPayload;
import com.seggellion.britannia_mod.network.NetworkHandler;
import net.minecraft.network.chat.Component;

import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;


public class SkillManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Random RNG = new Random();
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final ResourceLocation FONT_UO_CLASSIC =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");
    private static final TextColor TEAL_0093A4 = TextColor.fromRgb(0x0093A4);

    /** Skill definitions keyed by skill name (loaded once per server). */
private static final Map<String, SkillDef> SKILL_DEFS = new java.util.concurrent.ConcurrentHashMap<>();
private static final Map<UUID, PlayerSkills> PLAYER_SKILLS = new java.util.concurrent.ConcurrentHashMap<>();
private static final Map<UUID, SkillDataState> PLAYER_SKILL_STATES = new java.util.concurrent.ConcurrentHashMap<>();
private static final Map<UUID, Long> PLAYER_SKILL_REVISIONS = new java.util.concurrent.ConcurrentHashMap<>();

public enum SkillDataState {
    NOT_LOADED,
    LOADING,
    AVAILABLE,
    UNAVAILABLE
}

public record SkillSnapshot(SkillDataState state, float value) {
}


    /* =====  Public API  ===== */

    /**
     * Attempts a gain roll for the supplied skill.
     * @param player      server‑side player
     * @param skillName   lowercase skill id (e.g. "fishing")
     * @param success     did the action succeed? (needed for skills that gain on success / fail)
     */
  public static void trySkillGain(ServerPlayer player, String skillName, boolean success) {
    if (player == null) return;

    // Normalize key
    final String key = skillName.toLowerCase(Locale.ROOT);
    LOGGER.info("trySkillGain: {}", key);

    // Always have a PlayerSkills map (seeded at login, but belt & suspenders here)
    PlayerSkills p = PLAYER_SKILLS.computeIfAbsent(player.getUUID(), id -> new PlayerSkills());

    // Get def if ready, else a conservative default so MP can gain immediately
    SkillDef def = SKILL_DEFS.getOrDefault(
        key,
        new SkillDef(key, capitalize(key), 100f, 1.0f, true, true) // fallback until API loads
    );

    // Respect success/failure flags
    if ((success && !def.gainOnSuccess) || (!success && !def.gainOnFailure)) return;

    float current = p.get(key);
    if (current >= def.max) return;

    double gainChance = ((100.0 - current) / 100.0) * def.difficultyModifier;
    if (RNG.nextDouble() > gainChance) return;

    float newValue = Math.min(current + 0.1f, def.max);
    p.set(key, newValue);

    LOGGER.info("🎉 {} gained {} → {}", player.getScoreboardName(), key, newValue);

    String message = String.format(
        "Your skill in %s has increased by %.1f%%. It is now %.1f%%.",
        capitalize(key), newValue - current, newValue
    );
    Style style = Style.EMPTY.withFont(FONT_UO_CLASSIC).withColor(TEAL_0093A4);
    player.sendSystemMessage(Component.literal(""));
    player.sendSystemMessage(Component.literal(message).withStyle(style));

    // Send to client on server thread
    player.server.execute(() -> sendSkillSync(player));

    // Async persist
    postGain(player, key, newValue);
}

public static float awardSkillGain(ServerPlayer player, String skillName, float amount) {
    if (player == null || amount <= 0.0f) return 0.0f;

    final String key = skillName.toLowerCase(Locale.ROOT);
    PlayerSkills p = PLAYER_SKILLS.computeIfAbsent(player.getUUID(), id -> new PlayerSkills());
    SkillDef def = SKILL_DEFS.getOrDefault(
        key,
        new SkillDef(key, capitalize(key), 100f, 1.0f, true, true)
    );

    float current = p.get(key);
    if (current >= def.max) return 0.0f;

    float newValue = Math.min(current + amount, def.max);
    p.set(key, newValue);

    String message = String.format(
        "Your skill in %s has increased by %.1f%%. It is now %.1f%%.",
        capitalize(key), newValue - current, newValue
    );
    Style style = Style.EMPTY.withFont(FONT_UO_CLASSIC).withColor(TEAL_0093A4);
    player.sendSystemMessage(Component.literal(""));
    player.sendSystemMessage(Component.literal(message).withStyle(style));

    player.server.execute(() -> sendSkillSync(player));
    postGain(player, key, newValue);

    return newValue - current;
}

private static String capitalize(String s) {
    if (s.isEmpty()) return s;
    return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
}

    /* =====  Lifecycle hooks  ===== */

    /** Register event listeners – call once in your mod’s common setup. */
    public static void init() {
  
        NeoForge.EVENT_BUS.addListener(SkillManager::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(SkillManager::onPlayerLogOut);
        NeoForge.EVENT_BUS.addListener(SkillManager::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(SkillManager::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(SkillManager::onPlayerGameModeChange);
    }

// 3) Login: seed skills immediately, then fetch/merge async
private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent e) {
          LOGGER.info("PlayerLogin Initialized");
    if (e.getEntity().level().isClientSide) return;
    ServerPlayer sp = (ServerPlayer) e.getEntity();
    LOGGER.info("SKILL SYSTEM LOGIN (MP-safe)");

    // Ensure the player has a skills map right now so gains won’t be dropped in MP
    PLAYER_SKILLS.put(sp.getUUID(), new PlayerSkills());
    PLAYER_SKILL_STATES.put(sp.getUUID(), SkillDataState.LOADING);
    PLAYER_SKILL_REVISIONS.put(sp.getUUID(), 0L);
    sendSkillSync(sp);

    ServerHttpExecutor.run(sp.server, () -> {
        try {
            // Load defs if needed
            if (SKILL_DEFS.isEmpty()) {
                Map<String, SkillDef> fresh = fetchSkillConfigAsync(sp);
                if (fresh != null && !fresh.isEmpty()) {
                    // Normalize slugs to lowercase to match trySkillGain lookup
                    fresh.forEach((k, v) -> SKILL_DEFS.put(k.toLowerCase(Locale.ROOT), v));
                    LOGGER.info("✔ Skill defs ready: {}", SKILL_DEFS.keySet());
                }
            }

            // Load the authoritative values; an API failure leaves cultivation unavailable.
            PlayerSkills loaded = fetchPlayerSkillsAsync(sp);
            if (loaded == null) {
                sp.server.execute(() -> markSkillDataUnavailable(sp));
                return;
            }

            sp.server.execute(() -> {
                if (!isCurrentConnectedPlayer(sp)) return;
                PLAYER_SKILLS.put(sp.getUUID(), loaded);
                PLAYER_SKILL_STATES.put(sp.getUUID(), SkillDataState.AVAILABLE);
                sendSkillSync(sp);
                LOGGER.info("✅ Loaded {} skills for {}", loaded.size(), sp.getScoreboardName());
            });

        } catch (Exception ex) {
            LOGGER.error("Login skill bootstrap failed for {}", sp.getScoreboardName(), ex);
            sp.server.execute(() -> markSkillDataUnavailable(sp));
        }
    });
}
    private static void onPlayerLogOut(PlayerEvent.PlayerLoggedOutEvent e) {
        PLAYER_SKILLS.remove(e.getEntity().getUUID());
        PLAYER_SKILL_STATES.remove(e.getEntity().getUUID());
        PLAYER_SKILL_REVISIONS.remove(e.getEntity().getUUID());
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendSkillSync(player);
        }
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendSkillSync(player);
        }
    }

    private static void onPlayerGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
            player.server.execute(() -> sendSkillSync(player));
        }
    }

    private static boolean isCurrentConnectedPlayer(ServerPlayer player) {
        return player.isAlive() && player.connection != null
                && player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static void markSkillDataUnavailable(ServerPlayer player) {
        if (isCurrentConnectedPlayer(player)) {
            PLAYER_SKILL_STATES.put(player.getUUID(), SkillDataState.UNAVAILABLE);
            sendSkillSync(player);
        }
    }

    private static void sendSkillSync(ServerPlayer player) {
        if (player == null || player.connection == null) {
            return;
        }
        // Skill sync is a UI-only snapshot. Gametest mock players and other
        // connections that negotiated no britannia channels cannot accept the
        // payload; skip instead of letting NeoForge throw inside event handlers.
        if (!player.connection.hasChannel(SkillSyncPayload.TYPE)) {
            return;
        }
        UUID playerId = player.getUUID();
        SkillDataState state = PLAYER_SKILL_STATES.getOrDefault(playerId, SkillDataState.NOT_LOADED);
        PlayerSkills playerSkills = PLAYER_SKILLS.get(playerId);
        Map<String, Float> values = state == SkillDataState.AVAILABLE && playerSkills != null
                ? Map.copyOf(playerSkills.map)
                : Map.of();
        long revision = PLAYER_SKILL_REVISIONS.merge(playerId, 1L, Long::sum);
        boolean identificationBypass = com.seggellion.britannia_mod.farming.FlowerProtectionService
                .isAdministrator(player.isCreative(),
                        com.seggellion.britannia_mod.farming.FlowerProtectionService
                                .effectivePermissionLevel(player));
        NetworkHandler.sendToPlayer(player, SkillSyncPayload.create(
                state, revision, identificationBypass, values
        ));
    }

    /* =====  HTTP helpers  ===== */

    private static void fetchSkillConfig(ServerPlayer sp) {
        try {
            JsonArray arr = doGetJson(Endpoint.SKILL_CONFIG, Map.of(), sp).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                SkillDef def = new SkillDef(o);
                SKILL_DEFS.put(def.slug(), def);
            }
            LOGGER.info("✔ Loaded {} skill defs from API", SKILL_DEFS.size());
        } catch (Exception ex) {
            LOGGER.error("Failed to fetch skill config – skills unusable!", ex);
        }
    }


public static void setSkillAdmin(ServerPlayer player, String skillName, float value) {
        if (player == null) return;
        String key = skillName.toLowerCase(Locale.ROOT);

        // FIX 1: getUuid() -> getUUID()
        PlayerSkills p = PLAYER_SKILLS.computeIfAbsent(player.getUUID(), id -> new PlayerSkills());
        p.set(key, value);
        PLAYER_SKILL_STATES.put(player.getUUID(), SkillDataState.AVAILABLE);

        LOGGER.info("🛠️ ADMIN: Set {}'s {} skill to {}", player.getGameProfile().getName(), key, value);

        // Sync to client (using the public 'server' field, matching NeoForge standard)
        player.server.execute(() -> sendSkillSync(player));

        // Async persist to the new Rails endpoint
        postSetSkill(player, key, value);
    }

    private static void postSetSkill(ServerPlayer sp, String skillName, float newVal) {
        ServerHttpExecutor.run(sp.server, () -> {
            try {
                // Notice this targets a new "skills/set" endpoint, not "skills/gain"
                var requestUri = ServerAuthRegistry.credentials(sp.server).orElseThrow().apiUrls()
                        .resolve(Endpoint.SKILL_SET);
                HttpURLConnection c = (HttpURLConnection) requestUri.toURL().openConnection();
                BoundedHttp.configure(c);
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);

                // FIX 2: ServerWorld / getServerWorld() -> ServerLevel / serverLevel()
                ServerLevel world = sp.serverLevel();

                JsonObject body = new JsonObject();
                // FIX 4: getUuid() -> getUUID()
                body.addProperty("uuid", sp.getUUID().toString());
                body.addProperty("shard", shardName(sp));
                body.addProperty("skill_name", skillName);
                body.addProperty("username", sp.getGameProfile().getName());
                body.addProperty("value", newVal);
                byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);

                if (!RailsRequestAuthenticator.apply(c, world.getServer(), bodyBytes)) throw new IllegalStateException("Server authentication unavailable");

                c.setDoOutput(true);

                try (OutputStream os = c.getOutputStream()) {
                    os.write(bodyBytes);
                }
                BoundedHttp.readUtf8(c.getInputStream(), 64 * 1024);
            } catch (Exception e) {
                LOGGER.warn("Failed to POST admin skill set to Rails", e);
            }
        });
    }


private static void fetchPlayerSkills(ServerPlayer sp) {
    try {
        JsonArray arr = doGetJson(Endpoint.PLAYER_SKILLS, playerSkillQuery(sp), sp).getAsJsonArray();

        PlayerSkills ps = new PlayerSkills();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            ps.set(o.get("skill_name").getAsString(), o.get("value").getAsFloat());
        }
        PLAYER_SKILLS.put(sp.getUUID(), ps);
        PLAYER_SKILL_STATES.put(sp.getUUID(), SkillDataState.AVAILABLE);
        sendSkillSync(sp);
        LOGGER.info("✅ Loaded {} skills for {}", ps.size(), sp.getScoreboardName());

    } catch (Exception ex) {
        LOGGER.error("Failed to load player skills for {}", sp.getScoreboardName(), ex);
        PLAYER_SKILL_STATES.put(sp.getUUID(), SkillDataState.UNAVAILABLE);
    }
}


// 5) Harden networking (timeouts on POST too)
private static void postGain(ServerPlayer sp, String skillName, float newVal) {
    ServerHttpExecutor.run(sp.server, () -> {
        try {
            var requestUri = ServerAuthRegistry.credentials(sp.server).orElseThrow().apiUrls()
                    .resolve(Endpoint.SKILL_GAIN);
            HttpURLConnection c = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(c);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);

            JsonObject body = new JsonObject();
            body.addProperty("uuid", sp.getUUID().toString());
            body.addProperty("shard", shardName(sp));
            body.addProperty("skill_name", skillName);
            body.addProperty("value", newVal);
            byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);

            if (!RailsRequestAuthenticator.apply(c, sp.server, bodyBytes)) throw new IllegalStateException("Server authentication unavailable");

            c.setDoOutput(true);

            try (OutputStream os = c.getOutputStream()) {
                os.write(bodyBytes);
            }
            BoundedHttp.readUtf8(c.getInputStream(), 64 * 1024);
        } catch (Exception e) {
            LOGGER.warn("Failed to POST skill gain to Rails", e);
        }
    });
}


// 4) Async helpers: normalize keys and be tolerant
private static Map<String, SkillDef> fetchSkillConfigAsync(ServerPlayer sp) {
    try {
        JsonArray arr = doGetJson(Endpoint.SKILL_CONFIG, Map.of(), sp).getAsJsonArray();
        Map<String, SkillDef> fresh = new HashMap<>();
        for (JsonElement el : arr) {
            SkillDef def = new SkillDef(el.getAsJsonObject());
            fresh.put(def.slug().toLowerCase(Locale.ROOT), def);
        }
        LOGGER.info("✔ Loaded {} skill defs from API", fresh.size());
        return fresh;
    } catch (Exception ex) {
        LOGGER.error("Failed to fetch skill config", ex);
        return null;
    }
}

private static PlayerSkills fetchPlayerSkillsAsync(ServerPlayer sp) {
    try {
        JsonArray arr = doGetJson(Endpoint.PLAYER_SKILLS, playerSkillQuery(sp), sp).getAsJsonArray();
        PlayerSkills ps = new PlayerSkills();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            String name = o.get("skill_name").getAsString().toLowerCase(Locale.ROOT);
            ps.set(name, o.get("value").getAsFloat());
        }
        return ps;
    } catch (Exception ex) {
        // Preserve the distinction between an authoritative zero and unavailable data.
        LOGGER.error("Failed to load player skills for {}", sp.getScoreboardName(), ex);
        return null;
    }
}

 /* =====  Tiny JSON util  ===== */

// Add timeouts so MP servers don’t hang
private static JsonElement doGetJson(Endpoint endpoint, Map<String, String> query,
                                     ServerPlayer sp) throws IOException {
    var requestUri = ServerAuthRegistry.credentials(sp.server).orElseThrow().apiUrls()
            .resolveQuery(endpoint, query);
    HttpURLConnection c = (HttpURLConnection) requestUri.toURL().openConnection();
    c.setRequestProperty("Accept", "application/json");
    BoundedHttp.configure(c);

    if (!RailsRequestAuthenticator.apply(c, sp.server, new byte[0])) throw new IllegalStateException("Server authentication unavailable");

    String response = BoundedHttp.readUtf8(c.getInputStream(), MAX_RESPONSE_BYTES);
    return JsonParser.parseString(response);
}

private static String shardName(ServerPlayer player) {
    return ServerAuthRegistry.credentials(player.server).orElseThrow().shardName();
}

private static Map<String, String> playerSkillQuery(ServerPlayer player) {
    return Map.of(
            "uuid", player.getUUID().toString(),
            "username", player.getScoreboardName(),
            "shard", shardName(player)
    );
    }

    /* =====  Data classes  ===== */

    private record SkillDef(String slug, String displayName, float max, float difficultyModifier,
                            boolean gainOnSuccess, boolean gainOnFailure) {

        SkillDef(JsonObject o) {
            this(
                o.get("slug").getAsString(),
                o.get("name").getAsString(),
                o.get("max_value").getAsFloat(),
                o.get("skill_difficulty_modifier").getAsFloat(),
                o.get("gain_on_success").getAsBoolean(),
                o.get("gain_on_failure").getAsBoolean()
            );
        }
    }

// 1. The base method that accepts a UUID (Used by the Client Screen)
    public static float getSkill(java.util.UUID playerUUID, String skillName) {
        PlayerSkills ps = PLAYER_SKILLS.get(playerUUID);
        return (ps == null) ? 0f : ps.get(skillName);
    }

    /**
     * The human-readable name for a skill slug, from the Rails-published definition when one has
     * been loaded, otherwise a prettified form of the slug itself.
     *
     * <p>Guildmaster milestone 2. Added because nothing outside this class could previously read a
     * skill's display name at all -- {@code SKILL_DEFS} is private and {@code ClientSkillTable}
     * holds only the viewing player's own values, keyed by slug, with no names. The Guildmaster
     * spawn-screen readout needs "Animal Lore", not "animal-lore".
     *
     * <p>The fallback is load-bearing rather than defensive: {@code SKILL_DEFS} is populated on the
     * first player login, so a dedicated server that has not yet had one -- or one whose skill
     * config fetch failed -- would otherwise render every label blank.
     */
    public static String displayNameForSlug(String slug) {
        if (slug == null || slug.isBlank()) return "";
        SkillDef def = SKILL_DEFS.get(slug.toLowerCase(Locale.ROOT));
        if (def != null && def.displayName() != null && !def.displayName().isBlank()) {
            return def.displayName();
        }
        StringBuilder pretty = new StringBuilder(slug.length());
        boolean startOfWord = true;
        for (char character : slug.toCharArray()) {
            if (character == '-' || character == '_') {
                pretty.append(' ');
                startOfWord = true;
                continue;
            }
            pretty.append(startOfWord ? Character.toUpperCase(character) : character);
            startOfWord = false;
        }
        return pretty.toString();
    }

    /**
     * The configured maximum for a skill, from the Rails-published definition, falling back to the
     * same 100 this class already assumes when definitions have not loaded.
     *
     * <p>Guildmaster milestone 5: the training ceiling is the stricter of 40.0 and this, so a
     * Guildmaster can never push a skill past its own cap.
     */
    public static float maxValueForSlug(String slug) {
        if (slug == null || slug.isBlank()) return 100f;
        SkillDef def = SKILL_DEFS.get(slug.toLowerCase(Locale.ROOT));
        return def == null ? 100f : def.max();
    }

    /**
     * Records an authoritative value that Rails has <em>already</em> committed, and syncs it to the
     * client. Deliberately does not POST anything back.
     *
     * <p>Guildmaster milestone 5. {@link #setSkillAdmin} and {@link #awardSkillGain} both write
     * locally and then fire a request at Rails; for a training purchase that ordering is inverted —
     * Rails commits the skill, the treasury credit and the ledger row together first, and this only
     * brings the in-memory cache into line with what was already stored. Posting again here would
     * write the value twice and, worse, could overwrite a newer one.
     */
    public static void applyConfirmedValue(ServerPlayer player, String skillName, float value) {
        if (player == null || skillName == null) return;
        String key = skillName.toLowerCase(Locale.ROOT);
        PLAYER_SKILLS.computeIfAbsent(player.getUUID(), id -> new PlayerSkills()).set(key, value);
        PLAYER_SKILL_STATES.put(player.getUUID(), SkillDataState.AVAILABLE);
        player.server.execute(() -> sendSkillSync(player));
    }

    public static SkillSnapshot getSkillSnapshot(UUID playerUUID, String skillName) {
        SkillDataState state = PLAYER_SKILL_STATES.getOrDefault(playerUUID, SkillDataState.NOT_LOADED);
        return new SkillSnapshot(state, getSkill(playerUUID, skillName));
    }

    // 2. The helper method that accepts a Player (Fixes all your Server errors!)
    public static float getSkill(net.minecraft.world.entity.player.Player player, String skillName) {
        if (player == null) return 0f;
        
        // This just grabs the UUID from the player and passes it to the method above
        return getSkill(player.getUUID(), skillName);
    }

    /**
     * Maps a 0-to-100 skill to a 0-to-1 probability.
     *  P(50) ≈ 0.50, P(30) ≈ 0.27, P(70) ≈ 0.88.
     */
    public static double catchChance(float skill) {
        // shift & scale so midpoint is at 50 and the slope feels right
        double k     = 0.16;          // steeper ↗ increase this, flatter ↘ decrease
        double shift = skill - 50.0;
        return 1.0 / (1.0 + Math.exp(-k * shift));
    }


    private static class PlayerSkills {
        private final Map<String, Float> map = new HashMap<>();
        float get(String s)  { return map.getOrDefault(s, 0f); }
        void  set(String s, float v){ map.put(s, v); }
        int   size() { return map.size(); }
    }
}
