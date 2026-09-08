package com.seggellion.britannia_mod.quest.achievement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rowan farming questline M10: the third result of a quest achievement -- a persistent
 * {@code britannia_mod:quest/&lt;key&gt;} advancement -- granted at the same authoritative boundary
 * that produced the other two.
 *
 * <h2>Where this runs</h2>
 * Only from the server side of an authoritative Rails answer, next to
 * {@code QuestRewardService.apply}: {@code QuestProxyService#applyAuthoritativeResult} for a turn-in
 * and {@code QuestActionDispatcher#installState} for an action event. Rails has already committed
 * the node advance, the reward delivery and the website achievement in one transaction under the
 * journal row lock by the time either is reached, so this grants exactly what that transaction
 * decided, and never what a client claims.
 *
 * <h2>Why the advancement is also the idempotency token for the toast</h2>
 * Rails is idempotent on its own achievement (the unique index on user/shard/name) and on the
 * reward (the delivery ledger), and it answers a replayed request with the stored response --
 * <em>including the {@code client_actions} that response carried</em>. A response replayed after a
 * lost answer, and a duplicate action event answered from its stored row, therefore both re-announce
 * an achievement the player already has.
 *
 * <p>{@code PlayerAdvancements#award} is true exactly once per player per criterion and is persisted
 * in the player's own advancement file, so it is the one durable, per-player "first time" this side
 * owns. The announcement the client is given is therefore filtered by it: an achievement whose
 * advancement this player had already earned is removed from the forwarded response, so the toast
 * and its sound happen exactly as often as the advancement does.
 *
 * <p>The one deliberate exception is an achievement with no advancement resource at all
 * ({@link Grant#NO_ADVANCEMENT}): there is no token to decide with, so the announcement is left
 * alone and the pre-M10 behaviour stands. It is logged by name so the missing resource can be found.
 */
public final class QuestAchievementAward {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String NAMESPACE = "britannia_mod";
    /** The established location of a quest advancement; {@code quest/ring_destroyed} is the first. */
    public static final String ADVANCEMENT_FOLDER = "quest/";
    /** The {@code client_actions} type Rails uses for {@code grant_achievement}. */
    public static final String ACHIEVEMENT_ACTION = "achievement";

    /** The same bound {@code QuestProxyService} puts on a client-action array it reads. */
    static final int MAX_CLIENT_ACTIONS = 32;
    static final int MAX_KEY_LENGTH = 64;

    /** The lang key an achievement's advancement titles itself with. */
    public static final String TITLE_KEY_PREFIX = "advancements." + NAMESPACE + ".quest.";
    public static final String TITLE_KEY_SUFFIX = ".title";

    private QuestAchievementAward() {
    }

    /** One achievement an authoritative response announced: its stable key and its display name. */
    public record Announcement(String key, String title) {
    }

    /**
     * How to name one announced achievement on screen: the advancement's own translation key, with
     * what Rails called it as the fallback for anything this pack has no title for.
     *
     * <p>Pure and free of client classes so the naming rule is assertable in a plain unit test. An
     * empty {@code translationKey} means "there is no advancement key to translate" -- the caller
     * shows the fallback, or a translated placeholder when even that is empty.
     */
    public record Display(String translationKey, String fallback) {
    }

    /** {@link Display} for a client action's {@code key} and {@code name}. */
    public static Display display(@Nullable String key, @Nullable String name) {
        String shown = name == null ? "" : name.trim();
        String resolved = sanitize(key);
        if (resolved.isEmpty()) resolved = sanitize(shown);
        if (resolved.isEmpty()) return new Display("", shown);
        return new Display(TITLE_KEY_PREFIX + resolved + TITLE_KEY_SUFFIX,
            shown.isEmpty() ? resolved : shown);
    }

    /** What happened when one announcement was taken to this player's advancements. */
    public enum Grant {
        /** The advancement existed and this player did not have it: granted now, for the first time. */
        AWARDED,
        /** The advancement existed and this player already had it: nothing to grant, nothing to say. */
        ALREADY_EARNED,
        /** No such advancement is loaded. Nothing can be granted and nothing can be decided. */
        NO_ADVANCEMENT,
        /** The announced key carried nothing usable as an advancement path. */
        INVALID_KEY
    }

    /**
     * Whether the client should still be told about an announcement with this outcome.
     *
     * <p>Pure, so the rule is assertable without a server: only an advancement this player already
     * had suppresses the announcement, because only that outcome proves the player has been told
     * before.
     */
    public static boolean announceToPlayer(Grant grant) {
        return grant != Grant.ALREADY_EARNED;
    }

    /**
     * The achievements an authoritative response announces, in order and without repeats.
     *
     * <p>Reads the same {@code client_actions} array the client renders. The key is
     * {@code key} when Rails sends one and the sanitized {@code name} otherwise, so a Rails that
     * predates the explicit key still resolves {@code "First Harvest"} to {@code first_harvest}.
     */
    public static List<Announcement> announced(@Nullable JsonObject root) {
        if (root == null || !root.has("client_actions") || !root.get("client_actions").isJsonArray()) {
            return List.of();
        }
        JsonArray actions = root.getAsJsonArray("client_actions");
        if (actions.size() > MAX_CLIENT_ACTIONS) return List.of();

        List<Announcement> found = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonElement element : actions) {
            if (!element.isJsonObject()) continue;
            JsonObject action = element.getAsJsonObject();
            if (!isAchievement(action)) continue;
            String key = keyOf(action);
            if (key.isEmpty() || !seen.add(key)) continue;
            found.add(new Announcement(key, string(action, "name")));
        }
        return List.copyOf(found);
    }

    /** Whether this client action is the achievement one, under either spelling Rails may use. */
    public static boolean isAchievement(@Nullable JsonObject action) {
        if (action == null) return false;
        return ACHIEVEMENT_ACTION.equals(string(action, "type"))
            || ACHIEVEMENT_ACTION.equals(string(action, "action"));
    }

    /** The key this action names: the explicit {@code key}, else the display {@code name}. */
    public static String keyOf(@Nullable JsonObject action) {
        if (action == null) return "";
        String explicit = sanitize(string(action, "key"));
        return explicit.isEmpty() ? sanitize(string(action, "name")) : explicit;
    }

    /**
     * The advancement id for an achievement key, or null when the key sanitizes to nothing usable.
     * A path segment of {@code .} or {@code ..} is dropped rather than sanitized into one, so a key
     * can never name anything but a real advancement under {@code quest/}.
     */
    @Nullable
    public static ResourceLocation advancementId(String key) {
        String path = sanitize(key);
        if (path.isEmpty()) return null;
        try {
            return ResourceLocation.fromNamespaceAndPath(NAMESPACE, ADVANCEMENT_FOLDER + path);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    /**
     * Lower-cases and reduces a key to the characters an advancement path allows, so
     * {@code "First Harvest"} and {@code "first_harvest"} are the same advancement. Empty and
     * traversal-shaped segments are dropped, not substituted.
     */
    public static String sanitize(@Nullable String raw) {
        if (raw == null) return "";
        String reduced = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        if (reduced.length() > MAX_KEY_LENGTH) reduced = reduced.substring(0, MAX_KEY_LENGTH);

        StringBuilder path = new StringBuilder();
        for (String segment : reduced.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) continue;
            if (path.length() > 0) path.append('/');
            path.append(segment);
        }
        return path.toString();
    }

    /**
     * Grants the announced achievements' advancements to this player and returns the response to
     * forward: the same object when every announcement still stands, and a copy without the
     * already-earned ones when any does not.
     *
     * <p>{@code source} and {@code requestUuid} appear only in the log line, which is the only
     * place the three results of one completion can be seen to line up.
     */
    public static JsonObject grantAndFilter(@Nullable ServerPlayer player, @Nullable JsonObject root,
                                            String requestUuid, String source) {
        if (root == null) return null;
        List<Announcement> announcements = announced(root);
        if (player == null || announcements.isEmpty()) return root;

        Set<String> suppressed = new LinkedHashSet<>();
        for (Announcement announcement : announcements) {
            Grant grant = grant(player, announcement.key());
            LOGGER.info("event=quest_achievement_advancement player_uuid={} achievement_key={} "
                    + "advancement_id={} outcome={} announced={} request_uuid={} source={}",
                player.getStringUUID(), announcement.key(), advancementId(announcement.key()),
                grant, announceToPlayer(grant), requestUuid, source);
            if (!announceToPlayer(grant)) suppressed.add(announcement.key());
        }
        return suppressed.isEmpty() ? root : withoutAnnouncements(root, suppressed);
    }

    /** Takes one achievement key to this player's advancements. */
    public static Grant grant(ServerPlayer player, String key) {
        ResourceLocation id = advancementId(key);
        if (player == null || id == null) return Grant.INVALID_KEY;

        AdvancementHolder advancement = player.server.getAdvancements().get(id);
        if (advancement == null) return Grant.NO_ADVANCEMENT;

        boolean awarded = false;
        for (String criterion : advancement.value().criteria().keySet()) {
            awarded |= player.getAdvancements().award(advancement, criterion);
        }
        return awarded ? Grant.AWARDED : Grant.ALREADY_EARNED;
    }

    /**
     * A copy of the response whose {@code client_actions} no longer announce the named achievements.
     * Everything else -- other client actions, the node, the journal entry, the delivery -- is
     * carried over untouched, because only the announcement is being decided here.
     */
    public static JsonObject withoutAnnouncements(JsonObject root, Set<String> keys) {
        JsonObject copy = root.deepCopy();
        if (!copy.has("client_actions") || !copy.get("client_actions").isJsonArray()) return copy;

        JsonArray kept = new JsonArray();
        for (JsonElement element : copy.getAsJsonArray("client_actions")) {
            if (element.isJsonObject()) {
                JsonObject action = element.getAsJsonObject();
                if (isAchievement(action) && keys.contains(keyOf(action))) continue;
            }
            kept.add(element);
        }
        copy.add("client_actions", kept);
        return copy;
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException notAString) {
            return "";
        }
    }
}
