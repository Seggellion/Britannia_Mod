package com.seggellion.britannia_mod.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Rowan farming questline M8: the client-visible half of a quest node's metadata, parsed once.
 *
 * <p>Protocol section 3.2 lists what a client may see on a node: {@code journal_objective},
 * {@code progress_steps}, {@code rewards_preview}, {@code keep_items}, {@code help} and
 * {@code stage}, and says choices may carry {@code "presentation"}. This reads exactly those and
 * nothing else. The objective machinery on the same node -- {@code action_trigger},
 * {@code action_steps}, {@code location_trigger}, {@code pickup_trigger}, {@code destroy_trigger}
 * -- is deliberately not read here: it is the server's, and {@code QuestObjectiveTriggers} is
 * where it is parsed.
 *
 * <p>That is a rule about what the <b>screens</b> hold, not a claim that the metadata never
 * reaches a client. It does: {@code QuestObjectiveWatcher} and {@code QuestEventHandlers} ship
 * {@code node.metadata} whole inside {@code QuestTriggerResultS2CPayload}, and
 * {@code QuestActionDispatcher} ships the raw Rails body including
 * {@code accepted_quest.triggers}. Those raw-payload paths are a separate, pre-existing exposure
 * with its own gate and are untouched by this milestone. What reading a narrow set here buys is
 * that no quest screen can put a solution on the display or into a tooltip by accident, and that
 * the client-visible surface is one auditable list rather than "whatever the node happened to
 * carry".
 *
 * <p>Pure Gson and pure data, so JUnit can assert the whole mapping against the frozen
 * {@code quest_contract/v1} fixtures without a client.
 *
 * <h2>A presentation choice is not a choice</h2>
 * {@link #isPresentationChoice} answers the one question that matters at the call site: this
 * button opens something on the client and must never be sent as a CHOOSE. Rails refuses it with
 * {@code 422 presentation_only} anyway, but the reason the mod must not send it is not that the
 * server rejects it -- it is that the node holding the live objective would stay current only by
 * luck. Opening help leaves the quest exactly where it was.
 */
public record QuestNodePresentation(
        String journalObjective,
        Stage stage,
        Help help,
        List<ItemPreview> onAccept,
        List<ItemPreview> onComplete,
        List<ItemPreview> keepItems,
        List<Achievement> achievements,
        /**
         * Choice id to presentation kind, for every choice the node marks presentation only.
         *
         * <p>Read from the node's own {@code choices} map rather than from the published choice
         * list, because {@code QuestModels.QuestChoice} models only {@code id}, {@code text} and
         * {@code is_locked} -- the {@code presentation} flag Rails passes through has nowhere to
         * land there. The node metadata carries the same flag keyed by choice id, which is the
         * source this reads and the reason no change to the published choice model was needed.
         */
        Map<String, String> presentationChoices
) {

    public static final QuestNodePresentation NONE = new QuestNodePresentation(
            "", Stage.NONE, Help.NONE, List.of(), List.of(), List.of(), List.of(), Map.of());

    public QuestNodePresentation {
        presentationChoices = presentationChoices == null ? Map.of() : Map.copyOf(presentationChoices);
    }

    /** "Quest 3 of 5". {@code index} and {@code count} are zero when the node names no stage. */
    public record Stage(String questlineKey, int index, int count, String label) {
        public static final Stage NONE = new Stage("", 0, 0, "");

        public boolean known() {
            return index > 0 && count > 0;
        }
    }

    /** One item in a reward or keep preview. */
    public record ItemPreview(String id, int count) {}

    /** One achievement named in {@code rewards_preview.achievements}. */
    public record Achievement(String key, String title) {}

    /** A node's help text and its step-by-step guide. */
    public record Help(String title, String body, List<GuideStep> guide) {
        public static final Help NONE = new Help("", "", List.of());

        public boolean present() {
            return !title.isBlank() || !body.isBlank() || !guide.isEmpty();
        }
    }

    /**
     * One row of the mixing guide: what is held, what is done, what comes out and what comes back.
     *
     * @param mainHand item id for the main hand; never blank in authored content
     * @param offHand  item id for the off hand, or blank when the step uses one hand
     * @param gesture  the sentence describing the action
     * @param result   item id the step produces, or blank when the result is a world change
     * @param returned item ids handed back -- for the final mix, both bowls
     */
    public record GuideStep(String mainHand, String offHand, String gesture, String result,
                            List<String> returned) {
        public boolean usesOffHand() {
            return !offHand.isBlank();
        }
    }

    // ---------------------------------------------------------------- parsing

    /** Reads the client-visible fields off a node's {@code metadata} object. */
    public static QuestNodePresentation fromNodeMetadata(JsonObject metadata) {
        if (metadata == null) return NONE;
        JsonObject rewards = object(metadata, "rewards_preview");
        return new QuestNodePresentation(
                string(metadata, "journal_objective"),
                parseStage(object(metadata, "stage")),
                parseHelp(object(metadata, "help")),
                parseItems(rewards == null ? null : array(rewards, "on_accept")),
                parseItems(rewards == null ? null : array(rewards, "on_complete")),
                parseItems(array(metadata, "keep_items")),
                parseAchievements(rewards == null ? null : array(rewards, "achievements")),
                parsePresentationChoices(object(metadata, "choices")));
    }

    /**
     * The presentation kind for a choice id, or {@code ""} when the choice is a real transition.
     *
     * <p>This is the question every choice button asks before it does anything. A non-blank answer
     * means the button opens something on this client and sends nothing.
     */
    public String presentationFor(String choiceId) {
        if (choiceId == null) return "";
        return presentationChoices.getOrDefault(choiceId.trim(), "");
    }

    private static Map<String, String> parsePresentationChoices(JsonObject choices) {
        if (choices == null) return Map.of();
        Map<String, String> found = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : choices.entrySet()) {
            if (entry.getValue() == null || !entry.getValue().isJsonObject()) continue;
            String kind = string(entry.getValue().getAsJsonObject(), "presentation");
            if (kind.isBlank()) continue;
            found.put(entry.getKey(), kind.toLowerCase(Locale.ROOT));
        }
        return Map.copyOf(found);
    }

    /**
     * True when a published choice is presentation only.
     *
     * <p>Accepts the choice object as Rails publishes it -- {@code {id, text, is_locked,
     * presentation}} -- because that is the shape the mod already holds. Any non-blank
     * {@code presentation} counts: an unknown presentation kind is still not a transition, and
     * treating it as one is the failure that would move the quest.
     */
    public static boolean isPresentationChoice(JsonObject choice) {
        return choice != null && !string(choice, "presentation").isBlank();
    }

    /** The presentation kind a choice names, lower-cased, or blank. {@code "help"} is the only one authored. */
    public static String presentationKind(JsonObject choice) {
        return choice == null ? "" : string(choice, "presentation").toLowerCase(Locale.ROOT);
    }

    private static Stage parseStage(JsonObject source) {
        if (source == null) return Stage.NONE;
        return new Stage(string(source, "questline_key"), integer(source, "index"),
                integer(source, "count"), string(source, "label"));
    }

    private static Help parseHelp(JsonObject source) {
        if (source == null) return Help.NONE;
        List<GuideStep> steps = new ArrayList<>();
        JsonArray guide = array(source, "guide");
        if (guide != null) {
            for (JsonElement element : guide) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject step = element.getAsJsonObject();
                String mainHand = string(step, "main_hand");
                String gesture = string(step, "gesture");
                // A row with neither a hand nor a gesture describes nothing; drawing an empty row
                // is worse than drawing one row fewer.
                if (mainHand.isBlank() && gesture.isBlank()) continue;
                steps.add(new GuideStep(mainHand, string(step, "off_hand"), gesture,
                        string(step, "result"), strings(step, "returned")));
            }
        }
        return new Help(string(source, "title"), string(source, "body"), List.copyOf(steps));
    }

    private static List<ItemPreview> parseItems(JsonArray source) {
        if (source == null) return List.of();
        List<ItemPreview> items = new ArrayList<>();
        for (JsonElement element : source) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            String id = string(item, "id");
            if (id.isBlank()) continue;
            items.add(new ItemPreview(id, Math.max(1, integer(item, "count"))));
        }
        return List.copyOf(items);
    }

    private static List<Achievement> parseAchievements(JsonArray source) {
        if (source == null) return List.of();
        List<Achievement> found = new ArrayList<>();
        for (JsonElement element : source) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            String key = string(entry, "key");
            String title = string(entry, "title");
            if (key.isBlank() && title.isBlank()) continue;
            found.add(new Achievement(key, title));
        }
        return List.copyOf(found);
    }

    /** {@code returned} is authored as either a single id or a list of them. Both are accepted. */
    private static List<String> strings(JsonObject source, String key) {
        if (source == null || !source.has(key)) return List.of();
        JsonElement value = source.get(key);
        if (value == null || value.isJsonNull()) return List.of();
        List<String> out = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) {
                if (element != null && element.isJsonPrimitive()) {
                    String text = element.getAsString().trim();
                    if (!text.isEmpty()) out.add(text);
                }
            }
        } else if (value.isJsonPrimitive()) {
            String text = value.getAsString().trim();
            if (!text.isEmpty()) out.add(text);
        }
        return List.copyOf(out);
    }

    private static JsonObject object(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonObject()
                ? root.getAsJsonObject(key) : null;
    }

    private static JsonArray array(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonArray()
                ? root.getAsJsonArray(key) : null;
    }

    private static String string(JsonObject root, String key) {
        if (root == null || !root.has(key)) return "";
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return "";
        return value.getAsString().trim();
    }

    private static int integer(JsonObject root, String key) {
        if (root == null || !root.has(key)) return 0;
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return 0;
        try {
            return value.getAsInt();
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- queries

    public int totalRewardIcons() {
        return onAccept.size() + onComplete.size() + keepItems.size();
    }

    public boolean hasHelp() {
        return help.present();
    }
}
