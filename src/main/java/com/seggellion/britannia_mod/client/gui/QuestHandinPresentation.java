package com.seggellion.britannia_mod.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.quest.network.QuestModels.QuestResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * What the {@code handin} block on a transition answer means for the dialogue.
 *
 * <h2>Why this is not on the screen</h2>
 * {@code QuestDecisionScreen} extends {@code Screen}, so a test that touched these rules there
 * would have to stand up Minecraft. The rules are pure -- a small JSON object in, a state and some
 * item lines out -- so they live here beside the layout classes, which are client-free for the same
 * reason.
 *
 * <h2>The states</h2>
 * Two of the six presentation states are decided by the screen alone and never appear here: the
 * dialogue is <i>ready to turn in</i> whenever a turn-in choice is offered, and it is
 * <i>checking</i> for as long as a claim is in flight. The other four are the server's answer, and
 * they are what this parses.
 *
 * <p>{@link State#NONE} is the ordinary case and matters as much as the rest: almost every quest in
 * the game has no hand-in at all, so a response with no block must leave the dialogue behaving
 * exactly as it always has.
 */
public final class QuestHandinPresentation {

    /** The key the server writes its client-facing hand-in block under. */
    public static final String HANDIN_KEY = "handin";

    /** A hand-in never asks for more than eight things; anything longer is not one. */
    private static final int MAX_LINES = 8;

    public enum State {
        /** No hand-in in this answer. Every quest without one takes this path. */
        NONE,
        /** The player is short. The dialogue stays open and says exactly what is missing. */
        ITEMS_MISSING,
        /** Rowan took the goods and the quest moved on. */
        CONSUMED,
        /** The quest could not be finished; the goods are coming back on their own. */
        REFUNDED,
        /** Nothing could be completed. Carries a reason the player can act on, or report. */
        UNAVAILABLE
    }

    /** One item line: a namespaced id and a count, already concrete. */
    public record Line(String itemId, int count) {}

    public static final QuestHandinPresentation ABSENT =
            new QuestHandinPresentation(State.NONE, List.of(), List.of(), "", "");

    private final State state;
    private final List<Line> requires;
    private final List<Line> missing;
    private final String message;
    private final String reason;

    private QuestHandinPresentation(State state, List<Line> requires, List<Line> missing,
                                    String message, String reason) {
        this.state = state;
        this.requires = List.copyOf(requires);
        this.missing = List.copyOf(missing);
        this.message = message;
        this.reason = reason;
    }

    public State state() {
        return state;
    }

    /** What the quest giver is asking for, when the answer named it. */
    public List<Line> requires() {
        return requires;
    }

    /** What the player is still short of. */
    public List<Line> missing() {
        return missing;
    }

    /** What was actually taken, for an answer that reports a removal. */
    public List<Line> removed() {
        return requires;
    }

    /** The author's own words for a shortfall, when the quest supplied any. */
    public String message() {
        return message;
    }

    /** A short non-secret code naming why nothing could be completed. */
    public String reason() {
        return reason;
    }

    public boolean present() {
        return state != State.NONE;
    }

    /**
     * Whether this answer leaves the player standing at the same node with something still to do.
     *
     * <p>The dialogue stays open for these, which is the whole reason the block exists on the wire:
     * the node has not moved, so without it the screen would read the answer as a dismissal and
     * close on a click that was supposed to explain itself.
     */
    public boolean keepsDialogueOpen() {
        return state == State.ITEMS_MISSING || state == State.UNAVAILABLE || state == State.REFUNDED;
    }

    public static QuestHandinPresentation from(QuestResponse response) {
        return response == null ? ABSENT : from(response.handin);
    }

    /** Refuses rather than guesses: an unreadable block presents as no block at all. */
    public static QuestHandinPresentation from(JsonElement raw) {
        if (raw == null || !raw.isJsonObject()) return ABSENT;
        JsonObject block = raw.getAsJsonObject();
        State state = stateOf(string(block, "state"));
        if (state == State.NONE) return ABSENT;
        List<Line> requires = lines(block, "requires");
        if (requires.isEmpty()) requires = lines(block, "removed");
        return new QuestHandinPresentation(state, requires, lines(block, "missing"),
                string(block, "message"), string(block, "reason"));
    }

    private static State stateOf(String wire) {
        return switch (wire) {
            case "items_missing" -> State.ITEMS_MISSING;
            case "consumed" -> State.CONSUMED;
            case "refunded" -> State.REFUNDED;
            case "unavailable" -> State.UNAVAILABLE;
            default -> State.NONE;
        };
    }

    private static List<Line> lines(JsonObject block, String key) {
        if (!block.has(key) || !block.get(key).isJsonArray()) return List.of();
        JsonArray array = block.getAsJsonArray(key);
        List<Line> parsed = new ArrayList<>(Math.min(array.size(), MAX_LINES));
        for (JsonElement element : array) {
            if (parsed.size() >= MAX_LINES) break;
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            String itemId = string(entry, "item");
            if (itemId.isEmpty()) continue;
            int count = 0;
            if (entry.has("count") && entry.get("count").isJsonPrimitive()) {
                try {
                    count = entry.get("count").getAsInt();
                } catch (RuntimeException notANumber) {
                    continue;
                }
            }
            if (count < 0 || count > 1024) continue;
            parsed.add(new Line(itemId, count));
        }
        return parsed;
    }

    private static String string(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonPrimitive()) return "";
        String value = parent.get(key).getAsString();
        return value.length() > 512 ? "" : value;
    }
}
