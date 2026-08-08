package com.seggellion.britannia_mod.bank.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Pattern;

/**
 * What a banked item <em>is</em>, in the three forms an administrator or a player needs: the name
 * the player sees, the registry id, and how many. Milestone 17.
 *
 * <p>This is never read back out of the stored payload. It is resolved from the live
 * {@link ItemStack} at deposit time and travels as its own keys beside the payload, because the
 * payload is opaque to Rails by design -- that opacity is what keeps Rails independent of
 * Minecraft's serialization format and its version churn. Nothing here decodes anything.
 *
 * <h2>Every field is bounded here, before it is ever sent</h2>
 *
 * Rails validates all three and rejects what does not fit. A rejected deposit is a failed deposit,
 * so the client's job is to never construct a request Rails will refuse: the acceptance bar for
 * this milestone is that an over-length name is truncated <em>client-side</em>, never bounced back
 * from Rails. The bounds below therefore mirror
 * {@code BankTransferOperations::PayloadValidator}'s exactly, and are the same constants named in
 * {@code docs/banking_item_transfer.md}, which is the contract's one home.
 *
 * <p>Any field that cannot be resolved into something Rails will certainly accept is returned as
 * {@code null} and then omitted from the envelope entirely. The three are independently optional
 * precisely so that a name this code cannot make safe does not also cost the count.
 */
public record BankItemIdentity(String displayName, String itemKey, Integer count) {

    /** Characters, not UTF-16 units -- Rails counts Unicode characters, so this must too. */
    public static final int MAX_DISPLAY_NAME_LENGTH = 255;

    public static final int MAX_ITEM_KEY_LENGTH = 255;

    public static final int MAX_COUNT = 32_767;

    /** C0 and C1 control characters, DEL included. Rails rejects any name containing one. */
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\u0000-\\u001F\\u007F-\\u009F]");

    /** Kept character-for-character in step with Rails' own ITEM_KEY_FORMAT. */
    private static final Pattern ITEM_KEY_FORMAT = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_.\\-/]+");

    /** An identity with nothing resolvable in it. Every field omitted; the deposit still works. */
    public static final BankItemIdentity EMPTY = new BankItemIdentity(null, null, null);

    /**
     * Resolves identity from the exact stack a deposit captured. Callers must pass the same
     * snapshot the payload, fingerprint, and weight were computed from -- {@code count} is only
     * meaningful if it agrees with what the weight was calculated against, and if those two can
     * ever disagree the deposit itself is wrong and belongs in reconciliation, not in a display
     * field.
     */
    public static BankItemIdentity resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return EMPTY;
        }
        return new BankItemIdentity(resolveDisplayName(stack), resolveItemKey(stack), resolveCount(stack));
    }

    /** True when there is nothing worth sending, so the envelope stays exactly as v1 built it. */
    public boolean isEmpty() {
        return displayName == null && itemKey == null && count == null;
    }

    /**
     * The name as the player sees it in their own inventory, including any custom name component,
     * stripped of control characters and truncated to a length Rails will accept.
     *
     * <p>Control characters are removed rather than causing the whole name to be dropped: a name
     * is a courtesy to whoever reads it later, and a stray character is no reason to leave an
     * administrator looking at "Unnamed item". Truncation is silent for the same reason -- the
     * alternative is refusing a deposit over a cosmetic field, which would be a far worse trade.
     */
    private static String resolveDisplayName(ItemStack stack) {
        String raw;
        try {
            raw = stack.getHoverName().getString();
        } catch (RuntimeException resolutionFailed) {
            // A malformed custom-name component is not a reason to fail a deposit.
            return null;
        }
        if (raw == null) {
            return null;
        }

        String cleaned = CONTROL_CHARACTERS.matcher(raw).replaceAll("").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return truncateToCodePoints(cleaned, MAX_DISPLAY_NAME_LENGTH);
    }

    /**
     * Truncates on a codepoint boundary. {@link String#length()} counts UTF-16 units, so cutting
     * there could split a surrogate pair and produce a string that is not valid UTF-8 at all --
     * which Rails rejects outright, turning a cosmetic overflow into a failed deposit.
     */
    private static String truncateToCodePoints(String value, int maxCodePoints) {
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    /**
     * The item's registry id. A {@link ResourceLocation} is already constrained to the shape Rails
     * validates, but it is checked anyway rather than assumed: a modded id is third-party data,
     * and the cost of being wrong is a refused deposit.
     */
    private static String resolveItemKey(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) {
            return null;
        }
        String value = key.toString();
        if (value.length() > MAX_ITEM_KEY_LENGTH || !ITEM_KEY_FORMAT.matcher(value).matches()) {
            return null;
        }
        return value;
    }

    /**
     * The stack size actually being deposited. Out-of-range is returned as {@code null} rather
     * than clamped -- a wrong number is worse than no number, because a clamped count would read
     * as fact to whoever is investigating a dispute.
     */
    private static Integer resolveCount(ItemStack stack) {
        int count = stack.getCount();
        return count >= 1 && count <= MAX_COUNT ? count : null;
    }
}
