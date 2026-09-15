package com.seggellion.britannia_mod.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a stage of "From Soil to Supper" asks the player to have in hand, and what they still need.
 *
 * <p>Stages four and five are the two that ask for materials the player has already spent. Stage
 * four wants fertilized dirt, which is a bowl of dirt and a bowl of water and a dung -- all of
 * which stage three consumed. Stage five wants the produce, which means going round the whole loop
 * again: fresh dung, fresh dirt, another mix, a plot, a seed, water, and a harvest. Neither stage
 * says so anywhere the player can see, because the quest text is authored for the first time
 * through.
 *
 * <p>Client-free and free of Minecraft types on its API, so the rule -- which materials, and how
 * many of each, given what is already carried -- is a plain unit test rather than something only a
 * running server can check.
 *
 * <p>The subtraction is the point. A player who kept a spare dung is told to find dirt and nothing
 * else; a player carrying everything is told nothing at all. Sending someone to collect what is
 * already in their pack is how a guidance message trains people to ignore guidance messages.
 */
public final class RowanFarmingMaterials {

    /** Namespaced ids, so this file can be read against the registry without following a holder. */
    public static final String DUNG = "britannia_mod:dung";
    public static final String DIRT = "britannia_mod:dirt";
    public static final String EMPTY_BOWL = "britannia_mod:empty_bowl";
    public static final String FERTILIZED_DIRT = "britannia_mod:fertilized_dirt";

    /** One material and how many of it a stage needs. */
    public record Requirement(String itemId, int count) {
        public Requirement {
            if (count < 1) throw new IllegalArgumentException("a requirement asks for at least one");
        }
    }

    private RowanFarmingMaterials() {}

    /**
     * The raw materials a stage's own work consumes, in the order a player would gather them.
     *
     * <p>Tools are deliberately absent. The shovel, the hoe and the watering can are permanent and
     * are replaced through the equipment reissue path when they are genuinely lost; listing them
     * here would tell a player to go and find something they are holding.
     *
     * <p>The bowls are listed because the mix returns them empty and the next mix needs them again
     * -- two of them, which is the number the tutorial kit ships and the number a second mix costs.
     */
    public static List<Requirement> requirementsFor(String questKey) {
        if (questKey == null) return List.of();
        return switch (questKey) {
            // Mix Fertilized Dirt, from scratch: a bowl of dirt, a bowl of water, and dung.
            //
            // No bucket. The chain is bowl + dirt, bowl + dung, bowl filled at a water source, then
            // the two bowls mixed -- the bucket is never touched, and stage three's hand-in took it
            // anyway. Listing it sent every player looking for something they neither need nor can
            // still have, which is how a guidance message teaches people to ignore guidance.
            case RowanQuestlineHooks.MIX_QUEST_KEY, RowanQuestlineHooks.HARVEST_QUEST_KEY -> List.of(
                    new Requirement(DUNG, 1),
                    new Requirement(DIRT, 1),
                    new Requirement(EMPTY_BOWL, 2));
            default -> List.of();
        };
    }

    /** Whether this stage sends the player looking for dung, and so is worth expediting for. */
    public static boolean needsDung(String questKey) {
        for (Requirement requirement : requirementsFor(questKey)) {
            if (DUNG.equals(requirement.itemId())) return true;
        }
        return false;
    }

    /**
     * What is still to be found, given what the player is carrying.
     *
     * <p>{@code carried} maps a namespaced item id to how many the player holds; anything absent
     * counts as none. An empty answer means the player already has everything, and the caller says
     * nothing rather than congratulating them.
     */
    public static List<Requirement> outstanding(String questKey, Map<String, Integer> carried) {
        Map<String, Integer> held = carried == null ? Map.of() : carried;
        // The finished good short-circuits the whole list. A player who already has fertilized dirt
        // -- because they mixed a spare, or because stage five follows stage four immediately -- has
        // nothing to gather, and telling them to find dung is telling them to redo work they can see
        // in their own hands.
        if (held.getOrDefault(FERTILIZED_DIRT, 0) > 0) return List.of();

        List<Requirement> missing = new ArrayList<>();
        for (Requirement requirement : requirementsFor(questKey)) {
            int have = held.getOrDefault(requirement.itemId(), 0);
            int short_ = requirement.count() - Math.max(0, have);
            if (short_ > 0) missing.add(new Requirement(requirement.itemId(), short_));
        }
        return List.copyOf(missing);
    }

    /** The materials of every stage, for a caller that wants to count them all at once. */
    public static Map<String, Integer> tally(String questKey) {
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (Requirement requirement : requirementsFor(questKey)) {
            tally.merge(requirement.itemId(), requirement.count(), Integer::sum);
        }
        return Map.copyOf(tally);
    }
}
