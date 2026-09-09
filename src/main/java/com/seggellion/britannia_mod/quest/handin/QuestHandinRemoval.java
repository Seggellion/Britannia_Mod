package com.seggellion.britannia_mod.quest.handin;

import java.util.Locale;
import java.util.Objects;

/**
 * One concrete entry of a removal proof: what this server actually took, joined to the requirement
 * it answers by {@code requirementIndex} (protocol section 1.5.3, "Removal proof").
 *
 * <p>This is the same object twice over, deliberately. Before the mutation it is the <i>plan</i> --
 * the exact item and count a requirement resolved to. After the mutation it is the <i>evidence</i>,
 * persisted verbatim and replayed on every retry. Keeping one type for both is what makes "the
 * proof describes what the mod actually removed" a property of the code rather than a convention:
 * there is no second place a different answer could be computed.
 *
 * <p>A resolver's entry echoes the resolver and the pinned flag value it was answering. Rails
 * checks those against the requirement as <b>persisted</b>, so a hand-in cannot be answered with a
 * crop the player was never awarded. It does not check the concrete item -- not knowing that
 * mapping is why the resolver shape exists -- which is exactly why this type is the only thing
 * allowed to name it, and why it is never recomputed once stored.
 */
public record QuestHandinRemoval(int requirementIndex, String itemId, int count,
                                 String resolver, String flagValue) {

    public QuestHandinRemoval {
        if (requirementIndex < 0 || requirementIndex >= QuestHandinRequirement.MAX_REQUIREMENTS) {
            throw new IllegalArgumentException("requirement_index must be 0.."
                    + (QuestHandinRequirement.MAX_REQUIREMENTS - 1));
        }
        if (count < 1 || count > QuestHandinRequirement.MAX_COUNT) {
            throw new IllegalArgumentException("count must be 1.." + QuestHandinRequirement.MAX_COUNT);
        }
        itemId = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
        if (!QuestHandinRequirement.ITEM_ID_PATTERN.matcher(itemId).matches()) {
            throw new IllegalArgumentException("item must be a namespaced item id");
        }
        // Both or neither: a half-described resolver answer is one Rails refuses as
        // evidence_mismatch after the item is already gone.
        boolean hasResolver = resolver != null && !resolver.isBlank();
        boolean hasFlagValue = flagValue != null && !flagValue.isBlank();
        if (hasResolver != hasFlagValue) {
            throw new IllegalArgumentException("a resolver entry carries both resolver and flag_value");
        }
        resolver = hasResolver ? resolver.trim() : null;
        flagValue = hasFlagValue ? flagValue.trim() : null;
    }

    /** A literal requirement's answer: the item it named, and never dressed up as a resolver. */
    public static QuestHandinRemoval literal(int requirementIndex, String itemId, int count) {
        return new QuestHandinRemoval(requirementIndex, itemId, count, null, null);
    }

    /** A resolver requirement's answer, echoing what it was answering. */
    public static QuestHandinRemoval resolved(int requirementIndex, String itemId, int count,
                                              String resolver, String flagValue) {
        return new QuestHandinRemoval(requirementIndex, itemId, count,
                Objects.requireNonNull(resolver, "resolver"),
                Objects.requireNonNull(flagValue, "flagValue"));
    }

    public boolean answersResolver() {
        return resolver != null;
    }
}
