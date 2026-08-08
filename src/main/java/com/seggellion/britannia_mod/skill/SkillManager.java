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
    player.server.execute(() -> NetworkHandler.sendToPlayer(player, new SkillSyncPayload(p.map)));

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

    player.server.execute(() -> NetworkHandler.sendToPlayer(player, new SkillSyncPayload(p.map)));
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
    }

// 3) Login: seed skills immediately, then fetch/merge async
private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent e) {
          LOGGER.info("PlayerLogin Initialized");
    if (e.getEntity().level().isClientSide) return;
    ServerPlayer sp = (ServerPlayer) e.getEntity();
    LOGGER.info("SKILL SYSTEM LOGIN (MP-safe)");

    // Ensure the player has a skills map right now so gains won’t be dropped in MP
    PLAYER_SKILLS.putIfAbsent(sp.getUUID(), new PlayerSkills());

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

            // Load player’s values; if API fails, keep the seeded empty map
            PlayerSkills loaded = fetchPlayerSkillsAsync(sp);
            if (loaded == null) return;

            sp.server.execute(() -> {
                if (!sp.isAlive() || sp.connection == null) return;
                PLAYER_SKILLS.put(sp.getUUID(), loaded);
                NetworkHandler.sendToPlayer(sp, new SkillSyncPayload(loaded.map));
                LOGGER.info("✅ Loaded {} skills for {}", loaded.size(), sp.getScoreboardName());
            });

        } catch (Exception ex) {
            LOGGER.error("Login skill bootstrap failed for {}", sp.getScoreboardName(), ex);
        }
    });
}
    private static void onPlayerLogOut(PlayerEvent.PlayerLoggedOutEvent e) {
        PLAYER_SKILLS.remove(e.getEntity().getUUID());
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

        LOGGER.info("🛠️ ADMIN: Set {}'s {} skill to {}", player.getGameProfile().getName(), key, value);

        // Sync to client (using the public 'server' field, matching NeoForge standard)
        player.server.execute(() -> NetworkHandler.sendToPlayer(player, new SkillSyncPayload(p.map)));

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
        NetworkHandler.sendToPlayer(sp, new SkillSyncPayload(ps.map));
        LOGGER.info("✅ Loaded {} skills for {}", ps.size(), sp.getScoreboardName());

    } catch (Exception ex) {
        LOGGER.error("Failed to load player skills for {}", sp.getScoreboardName(), ex);
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
        // Return empty so MP still gains; we’ll POST gains and next login will resync.
        LOGGER.error("Failed to load player skills for {}", sp.getScoreboardName(), ex);
        return new PlayerSkills();
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
