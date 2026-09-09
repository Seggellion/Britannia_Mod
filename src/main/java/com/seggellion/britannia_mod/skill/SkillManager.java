package com.seggellion.britannia_mod.skill;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

import com.seggellion.britannia_mod.network.SkillSyncPayload;
import com.seggellion.britannia_mod.network.NetworkHandler;

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

    /**
     * Smallest total gain that is worth telling the player about. The engine's gain unit is 0.1,
     * so announcing every step chatted on every successful roll; at 0.2 an ordinary grind speaks
     * every other step instead.
     */
    static final float ANNOUNCE_THRESHOLD = 0.2f;

    /** Absorbs the drift from repeatedly adding 0.1f, so two steps reliably clear the threshold. */
    private static final float ANNOUNCE_EPSILON = 1.0e-4f;

    /** Skill definitions keyed by skill name (loaded once per server). */
private static final Map<String, SkillDef> SKILL_DEFS = new java.util.concurrent.ConcurrentHashMap<>();
private static final Map<UUID, PlayerSkills> PLAYER_SKILLS = new java.util.concurrent.ConcurrentHashMap<>();
private static final Map<UUID, SkillDataState> PLAYER_SKILL_STATES = new java.util.concurrent.ConcurrentHashMap<>();
private static final Map<UUID, Long> PLAYER_SKILL_REVISIONS = new java.util.concurrent.ConcurrentHashMap<>();

/** Per-player, per-skill value at the last announcement; the baseline unreported gain accrues from. */
private static final Map<UUID, Map<String, Float>> ANNOUNCED_SKILL_VALUES =
        new java.util.concurrent.ConcurrentHashMap<>();

public enum SkillDataState {
    NOT_LOADED,
    LOADING,
    AVAILABLE,
    UNAVAILABLE
}

public record SkillSnapshot(SkillDataState state, float value) {

    /**
     * Whether {@link #value} means anything (M9 item 4).
     *
     * <p>{@code getSkill} returns {@code 0f} both for a player who genuinely has 0 and for a player
     * whose data never arrived, so the float alone can never tell the two apart -- only the state
     * can. This is the accessor that says so out loud, so a caller cannot read the number without
     * having been shown the question.
     */
    public boolean valueKnown() {
        return state == SkillDataState.AVAILABLE;
    }

    /** The value, present only when it is a real one. An authoritative 0 is present and is 0. */
    public java.util.OptionalDouble knownValue() {
        return valueKnown() ? java.util.OptionalDouble.of(value) : java.util.OptionalDouble.empty();
    }
}


    /* =====  Public API  ===== */

    /**
     * Attempts a gain roll for the supplied skill.
     * @param player      server‑side player
     * @param skillName   lowercase skill id (e.g. "fishing")
     * @param success     did the action succeed? (needed for skills that gain on success / fail)
     */
  public static void trySkillGain(ServerPlayer player, String skillName, boolean success) {
    trySkillGainCapped(player, skillName, success, Float.POSITIVE_INFINITY);
  }

  /** Attempts the normal gain roll while enforcing an additional activity-specific cap. */
  public static float trySkillGainCapped(
      ServerPlayer player, String skillName, boolean success, float activityCap) {
    if (player == null || skillName == null || activityCap <= 0.0f || Float.isNaN(activityCap)) {
      return 0.0f;
    }

    // Normalize key
    final String key = SkillKeys.canonical(skillName);
    LOGGER.info("trySkillGain: {}", key);

    // Always have a PlayerSkills map (seeded at login, but belt & suspenders here)
    PlayerSkills p = PLAYER_SKILLS.computeIfAbsent(player.getUUID(), id -> new PlayerSkills());

    // Get def if ready, else a conservative default so MP can gain immediately
    SkillDef def = SKILL_DEFS.getOrDefault(
        key,
        // Fallback until the API loads. gainOnFailure is false to match every seeded Rails
        // row: it used to be true, which meant a failed attempt gained skill before definitions
        // arrived and stopped gaining once they did - the same action behaving differently
        // depending on whether an HTTP fetch had completed. Rails is authoritative, so the
        // fallback agrees with it. Whether failure should grant gain at all is an era question
        // (pre-AOS yes at reduced weight, AOS no) and belongs in the Rails data, not here.
        new SkillDef(key, capitalize(key), 100f, 1.0f, true, false)
    );

    // Respect success/failure flags
    if ((success && !def.gainOnSuccess) || (!success && !def.gainOnFailure)) return 0.0f;

    float current = p.get(key);
    float effectiveMax = Math.min(def.max, activityCap);
    if (current >= effectiveMax) return 0.0f;

    double gainChance = ((100.0 - current) / 100.0) * def.difficultyModifier;
    if (RNG.nextDouble() > gainChance) return 0.0f;

    float newValue = nextCappedGainValue(current, def.max, activityCap);
    if (newValue <= current) return 0.0f;
    p.set(key, newValue);

    LOGGER.info("🎉 {} gained {} → {}", player.getScoreboardName(), key, newValue);

    announceRoutineGain(player, key, current, newValue);

    // Send to client on server thread
    player.server.execute(() -> sendSkillSync(player));

    // Async persist
    postGain(player, key, newValue);
    return newValue - current;
}

static float nextCappedGainValue(float current, float definitionMax, float activityCap) {
    return Math.min(current + 0.1f, Math.min(definitionMax, activityCap));
}

/**
 * Whether the gain accumulated since the last announcement is worth reporting.
 *
 * <p>Pure so the threshold can be tested without a live player: the surrounding bookkeeping needs
 * a {@code ServerPlayer} and a network channel, this is the actual rule.
 */
static boolean shouldAnnounceRoutineGain(float announcedValue, float newValue) {
    return newValue - announcedValue + ANNOUNCE_EPSILON >= ANNOUNCE_THRESHOLD;
}

/**
 * Chats a routine gain only once the unreported total reaches {@link #ANNOUNCE_THRESHOLD},
 * reporting everything accrued since the last message rather than just this step.
 *
 * <p>The remainder is carried, not discarded, so ten 0.1 steps produce five messages summing to
 * the full 1.0 rather than losing the odd increments. Progression itself is unaffected either
 * way; the Skills GUI always shows the true value.
 */
private static void announceRoutineGain(
        ServerPlayer player, String key, float previousValue, float newValue) {
    Map<String, Float> announced = ANNOUNCED_SKILL_VALUES
            .computeIfAbsent(player.getUUID(), id -> new java.util.concurrent.ConcurrentHashMap<>());
    Float existing = announced.putIfAbsent(key, previousValue);
    float baseline = existing != null ? existing : previousValue;

    if (!shouldAnnounceRoutineGain(baseline, newValue)) {
        return;
    }
    announced.put(key, newValue);

    String message = String.format(
        "Your skill in %s has increased by %.1f%%. It is now %.1f%%.",
        capitalize(key), newValue - baseline, newValue
    );
    Style style = Style.EMPTY.withFont(FONT_UO_CLASSIC).withColor(TEAL_0093A4);
    player.sendSystemMessage(Component.literal(""));
    player.sendSystemMessage(Component.literal(message).withStyle(style));
}

/**
 * Rebases the announcement baseline after an authoritative write that is not a routine gain.
 * Without this an admin set or a training purchase would make the next 0.1 step report the whole
 * jump as though the player had earned it.
 */
private static void resetAnnouncedValue(ServerPlayer player, String key, float value) {
    ANNOUNCED_SKILL_VALUES
            .computeIfAbsent(player.getUUID(), id -> new java.util.concurrent.ConcurrentHashMap<>())
            .put(key, value);
}

public static float awardSkillGain(ServerPlayer player, String skillName, float amount) {
    if (player == null || amount <= 0.0f) return 0.0f;

    final String key = SkillKeys.canonical(skillName);
    PlayerSkills p = PLAYER_SKILLS.computeIfAbsent(player.getUUID(), id -> new PlayerSkills());
    SkillDef def = SKILL_DEFS.getOrDefault(
        key,
        // Matches the seeded Rails default, per the note in trySkillGain above.
        new SkillDef(key, capitalize(key), 100f, 1.0f, true, false)
    );

    float current = p.get(key);
    if (current >= def.max) return 0.0f;

    float newValue = Math.min(current + amount, def.max);
    p.set(key, newValue);

    announceRoutineGain(player, key, current, newValue);

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
    // Rebaselined against whatever Rails loads below, so a reconnect cannot report an old
    // session's pending fraction as a fresh gain.
    ANNOUNCED_SKILL_VALUES.remove(sp.getUUID());
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
                noteSkillFetchSuccess(sp.getUUID());
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
        ANNOUNCED_SKILL_VALUES.remove(e.getEntity().getUUID());
        // The retry budget is per session, like every other map here: a reconnect gets a fresh
        // login fetch and, if that fails too, a fresh bounded schedule.
        SKILL_RETRIES.remove(e.getEntity().getUUID());
        SKILL_FETCH_IN_FLIGHT.remove(e.getEntity().getUUID());
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
            // M9 item 4 / discovery D11: a failed load used to be the end of it for the session.
            // Book the next bounded attempt instead.
            noteSkillFetchFailure(player.getUUID());
            sendSkillSync(player);
        }
    }

    /* =====  Bounded skill-data retry (M9 item 4, discovery D11)  ===== */

    /** Failed attempts and the earliest time the next one may run, per connected player. */
    private static final Map<UUID, RetrySchedule> SKILL_RETRIES = new java.util.concurrent.ConcurrentHashMap<>();

    /** In-flight guard, so a tick and a plant attempt cannot both fire the same fetch. */
    private static final java.util.Set<UUID> SKILL_FETCH_IN_FLIGHT =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Wall clock, replaceable so a test does not have to sleep through a five-minute backoff. */
    private static java.util.function.LongSupplier retryClock = System::currentTimeMillis;

    /** Once a second, matching the action outbox it shares a tick handler with. */
    private static final int RETRY_TICK_INTERVAL = 20;

    private record RetrySchedule(int failedAttempts, long nextAttemptMillis, long lastManualMillis) {
    }

    /** Test seam. */
    static void installRetryClock(java.util.function.LongSupplier clock) {
        retryClock = Objects.requireNonNull(clock, "Retry clock is required");
    }

    /** Test seam. */
    static void resetRetryClock() {
        retryClock = System::currentTimeMillis;
    }

    /** Test seam: how many automatic attempts this player has already burned. */
    static int failedSkillFetchAttempts(UUID playerId) {
        RetrySchedule schedule = SKILL_RETRIES.get(playerId);
        return schedule == null ? 0 : schedule.failedAttempts();
    }

    /** Test seam. */
    static void forgetSkillRetries() {
        SKILL_RETRIES.clear();
        SKILL_FETCH_IN_FLIGHT.clear();
    }

    private static void noteSkillFetchFailure(UUID playerId) {
        SKILL_RETRIES.compute(playerId, (id, previous) -> {
            int attempts = (previous == null ? 0 : previous.failedAttempts()) + 1;
            long last = previous == null ? 0L : previous.lastManualMillis();
            return new RetrySchedule(attempts,
                    retryClock.getAsLong() + SkillDataBackoff.delayMillis(attempts), last);
        });
    }

    private static void noteSkillFetchSuccess(UUID playerId) {
        SKILL_RETRIES.remove(playerId);
    }

    /**
     * Whether an automatic retry is due for this player right now: the data really is missing, the
     * bound has not been spent, and the backoff has elapsed.
     */
    static boolean automaticRetryDue(UUID playerId, long nowMillis) {
        if (PLAYER_SKILL_STATES.getOrDefault(playerId, SkillDataState.NOT_LOADED) != SkillDataState.UNAVAILABLE) {
            return false;
        }
        RetrySchedule schedule = SKILL_RETRIES.get(playerId);
        if (schedule == null || SkillDataBackoff.exhausted(schedule.failedAttempts())) {
            return false;
        }
        return nowMillis >= schedule.nextAttemptMillis();
    }

    /**
     * Runs any skill-data retry whose backoff has elapsed. Called once a second from the game bus.
     *
     * <p>Only players whose state is {@code UNAVAILABLE} are candidates, so a healthy server does
     * no work here at all.
     */
    public static void tickSkillDataRetries(MinecraftServer server) {
        if (server == null || server.getTickCount() % RETRY_TICK_INTERVAL != 0) {
            return;
        }
        long now = retryClock.getAsLong();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (automaticRetryDue(player.getUUID(), now)) {
                beginSkillDataFetch(player);
            }
        }
    }

    /**
     * Asks for one more attempt because the player just tried to do something that needs the data
     * (M9 item 4). Rate-limited by {@link SkillDataBackoff#MANUAL_RETRY_COOLDOWN_MILLIS} and
     * ignored once the automatic bound is spent, so the retry stays bounded however hard a player
     * clicks. Returns whether a fetch was started.
     */
    public static boolean requestSkillDataRetry(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        SkillDataState state = PLAYER_SKILL_STATES.getOrDefault(playerId, SkillDataState.NOT_LOADED);
        if (state == SkillDataState.AVAILABLE || state == SkillDataState.LOADING) {
            return false;
        }
        long now = retryClock.getAsLong();
        RetrySchedule schedule = SKILL_RETRIES.get(playerId);
        if (schedule != null) {
            if (SkillDataBackoff.exhausted(schedule.failedAttempts())
                    || now - schedule.lastManualMillis() < SkillDataBackoff.MANUAL_RETRY_COOLDOWN_MILLIS) {
                return false;
            }
            SKILL_RETRIES.put(playerId, new RetrySchedule(
                    schedule.failedAttempts(), schedule.nextAttemptMillis(), now));
        } else {
            SKILL_RETRIES.put(playerId, new RetrySchedule(0, now, now));
        }
        return beginSkillDataFetch(player);
    }

    /**
     * One attempt at re-loading a player's skills, off the server thread, applying the result on it.
     *
     * <p>Deliberately the same shape as the login bootstrap, and deliberately not a second cache:
     * success installs the values and flips the state to {@code AVAILABLE}, which is all
     * {@code FarmingCultivationGate} was ever waiting for -- so the player plants without relogging.
     */
    private static boolean beginSkillDataFetch(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (!SKILL_FETCH_IN_FLIGHT.add(playerId)) {
            return false;
        }
        try {
            ServerHttpExecutor.run(player.server, () -> {
                PlayerSkills loaded = null;
                try {
                    loaded = fetchPlayerSkillsAsync(player);
                } catch (Exception failure) {
                    LOGGER.warn("Skill data retry failed for {}: {}",
                            player.getScoreboardName(), failure.toString());
                }
                PlayerSkills result = loaded;
                player.server.execute(() -> {
                    SKILL_FETCH_IN_FLIGHT.remove(playerId);
                    if (!isCurrentConnectedPlayer(player)) {
                        SKILL_RETRIES.remove(playerId);
                        return;
                    }
                    if (result == null) {
                        noteSkillFetchFailure(playerId);
                        PLAYER_SKILL_STATES.put(playerId, SkillDataState.UNAVAILABLE);
                        sendSkillSync(player);
                        return;
                    }
                    int burned = failedSkillFetchAttempts(playerId);
                    PLAYER_SKILLS.put(playerId, result);
                    PLAYER_SKILL_STATES.put(playerId, SkillDataState.AVAILABLE);
                    noteSkillFetchSuccess(playerId);
                    sendSkillSync(player);
                    LOGGER.info("Skill data recovered for {} after {} failed attempt(s)",
                            player.getScoreboardName(), burned);
                });
            });
        } catch (RuntimeException rejected) {
            // The HTTP pool is bounded and refuses work when saturated; that is a failed attempt
            // like any other, not a reason to leak the in-flight guard.
            SKILL_FETCH_IN_FLIGHT.remove(playerId);
            noteSkillFetchFailure(playerId);
            return false;
        }
        return true;
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
        String key = SkillKeys.canonical(skillName);

        // FIX 1: getUuid() -> getUUID()
        PlayerSkills p = PLAYER_SKILLS.computeIfAbsent(player.getUUID(), id -> new PlayerSkills());
        p.set(key, value);
        resetAnnouncedValue(player, key, value);
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
            ps.mergeHighest(SkillKeys.canonical(o.get("skill_name").getAsString()),
                    o.get("value").getAsFloat());
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
            // Canonical, not merely lowercase: a historical row persisted under a legacy alias
            // (blacksmith) lands on the canonical key (blacksmithy). If both spellings were ever
            // persisted, the higher value wins rather than whichever row Rails returned last.
            String name = SkillKeys.canonical(o.get("skill_name").getAsString());
            ps.mergeHighest(name, o.get("value").getAsFloat());
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

// 1. The base method that accepts a UUID
    public static float getSkill(java.util.UUID playerUUID, String skillName) {
        PlayerSkills ps = PLAYER_SKILLS.get(playerUUID);
        // Canonical on read as well as write: a caller spelling a legacy alias (or a different
        // case) must reach the same row the writes populated, never a phantom second skill.
        return (ps == null) ? 0f : ps.get(SkillKeys.canonical(skillName));
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
        String key = SkillKeys.canonical(skillName);
        PLAYER_SKILLS.computeIfAbsent(player.getUUID(), id -> new PlayerSkills()).set(key, value);
        resetAnnouncedValue(player, key, value);
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
        /** For loads only: two persisted spellings canonicalizing to one key keep the higher value. */
        void  mergeHighest(String s, float v) { map.merge(s, v, Math::max); }
        int   size() { return map.size(); }
    }
}
