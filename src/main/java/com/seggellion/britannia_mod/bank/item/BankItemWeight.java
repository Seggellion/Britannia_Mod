package com.seggellion.britannia_mod.bank.item;

import com.seggellion.britannia_mod.item.WeightedCommodityItem;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The single authoritative weight-resolution entry point for banking capacity. Rails' later
 * weight-recalculation service and every future capacity-reservation check should be built
 * against {@link #resolve(ItemStack)} / {@link #resolveTotal(Iterable)}, not against any
 * individual item class.
 *
 * <p>No shared weight interface or registry existed before this slice -- {@link WeightedWoodItem},
 * {@link WeightedFishItem}, and {@link WeightedCommodityItem} are three independent,
 * unrelated classes (two extend {@code Item}, one extends {@code BlockItem}; two declare an
 * instance {@code getWeight(ItemStack)}, one declares a {@code static} one; none share an
 * interface or common superclass -- confirmed by reading all three directly). This class does
 * not change what any of them weighs; it only centralizes *which* source a given stack's
 * weight comes from.
 *
 * <h2>Default weight for unmapped items</h2>
 * Any item with no specific weight mapping resolves to {@link #DEFAULT_UNIT_WEIGHT} (1.0 per
 * unit), not zero. A zero default would mean an unmapped item is free to bank at unlimited
 * count with no capacity cost -- a real, exploitable capacity-integrity gap, not a harmless
 * placeholder. 1.0 is not an arbitrary pick: it is the existing implicit convention already
 * used elsewhere in this codebase whenever no specific weight is defined --
 * {@link WeightedCommodityItem#getWeight(ItemStack)} itself already defaults to {@code 1.0}
 * for a commodity item with no explicit weight tag, and
 * {@code ServerEconomyService.describeSaleItem}/{@code classifyMappedCommodity} both fall back
 * to {@code stack.getCount()} (i.e. 1.0 per unit) for items with a weight-bearing commodity
 * mapping but no dedicated {@code Weighted*} class (grade stones, generic weight-mapped
 * commodities). This class simply makes that same convention the single authoritative default
 * for every item, instead of leaving it re-implemented ad hoc at each call site.
 *
 * <h2>Nested containers (ADR-009)</h2>
 * A shulker box's (or bundle's) resolved weight is <b>contents-inclusive</b>: it is the
 * container's own base per-unit weight (which itself falls through to
 * {@link #DEFAULT_UNIT_WEIGHT}, since no {@code Weighted*} class covers shulker boxes or
 * bundles) <b>plus</b> the recursively resolved weight of everything inside it. This directly
 * follows ADR-009's own framing ("must not treat a nested container as an opaque blob") applied
 * to weight rather than serialization: a shulker box full of heavy items must cost real bank
 * capacity, not the flat cost of one empty box. Treating a full container as weighing the same
 * as an empty one would be exactly the same class of capacity-integrity gap the zero-default
 * decision above is guarding against, just via a different route. Nesting recurses to
 * arbitrary depth (a bundle inside a shulker box inside a bundle, etc.) since each contained
 * stack's weight is resolved through this same {@link #resolve(ItemStack)} entry point.
 *
 * <h2>Guards</h2>
 * A per-unit weight source that produces a negative, {@code NaN}, or infinite value (a
 * corrupt or adversarially-crafted custom-data tag, for example) is treated identically to an
 * unmapped item and clamped to {@link #DEFAULT_UNIT_WEIGHT} -- never to zero. Falling back to
 * zero for bad data would let corrupt or adversarial weight data bank for free, the same
 * integrity concern the default-weight decision above exists to prevent. The final result
 * (after recursion and count multiplication) is guarded the same way as a last line of
 * defense against summation/overflow edge cases.
 */
public final class BankItemWeight {
    /**
     * The weight, in stones, of a single unit of any item with no more specific weight
     * source. See the class documentation for why this is 1.0 and not 0.0.
     */
    public static final double DEFAULT_UNIT_WEIGHT = 1.0;

    private BankItemWeight() {
    }

    /**
     * Resolves the total weight of {@code stack}, including its full count and (per ADR-009)
     * the recursively resolved weight of any nested container contents. Never negative, NaN,
     * or infinite for any real ItemStack; an empty/null stack resolves to {@code 0.0} (weight
     * of nothing is legitimately nothing -- unlike {@link BankItemCodec#serialize}, an empty
     * stack here is not a programmer-error precondition, just a stack with no weight).
     */
    public static double resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0;
        }
        double perUnit = sanitizePerUnit(perUnitWeight(stack));
        return sanitizeFinal(perUnit * stack.getCount());
    }

    /**
     * Resolves the combined weight of an arbitrary collection of stacks -- what a whole
     * deposit/withdrawal transfer will eventually need to check against remaining bank
     * capacity, rather than one item at a time.
     */
    public static double resolveTotal(Iterable<ItemStack> stacks) {
        double total = 0.0;
        for (ItemStack stack : stacks) {
            total += resolve(stack);
        }
        return sanitizeFinal(total);
    }

    /**
     * The weight of one unit of {@code stack} (not multiplied by count), before the final
     * sanitization pass: dispatches to whichever existing {@code Weighted*} class already
     * covers this item, adds recursively resolved nested-container contents on top, or falls
     * back to {@link #DEFAULT_UNIT_WEIGHT} for anything unmapped.
     */
    private static double perUnitWeight(ItemStack stack) {
        Item item = stack.getItem();
        double base;
        if (item instanceof WeightedWoodItem woodItem) {
            base = woodItem.getWeight(stack);
        } else if (item instanceof WeightedFishItem fishItem) {
            base = fishItem.getWeight(stack);
        } else if (item instanceof WeightedCommodityItem) {
            base = WeightedCommodityItem.getWeight(stack);
        } else {
            base = DEFAULT_UNIT_WEIGHT;
        }
        base = sanitizePerUnit(base);

        return base + resolveContainerContentsWeight(stack);
    }

    /**
     * The recursively resolved weight of everything inside {@code stack}, per ADR-009 --
     * {@code 0.0} if {@code stack} is not a container/bundle or carries no contents.
     */
    private static double resolveContainerContentsWeight(ItemStack stack) {
        double total = 0.0;

        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            for (ItemStack contained : container.stream().toList()) {
                total += resolve(contained);
            }
        }

        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack contained : bundle.items()) {
                total += resolve(contained);
            }
        }

        return total;
    }

    private static double sanitizePerUnit(double value) {
        return isUsable(value) ? value : DEFAULT_UNIT_WEIGHT;
    }

    private static double sanitizeFinal(double value) {
        return isUsable(value) ? value : DEFAULT_UNIT_WEIGHT;
    }

    private static boolean isUsable(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0.0;
    }
}
