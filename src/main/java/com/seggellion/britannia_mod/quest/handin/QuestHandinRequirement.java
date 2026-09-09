package com.seggellion.britannia_mod.quest.handin;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One entry of a hand-in's {@code requires} list, exactly as Rails published it (protocol section
 * 1.5.2).
 *
 * <p>There are two shapes and no third. A <b>literal</b> requirement names the item itself. A
 * <b>resolver</b> requirement carries a question -- which resolver, which flag, and the value that
 * flag held when the hand-in was <i>prepared</i> -- because Rails deliberately does not know the
 * answer: the crop-to-produce mapping lives in this mod's {@code CropRegistry}, and treating a
 * seed as its produce is precisely the mistake the shape avoids.
 *
 * <p>Modelled as a sealed interface rather than one record with nullable halves so that "an entry
 * carrying both an item and a resolver" is unrepresentable here, the same way Rails' validator
 * refuses it on the way out. Every field is bounds-checked in the compact constructor, so a
 * requirement that exists at all is one this mod is willing to act on; anything else throws before
 * it can reach an inventory.
 */
public sealed interface QuestHandinRequirement
        permits QuestHandinRequirement.Literal, QuestHandinRequirement.Resolver {

    /**
     * Rails' own bound: {@code requires} carries 1..8 entries, so a requirement index is 0..7.
     * Mirrored here because the removal proof is joined to the requirement by this index, and an
     * index Rails would refuse is one no proof could ever be matched against.
     */
    int MAX_REQUIREMENTS = 8;

    /** {@code ResultContract::MAX_COUNT}. A count outside this is refused, never clamped. */
    int MAX_COUNT = 1024;

    /**
     * {@code ResultContract::ITEM_ID_PATTERN}, character for character. An id this rejects would be
     * refused by Rails after the item had already been taken, so it is refused here first.
     */
    Pattern ITEM_ID_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** The only resolver this contract version defines (protocol section 1.5.2). */
    String RESOLVER_AWARDED_CROP_HARVEST_ITEM = "awarded_crop_harvest_item";

    /** Position in the hand-in's {@code requires} list; the join key for the removal proof. */
    int index();

    /** How many units this requirement asks for. Matched exactly, never "at least". */
    int count();

    /** A requirement naming its own item. */
    record Literal(int index, String itemId, int count) implements QuestHandinRequirement {
        public Literal {
            checkIndex(index);
            checkCount(count);
            itemId = normaliseItemId(itemId);
        }
    }

    /**
     * A requirement Rails carried as a question. {@code flagValue} is the value pinned at prepare
     * time -- not read live -- so a flag rewritten since cannot move the goalposts under a shard
     * that has already removed something. Rails refuses to prepare a resolver requirement whose
     * flag was never resolved, so a blank value here is a malformed publication and is rejected.
     */
    record Resolver(int index, String resolver, String flag, String flagValue, int count)
            implements QuestHandinRequirement {
        public Resolver {
            checkIndex(index);
            checkCount(count);
            resolver = requireToken(resolver, "resolver");
            flag = requireToken(flag, "flag");
            flagValue = requireToken(flagValue, "flag_value");
        }
    }

    private static void checkIndex(int index) {
        if (index < 0 || index >= MAX_REQUIREMENTS) {
            throw new IllegalArgumentException("requirement index must be 0.." + (MAX_REQUIREMENTS - 1));
        }
    }

    private static void checkCount(int count) {
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("requirement count must be 1.." + MAX_COUNT);
        }
    }

    private static String normaliseItemId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!ITEM_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("requirement item must be a namespaced item id");
        }
        return value;
    }

    /**
     * A short opaque token from Rails -- a resolver name, a flag name, a flag's value.
     *
     * <p>Quotes, backslashes and control characters are refused rather than escaped. These values
     * are written straight into the confirmation body, which is assembled as text so it can be
     * byte-compared against the frozen fixture; refusing the three characters that could break out
     * of a JSON string is what makes that assembly safe by construction rather than by trusting the
     * far side. A real crop id has never contained one.
     */
    private static String requireToken(String raw, String field) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > 255) {
            throw new IllegalArgumentException(field + " must be a non-empty token");
        }
        if (value.chars().anyMatch(c -> c == '"' || c == '\\' || Character.isISOControl(c))) {
            throw new IllegalArgumentException(field + " must not carry quotes, backslashes or control characters");
        }
        return value;
    }
}
