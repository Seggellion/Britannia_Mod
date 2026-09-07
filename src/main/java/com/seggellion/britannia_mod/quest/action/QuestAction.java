package com.seggellion.britannia_mod.quest.action;

import java.util.List;
import java.util.Locale;

/**
 * The eleven action values of contract {@code quest_action_event} version 1 (protocol section
 * 2.1), with the subject fields each one must carry.
 *
 * <p>The wire name is the authority: it is what Rails matches an authored {@code action_trigger}
 * or {@code action_step} against, so it is spelled here exactly once and never derived from an
 * enum constant's name.
 */
public enum QuestAction {
    WILD_RESOURCE_HARVEST("wild_resource_harvest", List.of("resource_id")),
    DIRT_GATHER("dirt_gather", List.of("item_id")),
    WATER_CONTAINER_FILL("water_container_fill", List.of("container_item_id", "source")),
    BOWL_PREPARE("bowl_prepare", List.of("output_item_id")),
    BOWL_WATER_FILL("bowl_water_fill", List.of("output_item_id")),
    FERTILE_DIRT_MIX("fertile_dirt_mix", List.of("output_item_id")),
    PLOT_HOE("plot_hoe", List.of("plot_key", "community_plot")),
    PLOT_FERTILIZE("plot_fertilize", List.of("plot_key", "community_plot")),
    CROP_PLANT("crop_plant", List.of("plot_key", "crop_id", "crop_cycle_uuid", "planter_uuid")),
    CROP_WATER("crop_water", List.of("plot_key", "crop_id", "crop_cycle_uuid", "hydration")),
    CROP_HARVEST("crop_harvest", List.of("plot_key", "crop_id", "crop_cycle_uuid", "planter_uuid"));

    /** The two sources {@code water_container_fill} distinguishes (section 2.1). */
    public static final String SOURCE_WELL = "well";
    public static final String SOURCE_BLOCK = "source_block";

    private final String wireName;
    private final List<String> requiredSubjectFields;

    QuestAction(String wireName, List<String> requiredSubjectFields) {
        this.wireName = wireName;
        this.requiredSubjectFields = requiredSubjectFields;
    }

    public String wireName() {
        return wireName;
    }

    public List<String> requiredSubjectFields() {
        return requiredSubjectFields;
    }

    /** The action with this wire name, or null. Never throws: the wire is not trusted. */
    public static QuestAction fromWireName(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (QuestAction action : values()) {
            if (action.wireName.equals(normalized)) return action;
        }
        return null;
    }
}
