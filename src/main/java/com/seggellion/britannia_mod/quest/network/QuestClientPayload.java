package com.seggellion.britannia_mod.quest.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rowan farming questline M11, deferred defect 2: what a quest result may say to a client.
 *
 * <h2>The exposure this closes</h2>
 * Three paths send an authoritative Rails answer on to the player's client inside
 * {@code QuestTriggerResultS2CPayload}, and until this milestone all three sent the body whole:
 * <ul>
 *   <li>{@code QuestActionDispatcher} sends the raw Rails body, whose {@code accepted_quest.triggers}
 *       is the journal serializer's <b>published</b> form -- placeholders already resolved and
 *       {@code require_bound} already reduced to the values <i>currently bound</i>. That is the
 *       plot key, the crop-cycle UUID and every {@code match} rule: the answer key;</li>
 *   <li>{@code QuestObjectiveWatcher} and {@code QuestEventHandlers} re-serialize the parsed
 *       response, whose {@code node.metadata} carries {@code action_trigger}, {@code action_steps}
 *       and the three legacy observers.</li>
 * </ul>
 *
 * <p>It was never an authority hole -- the server decides, {@code QuestActionC2SPayload.TRIGGER}
 * stays refused, and Rails re-evaluates everything -- but a client that is handed the solution can
 * be read, and {@code QuestEntryCodecs} goes to deliberate lengths to keep exactly these fields out
 * of the login/quit sync. Two doors and one lock is one lock.
 *
 * <h2>What is removed, and what is deliberately not</h2>
 * The six keys in {@link #SERVER_ONLY_KEYS}, wherever they appear in the body. That is the journal
 * entry's {@code triggers} object and the node's five observer objects -- the whole of what
 * {@code QuestObjectiveTriggers} parses, on both of the shapes it parses.
 *
 * <p>Everything else is carried over untouched, because two client paths read this same body and
 * both must keep working:
 * <ul>
 *   <li>M8's journal refresh parses {@code accepted_quest} / {@code accepted_quests} into
 *       {@code ClientQuestEntry}. Every field it reads -- {@code stage}, {@code objective},
 *       {@code progress}, {@code rewards_preview}, {@code keep_items}, {@code claim_pending},
 *       the quest giver -- is a statement about what the player has already done or been promised,
 *       and none of them is {@code triggers};</li>
 *   <li>M10's achievement filtering reads and rewrites {@code client_actions}, which this does not
 *       touch at all. The filtering happens on the server <i>before</i> this runs, so what the
 *       client receives is still the filtered list.</li>
 * </ul>
 * {@code node.metadata}'s presentation keys ({@code journal_objective}, {@code progress_steps},
 * {@code rewards_preview}, {@code keep_items}, {@code help}, {@code stage}, {@code choices}) are
 * exactly what {@code QuestNodePresentation} reads, and all of them survive.
 *
 * <p>A choice's {@code conditions.trigger_key} survives too, and that is on purpose: it is the name
 * of a trigger, not a bound value, and the payload carries the same name in its own
 * {@code triggerKey} field. Removing a name the packet already states buys nothing.
 *
 * <h2>Pure, and a deep copy</h2>
 * The input is never mutated: the server has already installed state from that same object by the
 * time this runs, and {@code QuestAchievementAward.grantAndFilter} may hand back the caller's own
 * body by identity. So this copies, strips the copy and returns it.
 */
public final class QuestClientPayload {

    private static final Gson GSON = new Gson();

    /**
     * The journal entry's published objective machinery, as
     * {@code QuestObjectiveTriggers.fromJournalEntry} reads it.
     */
    public static final String JOURNAL_TRIGGERS_KEY = "triggers";

    /**
     * The node's objective machinery, as {@code QuestObjectiveTriggers.fromNodeMetadata} reads it.
     * All five, not only the two farming ones: a location volume and a destroy target are solutions
     * by the same argument, and a partial list is how the next one added leaks.
     */
    public static final Set<String> NODE_OBSERVER_KEYS = Set.of(
            "action_trigger",
            "action_steps",
            "location_trigger",
            "pickup_trigger",
            "destroy_trigger");

    /** Every key this strips, wherever it appears. */
    public static final Set<String> SERVER_ONLY_KEYS = Set.of(
            JOURNAL_TRIGGERS_KEY,
            "action_trigger",
            "action_steps",
            "location_trigger",
            "pickup_trigger",
            "destroy_trigger");

    /**
     * How deep the walk goes. Rails bodies are a handful of levels; a bound is here so a pathological
     * body cannot turn a sanitizer into a stack overflow on the server thread. Past the bound the
     * subtree is dropped rather than kept, because "too deep to check" must not mean "sent anyway".
     */
    static final int MAX_DEPTH = 24;

    private QuestClientPayload() {
    }

    /**
     * The body to send to a client: a copy of {@code root} with {@link #SERVER_ONLY_KEYS} removed
     * at every depth. {@code null} in, {@code null} out, which is what
     * {@code QuestAchievementAward.grantAndFilter} answers for a null body.
     */
    @Nullable
    public static JsonObject sanitize(@Nullable JsonObject root) {
        if (root == null) return null;
        return (JsonObject) strip(root.deepCopy(), 0);
    }

    /** {@link #sanitize} then serialize -- the one call every sending site makes. */
    public static String toJson(@Nullable JsonObject root) {
        return GSON.toJson(sanitize(root));
    }

    /**
     * {@link #toJson} for an action-event answer, which needs one field added.
     *
     * <h2>Why</h2>
     * The {@code quest_action_event} envelope has no {@code success} field: section 2.3 says the
     * outcome is {@code result}, and none of the frozen {@code action_event_response_*} fixtures
     * carries one. {@code ClientNetworkHandler.handleQuestTriggerResult} binds the body to
     * {@code QuestModels.QuestResponse} and returns early whenever {@code success} is not true, so
     * forwarding that envelope unchanged meant the client discarded every action-event result --
     * the journal refresh, the quiet objective notice and the achievement toast all skipped.
     *
     * <p>{@code QuestActionDispatcher} reaches this only for {@code applied} and {@code duplicate},
     * after the server has committed the state and set {@code success} on its own parsed copy, so
     * saying so on the wire is stating the decision that was already made rather than inventing one.
     * Only ever added, never overwritten: a Rails that does send the field keeps its own answer.
     */
    public static String toAppliedResultJson(@Nullable JsonObject root) {
        JsonObject sanitized = sanitize(root);
        if (sanitized != null && !sanitized.has("success")) {
            sanitized.addProperty("success", true);
        }
        return GSON.toJson(sanitized);
    }

    /**
     * Serializes a parsed response for a client. The watcher and the environmental handlers hold a
     * {@code QuestModels.QuestResponse} rather than the raw body; it is serialized here so the
     * sanitizing step cannot be skipped by holding the object in a different shape.
     */
    public static String toJson(@Nullable QuestModels.QuestResponse response) {
        if (response == null) return GSON.toJson((JsonElement) null);
        JsonElement tree = GSON.toJsonTree(response);
        return tree.isJsonObject() ? toJson(tree.getAsJsonObject()) : GSON.toJson(tree);
    }

    /**
     * Whether a body is already free of every server-only key. The test-facing half of the same
     * rule, so "no bound value survives" is an assertion rather than a reading of the code.
     */
    public static boolean isSanitized(@Nullable JsonElement root) {
        return findServerOnlyKey(root, 0) == null;
    }

    /** The first server-only key still present, or {@code null}. Named so a failure can say which. */
    @Nullable
    public static String findServerOnlyKey(@Nullable JsonElement element, int depth) {
        if (element == null || depth > MAX_DEPTH) return null;
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> field : element.getAsJsonObject().entrySet()) {
                if (SERVER_ONLY_KEYS.contains(field.getKey())) return field.getKey();
                String deeper = findServerOnlyKey(field.getValue(), depth + 1);
                if (deeper != null) return deeper;
            }
            return null;
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                String deeper = findServerOnlyKey(item, depth + 1);
                if (deeper != null) return deeper;
            }
        }
        return null;
    }

    private static JsonElement strip(JsonElement element, int depth) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) return element;
        if (depth >= MAX_DEPTH) return com.google.gson.JsonNull.INSTANCE;

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            List<String> names = List.copyOf(object.keySet());
            for (String name : names) {
                if (SERVER_ONLY_KEYS.contains(name)) {
                    object.remove(name);
                    continue;
                }
                JsonElement value = object.get(name);
                if (value != null && (value.isJsonObject() || value.isJsonArray())) {
                    object.add(name, strip(value, depth + 1));
                }
            }
            return object;
        }

        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement value = array.get(i);
            if (value != null && (value.isJsonObject() || value.isJsonArray())) {
                array.set(i, strip(value, depth + 1));
            }
        }
        return array;
    }
}
