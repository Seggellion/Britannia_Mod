package com.seggellion.britannia_mod.bank.currency;

import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Milestone 10 NeoForge Slice 1: the server-side Currency Item Registry the playbook names
 * directly ("Add a server-side Currency Item Registry mapping only the existing approved coin
 * item types to gold, silver, and copper") -- the single authoritative mapping between this
 * mod's three coin {@link Item}s and the stable wire keys Rails'
 * {@code BankTransferOperations::CurrencyPayloadValidator} accepts
 * (docs/banking_currency_transfer.md: exactly {@code gold}/{@code silver}/{@code copper},
 * nothing else).
 *
 * <p>The complete, only coin set is {@link ItemRegistry#GOLD_COIN}/{@link
 * ItemRegistry#SILVER_COIN}/{@link ItemRegistry#COPPER_COIN} -- confirmed twice against the
 * real registry (Milestone 8 recon, re-confirmed Milestone 10 recon): plain {@code Item}
 * instances, {@code stacksTo(99)}, no data components, no fourth denomination, no cheque item.
 * This class deliberately resolves the {@code DeferredHolder}s at call time (the same direct
 * {@code item == ItemRegistry.X.get()} comparison {@code MerchantEconomyService} and {@code
 * ServerEconomyService} both already use) rather than building a static map at class-load,
 * which would race registry initialization.
 *
 * <h2>Top-level item identity only, deliberately</h2>
 * {@link #currencyKeyOf(ItemStack)} classifies by the stack's own item -- never by nested
 * contents. This is the opposite polarity of {@link
 * com.seggellion.britannia_mod.bank.item.BankItemEligibility#isCurrency}, which answers a
 * different question ("is there currency anywhere in this structure, including nested inside a
 * container?") for a different purpose (blocking the generic item path). A shulker box
 * containing coins is NOT a coin stack: it must never route to the currency protocol (its
 * non-coin wrapper cannot become a balance), and it stays correctly rejected by the item
 * path's own {@code CURRENCY} eligibility carve-out -- coins cannot be smuggled into item
 * banking inside a container either. Only a bare coin stack routes to currency.
 *
 * <p>Coin identity is item-only, matching this mod's own economic precedent exactly: {@code
 * MerchantEconomyService}/{@code ServerEconomyService} count and charge coins purely by {@code
 * stack.getItem()} comparison, never by component equality -- a renamed coin spends like any
 * other. Deposit revalidation therefore also matches on item + count (the playbook's own
 * "revalidate the exact slot/item/count"), not full component identity.
 */
public final class CurrencyItemRegistry {
    public static final String GOLD_KEY = "gold";
    public static final String SILVER_KEY = "silver";
    public static final String COPPER_KEY = "copper";

    private CurrencyItemRegistry() {
    }

    /**
     * The stable wire key for {@code stack}'s own item, or empty if the stack is not a bare
     * coin stack (empty stacks included). See the class docs for why nested contents are
     * deliberately not considered.
     */
    public static Optional<String> currencyKeyOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        return currencyKeyOf(stack.getItem());
    }

    public static Optional<String> currencyKeyOf(Item item) {
        if (item == ItemRegistry.GOLD_COIN.get()) return Optional.of(GOLD_KEY);
        if (item == ItemRegistry.SILVER_COIN.get()) return Optional.of(SILVER_KEY);
        if (item == ItemRegistry.COPPER_COIN.get()) return Optional.of(COPPER_KEY);
        return Optional.empty();
    }

    /** True exactly when {@link #currencyKeyOf(ItemStack)} would return a key. */
    public static boolean isCurrencyStack(ItemStack stack) {
        return currencyKeyOf(stack).isPresent();
    }

    /**
     * The reverse of {@link #currencyKeyOf(Item)} -- Milestone 10 NeoForge Slice 2 (currency
     * withdrawal): given the wire key a withdrawal request already carries, resolve which coin
     * {@link Item} to actually construct and insert. Empty for any key not exactly {@code
     * gold}/{@code silver}/{@code copper}.
     */
    public static Optional<Item> itemForKey(String currencyKey) {
        if (GOLD_KEY.equals(currencyKey)) return Optional.of(ItemRegistry.GOLD_COIN.get());
        if (SILVER_KEY.equals(currencyKey)) return Optional.of(ItemRegistry.SILVER_COIN.get());
        if (COPPER_KEY.equals(currencyKey)) return Optional.of(ItemRegistry.COPPER_COIN.get());
        return Optional.empty();
    }
}
