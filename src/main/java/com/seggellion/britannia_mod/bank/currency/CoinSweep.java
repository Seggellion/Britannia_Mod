package com.seggellion.britannia_mod.bank.currency;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Milestone 6b: what Deposit All Coins actually deposits.
 *
 * <p>Sweeps a player's main inventory and hotbar, totals the bare coin stacks by denomination,
 * and remembers exactly which slots those totals came from so the same slots -- and nothing else
 * -- can be removed later.
 *
 * <h2>What counts as a coin</h2>
 * {@link CurrencyItemRegistry#currencyKeyOf(ItemStack)}, and nothing else. That classifier is
 * top-level item identity only, which is exactly the polarity this needs (design §11.3, Playbook
 * Milestone 6):
 *
 * <ul>
 *   <li>a bare gold/silver/copper stack is swept;</li>
 *   <li>an ordinary item is not;</li>
 *   <li>a bank cheque is not -- it is not one of the three coin items, so it never matches, and
 *       Deposit All Coins never redeems anything;</li>
 *   <li>a shulker box <i>containing</i> coins is not, because the box is not a coin stack. The
 *       classifier's own docs call this out; Milestone 0 §3.6 confirmed it against the code
 *       rather than assuming it.</li>
 * </ul>
 *
 * <h2>Scope: main inventory and hotbar only</h2>
 * Slots {@code 0..35}. Armor and offhand are excluded, matching design §9.4's exclusion of them
 * from the Bank Box grids -- a player wearing a coin is not a case worth inventing, and the
 * offhand is deliberately not a landing target for withdrawal either (see
 * {@code BankingWithdrawalProxyService#hasSufficientCapacity}).
 *
 * <h2>Why the slots are remembered, not just the totals</h2>
 * The protocol destroys the coins <em>after</em> Rails has prepared the operation
 * (docs/banking_bulk_currency_deposit.md, step 4), so there is a window between counting and
 * removing. Recording {@code (slot, item, count)} lets the removal step re-check each slot still
 * holds exactly what was counted, and skip -- and fail -- rather than remove something else that
 * moved in. Totals alone could only say "take 250 copper from somewhere", which is how a sweep
 * ends up eating a stack the player moved in during the round trip.
 *
 * <p>Pure with respect to the world: sweeping mutates nothing. Removal is a separate, explicit
 * step.
 */
public final class CoinSweep {

    /** Main inventory and hotbar. Deliberately excludes armor and offhand -- see the class docs. */
    public static final int SWEPT_SLOT_COUNT = 36;

    private final List<SweptStack> stacks;
    private final int gold;
    private final int silver;
    private final int copper;

    private CoinSweep(List<SweptStack> stacks, int gold, int silver, int copper) {
        this.stacks = List.copyOf(stacks);
        this.gold = gold;
        this.silver = silver;
        this.copper = copper;
    }

    /** One counted stack, and where it was. */
    public record SweptStack(int slotIndex, String currencyKey, int count) {
        public SweptStack {
            Objects.requireNonNull(currencyKey, "currencyKey");
            if (slotIndex < 0) throw new IllegalArgumentException("slotIndex must not be negative");
            if (count <= 0) throw new IllegalArgumentException("count must be positive");
        }
    }

    /**
     * Counts every bare coin stack in the main inventory and hotbar. Mutates nothing.
     *
     * <p>Totals are accumulated as {@code long} and clamped to {@link Integer#MAX_VALUE}, which is
     * the ceiling Rails validates against and the exact capacity of the int32 columns the amounts
     * are stored in and added to. Thirty-six slots of a stack-99 coin cannot reach that in
     * ordinary play, but the arithmetic must not be the thing that overflows silently if a
     * modified server hands us a stack of two billion -- a clamped total is rejected honestly by
     * Rails as {@code BALANCE_CAPACITY_EXCEEDED} or accepted at its true ceiling, where a wrapped
     * negative would be neither.
     */
    public static CoinSweep of(Inventory inventory) {
        Objects.requireNonNull(inventory, "inventory");

        List<SweptStack> found = new ArrayList<>();
        long gold = 0L;
        long silver = 0L;
        long copper = 0L;

        int limit = Math.min(SWEPT_SLOT_COUNT, inventory.getContainerSize());
        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = inventory.getItem(slot);
            String key = CurrencyItemRegistry.currencyKeyOf(stack).orElse(null);
            if (key == null) continue;

            int count = stack.getCount();
            if (count <= 0) continue;

            found.add(new SweptStack(slot, key, count));
            switch (key) {
                case CurrencyItemRegistry.GOLD_KEY -> gold += count;
                case CurrencyItemRegistry.SILVER_KEY -> silver += count;
                case CurrencyItemRegistry.COPPER_KEY -> copper += count;
                default -> throw new IllegalStateException("unsupported currency key: " + key);
            }
        }

        return new CoinSweep(found, clamp(gold), clamp(silver), clamp(copper));
    }

    private static int clamp(long total) {
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    public int gold() {
        return gold;
    }

    public int silver() {
        return silver;
    }

    public int copper() {
        return copper;
    }

    /** The counted stacks, in ascending slot order. Immutable. */
    public List<SweptStack> stacks() {
        return stacks;
    }

    /**
     * True when there is nothing to deposit.
     *
     * <p>Checked against the totals rather than the stack list so the two can never disagree --
     * a stack counted with a zero total is already impossible, and this is the predicate that
     * decides whether a request is sent at all.
     */
    public boolean isEmpty() {
        return gold == 0 && silver == 0 && copper == 0;
    }

    /**
     * The sweep's total value in copper, for the local receipt's diagnostic amount only.
     *
     * <p>{@code long} because three clamped int32 totals converted to copper genuinely exceed
     * int32. Never sent to Rails, and never read back to reconstruct the operation -- see
     * {@link com.seggellion.britannia_mod.bank.transfer.BankTransferOperationType#BULK_CURRENCY_DEPOSIT}.
     */
    public long totalCopperValue() {
        return ((long) gold * CoinValues.COPPER_PER_GOLD)
                + ((long) silver * CoinValues.COPPER_PER_SILVER)
                + copper;
    }

    /** The canonical ratios, matching Rails' {@code BankCheque::COPPER_PER_GOLD}/{@code _SILVER}. */
    private static final class CoinValues {
        private static final long COPPER_PER_GOLD = 10_000L;
        private static final long COPPER_PER_SILVER = 100L;

        private CoinValues() {
        }
    }
}
