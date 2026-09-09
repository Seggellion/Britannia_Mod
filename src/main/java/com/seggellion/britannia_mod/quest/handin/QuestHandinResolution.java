package com.seggellion.britannia_mod.quest.handin;

import java.util.List;

/**
 * What a hand-in's requirements turned into, before anything is taken from anyone.
 *
 * <p>Two outcomes and no third, because the alternative -- a partially resolved plan -- is the
 * shape that loses a player's items. Either every requirement named a concrete item and count this
 * mod can act on, or the whole demand is refused with a reason and no inventory is touched.
 */
public sealed interface QuestHandinResolution
        permits QuestHandinResolution.Resolved, QuestHandinResolution.Refused {

    /** Every requirement answered, in requirement order. This is both the plan and the proof. */
    record Resolved(List<QuestHandinRemoval> plan) implements QuestHandinResolution {
        public Resolved {
            plan = List.copyOf(plan);
            if (plan.isEmpty()) throw new IllegalArgumentException("a resolution answers at least one requirement");
        }
    }

    /**
     * The demand cannot be acted on at all. {@code reason} is a short non-secret code for the log
     * and for the player-facing "unable to complete" notice; it never names another player's data.
     */
    record Refused(String reason) implements QuestHandinResolution {
        public Refused {
            reason = reason == null || reason.isBlank() ? "unresolvable_requirement" : reason;
        }
    }

    /** Refusal reasons. Kept as constants so tests and log analysis agree on the vocabulary. */
    String UNKNOWN_RESOLVER = "unknown_resolver";
    String UNKNOWN_CROP = "unknown_crop";
    String CROP_HAS_NO_HARVEST_ITEM = "crop_has_no_harvest_item";
    String UNKNOWN_ITEM = "unknown_item";
    String REGISTRY_UNAVAILABLE = "registry_unavailable";
    String MALFORMED_REQUIREMENT = "malformed_requirement";
}
