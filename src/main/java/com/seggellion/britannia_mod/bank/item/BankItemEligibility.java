package com.seggellion.britannia_mod.bank.item;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.Set;

/**
 * Item-eligibility policy for banking. Three carve-outs exist today, each documented and
 * approved as its own ADR in {@code docs/ultimacraft_banking_service_npc_compatibility_map.md}:
 *
 * <ul>
 *   <li>Currency (gold/silver/copper coins) -- {@link #isCurrency}. Section A.6 gives currency
 *       its own balance protocol; the complete, real currency item set today, confirmed by
 *       reading {@link ItemRegistry} and this mod's own denomination handling in
 *       {@code MerchantEconomyService}/{@code ServerEconomyService} (both enumerate exactly
 *       these three, nothing else), is gold, silver, and copper coins. No fourth denomination
 *       and no bank-cheque item exist yet.</li>
 *   <li>Quest-bound items -- {@link #isQuestBound} (ADR-013, revising ADR-010's original "no
 *       restriction" finding). {@code QuestCleanupService}'s cleanup mechanic only scans a
 *       player's live inventory, so banking a quest item would let it silently escape that
 *       cleanup. Detected via the existing informal marker {@code QuestRewardService} already
 *       stamps on every quest reward item's {@code CUSTOM_DATA} -- the {@code quest_item}
 *       string key, always written unconditionally by {@code QuestRewardService#stamp}
 *       alongside (but a strict superset of) the other, conditional quest tags. No new formal
 *       item type or component is introduced; this reads the same ad hoc convention
 *       {@code QuestCleanupService} itself already relies on.</li>
 *   <li>Unsupported mod origin -- {@link #isFromSupportedOrigin} (ADR-014). An allowlist, not a
 *       denylist, keyed by the item's own registry namespace: only {@code minecraft} and this
 *       mod's own namespace ({@link BritanniaMod#MODID}) are permitted. Deliberately checks
 *       only the item's own registry key, not any component attached to it (a foreign
 *       enchantment on an otherwise-eligible item is not detected by this pass -- an explicit,
 *       documented boundary of ADR-014, not a silent gap).</li>
 * </ul>
 *
 * All three checks recurse through nested container contents (shulker boxes, bundles) the same
 * way and to the same depth bound as {@link BankItemNesting#containerNestingDepthOf} -- an
 * ineligible item hidden inside an otherwise-bankable container must not slip through container
 * recursion just because the outer stack itself is eligible.
 *
 * <h2>Check order</h2>
 * {@link #checkEligible} checks currency, then quest-bound, then unsupported origin, and
 * reports the first violation found -- a deliberate order, not an accident of implementation
 * sequence:
 * <ol>
 *   <li>Currency first: a currency item silently entering generic item banking actively
 *       bypasses a whole separate accounting protocol (the account's real balance columns),
 *       which is a more severe failure mode than the other two categories.</li>
 *   <li>Quest-bound second: a real gameplay-integrity concern (breaking quest cleanup), but
 *       narrower in consequence than a currency/accounting bypass.</li>
 *   <li>Unsupported origin last: the most general, catch-all category of the three -- it is
 *       what remains ineligible once the two more specific, more severe categories above have
 *       already been ruled out.</li>
 * </ol>
 * This also matches the order these three checks were actually introduced (currency, then
 * quest-bound and unsupported-origin together, in that listed order), so the code's behavior
 * matches its own history rather than an arbitrary independent choice.
 */
public final class BankItemEligibility {
    private static final String QUEST_ITEM_KEY = "quest_item";
    private static final Set<String> SUPPORTED_NAMESPACES = Set.of("minecraft", BritanniaMod.MODID);

    public enum IneligibilityReason {
        CURRENCY_MUST_USE_BALANCE_PROTOCOL,
        QUEST_BOUND,
        UNSUPPORTED_ORIGIN
    }

    /** Carries a typed {@link IneligibilityReason} a caller can branch on, not just a message. */
    public static final class IneligibleItemException extends IllegalArgumentException {
        private final IneligibilityReason reason;

        public IneligibleItemException(IneligibilityReason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public IneligibilityReason reason() {
            return reason;
        }
    }

    private BankItemEligibility() {
    }

    /**
     * Throws {@link IneligibleItemException} for the first ineligibility reason found, checked
     * in the order documented on this class: currency, then quest-bound, then unsupported
     * origin. Each check considers {@code stack} and everything nested inside it.
     */
    public static void checkEligible(ItemStack stack) {
        if (isCurrency(stack)) {
            throw new IneligibleItemException(
                IneligibilityReason.CURRENCY_MUST_USE_BALANCE_PROTOCOL,
                "Currency items (gold/silver/copper coins) must use the balance protocol, not generic item banking"
            );
        }
        if (isQuestBound(stack)) {
            throw new IneligibleItemException(
                IneligibilityReason.QUEST_BOUND,
                "Quest-bound items are not bankable -- banking would let them escape QuestCleanupService's inventory-scan cleanup"
            );
        }
        if (!isFromSupportedOrigin(stack)) {
            throw new IneligibleItemException(
                IneligibilityReason.UNSUPPORTED_ORIGIN,
                "Items from unrecognized mod origins are not bankable -- only minecraft and " + BritanniaMod.MODID + " are supported"
            );
        }
    }

    /**
     * True if {@code stack} is itself a currency coin, or has one nested anywhere inside it.
     * Bounded the same way {@link BankItemNesting#containerNestingDepthOf} is (never descends
     * more than {@link BankItemNesting#MAX_DEPTH} + 1 levels) so this is always safe to call on
     * any in-memory ItemStack, including one built specifically to be very deep.
     */
    public static boolean isCurrency(ItemStack stack) {
        return anyNested(stack, s -> isCurrencyItem(s.getItem()));
    }

    /**
     * True if {@code stack}, or anything nested inside it, carries the {@code quest_item}
     * {@code CUSTOM_DATA} marker {@code QuestRewardService} stamps on every quest reward item.
     */
    public static boolean isQuestBound(ItemStack stack) {
        return anyNested(stack, BankItemEligibility::hasQuestItemMarker);
    }

    /**
     * True if {@code stack}'s own registry namespace, and that of everything nested inside it,
     * is on the supported-origin allowlist ({@code minecraft}, {@link BritanniaMod#MODID}).
     * Deliberately does not inspect any component attached to the item -- see this class's own
     * documentation for why that is an explicit, accepted boundary, not an oversight.
     */
    public static boolean isFromSupportedOrigin(ItemStack stack) {
        return !anyNested(stack, s -> !isSupportedOriginItem(s.getItem()));
    }

    private static boolean anyNested(ItemStack stack, java.util.function.Predicate<ItemStack> predicate) {
        return anyNested(stack, predicate, 1);
    }

    private static boolean anyNested(ItemStack stack, java.util.function.Predicate<ItemStack> predicate, int depth) {
        if (depth > BankItemNesting.MAX_DEPTH + 1) {
            return false;
        }
        if (predicate.test(stack)) {
            return true;
        }

        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            for (ItemStack contained : container.stream().toList()) {
                if (anyNested(contained, predicate, depth + 1)) return true;
            }
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack contained : bundle.items()) {
                if (anyNested(contained, predicate, depth + 1)) return true;
            }
        }
        return false;
    }

    private static boolean isCurrencyItem(Item item) {
        return item == ItemRegistry.GOLD_COIN.get()
            || item == ItemRegistry.SILVER_COIN.get()
            || item == ItemRegistry.COPPER_COIN.get();
    }

    private static boolean hasQuestItemMarker(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        CompoundTag tag = customData.copyTag();
        return tag.contains(QUEST_ITEM_KEY, Tag.TAG_STRING) && !tag.getString(QUEST_ITEM_KEY).isBlank();
    }

    private static boolean isSupportedOriginItem(Item item) {
        return isSupportedOriginKey(BuiltInRegistries.ITEM.getKey(item));
    }

    /**
     * The actual allowlist-membership check, exposed publicly specifically so tests can exercise
     * it directly against a synthetic {@link ResourceLocation} -- this codebase has no other
     * mod's item loaded in its test environment to construct a real foreign-origin ItemStack
     * from, and the item registry is frozen by the time any test runs, so a new cross-namespace
     * item cannot be registered on the fly either. Testing the real allowlist logic directly
     * against a fabricated key is the closest available proof of the actual rejection rule.
     * Returns {@code false} for a {@code null} key (an item with no registry entry at all).
     */
    public static boolean isSupportedOriginKey(ResourceLocation key) {
        return key != null && SUPPORTED_NAMESPACES.contains(key.getNamespace());
    }
}
